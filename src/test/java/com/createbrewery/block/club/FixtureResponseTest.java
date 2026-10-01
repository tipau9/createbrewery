package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixtureResponseTest {

    @Test
    void dimmerCurveKeepsTheEndsAndIsMonotonic() {
        assertEquals(0f, FixtureResponse.curve(0f), 1e-6);
        assertEquals(1f, FixtureResponse.curve(1f), 1e-6);
        assertTrue(FixtureResponse.curve(0.5f) < 0.5f, "low levels must look darker than linear");
        float prev = -1f;
        for (int i = 0; i <= 100; i++) {
            float v = FixtureResponse.curve(i / 100f);
            assertTrue(v >= prev);
            prev = v;
        }
        assertEquals(0f, FixtureResponse.curve(-3f), 1e-6, "out of range is clamped");
        assertEquals(1f, FixtureResponse.curve(7f), 1e-6);
    }

    private static int ticksTo(FixtureResponse r, FixtureResponse.Lamp lamp, float level, float goal, boolean up) {
        for (int i = 1; i < 200; i++) {
            float out = r.dimmer(lamp, level);
            if (up ? out >= goal : out <= goal) return i;
        }
        return 200;
    }

    @Test
    void tungstenFallsSlowerThanItRisesAndLedIsNearlyInstant() {
        FixtureResponse tungsten = new FixtureResponse();
        int rise = ticksTo(tungsten, FixtureResponse.Lamp.TUNGSTEN, 1f, 0.9f, true);
        int fall = ticksTo(tungsten, FixtureResponse.Lamp.TUNGSTEN, 0f, 0.1f, false);
        assertTrue(fall > rise * 2, "the filament must glow on: rise " + rise + ", fall " + fall);

        FixtureResponse led = new FixtureResponse();
        assertTrue(ticksTo(led, FixtureResponse.Lamp.LED, 1f, 0.9f, true) <= 3);
    }

    @Test
    void aHeldTargetConvergesAndStaysPut() {
        FixtureResponse r = new FixtureResponse();
        float out = 0;
        for (int i = 0; i < 100; i++) out = r.dimmer(FixtureResponse.Lamp.HEAD, 0.6f);
        assertEquals(FixtureResponse.curve(0.6f), out, 1e-3);
        assertEquals(out, r.dimmer(FixtureResponse.Lamp.HEAD, 0.6f), 1e-6, "an at-rest output drifted");
    }

    @Test
    void colourFadesToTheTargetAndFirstCallIsInstant() {
        FixtureResponse r = new FixtureResponse();
        assertEquals(0xFF0000, r.color(0xFF0000, 5f), "the first colour must not fade up from black");
        int c = r.color(0x0000FF, 5f);
        assertNotEquals(0x0000FF, c, "a fade is not instant");
        for (int i = 0; i < 100; i++) c = r.color(0x0000FF, 5f);
        assertEquals(0x0000FF, c);
        assertEquals(0x00FF00, r.color(0x00FF00, 0f), "zero ticks is instant");
    }

    @Test
    void motorNeverExceedsSpeedAndALongMoveTakesLonger() {
        FixtureResponse r = new FixtureResponse();
        int shortMove = ticksToAim(r, 5f);
        FixtureResponse l = new FixtureResponse();
        int longMove = ticksToAim(l, 100f);
        assertTrue(longMove > shortMove + 5, "short " + shortMove + ", long " + longMove);

        FixtureResponse m = new FixtureResponse();
        float last = 0, lastVel = 0;
        for (int i = 0; i < 60; i++) {
            m.motor(100f, 80f);
            assertTrue(Math.abs(m.pan - last) <= FixtureResponse.PAN_SPEED + 1e-3, "pan jumped");
            if (m.pan != 100f) assertTrue(Math.abs(Math.abs(m.pan - last) - lastVel) <= FixtureResponse.ACCEL + 1e-3, "accelerated too hard");
            lastVel = Math.abs(m.pan - last);
            last = m.pan;
        }
        assertEquals(100f, m.pan, 1e-3);
        assertEquals(80f, m.tilt, 1e-3);
    }

    private static int ticksToAim(FixtureResponse r, float deg) {
        for (int i = 1; i < 200; i++) {
            r.motor(deg, 0f);
            if (Math.abs(r.pan - deg) < 0.01f) return i;
        }
        return 200;
    }

    @Test
    void aMotorAtItsTargetDoesNotMove() {
        FixtureResponse r = new FixtureResponse();
        for (int i = 0; i < 5; i++) r.motor(0f, 0f);
        assertEquals(0f, r.pan);
        assertEquals(0f, r.tilt);
    }

    @Test
    void glideMovesAtMostATenth() {
        assertEquals(0.1f, FixtureResponse.glide(0f, 1f), 1e-6);
        assertEquals(0.9f, FixtureResponse.glide(1f, 0f), 1e-6);
        assertEquals(0.55f, FixtureResponse.glide(0.5f, 0.55f), 1e-6);
    }

    @Test
    void noFlashingStretchesEveryColourFade() {
        assertEquals(FixtureResponse.CALM_FADE, FixtureResponse.fade(2f, true), 1e-6);
        assertEquals(FixtureResponse.CALM_FADE, FixtureResponse.fade(0f, true), 1e-6);
        assertEquals(2f, FixtureResponse.fade(2f, false), 1e-6);
        // A red-to-cyan swap (the COMPLEMENT chase) moves a small step a tick, also for LED bar pixels.
        int c = FixtureResponse.blend(0xFF0000, 0x00FFFF, FixtureResponse.CALM_FADE);
        assertTrue((c >> 16 & 255) > 230 && (c >> 8 & 255) < 25, "one tick moved too far: " + Integer.toHexString(c));
        for (int i = 0; i < 300; i++) c = FixtureResponse.blend(c, 0x00FFFF, FixtureResponse.CALM_FADE);
        assertEquals(0x00FFFF, c);
    }
}
