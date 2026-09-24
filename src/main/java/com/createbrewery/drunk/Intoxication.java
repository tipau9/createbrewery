package com.createbrewery.drunk;

/**
 * Pure blood-alcohol maths, no Minecraft types, so it can be unit-tested headlessly
 * (same pattern as {@code FermentationProgress}).
 *
 * <p>Everything is in per-mille (‰) blood alcohol and runs on Minecraft time, where one hour is
 * 1000 ticks (50 s). With that clock the real pharmacology fits as-is: a drink is absorbed from
 * the stomach first-order with a half-life of about 15 minutes, so the level peaks 30-60 minutes
 * after drinking, slower on a full stomach; the liver removes a fixed 0.15‰ per hour (Widmark).
 */
public final class Intoxication {
    private Intoxication() {}

    /**
     * One beer (0.5 l, 5 %): about 0.3 per mille for an adult man, as in real life. Tipsy after
     * one, merry after three, properly drunk after five; poisoning takes a dozen.
     */
    public static final float PER_BEER = 0.3f;
    /** Share of the stomach absorbed per tick on an empty stomach: half-life 250 ticks (15 MC minutes). */
    public static final float ABSORB_SHARE_PER_TICK = 0.693f / 250f;
    /** Floor so the last sip does not trickle in forever. */
    public static final float ABSORB_MIN_PER_TICK = 0.0002f;
    /** ‰ removed from the blood per tick: 0.15‰ per Minecraft hour (1000 ticks). */
    public static final float ELIMINATE_PER_TICK = 0.15f / 1000f;
    /** Shared cooldown between two alcoholic drinks, like an Ender Pearl (20 ticks = 1 s). */
    public static final int DRINK_COOLDOWN = 20;

    // Symptom thresholds, in ‰.
    public static final float TIPSY = 0.3f;        // beer 1: sway, warm vision
    public static final float MERRY = 0.8f;        // beer 2: weaving walk, aim drift, hiccups, slurring
    public static final float DRUNK = 1.3f;        // beer 3: double vision, eyelids, delirium + aggro
    public static final float WASTED = 1.8f;       // beer 4: sluggish mouse, tripping, vomiting
    public static final float SMASHED = 2.4f;      // beer 5: micro-sleep
    public static final float BLACKOUT = 2.9f;     // beer 6: blackouts
    public static final float POISONING = 3.3f;     // beer 7: alcohol poisoning, damage over time
    /** A peak at or above this earns a hangover (a real session, about three beers). */
    public static final float HANGOVER_PEAK = 0.8f;
    /** On the way down, the hangover starts once the blood level falls below this. */
    public static final float HANGOVER_ONSET = 0.3f;

    /** How much of the stomach content reaches the blood this tick, on an empty stomach. */
    public static float absorbed(float stomach) {
        return absorbed(stomach, 0);
    }

    /** Same, but food slows it down: a full stomach (food 20) absorbs at half speed. */
    public static float absorbed(float stomach, int foodLevel) {
        if (stomach <= 0f) return 0f;
        float fed = 1f - 0.5f * Math.max(0, Math.min(20, foodLevel)) / 20f;
        return Math.min(stomach, Math.max(stomach * ABSORB_SHARE_PER_TICK * fed, ABSORB_MIN_PER_TICK));
    }

    /**
     * 0..1 how strong the "beer goggles" are: fades in with the first beer, full from ~0.45 per
     * mille, gone again once properly drunk (~1.5).
     */
    public static float mood(float blood) {
        return smoothstep(0.15f, 0.45f, blood) * (1f - smoothstep(1.0f, 1.5f, blood));
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    /** Party zone: one or two beers make you better company, not worse. */
    public static boolean inGoodMood(float blood) {
        return blood >= TIPSY && blood < DRUNK;
    }

    /** Blood level after one tick of elimination; never negative. */
    public static float eliminated(float blood) {
        return Math.max(0f, blood - ELIMINATE_PER_TICK);
    }

    /** Ticks until completely sober, counting alcohol still waiting in the stomach. */
    public static int ticksUntilSober(float blood, float stomach) {
        float total = Math.max(0f, blood) + Math.max(0f, stomach);
        if (total <= 0f) return 0;
        return (int) Math.ceil(total / ELIMINATE_PER_TICK);
    }

    /**
     * 0..1 strength of the visual effects: nothing below a light buzz, full at the blackout zone.
     * Square-root curve, so the first beers are already clearly felt.
     */
    public static float visualIntensity(float blood) {
        return ramp(blood, TIPSY);
    }

    /** 0 below {@code from}, rising steeply at first and reaching 1 at the blackout zone. */
    public static float ramp(float blood, float from) {
        float t = (blood - from) / (BLACKOUT - from);
        return (float) Math.sqrt(Math.max(0f, Math.min(1f, t)));
    }

    /** Health lost every {@link #POISON_INTERVAL} ticks; 0 below the poisoning threshold. */
    public static float poisonDamage(float blood) {
        if (blood < POISONING) return 0f;
        return 1f + (blood - POISONING) * 2f;
    }

    public static final int POISON_INTERVAL = 100;

    /** 0 = speaks normally, 1..3 = increasingly slurred chat. */
    public static int slurStage(float blood) {
        if (blood >= WASTED) return 3;
        if (blood >= DRUNK) return 2;
        if (blood >= MERRY) return 1;
        return 0;
    }

    /** Hangover length in ticks for a given peak; 0 if the peak was too low to earn one. */
    public static int hangoverTicks(float peak) {
        if (peak < HANGOVER_PEAK) return 0;
        return (int) (peak * 3000f); // 0.8 -> 2 min, 2.0 -> 5 min, 3.0 -> 7.5 min
    }

    /** 0 = normal hangover, 1 = the really bad one after a heavy night. */
    public static int hangoverLevel(float peak) {
        return peak >= 2.0f ? 1 : 0;
    }

    /** Coming down from a real session: time for the hangover to set in. */
    public static boolean hangoverStarts(float blood, float stomach, float peak) {
        return peak >= HANGOVER_PEAK && blood < HANGOVER_ONSET && stomach < 0.05f;
    }

}
