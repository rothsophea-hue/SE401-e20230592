package edu.itc.se.lab01.challenge;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Small portable benchmark (no shell script needed): runs WordCount in-process for
 * 1, 2, 4, 8 platform workers and for virtual threads, and prints a Markdown table.
 *
 * Usage: java edu.itc.se.lab01.challenge.Bench &lt;dir&gt; [repetitions=5]
 */
public final class Bench {

    private static final PrintStream QUIET = new PrintStream(OutputStream.nullOutputStream());

    private Bench() {}

    private static long run(Path dir, int workers, boolean virtual) throws Exception {
        Path csv = Path.of(System.getProperty("java.io.tmpdir"), "wordcount-bench.csv");
        WordCount wc = new WordCount(dir, workers, virtual, 3, 0).output(QUIET, csv);
        wc.run();
        return wc.elapsedMillis();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: Bench <dir> [repetitions=5]");
            System.exit(2);
        }
        Path dir = Path.of(args[0]);
        int reps = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        System.out.printf("cores=%d  java=%s  repetitions=%d (median reported)%n%n",
                Runtime.getRuntime().availableProcessors(), System.getProperty("java.version"), reps);
        run(dir, 2, false); // warm-up: JIT + OS file cache, result discarded

        int[] platform = {1, 2, 4, 8};
        long baseline = 0;
        System.out.println("| Mode | Median elapsed (s) | Best (s) | Speed-up vs 1 worker |");
        System.out.println("|---|---:|---:|---:|");
        for (int i = 0; i <= platform.length; i++) {
            boolean virtual = i == platform.length;
            long[] t = new long[reps];
            for (int r = 0; r < reps; r++) t[r] = run(dir, virtual ? 1 : platform[i], virtual);
            Arrays.sort(t);
            long median = t[reps / 2];
            if (i == 0) baseline = median;
            String label = virtual ? "virtual threads (" + WordCount.VIRTUAL_TASKS + " tasks)"
                    : platform[i] + " platform worker" + (platform[i] > 1 ? "s" : "");
            System.out.printf(Locale.US, "| %s | %.2f | %.2f | %.2fx |%n",
                    label, median / 1000.0, t[0] / 1000.0, (double) baseline / median);
        }
    }
}

