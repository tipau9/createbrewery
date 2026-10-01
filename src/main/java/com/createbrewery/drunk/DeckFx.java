package com.createbrewery.drunk;

/**
 * A DJ mixer channel's sound: a three-band kill EQ, the one-knob filter and an echo or reverb
 * send. Each {@link Emitter} playing a deck runs its own (the state is per stream). Plain Java,
 * no game classes.
 *
 * <p>The EQ is an isolator: Linkwitz-Riley crossovers (24 dB/oct) split the signal at 250 Hz and
 * 2.5 kHz and the bands are put back together at their own gains. Flat, the bands sum to the
 * input's level at every frequency; a killed band is gone - like the kills on a club mixer, not a
 * gentle shelf.
 */
public final class DeckFx {
    public static final int NONE = 0, ECHO = 1, REVERB = 2, FLANGER = 3, PHASER = 4, ROLL = 5, TRANS = 6, EFFECTS = 7;
    public static final int COLOR_SPACE = 0, COLOR_DUB_ECHO = 1, COLOR_SWEEP = 2, COLOR_NOISE = 3, COLOR_CRUSH = 4, COLOR_FILTER = 5, COLOR_FX_COUNT = 6;
    private static final double LOW_SPLIT = 250, HIGH_SPLIT = 2500;

    private final double rate;
    /** Low band, the rest; the rest split into mid and high; the low band through the same second split, so the phases line up. */
    private final Biquad[] low = lr4(true, LOW_SPLIT), rest = lr4(false, LOW_SPLIT), mid = lr4(true, HIGH_SPLIT), high = lr4(false, HIGH_SPLIT),
        lowLp = lr4(true, HIGH_SPLIT), lowHp = lr4(false, HIGH_SPLIT);
    private final Biquad sweep = new Biquad();
    private final Biquad noiseBiquad = new Biquad();
    private final Biquad phaser1 = new Biquad(), phaser2 = new Biquad();
    private final java.util.Random random = new java.util.Random();
    private float gLow = 1, gMid = 1, gHigh = 1, tLow = 1, tMid = 1, tHigh = 1;
    private double filterAt = 0;
    private int fx;
    private float send, sendTarget;
    private double beatSec = 0.5;

    // Sound Color FX
    private int colorType = COLOR_FILTER;
    private float colorAmount = 0f, colorParam = 0.5f;
    private float crushSample = 0f;
    private int crushHold = 0;

    // Echo: a half-beat delay with feedback, ringing out after the send closes.
    private final float[] echo;
    private int echoAt, echoLen;
    // Flanger: modulated short delay
    private final float[] flangerBuf;
    private int flangerAt;
    private double lfoPhase;
    // Roll: momentary beat loop repeat
    private final float[] rollBuf;
    private int rollAt, rollLen;
    // Reverb: Schroeder - four damped combs in parallel, two all-passes in series.
    private final float[][] combs;
    private final int[] combAt = new int[4];
    private final float[] combLow = new float[4];
    private final float[][] passes;
    private final int[] passAt = new int[2];

    public DeckFx(double rate) {
        this.rate = rate;
        for (Biquad[] pair : new Biquad[][] {low, rest, mid, high, lowLp, lowHp}) {
            for (Biquad b : pair) {
                if (b.lowPassAt > 0) b.lowPass(b.lowPassAt, rate, Math.sqrt(0.5));
                else b.highPass(-b.lowPassAt, rate, Math.sqrt(0.5));
            }
        }
        echo = new float[(int) (rate * 2)];
        flangerBuf = new float[(int) (rate * 0.02)]; // 20ms
        rollBuf = new float[(int) (rate * 2)];
        double s = rate / 44100.0;
        combs = new float[][] {new float[(int) (1116 * s)], new float[(int) (1188 * s)], new float[(int) (1277 * s)], new float[(int) (1356 * s)]};
        passes = new float[][] {new float[(int) (556 * s)], new float[(int) (441 * s)]};
    }

    /** Two Butterworth biquads in a row: one side of a Linkwitz-Riley crossover at {@code f}. */
    private static Biquad[] lr4(boolean lowPass, double f) {
        Biquad a = new Biquad(), b = new Biquad();
        a.lowPassAt = b.lowPassAt = lowPass ? f : -f;
        return new Biquad[] {a, b};
    }

    private static double run(Biquad[] pair, double x) {
        return pair[1].run(pair[0].run(x));
    }

    /** Metres (blocks) a second. */
    public static final double SPEED_OF_SOUND = 343;

    /** Seconds until a speaker's sound reaches you: its alignment delay, and the flight over {@code distance} blocks. */
    public static double propagation(double align, double distance) {
        return align + distance / SPEED_OF_SOUND;
    }

