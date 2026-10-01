package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmxProgramGateTest {
    @Test
    void blackoutMasterAndFaderGateAnEffect() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.faders[2] = 0.5f;
        s.master = 0.8f;
        assertEquals(0.4f, DmxProgram.gate(s, 2), 1e-6);
        s.blackout = true;
        assertEquals(0f, DmxProgram.gate(s, 2));
    }

    @Test
    void aHeldFlashButtonLetsTheGroupThroughAtMaster() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.faders[1] = 0f;
        s.master = 0.6f;
        s.flash = 1 << 1;
        assertEquals(0.6f, DmxProgram.gate(s, 1), 1e-6);
        s.blackout = true;
        assertEquals(0f, DmxProgram.gate(s, 1), "blackout beats a flash");
    }

    @Test
    void outOfRangeGroupIsWrappedNotThrown() {
        assertEquals(DmxProgram.gate(new DmxProgram.Settings(), 0), DmxProgram.gate(new DmxProgram.Settings(), DmxProgram.GROUPS), 1e-6);
    }
}
