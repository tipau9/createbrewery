package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrailOffTest {
    @Test
    void speechTrailsOffButKeepsItsStart() {
        Random random = new Random(7);
        boolean cut = false;
        for (int i = 0; i < 50; i++) {
            String said = Intoxication.trailOff("Hallo zusammen, kommt ihr mit in die Mine!", random);
            assertTrue(said.startsWith("Hallo"), said);
            assertFalse(said.contains("!"), said);
            cut |= !said.contains("Mine");
        }
        assertTrue(cut, "never lost the thread");
    }

    @Test
    void kokshypeShoutsButKeepsTheWords() {
        Random random = new Random(3);
        for (int i = 0; i < 50; i++) {
            String said = Intoxication.hype("ich hab eine idee. hört zu", random);
            assertTrue(said.toLowerCase().startsWith("ich hab eine idee! hört zu!"), said);
        }
    }

    @Test
    void moreSendsRaisesOrAddsTheAttribute() {
        int sends = 0x20003;
        // Sound Physics asked for 4: raised to 6, the rest kept.
        assertTrue(java.util.Arrays.equals(new int[] {1, 2, sends, 6, 0},
            Intoxication.withSends(new int[] {1, 2, sends, 4, 0}, sends, 6)));
        // Nobody asked: added. A trailing zero-filled tail is dropped.
        assertTrue(java.util.Arrays.equals(new int[] {1, 2, sends, 6, 0},
            Intoxication.withSends(new int[] {1, 2, 0, 0, 0}, sends, 6)));
        // Asked for more already: left alone.
        assertTrue(java.util.Arrays.equals(new int[] {sends, 8, 0}, Intoxication.withSends(new int[] {sends, 8, 0}, sends, 6)));
        assertTrue(java.util.Arrays.equals(new int[] {sends, 6, 0}, Intoxication.withSends(new int[0], sends, 6)));
    }
}
