package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClubStateTest {

    private static void tick(ClubState c, float kick, float drop, float tension, boolean playing, ClubState.Mixer m) {
        c.update(0.05f, kick, drop, tension, playing, 0.5, m, false);
    }

    private static void tick(ClubState c, float kick, float drop, float tension, boolean playing) {
        tick(c, kick, drop, tension, playing, ClubState.Mixer.NONE);
    }

    @Test
    void oneBeatPerKickWithHysteresis() {
        ClubState c = new ClubState();
        tick(c, 0.9f, 0, 0, true);
        assertTrue(c.beat);
        assertEquals(1, c.beatIndex);
        tick(c, 0.9f, 0, 0, true);
        assertFalse(c.beat, "a held kick fired twice");
        tick(c, 0.3f, 0, 0, true); // between the thresholds: still latched
        tick(c, 0.9f, 0, 0, true);
        assertFalse(c.beat, "re-armed above the off threshold");
        tick(c, 0.1f, 0, 0, true);
        tick(c, 0.9f, 0, 0, true);
        assertTrue(c.beat);
        assertEquals(2, c.beatIndex);
    }

    @Test
    void beatRunsOnThroughABreakdownThenFades() {
        ClubState c = new ClubState();
        tick(c, 1f, 0, 0, true);
        tick(c, 0f, 0, 0, true);
        int beats = 0;
        for (int i = 0; i < 200; i++) { // 10 s = 20 beats at 0.5 s, no kick
            tick(c, 0, 0, 0, true);
            if (c.beat) beats++;
        }
        assertTrue(beats >= 19 && beats <= 20, "flywheel beats: " + beats);
        assertTrue(c.breakdown, "a flywheel beat must not end the breakdown");
        assertTrue(c.env < 0.01f, "the punch follows real kicks only");
        for (int i = 0; i < 1000; i++) tick(c, 0, 0, 0, true); // long past 8 + 4 bars
        assertEquals(0f, c.flywheel, 1e-6);
        int late = 0;
        for (int i = 0; i < 40; i++) {
            tick(c, 0, 0, 0, true);
            if (c.beat) late++;
        }
        assertEquals(0, late, "the flywheel ran on forever");
    }

    @Test
    void aLateKickAfterAFlywheelBeatDoesNotDoubleStep() {
        ClubState c = new ClubState();
        tick(c, 1f, 0, 0, true);
        for (int i = 0; i < 10; i++) tick(c, 0, 0, 0, true); // 0.5 s: the flywheel beat
        assertTrue(c.beat);
        int index = c.beatIndex;
        tick(c, 1f, 0, 0, true); // the real kick, 50 ms late
        assertFalse(c.beat);
        assertEquals(index, c.beatIndex);
        assertEquals(0f, c.beatPhase, 1e-6);
    }

    @Test
    void dropFiresOnceAndDecays() {
        ClubState c = new ClubState();
        tick(c, 0, 1f, 0, true);
        assertTrue(c.dropEdge);
        assertEquals(1f, c.dropLevel, 1e-6);
        tick(c, 0, 1f, 0, true);
        assertFalse(c.dropEdge, "a held drop fired twice");
        for (int i = 0; i < 40; i++) tick(c, 0, 0f, 0, true);
        assertTrue(c.dropLevel < 0.2f, "the drop level did not die away");
        tick(c, 0, 1f, 0, true);
        assertTrue(c.dropEdge, "not re-armed after the drop ended");
    }

    @Test
    void breakdownAfterTheSameSilenceAsDmxProgram() {
        ClubState c = new ClubState();
        tick(c, 1f, 0, 0, true);
        for (int i = 0; i < 38; i++) tick(c, 0, 0, 0, true); // 1.9 s
        assertFalse(c.breakdown);
        for (int i = 0; i < 4; i++) tick(c, 0, 0, 0, true); // 2.1 s, max(2.0, 4 * 0.5)
        assertTrue(c.breakdown);
        tick(c, 1f, 0, 0, true);
        assertFalse(c.breakdown, "the next kick ends the breakdown");
    }

    @Test
    void silenceIsNotABreakdownAndNothingFires() {
        ClubState c = new ClubState();
        for (int i = 0; i < 100; i++) {
            tick(c, 0, 0, 0, false);
            assertFalse(c.breakdown);
            assertFalse(c.beat);
            assertFalse(c.dropEdge);
        }
        assertEquals(0f, c.buildUp, 1e-6);
    }

    @Test
    void mixerSharpensABuildUpButNeverCreatesOne() {
        ClubState c = new ClubState();
        ClubState.Mixer killed = new ClubState.Mixer(0f, 0.8f, false); // bass kill + high-pass swept up
        tick(c, 0, 0, 0f, true, killed);
        assertEquals(0f, c.buildUp, 1e-6, "a bass kill alone must not strobe");
        assertEquals(1f, c.bassCut, 1e-6);
        tick(c, 0, 0, 0.5f, true, killed);
        assertTrue(c.buildUp > 0.5f, "a real build-up should be sharpened");
        assertTrue(c.buildUp <= 1f);
        tick(c, 0, 0, 0.5f, true, ClubState.Mixer.NONE);
        assertEquals(0.5f, c.buildUp, 1e-6);
    }

    @Test
    void lowPassSweepIsFilterClosedNotBassCut() {
        ClubState c = new ClubState();
        tick(c, 0, 0, 0, true, new ClubState.Mixer(0.5f, -0.6f, false));
        assertEquals(0.6f, c.filterClosed, 1e-6);
        assertEquals(0f, c.bassCut, 1e-6);
    }

    @Test
    void theRawPulseIsKeptForBlocksThatFollowItContinuously() {
        ClubState c = new ClubState();
        tick(c, 0.3f, 0.1f, 0, true);
        assertEquals(0.3f, c.kick, 1e-6);
        assertEquals(0.1f, c.drop, 1e-6);
        assertFalse(c.beat, "0.3 is below the beat threshold, but the raw pulse still moves");
    }

    @Test
    void aSlightlyOffEqKnobIsNotABassCut() {
        ClubState c = new ClubState();
        tick(c, 0, 0, 0.5f, true, new ClubState.Mixer(0.49f, 0f, false));
        assertEquals(0f, c.bassCut, 1e-6);
        assertEquals(0.5f, c.buildUp, 1e-6);
    }

    @Test
    void flashingFlagIsPassedThrough() {
        ClubState c = new ClubState();
        c.update(0.05f, 0, 0, 0, true, 0.5, ClubState.Mixer.NONE, true);
        assertTrue(c.noFlashing);
    }

    @Test
    void mixerFollowsTheLoudestPlayingDeck() {
        boolean[] playing = {true, true};
        float[] gain = {0.2f, 0.9f}, low = {0.5f, 0f}, filter = {0f, 0.7f};
        boolean[] loop = {false, true};
        ClubState.Mixer m = ClubState.loudestMixer(playing, gain, low, filter, loop);
        assertEquals(0f, m.lowEq());
        assertEquals(0.7f, m.filter());
        assertTrue(m.looping());
        playing[1] = false; // the loud deck stopped: the other one is what is heard
        assertEquals(0.5f, ClubState.loudestMixer(playing, gain, low, filter, loop).lowEq());
        playing[0] = false;
        assertEquals(ClubState.Mixer.NONE, ClubState.loudestMixer(playing, gain, low, filter, loop));
    }
}
