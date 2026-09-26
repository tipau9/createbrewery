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
        return eliminated(blood, 0f);
    }

    /** Same, for a body used to alcohol: the liver of a regular drinker works up to 30 % faster. */
    public static float eliminated(float blood, float tolerance) {
        return Math.max(0f, blood - eliminationRate(tolerance));
    }

    public static float eliminationRate(float tolerance) {
        return ELIMINATE_PER_TICK * (1f + 0.3f * clamp01(tolerance));
    }

    /** Ticks until completely sober, counting alcohol still waiting in the stomach. */
    public static int ticksUntilSober(float blood, float stomach) {
        return ticksUntilSober(blood, stomach, 0f);
    }

    public static int ticksUntilSober(float blood, float stomach, float tolerance) {
        float total = Math.max(0f, blood) + Math.max(0f, stomach);
        if (total <= 0f) return 0;
        return (int) Math.ceil(total / eliminationRate(tolerance));
    }

    // ---- tolerance ----
    // 0..1. Built up by time spent with alcohol in the blood, lost again during sober days.
    // It changes how drunk you FEEL, not how much alcohol is in you: poisoning still follows the
    // real blood level, which is exactly why a high tolerance is dangerous.

    /** Tolerance gained per tick, per per-mille in the blood, scaled by what is still to gain. */
    public static final float TOLERANCE_GAIN = 3.5e-5f;
    /** Half of the tolerance is gone after 3 sober Minecraft days. */
    public static final float TOLERANCE_HALF_LIFE = 72000f;

    /** The blood level as the body experiences it: up to 40 % less for a seasoned drinker. */
    public static float felt(float blood, float tolerance) {
        return blood * (1f - 0.4f * clamp01(tolerance));
    }

    /** Tolerance after one tick at this blood level; grows ever more slowly towards 1. */
    public static float toleranceAfterTick(float tolerance, float blood) {
        if (blood <= 0f) return tolerance;
        return clamp01(tolerance + TOLERANCE_GAIN * blood * (1f - tolerance));
    }

    /** Tolerance left after {@code soberTicks} without alcohol. */
    public static float toleranceDecayed(float tolerance, long soberTicks) {
        if (soberTicks <= 0 || tolerance <= 0f) return tolerance;
        float left = tolerance * (float) Math.pow(0.5, soberTicks / TOLERANCE_HALF_LIFE);
        return left < 0.001f ? 0f : left;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
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

    /**
     * Xanax: slow, flat speech that loses the thread - pauses between words, no exclamation marks,
     * and often it just stops halfway.
     */
    public static String trailOff(String text, java.util.Random random) {
        String[] words = text.replace('!', '.').split(" ");
        int keep = random.nextFloat() < 0.4f ? Math.max(1, (int) (words.length * (0.4f + 0.3f * random.nextFloat()))) : words.length;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keep; i++) {
            if (i > 0) sb.append(random.nextFloat() < 0.3f ? "… " : " ");
            sb.append(words[i]);
        }
        if (keep < words.length) {
            String[] lost = {"…", "… äh…", "… was wollt ich sagen?", "… egal."};
            sb.append(lost[random.nextInt(lost.length)]);
        }
        return sb.toString();
    }

    /**
     * Koks: loud and full of yourself - words in capitals, every sentence an exclamation, and
     * often one more thing about yourself on top.
     */
    public static String hype(String text, java.util.Random random) {
        StringBuilder sb = new StringBuilder();
        for (String word : text.replace('.', '!').split(" ")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(random.nextFloat() < 0.2f ? word.toUpperCase(java.util.Locale.GERMAN) : word);
        }
        if (!sb.toString().endsWith("!")) sb.append('!');
        if (random.nextFloat() < 0.35f) {
            String[] ego = {" Ehrlich, ich bin genial.", " Hört mir zu!", " Und noch was—", " Ich hab das voll im Griff.",
                " Keiner kann das so wie ich."};
            sb.append(ego[random.nextInt(ego.length)]);
        }
        return sb.toString();
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


    /**
     * OpenAL context attributes (key-value pairs, ended by 0) with {@code key} raised to at least
     * {@code value}, or added. Anything after the ending 0 is dropped.
     */
    public static int[] withSends(int[] attributes, int key, int value) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        boolean found = false;
        for (int i = 0; i + 1 < attributes.length && attributes[i] != 0; i += 2) {
            int v = attributes[i + 1];
            if (attributes[i] == key) {
                v = Math.max(v, value);
                found = true;
            }
            out.add(attributes[i]);
            out.add(v);
        }
        if (!found) {
            out.add(key);
            out.add(value);
        }
        out.add(0);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
}
