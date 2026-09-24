package com.createbrewery.drunk;

/**
 * Pure blood-alcohol maths, no Minecraft types, so it can be unit-tested headlessly
 * (same pattern as {@code FermentationProgress}).
 *
 * <p>Everything is in per-mille (‰) blood alcohol. A drink does not hit the blood at once: it
 * lands in the stomach and is absorbed over roughly 20 seconds, so a beer "kicks in" rather
 * than switching the player's state instantly. The liver then removes alcohol at a fixed rate,
 * compressed from the real ~0.15‰/hour to 0.1‰ per 30 seconds so a session plays out in minutes.
 */
public final class Intoxication {
    private Intoxication() {}

    /** One beer. Six beers reach the blackout zone, seven are refused. */
    public static final float PER_BEER = 0.5f;
    /** ‰ moved from stomach to blood per tick: one beer is fully absorbed after 400 ticks. */
    public static final float ABSORB_PER_TICK = PER_BEER / 400f;
    /** ‰ removed from the blood per tick: 0.1‰ per 600 ticks (30 s). */
    public static final float ELIMINATE_PER_TICK = 0.1f / 600f;
    /** Above this total (blood + stomach) the player cannot get another drop down. */
    public static final float MAX_DRINKABLE = 3.0f;

    // Symptom thresholds, in ‰. Each beer (0.5‰) crosses roughly one of them.
    public static final float TIPSY = 0.3f;        // beer 1: sway, warm vision
    public static final float MERRY = 0.8f;        // beer 2: weaving walk, aim drift, hiccups, slurring
    public static final float DRUNK = 1.3f;        // beer 3: double vision, eyelids, delirium + aggro
    public static final float WASTED = 1.8f;       // beer 4: sluggish mouse, tripping, vomiting
    public static final float SMASHED = 2.4f;      // beer 5: micro-sleep
    public static final float BLACKOUT = 2.9f;     // beer 6: blackouts
    /** A peak at or above this earns a hangover once the player sobers up or sleeps. */
    public static final float HANGOVER_PEAK = 1.3f;

    /** How much of the stomach content reaches the blood this tick. */
    public static float absorbed(float stomach) {
        if (stomach <= 0f) return 0f;
        return Math.min(stomach, ABSORB_PER_TICK);
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

    /** 0..1 strength of the visual effects: nothing below a light buzz, full at the blackout zone. */
    public static float visualIntensity(float blood) {
        float t = (blood - TIPSY) / (BLACKOUT - TIPSY);
        return Math.max(0f, Math.min(1f, t));
    }

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
        return (int) (peak * 2400f); // 1.3‰ -> ~2.6 min, 3.0‰ -> 6 min
    }
}
