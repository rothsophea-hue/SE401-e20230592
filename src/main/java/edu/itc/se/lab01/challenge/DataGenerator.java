package edu.itc.se.lab01.challenge;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Writes test data: {@code files} text files of {@code wordsPerFile} random words each,
 * drawn from a fixed 2 000-word vocabulary with a skewed (Zipf-like) distribution so
 * that the top-N list is interesting. Text is deliberately messy (capitals, commas,
 * full stops, digits, accents) to exercise the tokenizer.
 *
 * Usage: java edu.itc.se.lab01.challenge.DataGenerator &lt;dir&gt; [files=200] [wordsPerFile=50000]
 */
public final class DataGenerator {

    static final int VOCABULARY_SIZE = 2_000;
    private static final String[] COMMON = {
            "the", "and", "of", "to", "a", "in", "is", "it", "that", "for",
            "was", "on", "with", "as", "his", "he", "be", "at", "by", "this"
    };
    private static final String[] ACCENTED = {"café", "naïve", "façade", "señor", "über"};

    private DataGenerator() {}

    static String[] vocabulary(long seed) {
        Random r = new Random(seed);
        String[] v = new String[VOCABULARY_SIZE];
        int i = 0;
        for (String w : COMMON) v[i++] = w;
        for (String w : ACCENTED) v[i++] = w;
        while (i < VOCABULARY_SIZE) {
            int len = 2 + r.nextInt(9);
            StringBuilder sb = new StringBuilder(len);
            for (int c = 0; c < len; c++) sb.append((char) ('a' + r.nextInt(26)));
            v[i++] = sb.toString();
        }
        return v;
    }

    /** Deterministic for a given seed. */
    static void generate(Path dir, int files, int wordsPerFile, long seed) throws IOException {
        Files.createDirectories(dir);
        String[] vocab = vocabulary(seed);
        for (int f = 0; f < files; f++) {
            Random r = new Random(seed * 31 + f);
            Path file = dir.resolve(String.format("file-%05d.txt", f));
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                int onLine = 0;
                for (int i = 0; i < wordsPerFile; i++) {
                    String word = vocab[(int) (Math.pow(r.nextDouble(), 3) * VOCABULARY_SIZE)];
                    if (r.nextInt(12) == 0) word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
                    w.write(word);
                    int p = r.nextInt(40);
                    if (p == 0) w.write(',');
                    else if (p == 1) w.write('.');
                    else if (p == 2) w.write(" 42");
                    w.write(' ');
                    if (++onLine >= 8 + r.nextInt(8)) {
                        w.write('\n');
                        onLine = 0;
                    }
                }
                w.write('\n');
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: DataGenerator <dir> [files=200] [wordsPerFile=50000]");
            System.exit(2);
        }
        Path dir = Path.of(args[0]);
        int files = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        int words = args.length > 2 ? Integer.parseInt(args[2]) : 50_000;
        long t0 = System.nanoTime();
        generate(dir, files, words, 12345L);
        System.out.printf("wrote %d files x %d words to %s in %.1fs%n",
                files, words, dir, (System.nanoTime() - t0) / 1e9);
    }
}
