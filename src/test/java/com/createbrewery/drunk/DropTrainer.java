package com.createbrewery.drunk;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Learns {@link DropDetector#DEFAULT} from your own songs.
 *
 * <pre>./gradlew trainDrops --args="C:/Musik/drops"            (learn, show the result)
 * ./gradlew trainDrops --args="C:/Musik/drops --apply"    (and write it into DropDetector.java)</pre>
 *
 * The folder holds MP3s and a {@code drops.txt} saying where their drops are, one song a line:
 * <pre>Pursuit of Happiness.mp3: 1:02, 2:35
 * On Sight.mp3: -                    (no drop at all)
 * ? Levels.mp3: 1:07                 (suggested, not checked yet: not learnt from)</pre>
 * Songs not in the file yet get a suggested line with a {@code ?}: check it, correct the times,
 * remove the {@code ?}. Then it learns from them too. What the songs sound like is cached in
 * {@code .drop-cache}, so a second run is quick.
 */
public final class DropTrainer {
    /** A detected drop counts when it is this close to a marked one: it fires a beat in, and marks are by ear. */
    private static final double EARLY = 1.5, LATE = 2.5;
    private static final Pattern DEFAULT_LINE = Pattern.compile("static final Params DEFAULT = new Params\\(.*\\);");

    record Song(String name, float[] kick, float[] level, float[] hats) {}

    record Label(boolean checked, List<Double> drops) {}

    record Score(int hits, int wrong, int missed) {
        double f1() {
            return hits == 0 ? 0 : 2.0 * hits / (2.0 * hits + wrong + missed);
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%.0f%% (%d richtig, %d falsch, %d verpasst)", 100 * f1(), hits, wrong, missed);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Aufruf: ./gradlew trainDrops --args=\"C:/pfad/zu/songs [--apply] [--runs 3000]\"");
            return;
        }
        Path dir = Path.of(args[0]);
        boolean apply = Arrays.asList(args).contains("--apply");
        int runs = 3000;
        for (int i = 1; i + 1 < args.length; i++) if (args[i].equals("--runs")) runs = Integer.parseInt(args[i + 1]);

        List<Path> mp3s;
        try (Stream<Path> files = Files.list(dir)) {
            mp3s = files.filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".mp3")).sorted().toList();
        }
        if (mp3s.isEmpty()) {
            System.out.println("Keine MP3s in " + dir);
            return;
        }
        Path labelFile = dir.resolve("drops.txt");
        Map<String, Label> labels = readLabels(labelFile);

        List<Song> songs = new ArrayList<>();
        for (Path mp3 : mp3s) songs.add(listen(mp3, dir.resolve(".drop-cache")));

        // New songs: suggest where their drops are, for you to check.
        List<String> suggested = new ArrayList<>();
        for (Song s : songs) {
            if (labels.containsKey(s.name)) continue;
            List<Double> found = detect(s, DropDetector.DEFAULT);
            suggested.add("? " + s.name + ": " + (found.isEmpty() ? "-" : String.join(", ", found.stream().map(DropTrainer::clock).toList())));
        }
        if (!suggested.isEmpty()) {
            String existing = Files.exists(labelFile) ? Files.readString(labelFile) : "";
            if (!existing.isEmpty() && !existing.endsWith("\n")) {
                Files.writeString(labelFile, System.lineSeparator(), java.nio.file.StandardOpenOption.APPEND);
            }
            Files.write(labelFile, suggested, StandardCharsets.UTF_8, Files.exists(labelFile)
                ? new java.nio.file.OpenOption[] {java.nio.file.StandardOpenOption.APPEND} : new java.nio.file.OpenOption[0]);
            System.out.println(suggested.size() + " neue Songs in drops.txt vorgeschlagen. Zeiten pruefen/korrigieren, dann das '?' entfernen:");
            suggested.forEach(l -> System.out.println("  " + l));
        }

        List<Song> train = songs.stream().filter(s -> labels.containsKey(s.name) && labels.get(s.name).checked).toList();
        if (train.size() < 3) {
            System.out.println("Erst " + train.size() + " gepruefte Songs in drops.txt - mindestens 3 zum Lernen.");
            return;
        }
        if (train.size() < 10) System.out.println("Hinweis: mit weniger als 10 Songs lernt es leicht nur diese Songs auswendig.");

        DropDetector.Params best = DropDetector.DEFAULT;
        Score bestScore = score(train, labels, best), before = bestScore;
        System.out.println("Bisher: " + before);
        Random r = new Random(42);
        for (int run = 0; run < runs; run++) {
            // Half the tries anywhere, half close to the best so far.
            DropDetector.Params p = run % 2 == 0 ? random(r) : nudge(best, r);
            Score s = score(train, labels, p);
            if (s.f1() > bestScore.f1() + 1e-9 || s.f1() == bestScore.f1() && s.wrong() < bestScore.wrong()) {
                best = p;
                bestScore = s;
                System.out.println("  Versuch " + run + ": " + s);
            }
        }

        System.out.println();
        System.out.println("Vorher:  " + before);
        System.out.println("Gelernt: " + bestScore);
        for (Song s : train) {
            System.out.printf("  %-40s markiert %-20s erkannt %s%n", s.name, times(labels.get(s.name).drops()), times(detect(s, best)));
        }
        String line = code(best);
        System.out.println();
        System.out.println(line);
        if (best == DropDetector.DEFAULT) {
            System.out.println("Nichts Besseres gefunden, bleibt wie es ist.");
        } else if (apply) {
            Path source = Path.of("src/main/java/com/createbrewery/drunk/DropDetector.java");
            String text = Files.readString(source);
            Matcher m = DEFAULT_LINE.matcher(text);
            if (!m.find()) throw new IllegalStateException("DEFAULT line not found in " + source);
            Files.writeString(source, m.replaceFirst(Matcher.quoteReplacement(line)));
            System.out.println("In DropDetector.java eingetragen. Neu bauen, dann ist es im Spiel.");
        } else {
            System.out.println("Mit --apply wird das in DropDetector.java eingetragen.");
        }
    }

    // --- Scoring ---

    static List<Double> detect(Song s, DropDetector.Params p) {
        DropDetector d = new DropDetector(p);
        float dt = (float) KickDetector.SLICE;
        List<Double> drops = new ArrayList<>();
        for (int i = 0; i < s.kick.length; i++) {
            d.hear(s.kick[i], s.level[i], s.hats[i], true, i * KickDetector.SLICE, dt);
            if (d.takeDrop()) drops.add(i * KickDetector.SLICE);
        }
        return drops;
    }

    static Score score(List<Song> songs, Map<String, Label> labels, DropDetector.Params p) {
        return songs.parallelStream().map(s -> match(labels.get(s.name).drops(), detect(s, p)))
            .reduce(new Score(0, 0, 0), (a, b) -> new Score(a.hits + b.hits, a.wrong + b.wrong, a.missed + b.missed));
    }

    /** Each marked drop takes the first detected one close enough to it. */
    static Score match(List<Double> marked, List<Double> found) {
        List<Double> left = new ArrayList<>(found);
        int hits = 0;
        for (double m : marked) {
            for (Iterator<Double> it = left.iterator(); it.hasNext(); ) {
                double f = it.next();
                if (f >= m - EARLY && f <= m + LATE) {
                    it.remove();
                    hits++;
                    break;
                }
            }
        }
        return new Score(hits, left.size(), marked.size() - hits);
    }

    // --- Search ---

    private static final double[][] RANGE = {
        {4, 32}, {20, 120}, {2, 8}, {1, 4}, {0.03, 0.4}, {0.05, 1.0}, {0.1, 0.8}, {1, 4}, {0.5, 2.5}, {0.03, 0.3}, {8, 30}};

    private static double[] values(DropDetector.Params p) {
        return new double[] {p.groove(), p.grooveMemory(), p.goneBeats(), p.goneMin(), p.buildRate(), p.letGo(),
            p.threshold(), p.backBeats(), p.backMin(), p.hushLevel(), p.spacing()};
    }

    private static DropDetector.Params params(double[] v) {
        for (int i = 0; i < v.length; i++) v[i] = Math.max(RANGE[i][0], Math.min(RANGE[i][1], v[i]));
        return new DropDetector.Params((int) Math.round(v[0]), round(v[1], 1), round(v[2], 2), round(v[3], 2), (float) round(v[4], 3),
            (float) round(v[5], 3), (float) round(v[6], 3), round(v[7], 2), round(v[8], 2), (float) round(v[9], 3), round(v[10], 1));
    }

    private static DropDetector.Params random(Random r) {
        double[] v = new double[RANGE.length];
        for (int i = 0; i < v.length; i++) v[i] = RANGE[i][0] + r.nextDouble() * (RANGE[i][1] - RANGE[i][0]);
        return params(v);
    }

    private static DropDetector.Params nudge(DropDetector.Params p, Random r) {
        double[] v = values(p);
        for (int i = 0; i < v.length; i++) if (r.nextBoolean()) v[i] += r.nextGaussian() * 0.1 * (RANGE[i][1] - RANGE[i][0]);
        return params(v);
    }

    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }

    static String code(DropDetector.Params p) {
        return String.format(Locale.ROOT, "static final Params DEFAULT = new Params(%d, %s, %s, %s, %sf, %sf, %sf, %s, %s, %sf, %s);",
            p.groove(), p.grooveMemory(), p.goneBeats(), p.goneMin(), p.buildRate(), p.letGo(), p.threshold(),
            p.backBeats(), p.backMin(), p.hushLevel(), p.spacing());
    }

    // --- drops.txt ---

    static Map<String, Label> readLabels(Path file) throws IOException {
        Map<String, Label> labels = new LinkedHashMap<>();
        if (!Files.exists(file)) return labels;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            boolean checked = !line.startsWith("?");
            if (!checked) line = line.substring(1).strip();
            int colon = line.toLowerCase(Locale.ROOT).lastIndexOf(".mp3:");
            if (colon < 0) {
                System.out.println("drops.txt: Zeile ohne 'name.mp3:' uebersprungen: " + raw);
                continue;
            }
            String name = line.substring(0, colon + 4).strip();
            List<Double> drops = new ArrayList<>();
            for (String t : line.substring(colon + 5).split(",")) {
                t = t.strip();
                if (!t.isEmpty() && !t.equals("-")) drops.add(seconds(t));
            }
            labels.put(name, new Label(checked, drops));
        }
        return labels;
    }

    /** "1:02", "1:02.5" or "62". */
    static double seconds(String t) {
        int colon = t.indexOf(':');
        return colon < 0 ? Double.parseDouble(t) : Integer.parseInt(t.substring(0, colon)) * 60 + Double.parseDouble(t.substring(colon + 1));
    }

    static String clock(double t) {
        return String.format(Locale.ROOT, "%d:%04.1f", (int) (t / 60), t % 60);
    }

    private static String times(List<Double> t) {
        return t.isEmpty() ? "-" : String.join(", ", t.stream().map(DropTrainer::clock).toList());
    }

    // --- Listening ---

    /** What the kick detector hears of an MP3, slice by slice, as in the game; cached per file. */
    static Song listen(Path mp3, Path cacheDir) throws Exception {
        String name = mp3.getFileName().toString();
        Path cache = cacheDir.resolve(name + "." + Files.size(mp3) + "." + Files.getLastModifiedTime(mp3).toMillis() + ".bin");
        if (Files.exists(cache)) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(cache)))) {
                int n = in.readInt();
                float[] kick = new float[n], level = new float[n], hats = new float[n];
                for (int i = 0; i < n; i++) {
                    kick[i] = in.readFloat();
                    level[i] = in.readFloat();
                    hats[i] = in.readFloat();
                }
                return new Song(name, kick, level, hats);
            }
        }
        System.out.println("Hoere " + name + " ...");
        Song song = decode(name, mp3);
        Files.createDirectories(cacheDir);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(cache)))) {
            out.writeInt(song.kick.length);
            for (int i = 0; i < song.kick.length; i++) {
                out.writeFloat(song.kick[i]);
                out.writeFloat(song.level[i]);
                out.writeFloat(song.hats[i]);
            }
        }
        return song;
    }

    private static Song decode(String name, Path mp3) throws Exception {
        KickDetector detector = new KickDetector();
        List<float[][]> parts = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(mp3))) {
            Bitstream bits = new Bitstream(in);
            Decoder decoder = new Decoder();
            ByteBuffer pcm = null;
            AudioFormat format = null;
            Header h;
            while ((h = bits.readFrame()) != null) {
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(h, bits);
                if (format == null) {
                    format = new AudioFormat(out.getSampleFrequency(), 16, out.getChannelCount(), true, false);
                    // A whole number of slices per chunk: the detector drops what is left over.
                    int perSlice = Math.max(4, (int) (format.getSampleRate() * KickDetector.SLICE));
                    pcm = ByteBuffer.allocate(perSlice * 50 * 2 * format.getChannels()).order(ByteOrder.LITTLE_ENDIAN);
                }
                short[] samples = out.getBuffer();
                for (int i = 0; i < out.getBufferLength(); i++) {
                    pcm.putShort(samples[i]);
                    if (!pcm.hasRemaining()) {
                        pcm.flip();
                        parts.add(detector.slices(format, pcm));
                        pcm.clear();
                    }
                }
                bits.closeFrame();
            }
            if (pcm != null && pcm.position() > 0) {
                pcm.flip();
                parts.add(detector.slices(format, pcm));
            }
        }
        int n = parts.stream().mapToInt(p -> p[KickDetector.KICK].length).sum();
        float[] kick = new float[n], level = new float[n], hats = new float[n];
        int at = 0;
        for (float[][] p : parts) {
            int len = p[KickDetector.KICK].length;
            System.arraycopy(p[KickDetector.KICK], 0, kick, at, len);
            System.arraycopy(p[KickDetector.LEVEL], 0, level, at, len);
            System.arraycopy(p[KickDetector.HATS], 0, hats, at, len);
            at += len;
        }
        return new Song(name, kick, level, hats);
    }
}
