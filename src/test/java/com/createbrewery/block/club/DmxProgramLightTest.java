package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmxProgramLightTest {

    private static DmxProgram run(DmxProgram.Settings s, int beats) {
        DmxProgram p = new DmxProgram();
        ClubState c = new ClubState();
        long t = 0;
        for (int i = 0; i < beats; i++) {
            c.update(0.05f, 1f, 0, 0, true, 0.5, ClubState.Mixer.NONE, false);
            p.update(s, t++, 0.05f, c);
            for (int k = 0; k < 9; k++) {
                c.update(0.05f, 0f, 0, 0, true, 0.5, ClubState.Mixer.NONE, false);
                p.update(s, t++, 0.05f, c);
            }
        }
        return p;
    }

    @Test
    void fanZeroEqualsTheGroupLevelInEveryProgram() {
        for (int program = 0; program < DmxProgram.PROGRAMS; program++) {
            DmxProgram.Settings s = new DmxProgram.Settings();
            s.program = program;
            DmxProgram p = run(s, 3);
            for (int g = 0; g < DmxProgram.GROUPS; g++) {
                assertEquals(p.level[g], p.levelFor(s, g, 0), 1e-5, "program " + program + " group " + g);
            }
        }
    }

    @Test
    void fanShiftsTheChaseSoThePatternTravels() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.program = DmxProgram.CHASE;
        DmxProgram p = run(s, 2);
        int onAt0 = -1, onAt1 = -1;
        for (int g = 0; g < DmxProgram.GROUPS; g++) {
            if (p.levelFor(s, g, 0) > 0.5f) onAt0 = g;
            if (p.levelFor(s, g, 1) > 0.5f) onAt1 = g;
        }
        assertTrue(onAt0 >= 0 && onAt1 >= 0);
        assertNotEquals(onAt0, onAt1, "a fan of 1 must move the running light by a step");
    }

    @Test
    void fanWrapsAndNeverThrows() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        DmxProgram p = run(s, 1);
        for (int fan : new int[] {-9, -1, 0, 7, 8, 100}) {
            for (int g = 0; g < DmxProgram.GROUPS; g++) {
                float l = p.levelFor(s, g, fan);
                assertTrue(l >= 0f && l <= 1f);
                assertTrue(p.colorFor(s, g, fan) >= 0);
            }
        }
    }

    @Test
    void staticColourIsThePaletteAndEveryPixelMatches() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colors[2] = 4;
        DmxProgram p = run(s, 1);
        int base = DmxProgram.PALETTE[4];
        assertEquals(base, p.colorFor(s, 2, 0));
        for (int i = 0; i < 8; i++) assertEquals(p.colorFor(s, 2, 0), p.pixelColor(s, i, 8, 2, 0));
    }

    @Test
    void rainbowDiffersByGroupFanAndPixel() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colorFx = DmxProgram.RAINBOW;
        s.program = DmxProgram.MANUAL;
        DmxProgram p = run(s, 1);
        assertNotEquals(p.colorFor(s, 0, 0), p.colorFor(s, 1, 0));
        assertNotEquals(p.colorFor(s, 0, 0), p.colorFor(s, 0, 1));
        assertNotEquals(p.pixelColor(s, 0, 8, 0, 0), p.pixelColor(s, 7, 8, 0, 0));
    }

    @Test
    void complementAlternatesWithTheStep() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colorFx = DmxProgram.COMPLEMENT;
        s.colors[0] = 1;
        DmxProgram p = run(s, 1);
        int a = p.colorFor(s, 0, 0), b = p.colorFor(s, 0, 1);
        assertEquals(a ^ 0xFFFFFF, b, "fan 1 shows the other half of the pair");
    }

    @Test
    void fadeMovesThroughThePaletteOverTime() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colorFx = DmxProgram.FADE;
        s.program = DmxProgram.MANUAL;
        DmxProgram early = run(s, 1), late = run(s, 12);
        assertNotEquals(early.colorFor(s, 0, 0), late.colorFor(s, 0, 0));
    }

    @Test
    void everyMoveGivesFiniteAnglesForEveryGroupAndFan() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        DmxProgram p = run(s, 2);
        for (int move = 0; move < DmxProgram.MOVES; move++) {
            s.move = move;
            for (int g = 0; g < DmxProgram.GROUPS; g++) {
                for (int fan = 0; fan < 8; fan++) {
                    float[] a = p.aim(s, g, fan);
                    assertTrue(Float.isFinite(a[0]) && Float.isFinite(a[1]), "move " + move);
                }
            }
        }
    }

    @Test
    void theOldMovesKeepTheirMeaning() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        DmxProgram p = new DmxProgram(); // movePhase 0
        s.move = DmxProgram.CIRCLE;
        float[] a = p.aim(s, 0, 0);
        assertEquals(30f, a[0], 1e-4);
        assertEquals(0f, a[1], 1e-4);
        s.move = DmxProgram.SWEEP;
        assertEquals(15f, p.aim(s, 3, 0)[1], 1e-4);
    }

    @Test
    void theDropStillGoesWhite() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colors[3] = 1;
        DmxProgram p = new DmxProgram();
        ClubState c = new ClubState();
        c.update(0.05f, 0, 1f, 0, true, 0.5, ClubState.Mixer.NONE, false);
        p.update(s, 0, 0.05f, c);
        assertEquals(0xFFFFFF, p.colorFor(s, 3, 0));
        assertEquals(0xFFFFFF, p.color[3]);
    }
}
