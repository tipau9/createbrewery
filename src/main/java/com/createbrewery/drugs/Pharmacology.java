package com.createbrewery.drugs;

/**
 * The pure maths behind the drugs, like {@link com.createbrewery.drunk.Intoxication} is for
 * alcohol. Game time runs about 20-25 times faster than real pharmacology: a Koks high of an
 * hour in real life lasts three minutes here, and its come-up and fade shrink with it.
 */
public final class Pharmacology {
    private Pharmacology() {}

    /** Cardiac load from which the heart races (warning), strains, and can give out. */
    public static final float HEART_RACING = 1.0f;
    public static final float HEART_STRAINED = 1.5f;
    public static final float HEART_CRITICAL = 2.0f;

    /**
     * 0..1 how strongly a dose acts right now: it comes up over {@code onset} ticks after the
     * first dose (smoothly, like a drug reaching the brain), holds, and fades over the last
     * {@code fade} ticks. A top-up while already high does not restart the come-up.
     */
    public static float strength(int elapsed, int remaining, int onset, int fade, boolean topUp) {
        float up = topUp || onset <= 0 ? 1f : smoothstep(elapsed / (float) onset);
        float down = fade <= 0 ? 1f : clamp01(remaining / (float) fade);
        return up * down;
    }

    /**
     * How strong {@code amplifier + 1} stacked doses feel, 0..1: every further line or joint adds
     * half as much as the one before (acute tolerance), so chasing the first high never works.
     * The strain on the heart does not share this discount - see {@link #heartLoad}.
     */
    public static float dosesFelt(int amplifier) {
        return amplifier < 0 ? 0f : feltFor(amplifier + 1);
    }

    /** The same for a fractional number of doses (a few hits of a joint), capped at 1. */
    public static float feltFor(float doses) {
        if (doses <= 0f) return 0f;
        return Math.min(1f, (1f - (float) Math.pow(0.5, doses)) / (1f - 0.0625f));
    }

    /**
     * The load on the heart the body is heading for. Amplifiers are -1 when the drug is absent,
     * strengths 0..1 as from {@link #strength}.
     *
     * <ul>
     *   <li>Koks: each line adds the same strain, whatever it still does for the mood.</li>
     *   <li>Koks with alcohol: the liver makes cocaethylene, harder on the heart than either.</li>
     *   <li>Koks with Keta (CK): Keta raises pulse and blood pressure on its own, and together
     *       they add up further.</li>
     *   <li>Weed: a faster pulse; with Koks on top, more so.</li>
     *   <li>Sprinting and heat make a stimulated heart work harder still.</li>
     * </ul>
     */
    public static float heartLoad(int cokeAmp, float coke, int ketaAmp, float keta, float weed,
                                  float blood, boolean exertion, boolean hot) {
        float cokeLoad = cokeAmp < 0 ? 0f : 0.45f * (cokeAmp + 1) * coke;
        float ketaLoad = ketaAmp < 0 ? 0f : 0.1f * (ketaAmp + 1) * keta;
        float load = cokeLoad + ketaLoad + 0.15f * weed;
        if (cokeLoad > 0f) {
            load += 0.25f * Math.min(blood, 2.5f) * coke; // cocaethylene
            load += 0.35f * keta * coke;                  // CK
            load += 0.15f * weed * coke;
        }
        if (exertion) load *= 1.35f;
        if (hot) load *= 1.2f;
        return load;
    }

    /**
     * Extra heart load from MDMA and Meth, on top of {@link #heartLoad}. Meth is by far the
     * hardest on the heart: every stacked dose adds its full strain, and heat makes it worse.
     */
    public static float stimulantLoad(float mdma, int methAmp, float meth, float heat) {
        float load = 0.35f * mdma + (methAmp < 0 ? 0f : 0.5f * (methAmp + 1) * meth);
        return load * (1f + 0.4f * Math.max(0f, heat));
    }

    /** Body heat above normal from which you overheat (Hitzschlag) and take damage. */
    public static final float OVERHEATED = 1.0f;

    /**
     * Body heat after one second. MDMA (and Meth) drive it up, dancing and sprinting far more,
     * heat around you too; water and rest bring it back down. 0 is a normal body.
     */
    public static float heatStep(float heat, float stimulated, boolean exertion, boolean hot, boolean inWater) {
        float gain = stimulated * (exertion ? 0.035f : 0.006f) * (hot ? 2f : 1f);
        float loss = inWater ? 0.08f : 0.01f;
        return Math.max(0f, heat + gain - loss);
    }

    /** Chance per second that a strained heart gives out. */
    public static float heartAttackChance(float load) {
        if (load >= HEART_CRITICAL) return 0.03f + 0.12f * (load - HEART_CRITICAL);
        return load >= 1.4f ? 0.003f : 0f;
    }

    private static float smoothstep(float x) {
        x = clamp01(x);
        return x * x * (3f - 2f * x);
    }

    private static float clamp01(float x) {
        return Math.max(0f, Math.min(1f, x));
    }
}
