package com.createbrewery.block.club;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the {@code max} nearest of the keys offered during a tick: those may use a scarce resource
 * (a Veil light) the next tick. Free of Minecraft classes.
 */
final class LightBudget<K> {
    private final int max;
    private final List<Object[]> offers = new ArrayList<>();
    private Set<K> allowed = new HashSet<>();

    LightBudget(int max) {
        this.max = max;
    }

    void offer(K key, double distSq) {
        offers.add(new Object[] {key, distSq});
    }

    /** Call once a tick after every offer: chooses who may use the resource until the next call. */
    @SuppressWarnings("unchecked")
    void endTick() {
        offers.sort((a, b) -> Double.compare((Double) a[1], (Double) b[1]));
        Set<K> next = new HashSet<>();
        for (int i = 0; i < Math.min(max, offers.size()); i++) next.add((K) offers.get(i)[0]);
        allowed = next;
        offers.clear();
    }

    boolean allowed(K key) {
        return allowed.contains(key);
    }
}
