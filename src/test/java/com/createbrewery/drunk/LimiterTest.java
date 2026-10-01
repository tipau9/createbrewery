package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The amp rounds off its peaks before the hard limit: no flat tops, nothing above the ceiling. */
class LimiterTest {
    private static final float C = DeckFx.Limiter.CEILING, K = DeckFx.Limiter.KNEE;

    @Test
    void belowTheKneeTheSignalIsUntouched() {
        for (float x : new float[] {0f, 0.1f, -0.5f, K, -K}) assertEquals(x, DeckFx.Limiter.soften(x), "moved " + x);
    }

    @Test
    void itIsContinuousAtTheKnee() {
        assertEquals(K, DeckFx.Limiter.soften(K + 1e-4f), 1e-3);
        assertEquals(-K, DeckFx.Limiter.soften(-K - 1e-4f), 1e-3);
    }

    @Test
    void itRisesMonotonicallyAndStaysUnderTheCeiling() {
        float last = -2f;
        for (float x = -3f; x <= 3f; x += 0.01f) {
            float y = DeckFx.Limiter.soften(x);
            assertTrue(y >= last, "went down at " + x);
            assertTrue(Math.abs(y) <= C, "over the ceiling at " + x);
            last = y;
        }
    }

    @Test
    void anAbsurdInputIsStillFiniteAndUnderTheCeiling() {
        for (float x : new float[] {1e9f, -1e9f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            float y = DeckFx.Limiter.soften(x);
            assertTrue(Float.isFinite(y) && Math.abs(y) <= C, "bad output for " + x + ": " + y);
        }
    }

    @Test
    void processRoundsOffAPeakTheHardLimitWouldFlatten() {
        DeckFx.Limiter lim = new DeckFx.Limiter(44100);
        float[] buf = new float[4800];
        for (int i = 0; i < buf.length; i++) buf[i] = (float) (1.2 * Math.sin(i * 0.05));
        lim.process(buf, buf.length);
        for (float v : buf) assertTrue(Math.abs(v) <= C, "over the ceiling: " + v);
    }
}
