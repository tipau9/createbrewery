package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A night at the subs rings your ears; one further back does not; the whistle loops clean. */
class HearingTest {
    /** Seconds until the limit, taking {@code perSecond} each second; -1 if never within a day. */
    private static int secondsToLimit(float perSecond) {
        float dose = 0f;
        for (int s = 1; s < 86400; s++) {
            dose = Hearing.step(dose, perSecond);
            if (dose >= Hearing.LIMIT) return s;
        }
        return -1;
    }

    @Test
    void closeToTheSubsRingsWithinMinutes() {
        int s = secondsToLimit(Hearing.exposure(1f, 1f, 1));
        assertTrue(s > 200 && s < 600, "a block from the subs: " + s + " s");
        // Nose against the box is no worse than a block away.
        assertEquals(Hearing.exposure(1f, 1f, 1), Hearing.exposure(1f, 1f, 0.1), 1e-6);
    }

    @Test
    void theBackOfTheRoomIsSafe() {
        assertEquals(-1, secondsToLimit(Hearing.exposure(1f, 1f, 12)));
    }

    @Test
    void aClientCannotClaimMore() {
        assertEquals(Hearing.MAX_PER_SECOND - Hearing.RECOVERY, Hearing.step(0f, 1e9f), 1e-6);
        assertEquals(0f, Hearing.step(0f, -50f), 1e-6);
    }

    @Test
    void dullHearingGoesDownTo800Hz() {
        assertEquals(20000, Hearing.cutoff(0f), 1);
        assertEquals(800, Hearing.cutoff(1f), 1);
        assertEquals(1f, Hearing.gainHf(0f), 1e-6);
    }

    @Test
    void onlyAnOverdrivenBoxCloseByCrackles() {
        assertEquals(0f, Hearing.harsh(0.9f, 0.97f, 1));
        assertEquals(0f, Hearing.harsh(3f, 0.97f, 10));
        assertTrue(Hearing.harsh(3f, 0.97f, 1) > 0.5f);
        assertEquals(0.3f, Hearing.crackle(0.3f, 0f));
    }

    @Test
    void theWhistleLoopsWithoutAClick() {
        for (int rate : new int[] {44100, 48000}) {
            short[] ring = Hearing.ring(rate);
            // The sample after the last is the first again: continuing the sine one step lands on it.
            double next = Math.sin(2 * Math.PI * Hearing.RING_HZ * rate / rate) * 0.5 * 32767;
            assertEquals(ring[0], next, 1, rate + " Hz");
        }
    }
}
