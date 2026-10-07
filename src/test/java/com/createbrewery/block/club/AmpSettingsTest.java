package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static com.createbrewery.block.club.AmpSettings.*;
import static org.junit.jupiter.api.Assertions.*;

class AmpSettingsTest {
    @Test
    void startsOnAsAClub() {
        AmpSettings s = new AmpSettings();
        assertTrue(s.power);
        assertEquals(CLUB, s.preset);
        assertEquals(100f, s.crossover);
        assertEquals(3f, s.zones[SUBS].gain);
        assertEquals(30f, s.zones[SUBS].hpf);
        assertEquals(-10f, s.zones[MONITOR].gain);
    }

    @Test
    void clampsWhatItIsGiven() {
        AmpSettings s = new AmpSettings();
        s.set(MASTER, 0, 99f);
        assertEquals(MAX_MASTER, s.master);
        s.set(MASTER, 0, -99f);
        assertEquals(MIN_MASTER, s.master);
        s.set(CROSSOVER, 0, 10f);
        assertEquals(MIN_CROSSOVER, s.crossover);
        s.set(ZONE_LOW, FLOOR, 50f);
        assertEquals(MAX_EQ, s.zones[FLOOR].low);
        s.set(ZONE_HPF, SUBS, 5f);
        assertEquals(0f, s.zones[SUBS].hpf, "below 20 Hz is off");
        s.set(ZONE_HPF, SUBS, 500f);
        assertEquals(MAX_HPF, s.zones[SUBS].hpf);
        s.set(ZONE_DELAY, ROOM, -3f);
        assertEquals(0f, s.zones[ROOM].delayMs);
        s.set(ZONE_LIMIT, ROOM, 3f);
        assertEquals(0f, s.zones[ROOM].limit);
    }

    @Test
    void refusesGarbage() {
        AmpSettings s = new AmpSettings();
        assertFalse(s.set(MASTER, 0, Float.NaN));
        assertFalse(s.set(MASTER, 0, Float.POSITIVE_INFINITY));
        assertEquals(0f, s.master);
        assertFalse(s.set(ZONE_GAIN, 9, 0f));
        assertFalse(s.set(ZONE_GAIN, -1, 0f));
        assertFalse(s.set((byte) 99, 0, 1f));
        assertFalse(s.set(AUTO_SETUP, 0, 1f), "the rack runs Auto Setup, not the settings");
        assertEquals(CLUB, s.preset, "nothing refused may turn it Custom");
    }

    @Test
    void anyMoveButPowerMakesItCustom() {
        AmpSettings s = new AmpSettings();
        s.set(POWER, 0, 0f);
        assertFalse(s.power);
        assertEquals(CLUB, s.preset);
        s.set(ZONE_MUTE, ROOM, 1f);
        assertEquals(CUSTOM, s.preset);
        s.set(PRESET, 0, NIGHT);
        assertEquals(NIGHT, s.preset);
        assertEquals(-20f, s.master);
        assertTrue(s.zones[ROOM].mute, "a preset leaves mutes alone");
    }

    @Test
    void presetsFollowTheTable() {
        AmpSettings s = new AmpSettings();
        s.set(PRESET, 0, LIVE);
        assertEquals(2f, s.zones[FLOOR].mid);
        assertEquals(35f, s.zones[SUBS].hpf);
        assertEquals(-6f, s.zones[MONITOR].gain);
        s.set(PRESET, 0, BACKGROUND);
        assertEquals(0f, s.zones[FLOOR].mid, "LIVE's mid boost is gone");
        assertEquals(-2f, s.zones[DELAY].high);
        assertEquals(-12f, s.master);
        assertFalse(s.set(PRESET, 0, CUSTOM), "CUSTOM is not a preset to apply");
        assertEquals(BACKGROUND, s.preset);
    }

    @Test
    void resetPutsOneControlBack() {
        AmpSettings s = new AmpSettings();
        s.set(MASTER, 0, -10f);
        s.set(CROSSOVER, 0, 150f);
        s.set(ZONE_INVERT, SUBS, 1f);
        s.set(RESET, 0, MASTER);
        s.set(RESET, 0, CROSSOVER);
        s.set(RESET, SUBS, ZONE_INVERT);
        assertEquals(0f, s.master);
        assertEquals(100f, s.crossover);
        assertFalse(s.zones[SUBS].invert);
    }

    @Test
    void getMirrorsSet() {
        AmpSettings s = new AmpSettings();
        byte[] actions = {MASTER, CROSSOVER, ZONE_GAIN, ZONE_LOW, ZONE_MID, ZONE_HIGH, ZONE_HPF, ZONE_DELAY, ZONE_LIMIT};
        float[] values = {-7f, 140f, -4f, 3f, -2f, 5f, 50f, 33f, -8f};
        for (int i = 0; i < actions.length; i++) {
            s.set(actions[i], DELAY, values[i]);
            assertEquals(values[i], s.get(actions[i], DELAY), 1e-4, "action " + actions[i]);
        }
        s.set(SLOPE, 0, LR48);
        assertEquals(LR48, s.get(SLOPE, 0));
        s.set(ALIGN, 0, 1f);
        assertEquals(1f, s.get(ALIGN, 0));
    }

