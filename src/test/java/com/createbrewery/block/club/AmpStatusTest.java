package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static com.createbrewery.block.club.AmpSettings.*;
import static org.junit.jupiter.api.Assertions.*;

class AmpStatusTest {
    private static final int[] RIG = {4, 2, 0, 1, 1};
    private static final boolean[] NONE_MUTED = new boolean[ZONES];

    private static AmpStatus.Status of(boolean linked, boolean other, boolean power, float limit, int[] counts, boolean[] muted) {
        float[] limits = new float[ZONES];
        limits[FLOOR] = limit;
        return AmpStatus.of(linked, other, power, limits, counts, new boolean[ZONES], muted);
    }

    @Test
    void allGood() {
        AmpStatus.Status s = of(true, false, true, 0f, RIG, NONE_MUTED);
        assertEquals(AmpStatus.Light.GREEN, s.light());
        assertEquals("createbrewery.amp.status.ok", s.langKey());
    }

    @Test
    void theWorstRuleWins() {
        assertEquals("unlinked", of(false, true, false, 9f, new int[ZONES], NONE_MUTED).key());
        assertEquals("other_rack", of(true, true, false, 9f, RIG, NONE_MUTED).key());
        assertEquals("power_off", of(true, false, false, 9f, RIG, NONE_MUTED).key());
        assertEquals("too_loud", of(true, false, true, 6.5f, new int[ZONES], NONE_MUTED).key());
        assertEquals(AmpStatus.Light.RED, of(true, false, true, 6.5f, RIG, NONE_MUTED).light());
        AmpStatus.Status limit = of(true, false, true, 3f, new int[ZONES], NONE_MUTED);
        assertEquals("at_limit", limit.key());
        assertEquals(AmpStatus.Light.YELLOW, limit.light());
        assertEquals("no_speakers", of(true, false, true, 0.5f, new int[] {0, 0, 0, 0, 1}, NONE_MUTED).key());
        assertEquals("no_subs", of(true, false, true, 0f, new int[] {3, 0, 0, 0, 1}, NONE_MUTED).key());
    }

    @Test
    void theLimitNamesTheLoudestZone() {
        float[] limits = new float[ZONES];
        limits[SUBS] = 2f;
        limits[ROOM] = 7f;
        AmpStatus.Status s = AmpStatus.of(true, false, true, limits, RIG, new boolean[ZONES], NONE_MUTED);
        assertEquals("too_loud", s.key());
        assertEquals(ROOM, s.zone());
    }

    @Test
    void aSoloLeftOnIsShownBeforeAMute() {
        boolean[] solo = new boolean[ZONES], muted = new boolean[ZONES];
        solo[SUBS] = true;
        muted[FLOOR] = true;
        AmpStatus.Status s = AmpStatus.of(true, false, true, new float[ZONES], RIG, solo, muted);
        assertEquals("solo", s.key());
        assertEquals(SUBS, s.zone());
        assertEquals(AmpStatus.Light.YELLOW, s.light());
        assertEquals("solo", AmpStatus.of(true, false, true, new float[ZONES], new int[] {3, 0, 0, 0, 1}, solo, NONE_MUTED).key(),
            "a rig without subs still hears why it went quiet");
    }

    @Test
    void aMutedZoneCountsOnlyWithSpeakersInIt() {
        boolean[] muted = new boolean[ZONES];
        muted[DELAY] = true;
        assertEquals("ok", of(true, false, true, 0f, RIG, muted).key(), "DELAY has no speakers");
        muted[ROOM] = true;
        AmpStatus.Status s = of(true, false, true, 0f, RIG, muted);
        assertEquals("muted", s.key());
        assertEquals(ROOM, s.zone());
        assertEquals(AmpStatus.Light.YELLOW, s.light());
    }
}
