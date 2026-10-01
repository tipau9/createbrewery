package com.createbrewery.drunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which of a song's speakers may run this frame (each is an OpenAL source with its own DSP): the
 * {@code max} nearest, with the subwoofers - few and carrying the bass - always among them.
 * Free of Minecraft classes.
 */
final class EmitterBudget {
    private EmitterBudget() {}

    record Offer<K>(K key, double distSq, boolean always) {}

    /** At most this many offers are kept for being "always" (subwoofers). */
    static final int MAX_ALWAYS = 4;
    /** One already playing counts as this much nearer (0.8 squared), so a tie at the border does not flap. */
    static final double STICKY = 0.64;

    static <K> Set<K> choose(List<Offer<K>> offers, int max, Set<K> running) {
        List<Offer<K>> sorted = new ArrayList<>(offers);
        sorted.sort(Comparator.comparingDouble(o -> running.contains(o.key()) ? o.distSq() * STICKY : o.distSq()));
        Set<K> kept = new LinkedHashSet<>();
        int always = 0;
        for (Offer<K> o : sorted) {
            if (o.always() && always < MAX_ALWAYS && kept.size() < max) {
                kept.add(o.key());
                always++;
            }
        }
        for (Offer<K> o : sorted) {
            if (kept.size() >= max) break;
            kept.add(o.key());
        }
        return kept;
    }
}
