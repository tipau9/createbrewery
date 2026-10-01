package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DmxTimecodeTest {

    @Test
    void aLookCarriesTheLightSettingsAndAppliesThemBack() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.colorFx = 2;
        s.gobo = 3;
        s.zoom = 0;
        s.prism = true;
        DmxTimecode.Look look = DmxTimecode.Look.of(s);
        DmxProgram.Settings back = new DmxProgram.Settings();
        look.applyTo(back);
        assertEquals(2, back.colorFx);
        assertEquals(3, back.gobo);
        assertEquals(0, back.zoom);
        assertTrue(back.prism);
    }

    @Test
    void freshSettingsLookLikeToday() {
        DmxProgram.Settings s = new DmxProgram.Settings();
        assertEquals(0, s.colorFx);
        assertEquals(0, s.gobo);
        assertEquals(1, s.zoom, "zoom defaults to normal");
        assertFalse(s.prism);
        assertEquals(8, DmxProgram.MOVES);
    }
    private static DmxTimecode.Look look(float fader) {
        DmxProgram.Settings s = new DmxProgram.Settings();
        s.faders[0] = fader;
        return DmxTimecode.Look.of(s);
    }

    @Test
    void theLatestCuePassedIsInEffect() {
        DmxTimecode tc = new DmxTimecode();
        tc.record(7, 100, look(0.1f));
        tc.record(7, 40, look(0.2f));
        assertEquals(-1, tc.at(7, 10), "a cue before the first");
        assertEquals(0.2f, tc.look(7, tc.at(7, 60)).faders()[0]);
        assertEquals(0.1f, tc.look(7, tc.at(7, 500)).faders()[0]);
        assertEquals(-1, tc.at(8, 500), "another record's show");
    }

    @Test
    void aDragIsOneCueAndListsStayCapped() {
        DmxTimecode tc = new DmxTimecode();
        for (int t = 0; t < 4; t++) tc.record(1, 200 + t, look(t / 4f));
        assertEquals(1, tc.count(1), "a fader drag left several cues");
        for (int t = 0; t < 10_000; t += 10) tc.record(1, t, look(0.5f));
        assertEquals(DmxTimecode.MAX_CUES, tc.count(1));
        for (int s = 0; s < DmxTimecode.MAX_SONGS + 3; s++) tc.record(100 + s, 0, look(0f));
        assertEquals(DmxTimecode.MAX_SONGS, tc.songs.size());
    }
}
