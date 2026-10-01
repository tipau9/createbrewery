package com.createbrewery.drunk;

/**
 * What walls do to music on its way to your ears. A wall is a strong low-pass: highs and mids
 * (vocals, synths, hats) die, the bass comes through, so outside a club you hear the kick and the
 * bassline thumping and the rest as a dull murmur. This is two Butterworth biquads in a row
 * (24 dB per octave) whose corner falls from above hearing to ~250 Hz behind one wall and lower
 * behind more, plus a broadband loss per wall.
 *
 * <p>OpenAL's own low-pass cannot do this: its corner is fixed at 5 kHz, so everything below -
 * which is nearly all of a song - went straight through "walls". Plain Java, no game classes.
 */
final class WallFilter {
    private final double rate;
    private double b0 = 1, b1, b2, a1, a2;
    /** Each biquad's state (transposed direct form II). */
    private double s1, s2, t1, t2;
    private double cutoff = -1, gain = 1;

    WallFilter(double rate) {
        this.rate = rate;
    }

    /** The corner frequency for {@code muffle} (0 clear .. 1 walled off) behind {@code walls} solid blocks. */
    static double cutoff(double muffle, double walls) {
        double walledOff = Math.max(110.0, 250.0 / (1 + 0.6 * Math.max(0, walls - 1)));
        return Math.exp(Math.log(20000) + (Math.log(walledOff) - Math.log(20000)) * clamp01(muffle));
    }

    /**
     * How walled off a speaker is, 0..1, from the solid blocks on the straight line ({@code middle})
     * and how many of the four rays ({@code blocked}, the straight one included) hit something. A wall
     * on the straight line muffles at once, as in front of a real club's door-less facade; a gap beside
     * it only lets some sound round the corner. A clear line with a corner in the way stays mostly open.
     */
    static float muffleFor(int middle, int blocked) {
        return middle > 0 ? 0.6f + 0.4f * (Math.max(1, blocked) - 1) / 3f : blocked / 4f;
    }

    /** The broadband loss: about 14 dB for the first wall, 6 more for each one after (a club is loud inside, a thump outside). */
    static double loss(double muffle, double walls) {
        double db = walls <= 0 ? 0 : 14 + 6 * (Math.min(walls, 5) - 1);
        return Math.pow(10, -db * clamp01(muffle) / 20);
    }

    void set(double muffle, double walls) {
        gain = loss(muffle, walls);
        double c = Math.min(cutoff(muffle, walls), rate * 0.45);
        if (Math.abs(c - cutoff) < 0.5) return;
        cutoff = c;
        // RBJ cookbook low-pass, Q = 1/sqrt(2).
        double w = 2 * Math.PI * c / rate, cos = Math.cos(w), alpha = Math.sin(w) / (2 * Math.sqrt(0.5));
        double a0 = 1 + alpha;
        b0 = (1 - cos) / 2 / a0;
        b1 = (1 - cos) / a0;
        b2 = b0;
        a1 = -2 * cos / a0;
        a2 = (1 - alpha) / a0;
    }

    /** Filters {@code n} samples of {@code in} from {@code from} into {@code out} in place-order. */
    void process(float[] in, int from, int n, float[] out) {
        for (int i = 0; i < n; i++) {
            double x = in[from + i];
            double y = b0 * x + s1;
            s1 = b1 * x - a1 * y + s2;
            s2 = b2 * x - a2 * y;
            double z = b0 * y + t1;
            t1 = b1 * y - a1 * z + t2;
            t2 = b2 * y - a2 * z;
            out[i] = (float) (z * gain);
        }
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
