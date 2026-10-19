package edu.itc.se.lab01.challenge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parallel word-frequency analyser (JDK only).
 *
 * <pre>
 *   java edu.itc.se.lab01.challenge.WordCount &lt;dir&gt; [--top N] [--timeout SECONDS] [--virtual] [--workers N]
 * </pre>
 *
 * Pipeline: one scanner thread -> bounded queue (16) -> N workers -> shared
 * {@code ConcurrentHashMap<String, LongAdder>}; a reporter thread prints progress
 * every 500 ms. Ctrl+C and {@code --timeout} both end up in {@link #shutdown()}.
 *
 * Exit codes: 0 = complete, 1 = partial result, 2 = bad usage.
 */
public class WordCount {

    static final int QUEUE_CAPACITY = 16;
    /** Number of worker tasks used in --virtual mode (virtual threads are cheap). */
    static final int VIRTUAL_TASKS = 64;
    private static final long GRACE_MILLIS = 2_000;

    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}]+");
    private static final Path POISON = Path.of("__POISON__");

    private final BlockingQueue<Path> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
    private final AtomicInteger filesDone = new AtomicInteger();
    private final LongAdder wordsDone = new LongAdder();
    /**
     * Terminal-state latch. Whoever flips it (normal completion, timeout, Ctrl+C)
     * owns the final printout; everybody else only waits for that printout.
     */
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final CountDownLatch printed = new CountDownLatch(1);

    private final Path dir;
    private final int workers;
    private final boolean virtual;
    private final int top;
    private final long timeoutSec;
    private final int tasks; // number of worker tasks == number of poison pills

    private volatile ExecutorService pool;
    private volatile Thread scanner;
    private volatile ScheduledExecutorService reporter;
    private volatile Thread hook;

    private volatile long startNanos;
    private volatile long endNanos;
    private volatile int totalFiles;
    private volatile boolean partial;
    private volatile boolean scanFailed;

    // Output sinks and a test-only throttle; package-private so tests can replace them.
    PrintStream out = System.out;
    Path csv = Path.of("result.csv");
    /** Test hook: artificial per-file delay (ms) to simulate slow storage. 0 in production. */
    volatile long fileDelayMs;

    record Ranked(String word, long count) {}

    WordCount(Path dir, int workers, boolean virtual, int top, long timeoutSec) {
        if (workers < 1) throw new IllegalArgumentException("workers must be >= 1");
        if (top < 1) throw new IllegalArgumentException("top must be >= 1");
        if (timeoutSec < 0) throw new IllegalArgumentException("timeout must be >= 0");
        this.dir = dir;
        this.workers = workers;
        this.virtual = virtual;
        this.top = top;
        this.timeoutSec = timeoutSec;
        this.tasks = virtual ? VIRTUAL_TASKS : workers;
    }

    /** Redirects console output and result.csv (used by tests and the benchmark). */
    WordCount output(PrintStream out, Path csv) {
        this.out = out;
        this.csv = csv;
        return this;
    }

    // ------------------------------------------------------------------ run

    void run() throws InterruptedException {
        totalFiles = countFiles();
        startNanos = System.nanoTime();

        pool = virtual
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(tasks, Thread.ofPlatform().name("wc-worker-", 0).factory());
        scanner = Thread.ofPlatform().name("scanner").start(this::scan);
        reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wc-reporter");
            t.setDaemon(true);
            return t;
        });
        reporter.scheduleAtFixedRate(this::report, 500, 500, TimeUnit.MILLISECONDS);
        hook = new Thread(this::onJvmShutdown, "shutdown-hook");
        Runtime.getRuntime().addShutdownHook(hook);

        CountDownLatch allWorkersDone = new CountDownLatch(tasks);
        for (int i = 0; i < tasks; i++) {
            pool.submit(() -> {
                try {
                    work();
                } catch (Throwable t) {
                    System.err.println("worker failed: " + t);
                } finally {
                    allWorkersDone.countDown();
                }
            });
        }

        boolean finished;
        try {
            finished = timeoutSec > 0
                    ? allWorkersDone.await(timeoutSec, TimeUnit.SECONDS)
                    : awaitForever(allWorkersDone);
        } catch (InterruptedException e) {
            shutdown();
            Thread.currentThread().interrupt();
            removeHook();
            return;
        }

        if (finished) {
            finish();
        } else {
            out.printf(Locale.US, "[%.1fs] deadline reached - stopping%n", elapsedSeconds());
            shutdown();
        }
        removeHook();
    }

    private static boolean awaitForever(CountDownLatch latch) throws InterruptedException {
        latch.await();
        return true;
    }

    /** Normal completion: all files were processed by the workers. */
    private void finish() throws InterruptedException {
        if (!stopping.compareAndSet(false, true)) {
            awaitPrinted(); // Ctrl+C won the race; its hook prints the partial result
            return;
        }
        scanner.join();
        reporter.shutdownNow();
        pool.shutdown();
        pool.awaitTermination(GRACE_MILLIS, TimeUnit.MILLISECONDS);
        printResult(scanFailed);
    }

    private int countFiles() {
        try (Stream<Path> s = Files.walk(dir)) {
            return (int) s.filter(Files::isRegularFile).count();
        } catch (IOException | UncheckedIOException e) {
            System.err.println("cannot list " + dir + ": " + e.getMessage());
            return 0;
        }
    }

    // -------------------------------------------------------------- pipeline

    /** Producer: walks the directory; queue.put blocks when 16 paths are waiting (back-pressure). */
    private void scan() {
        boolean interrupted = false;
        try (Stream<Path> s = Files.walk(dir)) {
            Iterator<Path> it = s.filter(Files::isRegularFile).iterator();
            while (it.hasNext()) {
                queue.put(it.next());
            }
        } catch (InterruptedException e) {
            interrupted = true;
            Thread.currentThread().interrupt();
        } catch (IOException | UncheckedIOException e) {
            scanFailed = true;
            System.err.println("scan failed: " + e.getMessage());
        } finally {
            // One poison pill per worker, so every worker terminates even if the scan failed.
            // After an interrupt the workers are interrupted too, so no pills are needed.
            if (!interrupted) {
                try {
                    for (int i = 0; i < tasks; i++) queue.put(POISON);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Consumer: take a path, count its words, repeat until poisoned or interrupted. */
    private void work() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Path p = queue.take();
                if (p == POISON) return;
                long delay = fileDelayMs;
                if (delay > 0) Thread.sleep(delay);
                if (countFile(p)) filesDone.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore the flag for the pool
        }
    }

    /** @return true if the whole file was counted, false if it was abandoned because of an interrupt. */
    private boolean countFile(Path p) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(Files.newInputStream(p), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) return false;
                long n = 0;
                for (String token : NON_LETTERS.split(line)) {
                    if (token.isEmpty()) continue;
                    String w = token.toLowerCase(Locale.ROOT);
                    LongAdder a = counts.get(w); // lock-free fast path for words already seen
                    if (a == null) a = counts.computeIfAbsent(w, k -> new LongAdder());
                    a.increment();
                    n++;
                }
                wordsDone.add(n);
            }
            return true;
        } catch (IOException e) {
            if (Thread.currentThread().isInterrupted()) return false; // ClosedByInterruptException
            System.err.println("skipping unreadable file " + p + ": " + e.getMessage());
            return true;
        }
    }

    // -------------------------------------------------------------- progress

    /** Lock-free: LongAdder.sum() reads and a weakly consistent iteration over the map. */
    private void report() {
        if (stopping.get()) return;
        try {
            double sec = elapsedSeconds();
            long words = wordsDone.sum();
            long wps = sec > 0 ? (long) (words / sec) : 0;
            TopK topK = new TopK(3);
            for (Map.Entry<String, LongAdder> e : counts.entrySet()) {
                topK.offer(e.getKey(), e.getValue().sum());
            }
            StringBuilder sb = new StringBuilder("top:");
            for (Ranked r : topK.result()) {
                sb.append(String.format(Locale.US, " %s(%,d)", r.word(), r.count()));
            }
            out.println(String.format(Locale.US, "[%.1fs] files %d/%d  words %,d  %,d w/s  %s",
                    sec, filesDone.get(), totalFiles, words, wps, sb));
        } catch (RuntimeException e) {
            // a failing report must never cancel the schedule
        }
    }

    // ---------------------------------------------------------- cancellation

    /** Runs on the JVM shutdown-hook thread (Ctrl+C / SIGTERM / System.exit). */
    private void onJvmShutdown() {
        if (!stopping.get()) out.println("\nstop requested - stopping");
        shutdown();
    }

    /**
     * Single cancellation path for Ctrl+C and --timeout: interrupt the scanner and
     * workers, wait at most 2 s in total, then print the [PARTIAL] result.
     */
    void shutdown() {
        if (!stopping.compareAndSet(false, true)) {
            awaitPrinted();
            return;
        }
        ScheduledExecutorService rep = reporter;
        Thread sc = scanner;
        ExecutorService p = pool;
        if (rep != null) rep.shutdownNow();
        if (sc != null) sc.interrupt();
        if (p != null) p.shutdownNow(); // interrupts every worker
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(GRACE_MILLIS);
        try {
            if (sc != null) sc.join(remainingMillis(deadline));
            if (p != null) p.awaitTermination(remainingMillis(deadline), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        printResult(true);
    }

    private static long remainingMillis(long deadlineNanos) {
        return Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()));
    }

    private void awaitPrinted() {
        try {
            printed.await(GRACE_MILLIS + 1_000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void removeHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down
        }
    }

    // ---------------------------------------------------------------- result

    void printResult(boolean partial) {
        try {
            this.partial = partial;
            endNanos = System.nanoTime();
            Map<String, Long> snap = snapshot();
            long total = snap.values().stream().mapToLong(Long::longValue).sum();

            TopK topK = new TopK(top);
            snap.forEach(topK::offer);
            List<Ranked> ranked = topK.result();

            out.println(String.format(Locale.US, "%s files=%d words=%,d elapsed=%.2fs mode=%s",
                    partial ? "[PARTIAL]" : "DONE ", filesDone.get(), total,
                    elapsedMillis() / 1000.0, mode()));
            out.println(String.format(Locale.US, " %4s  %-14s%12s  %9s", "rank", "word", "count", "share"));
            StringBuilder csvText = new StringBuilder("rank,word,count,share_percent\n");
            int rank = 1;
            for (Ranked r : ranked) {
                double pct = total > 0 ? 100.0 * r.count() / total : 0;
                out.println(String.format(Locale.US, " %4d  %-14s%,12d  %6.2f %%", rank, r.word(), r.count(), pct));
                csvText.append(String.format(Locale.ROOT, "%d,%s,%d,%.4f%n", rank, r.word(), r.count(), pct)
                        .replace("\r", ""));
                rank++;
            }
            try {
                Files.writeString(csv, csvText, StandardCharsets.UTF_8);
                out.println(csv.getFileName() + " written" + (partial ? " (partial data)" : ""));
            } catch (IOException e) {
                System.err.println("cannot write " + csv + ": " + e.getMessage());
            }
        } finally {
            printed.countDown();
        }
    }

    // ------------------------------------------------------------- accessors

    /** Copy of the current counts as plain longs (used for the result and by the tests). */
    Map<String, Long> snapshot() {
        Map<String, Long> m = new HashMap<>(Math.max(16, counts.size() * 2));
        counts.forEach((w, a) -> m.put(w, a.sum()));
        return m;
    }

    boolean isPartial() { return partial; }
    int filesDone() { return filesDone.get(); }
    long wordsCounted() { return wordsDone.sum(); }

    long elapsedMillis() {
        long end = endNanos != 0 ? endNanos : System.nanoTime();
        return (end - startNanos) / 1_000_000;
    }

    private double elapsedSeconds() { return (System.nanoTime() - startNanos) / 1e9; }

    String mode() { return virtual ? "virtual(" + tasks + ")" : "platform(" + workers + ")"; }

    // --------------------------------------------------------------- helpers

    /** Bounded "best k" selector: a size-k min-heap, O(n log k). Ties are broken alphabetically. */
    private static final class TopK {
        private static final Comparator<Ranked> BEST_FIRST = (a, b) ->
                a.count() != b.count() ? Long.compare(b.count(), a.count()) : a.word().compareTo(b.word());
        private final int k;
        private final PriorityQueue<Ranked> heap; // head = worst of the current best k

        TopK(int k) {
            this.k = k;
            this.heap = new PriorityQueue<>(Math.max(1, k), BEST_FIRST.reversed());
        }

        void offer(String word, long count) {
            Ranked r = new Ranked(word, count);
            if (heap.size() < k) {
                heap.add(r);
            } else if (BEST_FIRST.compare(r, heap.peek()) < 0) {
                heap.poll();
                heap.add(r);
            }
        }

        List<Ranked> result() {
            List<Ranked> list = new ArrayList<>(heap);
            list.sort(BEST_FIRST);
            return list;
        }
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        Path dir = null;
        int top = 10;
        long timeout = 0;
        boolean virtual = false;
        int workers = Runtime.getRuntime().availableProcessors();
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--top" -> top = Integer.parseInt(value(args, ++i));
                    case "--timeout" -> timeout = Long.parseLong(value(args, ++i));
                    case "--workers" -> workers = Integer.parseInt(value(args, ++i));
                    case "--virtual" -> virtual = true;
                    default -> {
                        if (args[i].startsWith("--")) throw new IllegalArgumentException("unknown option " + args[i]);
                        if (dir != null) throw new IllegalArgumentException("only one directory allowed");
                        dir = Path.of(args[i]);
                    }
                }
            }
            if (dir == null) throw new IllegalArgumentException("missing <dir>");
            if (!Files.isDirectory(dir)) throw new IllegalArgumentException("not a directory: " + dir);
            // constructor validates numeric ranges
            WordCount wc = new WordCount(dir, workers, virtual, top, timeout);
            wc.run();
            if (wc.isPartial()) System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println("usage: java edu.itc.se.lab01.challenge.WordCount <dir> "
                    + "[--top N] [--timeout SECONDS] [--virtual] [--workers N]");
            System.exit(2);
        }
    }

    private static String value(String[] args, int i) {
        if (i >= args.length) throw new IllegalArgumentException("missing value for " + args[i - 1]);
        return args[i];
    }
}