    /**
     * The playback speed that brings a speaker {@code aheadSeconds} early (negative: late) back to
     * where it should be heard, within +-3 % - walking towards a speaker you hear it a touch higher,
     * which is the Doppler shift it really has.
     */
    public static float servo(double aheadSeconds) {
        if (Math.abs(aheadSeconds) < 0.001) return 1f;
        return (float) Math.max(0.97, Math.min(1.03, 1 - aheadSeconds * 4));
    }

    /** Song frame {@code from} as heard inside a loop of {@code len} frames from {@code start}. */
    public static long loopFrame(long from, long start, long len) {
        return from < start ? from : start + (from - start) % len;
    }

    /** A knob 0..1 as a gain: 0 kills the band, the middle is unity, fully open is +6 dB. */
    public static float eqGain(float knob) {
        knob = Math.max(0f, Math.min(1f, knob));
        return knob <= 0.5f ? (knob * 2) * (knob * 2) : 1f + (knob - 0.5f) * 2f;
    }

    /**
     * The mixer's settings for this deck: EQ knobs 0..1, filter -1 (low-pass closed) .. 0 (off) ..
     * 1 (high-pass closed), the effect and how much is sent to it, and seconds per beat.
     */
    public void set(float low, float mid, float high, float filter, int effect, float amount, double beat) {
        tLow = eqGain(low);
        tMid = eqGain(mid);
        tHigh = eqGain(high);
        beatSec = beat > 0 ? beat : 0.5;
        if (Math.abs(filter - filterAt) > 1e-3) {
            filterAt = filter;
            // Exponential sweeps: the low-pass from above hearing down to 80 Hz, the high-pass from 20 Hz up to 8 kHz.
            if (filter < -0.02) sweep.lowPass(Math.exp(Math.log(20000) + (Math.log(80) - Math.log(20000)) * -filter), rate, 1.4);
            else if (filter > 0.02) sweep.highPass(Math.exp(Math.log(20) + (Math.log(8000) - Math.log(20)) * filter), rate, 1.4);
        }
        if (effect != fx) {
            fx = effect;
            clearTails();
        }
        sendTarget = Math.max(0f, Math.min(1f, amount));
        echoLen = (int) Math.max(1, Math.min(echo.length - 1, beatSec * 0.5 * rate));
        rollLen = (int) Math.max(1, Math.min(rollBuf.length - 1, beatSec * 0.25 * rate));
    }

    public void setColorFx(int type, float amount, float param) {
        this.colorType = type;
        this.colorAmount = Math.abs(amount) < 0.02f ? 0f : amount;
        this.colorParam = param;
        if (type == COLOR_NOISE && Math.abs(this.colorAmount) > 0.01f) {
            double f = this.colorAmount > 0
                ? Math.exp(Math.log(1000) + (Math.log(14000) - Math.log(1000)) * this.colorAmount)
                : Math.exp(Math.log(1000) + (Math.log(80) - Math.log(1000)) * -this.colorAmount);
            noiseBiquad.bandPass(f, rate, 1.2 + param * 2.0);
        } else if (type == COLOR_SWEEP && Math.abs(this.colorAmount) > 0.01f) {
            double f = this.colorAmount > 0
                ? Math.exp(Math.log(400) + (Math.log(12000) - Math.log(400)) * this.colorAmount)
                : Math.exp(Math.log(400) + (Math.log(60) - Math.log(400)) * -this.colorAmount);
            sweep.bandPass(f, rate, 2.0 + param * 3.0);
        }
    }

