
package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {

    // Count prime numbers in the range [from, to)
    static long countPrimes(long from, long to) {
        long count = 0;

        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;

            for (long d = 2; d <= n / d; d++) {
                if (n % d == 0) {
                    prime = false;
                    break;
                }
            }

            if (prime) {
                count++;
            }
        }

        return count;
    }

    // Return a Callable that counts primes in a range
    static Callable<Long> PrimeCounter(long from, long to) {
        return () -> countPrimes(from, to);
    }

    // Simulate a download.
    // URLs ending in /timeout take 5 seconds.
    // Every 50th numbered download fails with IOException.
    static Callable<String> FakeDownload(String url) {
        return () -> {
            if (url.endsWith("/timeout")) {
                Thread.sleep(5000);
            } else {
                Thread.sleep(200);
            }

            String lastPart = url.substring(url.lastIndexOf('/') + 1);

            if (!lastPart.equals("timeout")) {
                int number = Integer.parseInt(lastPart);

                if (number % 50 == 0) {
                    throw new IOException("boom");
                }
            }

            return url + " -> " + url.length();
        };
    }

    // Submit 500 downloads and collect successful and failed results.
    static void runDownloads(ExecutorService executor, String label)
            throws InterruptedException {

        List<Future<String>> futures = new ArrayList<>();

        long start = System.nanoTime();

        for (int i = 1; i <= 500; i++) {
            String url = "https://example.com/file/" + i;
            futures.add(executor.submit(FakeDownload(url)));
        }

        int ok = 0;
        int failed = 0;

        for (Future<String> future : futures) {
            try {
                String result = future.get();
                ok++;

                // Print a sample instead of all 500 results.
                if (ok <= 2) {
                    System.out.println("Downloaded: " + result);
                }

            } catch (ExecutionException e) {
                failed++;
                // The task failed, but the program continues.
            }
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;

        System.out.printf(
                "%s: ok=%d failed=%d, elapsed=%d ms%n",
                label, ok, failed, elapsed
        );
    }

    // Test a single download with a one-second timeout.
    static void testTimeout(ExecutorService executor)
            throws InterruptedException {

        Future<String> future = executor.submit(
                FakeDownload("https://example.com/timeout")
        );

        try {
            String result = future.get(1, TimeUnit.SECONDS);
            System.out.println("Download completed: " + result);

        } catch (TimeoutException e) {
            future.cancel(true);
            System.out.println("Download timed out (cancelled)");
        } catch (ExecutionException e) {
            System.out.println("Download failed: " + e.getCause());
        }
    }

    // Bonus: CompletableFuture with handle() for failures.
    static void runCompletableFutureDemo(ExecutorService executor) {
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (int i = 1; i <= 500; i++) {
            final String url = "https://example.com/file/" + i;

            CompletableFuture<String> future =
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            return FakeDownload(url).call();
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    }, executor).handle((result, error) -> {
                        if (error != null) {
                            return url + " -> fallback (download failed)";
                        }
                        return result;
                    });

            futures.add(future);
        }

        CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        ).join();

        long fallbackCount = futures.stream()
                .map(CompletableFuture::join)
                .filter(result -> result.contains("fallback"))
                .count();

        System.out.println(
                "CompletableFuture: completed=" + futures.size()
                + ", fallback=" + fallbackCount
        );
    }

    public static void main(String[] args) throws Exception {

        final long LIMIT = 4_000_000L;
        final int CHUNKS = 16;

        // PART 1: Single-threaded prime counting
        System.out.println("=== CPU-BOUND: PRIME COUNTING ===");

        long start = System.nanoTime();
        long single = countPrimes(0, LIMIT);
        long singleMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf(
                "Single-threaded: %d primes in %d ms%n",
                single, singleMs
        );

        // PART 2: Parallel prime counting
        start = System.nanoTime();
        long total = 0;

        try (ExecutorService pool = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors())) {

            List<Future<Long>> futures = new ArrayList<>();

            for (int i = 0; i < CHUNKS; i++) {
                long from = i * LIMIT / CHUNKS;
                long to = (i + 1L) * LIMIT / CHUNKS;

                futures.add(pool.submit(PrimeCounter(from, to)));
            }

            for (Future<Long> future : futures) {
                total += future.get();
            }
        }

        long parallelMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf(
                "Thread pool: %d primes in %d ms%n",
                total, parallelMs
        );

        System.out.println("Counts match: " + (single == total));

        // PART 3: 500 downloads using 20 platform threads
        System.out.println("\n=== I/O-BOUND: PLATFORM THREADS ===");

        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            runDownloads(pool, "Platform pool");
        }

        // PART 4: 500 downloads using virtual threads
        System.out.println("\n=== I/O-BOUND: VIRTUAL THREADS ===");

        try (ExecutorService pool =
                     Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloads(pool, "Virtual threads");
        }

        // PART 5: Timeout and cancellation
        System.out.println("\n=== TIMEOUT TEST ===");

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            testTimeout(pool);
        }

        // PART 6: CompletableFuture bonus
        System.out.println("\n=== COMPLETABLEFUTURE BONUS ===");

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            runCompletableFutureDemo(pool);
        }

        System.out.println("\nAll tasks finished. Program exiting normally.");
    }
}
