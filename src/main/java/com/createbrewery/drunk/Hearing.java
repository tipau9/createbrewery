package com.createbrewery.drunk;

/**
 * What loud bass does to your ears, as numbers: the dose a night in front of the subs builds up,
 * the ringing and dull hearing of a tinnitus, and the crackle an overdriven box makes right next
 * to it. Plain Java, no game classes; see {@link HearingClient} and {@link HearingDose}.
 *
 * <p>Dose is bass (0..1) over distance in blocks, summed each second: full bass a block from a
 * sub is 1 a second, four blocks away a quarter. Game time is compressed, so is the night.
 */
public final class Hearing {
    private Hearing() {}

    /** Dose that sets off a tinnitus: five minutes a block from the subs, twenty at four blocks. */
    public static final float LIMIT = 300f;
    /** Dose healed a second, always: ten blocks out (0.1 a second) the ears just keep up. */
    public static final float RECOVERY = 0.1f;
    /** What one second can add, at most (the server's bound on what a client claims). */
    public static final float MAX_PER_SECOND = 4f;
    /** How long a tinnitus rings, in ticks (three minutes); set off again it starts over. */
    public static final int TINNITUS_TICKS = 3600;
    /** Blocks within which an overdriven box crackles in your ears. */
    public static final double HARSH_REACH = 6.0;

    /** The tinnitus whistle, Hz, and how loud: quiet, but it does not stop. */
    public static final double RING_HZ = 6000;
    public static final float RING_GAIN = 0.05f;
    /** Earplugs: 15 % quieter. */
    public static final float PLUGGED = 0.85f;

    /** A rack zone pushed this far (dB) past the limiter's ceiling crackles fully. */
    private static final double HARSH_DB = 6.0;

    /** Dull hearing at full tinnitus: the low-pass corner in Hz, 20 kHz (open) down to 800. */
    public static double cutoff(float tinnitus) {
        return Math.exp(Math.log(20000) + (Math.log(800) - Math.log(20000)) * clamp01(tinnitus));
    }

    /**
     * The same for a sound the game plays (OpenAL's own filter, a shelf above ~5 kHz): what is
     * left of the highs.
     */
    // ponytail: OpenAL's low-pass corner is fixed at 5 kHz, so game sounds only get a dull shelf, not 800 Hz; a loopback mixer would do it properly.
    public static float gainHf(float tinnitus) {
        return 1f - 0.95f * clamp01(tinnitus);
    }

    /** One second of dose taken in: what is left after healing, never below 0. */
    public static float step(float dose, float taken) {
        return Math.max(0f, dose + Math.max(0f, Math.min(MAX_PER_SECOND, taken)) - RECOVERY);
    }

    /** What a sub at {@code distance} blocks adds a second with {@code bass} (0..1) and its drive. */
    public static float exposure(float bass, float drive, double distance) {
        return (float) (Math.max(0f, bass) * Math.max(0f, drive) / Math.max(1.0, distance));
    }

    /**
     * How much an overdriven box crackles at {@code distance} blocks, 0..1: nothing until its
     * signal is pushed past the limiter's {@code ceiling}, full {@value #HARSH_DB} dB over and close.
     */
    public static float harsh(float peak, float ceiling, double distance) {
        if (peak <= ceiling || ceiling <= 0) return 0f;
        double over = 20 * Math.log10(peak / ceiling) / HARSH_DB;
        return (float) (clamp01(over) * clamp01(1 - distance / HARSH_REACH));
    }

    /** A sample as torn ears hear it: soft-clipped harder and bit-crushed coarser with {@code amount}. */
    public static float crackle(float x, float amount) {
        if (amount <= 0f) return x;
        double k = 1 + 8 * amount;
        double y = Math.tanh(k * x) / Math.tanh(k);
        double levels = Math.pow(2, 12 - 8 * amount);
        y = Math.round(y * levels) / levels;
        return (float) (x + (y - x) * amount);
    }

    /** One second of the whistle at {@code rate} as 16-bit mono: a whole number of cycles, so it loops without a click. */
    public static short[] ring(int rate) {
        short[] out = new short[rate];
        for (int i = 0; i < rate; i++) out[i] = (short) Math.round(Math.sin(2 * Math.PI * RING_HZ * i / rate) * 0.5 * 32767);
        return out;
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
