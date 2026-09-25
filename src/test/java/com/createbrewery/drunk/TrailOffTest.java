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
}
