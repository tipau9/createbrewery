package com.createbrewery.drunk;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Stream;

/**
 * Checks suggested drops by ear, quickly, and writes them into drops.txt.
 *
 * <pre>./gradlew labelDrops --args="Musik/drops"</pre>
 *
 * For every song not yet checked in drops.txt (missing, or only a {@code ?} suggestion) that allin1 has analysed ({@code .allin1/}, see
 * tools/drops_allin1.py), the three strongest of its section boundaries (scored like
 * {@link PreScan}) are played, three seconds either side, one after the other. Answer each:
 * j = a drop, n = not one, w = again, s = skip the song for now, q = stop. Once a song is done
 * its line goes into drops.txt at once, so stopping loses nothing.
 */
public final class DropLabeler {
    private static final int PER_ROUND = 3;
    /** Two candidates closer than this are one moment. */
    private static final double APART = 8.0;
    private static final double BEFORE = 3.0, AFTER = 3.0;

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "Musik/drops");
        Path labelFile = dir.resolve("drops.txt");
        Map<String, DropTrainer.Label> labels = DropTrainer.readLabels(labelFile);
        Map<String, List<Double>> bounds = DropTrainer.readBoundaries(dir.resolve(".allin1"));
        List<Path> todo;
        try (Stream<Path> files = Files.list(dir)) {
            todo = files.filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".mp3"))
                .filter(f -> {
                    DropTrainer.Label l = labels.get(f.getFileName().toString());
                    return (l == null || !l.checked()) && bounds.containsKey(f.getFileName().toString());
                })
                .sorted().toList();
        }
        System.out.println(todo.size() + " Songs zu pruefen. Pro Kandidat: j = Drop, n = kein Drop, w = nochmal, s = Song ueberspringen, q = Ende");
        // Lines go on at the end: make sure the last one is finished.
        if (Files.exists(labelFile)) {
            String text = Files.readString(labelFile, StandardCharsets.UTF_8);
            if (!text.isEmpty() && !text.endsWith(System.lineSeparator()) && text.charAt(text.length() - 1) != '\n') {
                Files.writeString(labelFile, System.lineSeparator(), StandardOpenOption.APPEND);
            }
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        int done = 0;
        for (Path mp3 : todo) {
            String name = mp3.getFileName().toString();
            DropTrainer.Song song = DropTrainer.listen(mp3, dir.resolve(".drop-cache"));
            List<double[]> ranked = new ArrayList<>(PreScan.scored(song, PreScan.Params.DEFAULT, bounds.get(name)));
            ranked.sort(Comparator.comparingDouble((double[] c) -> -c[1]));
            List<Double> candidates = new ArrayList<>();
            for (double[] c : ranked) {
                double t = c[0] * KickDetector.SLICE;
                if (candidates.stream().allMatch(o -> Math.abs(o - t) >= APART)) candidates.add(t);
            }
            System.out.printf("%n[%d/%d] %s  (%d Kandidaten)%n", ++done, todo.size(), name, candidates.size());
            Pcm pcm = decode(mp3);
            List<Double> drops = new ArrayList<>();
            boolean skip = false;
            int shown = 0;
            while (shown < candidates.size() && !skip) {
                List<Double> round = new ArrayList<>(candidates.subList(shown, Math.min(candidates.size(), shown + PER_ROUND)));
                round.sort(null);
                shown += round.size();
                for (int k = 0; k < round.size() && !skip; k++) {
                    double t = round.get(k);
                    while (true) {
                        System.out.printf("  %s  [%d/%d]  ", DropTrainer.clock(t), k + 1, round.size());
                        play(pcm, t - BEFORE, t + AFTER);
                        System.out.print("Drop? ");
                        String a = in.readLine();
                        if (a == null || a.strip().equalsIgnoreCase("q")) {
                            System.out.println("Ende. Fertige Songs stehen in " + labelFile);
                            return;
                        }
                        a = a.strip().toLowerCase(Locale.ROOT);
                        if (a.equals("j")) drops.add(t);
                        else if (a.equals("s")) skip = true;
                        else if (!a.equals("n")) continue; // w or anything else: again
                        break;
                    }
                }
                if (!skip && shown < candidates.size()) {
                    System.out.print("  Enter = Song fertig, m = " + PER_ROUND + " weitere Kandidaten: ");
                    String a = in.readLine();
                    if (a == null || !a.strip().equalsIgnoreCase("m")) break;
                }
            }
            if (skip) {
                System.out.println("  uebersprungen");
                continue;
            }
            drops.sort(null);
            String line = name + ": " + (drops.isEmpty() ? "-" : String.join(", ", drops.stream().map(DropTrainer::clock).toList()));
            Files.writeString(labelFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("  -> " + line);
        }
        System.out.println("Alle Songs geprueft.");
    }

    record Pcm(short[] samples, float rate) {}

    /** The whole MP3 as mono 16-bit samples. */
    static Pcm decode(Path mp3) throws Exception {
        short[] out = new short[1 << 20];
        int n = 0;
        float rate = 44100;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(mp3))) {
            Bitstream bits = new Bitstream(is);
            Decoder decoder = new Decoder();
            Header h;
            while ((h = bits.readFrame()) != null) {
                SampleBuffer b = (SampleBuffer) decoder.decodeFrame(h, bits);
                rate = b.getSampleFrequency();
                int ch = b.getChannelCount();
                short[] s = b.getBuffer();
                for (int i = 0; i + ch <= b.getBufferLength(); i += ch) {
                    int sum = 0;
                    for (int c = 0; c < ch; c++) sum += s[i + c];
                    if (n == out.length) out = Arrays.copyOf(out, out.length * 2);
                    out[n++] = (short) (sum / ch);
                }
                bits.closeFrame();
            }
        }
        return new Pcm(Arrays.copyOf(out, n), rate);
    }

    /** Plays seconds {@code from} .. {@code to} and waits until it has been heard. */
    static void play(Pcm pcm, double from, double to) throws Exception {
        int a = (int) Math.max(0, from * pcm.rate()), b = (int) Math.min(pcm.samples().length, to * pcm.rate());
        if (b <= a) return;
        byte[] bytes = new byte[(b - a) * 2];
        for (int i = a; i < b; i++) {
            bytes[(i - a) * 2] = (byte) pcm.samples()[i];
            bytes[(i - a) * 2 + 1] = (byte) (pcm.samples()[i] >> 8);
        }
        AudioFormat format = new AudioFormat(pcm.rate(), 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            line.write(bytes, 0, bytes.length);
            line.drain();
        }
    }
}