    @Test
    void driveIsMasterTimesZone() {
        AmpSettings s = new AmpSettings();
        s.set(ZONE_GAIN, FLOOR, 0f);
        s.set(MASTER, 0, 0f);
        assertEquals(1f, s.drive(FLOOR), 1e-4);
        s.set(ZONE_GAIN, FLOOR, -6f);
        assertEquals(0.501f, s.drive(FLOOR), 1e-3);
        s.set(ZONE_GAIN, FLOOR, MIN_GAIN);
        assertEquals(0f, s.drive(FLOOR), "the bottom of the fader is off");
        s.set(ZONE_GAIN, FLOOR, 0f);
        s.set(ZONE_MUTE, FLOOR, 1f);
        assertEquals(0f, s.drive(FLOOR));
    }

    @Test
    void unassignedTopsPlayTheDanceFloor() {
        AmpSettings s = new AmpSettings();
        assertEquals(FLOOR, s.zoneOf(42L));
        s.assign.put(42L, new Assignment(ROOM, true));
        assertEquals(ROOM, s.zoneOf(42L));
    }

    @Test
    void clampAllMendsALoadedRack() {
        AmpSettings s = new AmpSettings();
        s.set(PRESET, 0, LIVE);
        s.master = Float.NaN;
        s.crossover = 5000f;
        s.zones[ROOM].low = -80f;
        s.zones[SUBS].hpf = 3f;
        s.slope = 7;
        s.clampAll();
        assertEquals(0f, s.master);
        assertEquals(MAX_CROSSOVER, s.crossover);
        assertEquals(-MAX_EQ, s.zones[ROOM].low);
        assertEquals(0f, s.zones[SUBS].hpf);
        assertEquals(LR48, s.slope);
        assertEquals(LIVE, s.preset, "mending is not a move");
    }

    @Test
    void migratesBothOldRacks() {
        // bd56b48: deep contour, 40 Hz sub cut, tops muted, 20 ms delay, speed of sound on.
        AmpSettings s = AmpSettings.migrate(new Legacy(150f, 0.5f, 0.5f, true, true, false, 2, 1, 20f));
        assertEquals(150f, s.crossover);
        assertEquals(2.6f, s.zones[SUBS].gain, 0.05);
        assertEquals(0f, s.zones[FLOOR].gain, 1e-4);
        assertEquals(40f, s.zones[SUBS].hpf);
        assertTrue(s.zones[FLOOR].mute && s.zones[DELAY].mute && s.zones[ROOM].mute);
        assertFalse(s.zones[SUBS].mute);
        assertTrue(s.align);
        for (Zone z : s.zones) assertEquals(20f, z.delayMs);
        assertEquals(CUSTOM, s.preset);

        // The original rack: sub knob all the way down, top knob full.
        AmpSettings o = AmpSettings.migrate(new Legacy(100f, 0f, 1f, false, false, false, 0, 0, 0f));
        assertEquals(MIN_GAIN, o.zones[SUBS].gain);
        assertEquals(6f, o.zones[FLOOR].gain, 0.05);
        assertEquals(0f, o.zones[SUBS].hpf);
    }

    @Test
    void aSoloSilencesTheOtherZonesButNotTheMonitor() {
        AmpSettings s = new AmpSettings();
        int preset = s.preset;
        assertTrue(s.set(ZONE_SOLO, SUBS, 1f));
        assertEquals(preset, s.preset, "solo is a check, not a new sound");
        assertTrue(s.drive(SUBS) > 0f);
        assertEquals(0f, s.drive(FLOOR));
        assertTrue(s.drive(MONITOR) > 0f);
        assertTrue(s.set(RESET, SUBS, ZONE_SOLO));
        assertFalse(s.soloed());
        assertTrue(s.drive(FLOOR) > 0f);
        assertFalse(s.set(ZONE_SOLO, ZONES, 1f));
    }

    @Test
    void aUserPresetBringsTheSoundBack() {
        AmpSettings s = new AmpSettings();
        s.set(MASTER, 0, -7f);
        s.set(ZONE_LOW, SUBS, 4f);
        s.set(CROSSOVER, 0, 120f);
        assertTrue(s.set(USER_SAVE, 0, 2f));
        assertEquals(2, s.userSlot);
        s.applyPreset(NIGHT);
        assertEquals(-1, s.userSlot);
        assertTrue(s.set(USER_LOAD, 0, 2f));
        assertEquals(-7f, s.master);
        assertEquals(4f, s.zones[SUBS].low);
        assertEquals(120f, s.crossover);
        assertEquals(2, s.userSlot);
        s.set(MASTER, 0, -3f);
        assertEquals(-1, s.userSlot, "a move leaves the slot");
        assertFalse(s.set(USER_LOAD, 0, 1f), "slot 2 was never stored");
        assertFalse(s.set(USER_SAVE, 0, USER_SLOTS));
        assertFalse(s.set(USER_SAVE, 0, 0.5f));
        assertFalse(s.restore(new float[3]));
    }
}
