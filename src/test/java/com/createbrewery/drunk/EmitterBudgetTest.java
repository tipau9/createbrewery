package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Which speakers of a song may play: the nearest, subwoofers first, and no flapping at the border. */
class EmitterBudgetTest {
    private static EmitterBudget.Offer<String> top(String key, double dist) {
        return new EmitterBudget.Offer<>(key, dist * dist, false);
    }

    private static EmitterBudget.Offer<String> sub(String key, double dist) {
        return new EmitterBudget.Offer<>(key, dist * dist, true);
    }

    @Test
    void theNearestAreKept() {
        Set<String> kept = EmitterBudget.choose(List.of(top("e", 5), top("b", 2), top("a", 1), top("d", 4), top("c", 3)), 3, Set.of());
        assertEquals(Set.of("a", "b", "c"), kept);
    }

    @Test
    void aFarSubwooferStillPlays() {
        Set<String> kept = EmitterBudget.choose(List.of(top("a", 1), top("b", 2), sub("s", 30)), 2, Set.of());
        assertTrue(kept.contains("s"));
        assertEquals(2, kept.size());
        assertTrue(kept.contains("a"));
    }

    @Test
    void moreSubsThanTheAlwaysLimitKeepTheNearestFourAsSubs() {
        List<EmitterBudget.Offer<String>> offers = List.of(
            sub("s1", 10), sub("s2", 11), sub("s3", 12), sub("s4", 13), sub("s5", 14), sub("s6", 15),
            top("t1", 1), top("t2", 2));
        Set<String> kept = EmitterBudget.choose(offers, 6, Set.of());
        assertEquals(6, kept.size());
        assertTrue(kept.containsAll(Set.of("s1", "s2", "s3", "s4")), "the four nearest subs");
        assertTrue(kept.containsAll(Set.of("t1", "t2")), "the rest of the places go to the nearest");
    }

    @Test
    void aLimitBelowTheAlwaysCountStillHoldsTheLimit() {
        Set<String> kept = EmitterBudget.choose(List.of(sub("s1", 3), sub("s2", 2), sub("s3", 1)), 2, Set.of());
        assertEquals(Set.of("s2", "s3"), kept);
    }

    @Test
    void fewerOffersThanPlacesKeepsAll() {
        assertEquals(Set.of("a", "b"), EmitterBudget.choose(List.of(top("a", 1), top("b", 9)), 12, Set.of()));
    }

    @Test
    void oneAlreadyPlayingWinsAnAlmostTie() {
        List<EmitterBudget.Offer<String>> offers = List.of(top("playing", 10), top("new", 9));
        assertEquals(Set.of("playing"), EmitterBudget.choose(offers, 1, Set.of("playing")));
        // ... but not when the newcomer is clearly nearer.
        assertEquals(Set.of("new"), EmitterBudget.choose(List.of(top("playing", 10), top("new", 4)), 1, Set.of("playing")));
    }
}
