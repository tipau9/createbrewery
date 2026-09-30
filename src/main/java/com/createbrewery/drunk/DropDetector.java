package com.createbrewery.drunk;

/**
 * Follows a song as it is heard (see {@link MusicPulse#update}): counts the kicks, learns the
 * tempo, and hears the shape of a rave track - the build-up and the drop.
 *
 * <ul>
 *   <li>Groove: kicks in a row on the beat (the numbers here are the defaults, see {@link Params}). Only a song that has had one can build up and
 *       drop - hip-hop, breakbeats and songs whose kick comes and goes never had it, so a pause
 *       in them is just a pause.</li>
 *   <li>Build-up, two kinds, within a minute of the groove: the kick has been gone for more than a
 *       bar while the music goes on (a breakdown), or the kick rolls, coming faster than the beat.
 *       Risers - louder than a few seconds ago, the hats busier - speed it up.</li>
 *   <li>Drop: the kick comes back after a build-up and a pause of at least half a bar, or right
 *       after a moment of near silence - and the next kick lands on the beat. Only then does it
 *       count, one beat in: a single stray kick in a breakdown is not the drop, and the build-up
 *       goes on. Harder the longer the build-up, and hardest right after that silence.</li>
 * </ul>
 * A pause of a bar or two is not a build-up, a song without drums is not one long build-up, and
 * two drops are at least a quarter of a minute apart.
 *
 * <p>The above is the four-on-the-floor case. For everything else - trap, hip-hop, pop, rock, a beat
 * switch - there is a second, genre-blind path: the bass (and the whole mix, and the highs) suddenly
 * much louder than the few seconds before, after a dip, and staying there. It needs no kick and no
 * tempo, only the absolute loudness of the bands (see {@link KickDetector#BASS}).
 */
final class DropDetector {
    /**
     * Everything that decides what counts as a build-up and a drop, in seconds, beats and 0..1.
     * DEFAULT is learnt from real songs by DropTrainer (./gradlew trainDrops).
     */
    record Params(int groove, double grooveMemory, double goneBeats, double goneMin, float buildRate, float letGo,
                  float threshold, double backBeats, double backMin, float hushLevel, double spacing,
                  double eRecent, double ePrior, double eGap, float wBass, float wLoud, float wHigh, float wDip,
                  float eThreshold, float eSustain) {}

    // trained: DropTrainer rewrites the next line
    static final Params DEFAULT = new Params(5, 45.0, 3.5, 2.0, 0.187f, 0.2f, 0.25f, 3.75, 0.97, 0.03f, 12.0,
        0.5, 3.0, 1.0, 0.47f, 0.0f, 0.32f, 0.56f, 0.48f, -1.0f);

    private final Params p;

    DropDetector() {
        this(DEFAULT);
    }

    DropDetector(Params p) {
        this.p = p;
    }

    int beats;
    /** 0..1 how far into a build-up. */
    float tension;
    /** 1 at a drop, dying away over a second or two. */
    float drop;
    private double lastKick = Double.NEGATIVE_INFINITY, lastDrop = Double.NEGATIVE_INFINITY, hush = Double.NEGATIVE_INFINITY;
    private double rollAt = Double.NEGATIVE_INFINITY, lastGroove = Double.NEGATIVE_INFINITY;
    private double period = 0.5;
    private float calm, busy;
    private boolean kicking, dropped, lastFast;
    /** Kicks in a row on the beat. */
    private int streak;
    /** The kick that may be the drop, waiting for the next one on the beat (NaN when none). */
    private double candidate = Double.NaN, keptLastKick;
    private float candidateDrop;

    /** Once a frame: what is heard now (kick, level and hats, each 0..1), the time in seconds. */
    void hear(float kick, float level, float hats, boolean heard, double now, float dt) {
        hear(kick, level, hats, Float.NaN, Float.NaN, Float.NaN, heard, now, dt);
    }

    /**
     * The same, with the absolute loudness of the bass, of everything and of the highs (see
     * {@link KickDetector#BASS}); NaN when unknown.
     */
    void hear(float kick, float level, float hats, float bass, float loud, float high, boolean heard, double now, float dt) {
        if (heard && !Float.isNaN(bass)) energy(bass, loud, high, now, dt);
        if (kick > 0.5f && !kicking) onKick(now);
        kicking = kick > 0.5f;
        // No second kick on the beat: a stray kick in the breakdown, the build-up goes on.
        if (!Double.isNaN(candidate) && now - candidate > 1.5 * period) {
            lastKick = keptLastKick;
            candidate = Double.NaN;
        }

        double quiet = now - lastKick;
        boolean grooved = now - lastGroove < p.grooveMemory();
        float rising = Math.max(0f, level - calm) * 3f + Math.max(0f, hats - busy) * 2f;
        boolean gone = heard && grooved && quiet > Math.max(p.goneMin(), p.goneBeats() * period);
        boolean rolling = heard && grooved && now - rollAt < period;
        if (gone || rolling) {
            tension += dt * (p.buildRate() + rising);
        } else if (heard) {
            tension -= dt * p.letGo(); // the groove goes on, or there never was one: a build-up slowly let go
        } else {
            tension -= dt / 2f;
        }
        tension = Math.max(0f, Math.min(1f, tension));
        if (heard && level < p.hushLevel()) hush = now;
        calm += (level - calm) * Math.min(1f, dt / 3f);
        busy += (hats - busy) * Math.min(1f, dt / 1f);
        drop *= (float) Math.exp(-dt * 1.2);
    }

