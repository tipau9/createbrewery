package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A stack of speakers is not eight times as loud as one. */
class PaLevelTest {
    @Test
    void oneSpeakerPlaysAtFullLevel() {
        assertEquals(1f, PaLevel.stackGain(1));
    }

    @Test
    void nobodyNearIsNotADivisionByZero() {
        assertEquals(1f, PaLevel.stackGain(0));
        assertEquals(1f, PaLevel.stackGain(-3));
    }

    @Test
    void levelFallsWithTheSquareRootOfTheCount() {
        assertEquals(0.5f, PaLevel.stackGain(4), 1e-6);
        assertEquals((float) (1 / Math.sqrt(8)), PaLevel.stackGain(8), 1e-6);
    }

    @Test
    void moreSpeakersNeverPlayLouderEach() {
        float last = 2f;
        for (int n = 0; n <= 32; n++) {
            float g = PaLevel.stackGain(n);
            assertTrue(g <= last, "rose at " + n);
            last = g;
        }
    }
}
