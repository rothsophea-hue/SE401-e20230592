package edu.itc.se.lab01.challenge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Single-threaded reference implementation used to verify {@link WordCount}.
 *
 * It follows the same specification (words = maximal runs of Unicode letters,
 * lower-cased with {@link Locale#ROOT}) but is deliberately written differently:
 * a plain {@link HashMap}, no regex, no threads, files in sorted order.
 */
public final class ReferenceCount {

    private ReferenceCount() {}

    public static Map<String, Long> count(Path dir) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(Files::isRegularFile).sorted().toList();
        }
        Map<String, Long> counts = new HashMap<>();
        for (Path f : files) {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    countLine(line, counts);
                }
            }
        }
        return counts;
    }

    static void countLine(String line, Map<String, Long> counts) {
        StringBuilder word = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            int cp = line.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp)) {
                word.appendCodePoint(cp);
            } else if (!word.isEmpty()) {
                counts.merge(word.toString().toLowerCase(Locale.ROOT), 1L, Long::sum);
                word.setLength(0);
            }
        }
        if (!word.isEmpty()) {
            counts.merge(word.toString().toLowerCase(Locale.ROOT), 1L, Long::sum);
        }
    }

    public static long total(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Prints the top N words, handy for eyeballing against WordCount. */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: ReferenceCount <dir> [topN=10]");
            System.exit(2);
        }
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        long t0 = System.nanoTime();
        Map<String, Long> m = count(Path.of(args[0]));
        double sec = (System.nanoTime() - t0) / 1e9;
        long total = total(m);
        System.out.printf(Locale.US, "reference (1 thread): words=%,d distinct=%,d elapsed=%.2fs%n", total, m.size(), sec);
        m.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(n)
                .forEach(e -> System.out.printf(Locale.US, "%-14s%,12d  %6.2f %%%n",
                        e.getKey(), e.getValue(), 100.0 * e.getValue() / total));
    }
}