    /** In place, {@code n} samples. Gains glide across the block, so turning a knob never clicks. */
    public void process(float[] buf, int n) {
        float dl = (tLow - gLow) / n, dm = (tMid - gMid) / n, dh = (tHigh - gHigh) / n, ds = (sendTarget - send) / n;
        boolean filtering = Math.abs(filterAt) > 0.02;
        double lfoSpeed = 2.0 * Math.PI / Math.max(0.1, beatSec) / rate;

        for (int i = 0; i < n; i++) {
            gLow += dl;
            gMid += dm;
            gHigh += dh;
            send += ds;
            double x = buf[i];
            double lo = run(low, x), r = run(rest, x);
            lo = run(lowLp, lo) + run(lowHp, lo);
            double y = lo * gLow + run(mid, r) * gMid + run(high, r) * gHigh;

            // 1. Sound Color FX processing
            switch (colorType) {
                case COLOR_FILTER -> {
                    if (filtering) y = sweep.run(y);
                }
                case COLOR_NOISE -> {
                    if (Math.abs(colorAmount) > 0.01f) {
                        float noiseGain = Math.abs(colorAmount) * (0.20f + colorParam * 0.30f);
                        double white = random.nextFloat() * 2.0f - 1.0f;
                        y += noiseBiquad.run(white) * noiseGain;
                    }
                }
                case COLOR_CRUSH -> {
                    if (Math.abs(colorAmount) > 0.01f) {
                        float depth = Math.abs(colorAmount);
                        float levels = (float) Math.pow(2, 2 + (1.0f - depth) * 10);
                        int holdPeriod = 1 + (int) (depth * (8 + colorParam * 16));
                        if (++crushHold >= holdPeriod) {
                            crushHold = 0;
                            crushSample = (float) (Math.round(y * levels) / levels);
                        }
                        y = crushSample;
                    }
                }
                case COLOR_DUB_ECHO -> {
                    if (Math.abs(colorAmount) > 0.01f) {
                        y = echo(y);
                    }
                }
                case COLOR_SPACE -> {
                    if (Math.abs(colorAmount) > 0.01f) {
                        y = reverb(y);
                    }
                }
                case COLOR_SWEEP -> {
                    if (Math.abs(colorAmount) > 0.01f) {
                        y = sweep.run(y) * (1.0f + colorParam * 0.5f);
                    }
                }
            }

            // 2. Beat FX processing
            if (fx == ECHO) {
                y = echo(y);
            } else if (fx == REVERB) {
                y = reverb(y);
            } else if (fx == FLANGER) {
                y = flanger(y);
            } else if (fx == PHASER) {
                y = phaser(y);
            } else if (fx == ROLL) {
                y = roll(y);
            } else if (fx == TRANS) {
                y = trans(y);
            }

            lfoPhase += lfoSpeed;
            if (lfoPhase > Math.PI * 2) lfoPhase -= Math.PI * 2;
            buf[i] = (float) y;
        }
        gLow = tLow;
        gMid = tMid;
        gHigh = tHigh;
        send = sendTarget;
    }

    private double flanger(double x) {
        flangerBuf[flangerAt] = (float) x;
        double mod = (Math.sin(lfoPhase * 2.0) * 0.5 + 0.5) * (flangerBuf.length - 2) + 1;
        int read = flangerAt - (int) mod;
        if (read < 0) read += flangerBuf.length;
        double delayed = flangerBuf[read];
        if (++flangerAt == flangerBuf.length) flangerAt = 0;
        return x * (1.0 - send * 0.5) + delayed * send * 0.5;
    }

    private double phaser(double x) {
        double freq = 500.0 + (Math.sin(lfoPhase) * 0.5 + 0.5) * 3500.0;
        phaser1.allPass(freq, rate, 1.0);
        phaser2.allPass(freq * 1.5, rate, 1.0);
        double phased = phaser2.run(phaser1.run(x));
        return x * (1.0 - send * 0.5) + phased * send * 0.5;
    }

    private double roll(double x) {
        if (send < 0.05f) {
            rollBuf[rollAt] = (float) x;
            if (++rollAt >= rollLen) rollAt = 0;
            return x;
        }
        double looped = rollBuf[rollAt];
        if (++rollAt >= rollLen) rollAt = 0;
        return x * (1.0 - send) + looped * send;
    }

    private double trans(double x) {
        double tremolo = Math.sin(lfoPhase * 4.0) > 0 ? 1.0 : 0.0;
        return x * ((1.0 - send) + tremolo * send);
    }

    private double echo(double x) {
        int read = echoAt - echoLen;
        if (read < 0) read += echo.length;
        double delayed = echo[read];
        echo[echoAt] = (float) (x * send + delayed * 0.55);
        if (++echoAt == echo.length) echoAt = 0;
        return x + delayed * 0.8;
    }

    private double reverb(double x) {
        double in = x * send * 0.25, wet = 0;
        for (int c = 0; c < 4; c++) {
            float[] line = combs[c];
            double out = line[combAt[c]];
            // Damped: the highs die sooner, like a real room.
            combLow[c] = (float) (out * 0.7 + combLow[c] * 0.3);
            line[combAt[c]] = (float) (in + combLow[c] * 0.84);
            if (++combAt[c] == line.length) combAt[c] = 0;
            wet += out;
        }
        for (int p = 0; p < 2; p++) {
            float[] line = passes[p];
            double stored = line[passAt[p]];
            double out = -wet + stored;
            line[passAt[p]] = (float) (wet + stored * 0.5);
            if (++passAt[p] == line.length) passAt[p] = 0;
            wet = out;
        }
        return x + wet * 0.6;
    }

