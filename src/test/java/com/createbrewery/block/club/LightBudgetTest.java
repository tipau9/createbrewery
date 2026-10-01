package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightBudgetTest {
    @Test
    void keepsTheNearestNOfferedLastTick() {
        LightBudget<String> b = new LightBudget<>(2);
        b.offer("far", 900);
        b.offer("near", 4);
        b.offer("mid", 100);
        assertFalse(b.allowed("near"), "nothing is allowed before the first endTick");
        b.endTick();
        assertTrue(b.allowed("near"));
        assertTrue(b.allowed("mid"));
        assertFalse(b.allowed("far"));
    }

    @Test
    void aKeyThatStopsOfferingLosesItsSlot() {
        LightBudget<String> b = new LightBudget<>(1);
        b.offer("a", 1);
        b.endTick();
        assertTrue(b.allowed("a"));
        b.offer("b", 5);
        b.endTick();
        assertFalse(b.allowed("a"));
        assertTrue(b.allowed("b"));
    }

    @Test
    void tiesGoToTheFirstOffered() {
        LightBudget<String> b = new LightBudget<>(1);
        b.offer("x", 10);
        b.offer("y", 10);
        b.endTick();
        assertTrue(b.allowed("x"));
        assertFalse(b.allowed("y"));
    }
}
