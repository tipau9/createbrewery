package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AmpMetersTest {
    @Test
    void risesAtOnceHoldsASecondThenFalls() {
        AmpMeters m = new AmpMeters();
        assertEquals(AmpMeters.FLOOR_DB, m.peakDb(AmpSettings.FLOOR));
        m.add(AmpSettings.FLOOR, 0.5f, 1f);
        m.step(0.05f);
        assertEquals(-6.02, m.peakDb(AmpSettings.FLOOR), 0.05);
        for (int i = 0; i < 18; i++) m.step(0.05f); // 0.9 s of silence: still held
        assertEquals(-6.02, m.peakDb(AmpSettings.FLOOR), 0.05);
        for (int i = 0; i < 4; i++) m.step(0.05f); // past the hold, falling 20 dB/s
        assertTrue(m.peakDb(AmpSettings.FLOOR) < -6.5f && m.peakDb(AmpSettings.FLOOR) > -10f, "" + m.peakDb(AmpSettings.FLOOR));
        for (int i = 0; i < 100; i++) m.step(0.05f);
        assertEquals(AmpMeters.FLOOR_DB, m.peakDb(AmpSettings.FLOOR));
    }

    @Test
    void theLoudestOfAFrameWins() {
        AmpMeters m = new AmpMeters();
        m.add(AmpSettings.SUBS, 0.1f, 1f);
        m.add(AmpSettings.SUBS, 0.8f, 0.5f);
        m.add(AmpSettings.SUBS, 0.3f, 0.9f);
        m.step(0.016f);
        assertEquals(-1.94, m.peakDb(AmpSettings.SUBS), 0.05);
        assertEquals(6.02, m.limitDb(AmpSettings.SUBS), 0.05);
        assertEquals(6.02, m.worstLimit(), 0.05);
        assertEquals(0f, m.limitDb(AmpSettings.FLOOR));
    }

    @Test
    void ignoresZonesThatAreNot() {
        AmpMeters m = new AmpMeters();
        m.add(-1, 1f, 0.1f);
        m.add(9, 1f, 0.1f);
        m.step(0.016f);
        assertEquals(0f, m.worstLimit());
    }
}