    private void clearTails() {
        java.util.Arrays.fill(echo, 0f);
        java.util.Arrays.fill(flangerBuf, 0f);
        java.util.Arrays.fill(rollBuf, 0f);
        for (float[] c : combs) java.util.Arrays.fill(c, 0f);
        for (float[] p : passes) java.util.Arrays.fill(p, 0f);
        java.util.Arrays.fill(combLow, 0f);
    }

    /**
     * An amp's peak limiter: no look-ahead (so every speaker of a song keeps the same timing and the
     * crossover still sums), the gain drops at once to keep a peak under the ceiling and comes back
     * up over about 80 ms.
     */
    public static final class Limiter {
        public static final float CEILING = 0.97f;
        private final float release;
        private float gain = 1f, reduction = 1f;

        public Limiter(double rate) {
            release = (float) (1 - Math.exp(-1 / (0.08 * rate)));
        }

        public void process(float[] buf, int n) {
            for (int i = 0; i < n; i++) {
                float peak = Math.abs(buf[i]);
                float want = peak > CEILING ? CEILING / peak : 1f;
                gain = Math.min(want, gain + (1f - gain) * release);
                buf[i] *= gain;
                reduction = Math.min(reduction, gain);
            }
        }

        /** The lowest gain since the last call (1 = not limiting), then starts over. */
        public float takeReduction() {
            float r = reduction;
            reduction = 1f;
            return r;
        }
    }

    /**
     * One side of an amp rack's crossover: a Linkwitz-Riley low-pass for the subs or high-pass for
     * the tops, at the same corner, so the two add back up flat.
     */
    public static final class Crossover {
        private final double rate;
        private Biquad[] pair;
        private boolean lowPass;
        private double f;

        public Crossover(double rate) {
            this.rate = rate;
        }

        /** Retunes only on a change; turned from one side to the other it starts from rest. */
        public void set(boolean lowPass, double f) {
            if (pair != null && lowPass == this.lowPass && f == this.f) return;
            if (pair == null || lowPass != this.lowPass) pair = lr4(lowPass, f);
            this.lowPass = lowPass;
            this.f = f;
            for (Biquad b : pair) {
                if (lowPass) b.lowPass(f, rate, Math.sqrt(0.5));
                else b.highPass(f, rate, Math.sqrt(0.5));
            }
        }

        public void process(float[] buf, int n) {
            for (int i = 0; i < n; i++) buf[i] = (float) run(pair, buf[i]);
        }
    }

    /** One RBJ biquad, transposed direct form II. */
    private static final class Biquad {
        private double b0 = 1, b1, b2, a1, a2, s1, s2;
        /** For the crossovers: the corner, negative for a high-pass. */
        double lowPassAt;

        void lowPass(double f, double rate, double q) {
            double w = 2 * Math.PI * Math.min(f, rate * 0.45) / rate, cos = Math.cos(w), alpha = Math.sin(w) / (2 * q), a0 = 1 + alpha;
            b0 = (1 - cos) / 2 / a0;
            b1 = (1 - cos) / a0;
            b2 = b0;
            a1 = -2 * cos / a0;
            a2 = (1 - alpha) / a0;
        }

        void highPass(double f, double rate, double q) {
            double w = 2 * Math.PI * Math.min(f, rate * 0.45) / rate, cos = Math.cos(w), alpha = Math.sin(w) / (2 * q), a0 = 1 + alpha;
            b0 = (1 + cos) / 2 / a0;
            b1 = -(1 + cos) / a0;
            b2 = b0;
            a1 = -2 * cos / a0;
            a2 = (1 - alpha) / a0;
        }

        void bandPass(double f, double rate, double q) {
            double w = 2 * Math.PI * Math.min(f, rate * 0.45) / rate, cos = Math.cos(w), alpha = Math.sin(w) / (2 * q), a0 = 1 + alpha;
            b0 = alpha / a0;
            b1 = 0;
            b2 = -alpha / a0;
            a1 = -2 * cos / a0;
            a2 = (1 - alpha) / a0;
        }

        void allPass(double f, double rate, double q) {
            double w = 2 * Math.PI * Math.min(f, rate * 0.45) / rate, cos = Math.cos(w), alpha = Math.sin(w) / (2 * q), a0 = 1 + alpha;
            b0 = (1 - alpha) / a0;
            b1 = -2 * cos / a0;
            b2 = (1 + alpha) / a0;
            a1 = -2 * cos / a0;
            a2 = (1 - alpha) / a0;
        }

        double run(double x) {
            double y = b0 * x + s1;
            s1 = b1 * x - a1 * y + s2;
            s2 = b2 * x - a2 * y;
            return y;
        }
    }
}
