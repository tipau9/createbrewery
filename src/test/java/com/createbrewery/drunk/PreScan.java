package com.createbrewery.drunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the drops of a song known in full beforehand - a record in a deck, a cached stream - by
 * looking both ways from every moment: a drop is where the bass, the whole mix and the kick come
 * in and then stay for the next bars, after bars without them, best after a riser. Unlike
 * {@link DropDetector}, which only hears the past, a fill or a single kick in a breakdown cannot
 * fool it: what follows does not stay.
 *
 * <p>Works on the slices {@link KickDetector} makes (BASS, LOUD and HIGH are dB-scaled, 1 = 60 dB).
 * Offline only for now, scored by {@link DropTrainer} with {@code --prescan}.
 */
final class PreScan {
    /**
     * {@code window}: seconds looked back and ahead; the weights of the bass coming in, the whole
     * mix coming in, the kick coming in and the riser before; the score a drop needs; the fewest
     * seconds between two drops.
     */
    record Params(double window, double wBass, double wLoud, double wKick, double wBuild, double threshold, double spacing) {
        static final Params DEFAULT = new Params(8.0, 1.0, 1.0, 0.5, 0.5, 1.0, 12.0);
    }

    /** Candidates every 0.1 s; the found drop is then moved onto the sharpest rise within half a second. */
    private static final int STEP = 5, SNAP = 25;
    /** About -40 dB: the song has begun. */
    private static final float AUDIBLE = 0.5f;

    static List<Double> detect(DropTrainer.Song s, Params p) {
        return detect(s, p, null);
    }

    /**
     * With {@code at} (seconds, e.g. allin1's section boundaries, which sit on the downbeat) only
     * those moments are candidates, and they are taken as they are; null: every 0.1 s, snapped.
     */
    static List<Double> detect(DropTrainer.Song s, Params p, List<Double> at) {
        int n = s.kick().length;
        int w = (int) Math.round(p.window() / KickDetector.SLICE);
        if (n < 2 * w + 1) return List.of();
        double[] bass = prefix(s.bass()), loud = prefix(s.loud()), high = prefix(s.high());
        float[] onset = new float[n];
        for (int i = 1; i < n; i++) onset[i] = s.kick()[i] > 0.5f && s.kick()[i - 1] <= 0.5f ? 1f : 0f;
        double[] kicks = prefix(onset);
        double perSecond = 1.0 / p.window();

        // The silence before the first note is not a breakdown: the window back starts after it.
        int start = 0;
        while (start < n && s.loud()[start] < AUDIBLE) start++;
        List<double[]> candidates = new ArrayList<>(); // {slice, score}
        int q = w / 4;
        List<Integer> slices = new ArrayList<>();
        if (at == null) for (int i = start + w; i + w < n; i += STEP) slices.add(i);
        else for (double t : at) {
            int i = (int) Math.round(t / KickDetector.SLICE);
            if (i >= start + w && i + w < n) slices.add(i);
        }
        for (int i : slices) {
            // What comes after has to stay: the weakest quarter of the window ahead counts.
            double bassAfter = Double.MAX_VALUE, loudAfter = Double.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                bassAfter = Math.min(bassAfter, mean(bass, i + k * q, i + (k + 1) * q));
                loudAfter = Math.min(loudAfter, mean(loud, i + k * q, i + (k + 1) * q));
            }
            double dBass = bassAfter - mean(bass, i - w, i);
            double dLoud = loudAfter - mean(loud, i - w, i);
            // Kicks a second, after against before (about 2 at 120 bpm).
            double dKick = ((kicks[i + w] - kicks[i]) - (kicks[i] - kicks[i - w])) * perSecond / 2;
            // A riser: the highs louder in the last half of the window back than in the first.
            double build = mean(high, i - w / 2, i) - mean(high, i - w, i - w / 2);
            // x10: a tenth of the 60 dB scale (6 dB) weighs as much as a kick a beat more.
            double score = 10 * (p.wBass() * dBass + p.wLoud() * dLoud + p.wBuild() * build) + p.wKick() * dKick;
            if (score >= p.threshold()) candidates.add(new double[] {i, score});
        }

        // The strongest first; nothing else within the spacing of one already taken.
        candidates.sort(Comparator.comparingDouble((double[] c) -> -c[1]));
        int gap = (int) Math.round(p.spacing() / KickDetector.SLICE);
        List<Integer> taken = new ArrayList<>();
        for (double[] c : candidates) {
            int i = (int) c[0];
            if (taken.stream().noneMatch(t -> Math.abs(t - i) < gap)) taken.add(i);
        }
        List<Double> drops = new ArrayList<>();
        for (int i : taken) drops.add((at == null ? snap(s, i) : i) * KickDetector.SLICE);
        drops.sort(null);
        return drops;
    }

    /** The slice within half a second of {@code i} where bass and mix rise most sharply. */
    private static int snap(DropTrainer.Song s, int i) {
        int best = i;
        double rise = Double.NEGATIVE_INFINITY;
        for (int j = Math.max(3, i - SNAP); j < Math.min(s.kick().length - 3, i + SNAP); j++) {
            double r = s.bass()[j + 2] - s.bass()[j - 3] + s.loud()[j + 2] - s.loud()[j - 3];
            if (r > rise) {
                rise = r;
                best = j;
            }
        }
        return best;
    }

    private static double[] prefix(float[] v) {
        double[] p = new double[v.length + 1];
        for (int i = 0; i < v.length; i++) p[i + 1] = p[i] + v[i];
        return p;
    }

    /** Mean of slices from (inclusive) .. to (exclusive). */
    private static double mean(double[] prefix, int from, int to) {
        return to <= from ? 0 : (prefix[to] - prefix[from]) / (to - from);
    }
}
