package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** An emitter restarts only for real trouble, and not twice within a quarter second. */
class SyncPolicyTest {
    private static final double RATE = 44100;

    private static long frames(double seconds) {
        return (long) (seconds * RATE);
    }

    private static SyncPolicy.Decision decide(SyncPolicy p, double aheadSeconds, double now) {
        return p.decide(true, 5, frames(10) + frames(aheadSeconds), frames(10), RATE, now);
    }

    @Test
    void inStepKeepsAndLeavesThePitchAlone() {
        SyncPolicy.Decision d = decide(new SyncPolicy(), 0.005, 1);
        assertEquals(SyncPolicy.Action.KEEP, d.action());
        assertEquals(1f, d.pitch());
    }

    @Test
    void smallDriftIsCorrectedThroughPitchNotRestarted() {
        SyncPolicy.Decision early = decide(new SyncPolicy(), 0.1, 1);
        assertEquals(SyncPolicy.Action.SERVO, early.action());
        assertTrue(early.pitch() < 1f && early.pitch() >= 1f - SyncPolicy.SERVO_RANGE - 1e-6f, "ahead: slow down, within 2 %");
        SyncPolicy.Decision late = decide(new SyncPolicy(), -0.1, 1);
        assertEquals(SyncPolicy.Action.SERVO, late.action());
        assertTrue(late.pitch() > 1f && late.pitch() <= 1f + SyncPolicy.SERVO_RANGE + 1e-6f, "behind: speed up, within 2 %");
    }

    @Test
    void theServoEasesInInsteadOfJumpingToTheFullShift() {
        // Right at the soft edge the pitch is nearly untouched (a speaker hovering there must not warble) ...
        assertEquals(1f, decide(new SyncPolicy(), 0.021, 1).pitch(), 0.001f);
        // ... and it grows steadily with the drift up to the 2 % limit.
        float last = 1f;
        for (double ahead = 0.021; ahead <= 0.24; ahead += 0.01) {
            float p = decide(new SyncPolicy(), ahead, 1).pitch();
            assertTrue(p <= last + 1e-6f, "pitch rose at " + ahead);
            assertTrue(p >= 1f - SyncPolicy.SERVO_RANGE - 1e-6f);
            last = p;
        }
        assertEquals(1f - SyncPolicy.SERVO_RANGE, decide(new SyncPolicy(), 0.2, 1).pitch(), 1e-6f);
    }

    @Test
    void hugeDriftRestarts() {
        SyncPolicy.Decision d = decide(new SyncPolicy(), 0.5, 1);
        assertEquals(SyncPolicy.Action.RESTART, d.action());
        assertEquals("drift", d.reason());
    }

    @Test
    void aStoppedSourceRestarts() {
        SyncPolicy.Decision d = new SyncPolicy().decide(false, 5, frames(10), frames(10), RATE, 1);
        assertEquals(SyncPolicy.Action.RESTART, d.action());
        assertEquals("stopped", d.reason());
    }

    @Test
    void aSourceThatRanDryRestarts() {
        SyncPolicy.Decision d = new SyncPolicy().decide(true, 0, frames(10), frames(10), RATE, 1);
        assertEquals(SyncPolicy.Action.RESTART, d.action());
        assertEquals("starved", d.reason());
    }

    @Test
    void theFirstRestartNeverWaits() {
        assertEquals(SyncPolicy.Action.RESTART, new SyncPolicy().decide(false, 0, 0, 0, RATE, 0).action());
    }

    @Test
    void restartsAreRateLimited() {
        SyncPolicy p = new SyncPolicy();
        assertEquals(SyncPolicy.Action.RESTART, decide(p, 5, 1.0).action());
        // Still wildly off a tenth of a second later: no second restart, the pitch pulls it back instead.
        SyncPolicy.Decision again = decide(p, 5, 1.1);
        assertNotEquals(SyncPolicy.Action.RESTART, again.action());
        assertEquals(SyncPolicy.Action.RESTART, decide(p, 5, 1.3).action());
    }
}