    // ---- the genre-blind path: bands louder than a moment ago ----

    private static final double BUCKET = 0.1;
    private static final int BUCKETS = 400;
    /** The last 40 s in 0.1 s means: bass, everything, highs; the newest at {@code head - 1}. */
    private final float[][] ring = new float[3][BUCKETS];
    private int head, filled;
    private double span;
    private final double[] acc = new double[3];

    private void energy(float bass, float loud, float high, double now, float dt) {
        acc[0] += bass * dt;
        acc[1] += loud * dt;
        acc[2] += high * dt;
        span += dt;
        if (span < BUCKET) return;
        for (int c = 0; c < 3; c++) {
            ring[c][head] = (float) (acc[c] / span);
            acc[c] = 0;
        }
        span = 0;
        head = (head + 1) % BUCKETS;
        filled = Math.min(BUCKETS, filled + 1);
        judge(now);
    }

    /** Mean of the buckets {@code from} to {@code to} (exclusive) ago; 0 is the newest. */
    private float mean(int c, int from, int to) {
        float sum = 0f;
        for (int a = from; a < to; a++) sum += ring[c][(head - 1 - a + 2 * BUCKETS) % BUCKETS];
        return sum / (to - from);
    }

    /** The lowest mean over blocks of {@code step} buckets in {@code from}..{@code to} ago. */
    private float lowest(int c, int from, int to, int step) {
        float low = Float.MAX_VALUE;
        for (int a = from; a + step <= to; a += step) low = Math.min(low, mean(c, a, a + step));
        return low == Float.MAX_VALUE ? mean(c, from, to) : low;
    }

    private void judge(double now) {
        int r = Math.max(1, (int) Math.round(p.eRecent() / BUCKET));
        int gap = Math.max(0, (int) Math.round(p.eGap() / BUCKET));
        int prior = Math.max(2, (int) Math.round(p.ePrior() / BUCKET));
        if (filled < r + gap + prior || now - lastDrop < p.spacing()) return;
        int from = r + gap, to = r + gap + prior;
        float dBass = mean(0, 0, r) - mean(0, from, to);
        float dLoud = mean(1, 0, r) - mean(1, from, to);
        float dHigh = mean(2, 0, r) - mean(2, from, to);
        float dip = mean(0, 0, r) - lowest(0, from, to, 5);
        float score = p.wBass() * dBass + p.wLoud() * dLoud + p.wHigh() * dHigh + p.wDip() * dip;
        // It has to stay: even the quietest bit of the recent window is well above what came before.
        float stays = (r >= 3 ? lowest(0, 0, r, 3) : mean(0, 0, r)) - mean(0, from, to);
        if (score < p.eThreshold() || stays < p.eSustain()) return;
        drop = Math.min(1f, 0.4f + score);
        dropped = true;
        lastDrop = now;
        tension = 0f;
        candidate = Double.NaN;
    }

    private void onKick(double now) {
        beats++;
        double gap = now - lastKick;
        // Once the tempo is known, a kick roll (or a stray kick) does not drag it away.
        boolean learn = beats < p.groove() ? gap > 0.25 && gap < 1.2 : gap > 0.75 * period && gap < 1.5 * period;
        if (learn) period += (gap - period) * 0.2;
        boolean onBeat = gap > 0.8 * period && gap < 1.25 * period;
        streak = onBeat ? streak + 1 : 0;
        if (streak >= p.groove()) lastGroove = now;
        // Two fast kicks in a row: a roll. One alone is a ghost kick or a broken beat.
        boolean fast = gap > 0.05 && gap < 0.6 * period;
        if (fast && lastFast) rollAt = now;
        lastFast = fast;

        if (!Double.isNaN(candidate)) {
            if (gap > 0.75 * period && gap < 1.5 * period) { // the kick is really back: the drop
                drop = candidateDrop;
                dropped = true;
                lastDrop = candidate;
                tension = 0f;
            }
            candidate = Double.NaN;
        }
        boolean back = gap > Math.max(p.backMin(), p.backBeats() * period) || now - hush < 1.0;
        if (back && tension > p.threshold() && now - lastDrop > p.spacing()) {
            candidate = now;
            keptLastKick = lastKick;
            candidateDrop = Math.min(1f, 0.4f + tension + (now - hush < 1.0 ? 0.3f : 0f));
        }
        lastKick = now;
    }

    /** True once after each drop. */
    boolean takeDrop() {
        boolean was = dropped;
        dropped = false;
        return was;
    }

    /** Seconds from the given time to the nearest beat. */
    double offBeat(double now) {
        double since = (now - lastKick) % period;
        return Math.min(since, period - since);
    }

    /** The beat's length in seconds, as learnt so far. */
    double period() {
        return period;
    }
}
