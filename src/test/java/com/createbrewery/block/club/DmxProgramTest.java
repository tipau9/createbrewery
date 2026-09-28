package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmxProgramTest {

    private static long time;

    private static void tick(DmxProgram p, DmxProgram.Settings s, float kick, float drop, float tension, boolean playing, boolean noFlashing) {
        p.update(s, time++, 0.05f, kick, drop, tension, playing, 0.5, noFlashing);
    }

    @Test
    void flashBeatsTheFaderAndBlackoutBeatsEverything() {
        DmxProgram p = new DmxProgram();
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.program = DmxProgram.MANUAL;
        s.faders[0] = 0.2f;
        tick(p, s, 0, 0, 0, false, false);
        assertEquals(0.2f, p.level[0], 1e-6);
        s.flash = 1;
        tick(p, s, 0, 0, 0, false, false);
        assertEquals(1f, p.level[0], 1e-6);
        s.master = 0.5f;
        tick(p, s, 0, 0, 0, false, false);
        assertEquals(0.5f, p.level[0], 1e-6, "master scales the flash too");
        s.blackout = true;
        tick(p, s, 0, 0, 0, false, false);
        for (float l : p.level) assertEquals(0f, l);
    }

    @Test
    void theDropGoesFullWhite() {
        DmxProgram p = new DmxProgram();
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colors[3] = 1; // red
        tick(p, s, 0, 1f, 0, true, false);
        assertEquals(s.faders[3], p.level[3], 1e-4);
        assertEquals(0xFFFFFF, p.color[3]);
    }

    @Test
    void chaseStepsThroughStoredScenesOnTheBeat() {
        DmxProgram p = new DmxProgram();
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.program = DmxProgram.CHASE;
        float[] a = new float[8], b = new float[8];
        a[0] = 1f;
        b[1] = 1f;
        s.scenes[0] = new DmxProgram.Scene(a, new int[8]);
        s.scenes[2] = new DmxProgram.Scene(b, new int[8]);
        boolean[] seen = new boolean[2];
        for (int beat = 0; beat < 4; beat++) {
            tick(p, s, 1f, 0, 0, true, false);
            seen[p.level[0] == 1f ? 0 : 1] = true;
            assertEquals(1f, p.level[0] + p.level[1], 1e-6, "one scene at a time");
            tick(p, s, 0f, 0, 0, true, false);
        }
        assertTrue(seen[0] && seen[1], "the chase never moved on");
    }

    @Test
    void buildUpsOnlyStrobeWhenFlashingIsAllowed() {
        for (boolean noFlashing : new boolean[] {false, true}) {
            DmxProgram p = new DmxProgram();
            DmxProgram.Settings s = new DmxProgram.Settings();
            tick(p, s, 1f, 0, 0, true, noFlashing);
            boolean dark = false;
            for (int i = 0; i < 20; i++) {
                tick(p, s, 0f, 0, 1f, true, noFlashing);
                dark |= p.level[0] == 0f;
            }
            assertEquals(!noFlashing, dark, noFlashing ? "strobed with flashing lights hidden" : "a build-up did not strobe");
        }
    }

    @Test
    void withFlashingHiddenNothingJumps() {
        DmxProgram p = new DmxProgram();
        DmxProgram.Settings s = new DmxProgram.Settings();
        float[] last = new float[8];
        for (int i = 0; i < 80; i++) {
            // Kicks, a drop and then a chase: all the things that punch.
            if (i == 40) s.program = DmxProgram.CHASE;
            tick(p, s, i % 10 == 0 ? 1f : 0f, i == 20 ? 1f : 0f, 0, true, true);
            for (int g = 0; g < 8; g++) {
                assertTrue(Math.abs(p.level[g] - last[g]) <= DmxProgram.GLIDE + 1e-6, "group " + g + " jumped at tick " + i);
                last[g] = p.level[g];
            }
        }
    }
}
