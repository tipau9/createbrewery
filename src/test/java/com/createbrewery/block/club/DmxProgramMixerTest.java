package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmxProgramMixerTest {
    private static float punch(ClubState.Mixer mixer) {
        DmxProgram p = new DmxProgram();
        DmxProgram.Settings s = new DmxProgram.Settings();
        ClubState c = new ClubState();
        c.update(0.05f, 1f, 0, 0, true, 0.5, mixer, false);
        p.update(s, 0, 0.05f, c);
        return p.level[0] + p.level[1];
    }

    @Test
    void aBassKillDarkensTheRoom() {
        assertTrue(punch(new ClubState.Mixer(0f, 0f, false)) < punch(ClubState.Mixer.NONE) * 0.6f);
    }

    @Test
    void aClosedFilterDarkensTheRoom() {
        assertTrue(punch(new ClubState.Mixer(0.5f, -1f, false)) < punch(ClubState.Mixer.NONE) * 0.6f);
    }

    @Test
    void theDropBeatsTheMixerDimming() {
        DmxProgram p = new DmxProgram();
        DmxProgram.Settings s = new DmxProgram.Settings();
        ClubState c = new ClubState();
        c.update(0.05f, 1f, 1f, 0, true, 0.5, new ClubState.Mixer(0f, 0f, false), false);
        p.update(s, 0, 0.05f, c);
        assertEquals(s.faders[0], p.level[0], 1e-4, "the drop must hit at full level even with the bass killed");
    }
}
