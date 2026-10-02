package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.createbrewery.block.club.AmpSettings.*;
import static org.junit.jupiter.api.Assertions.*;

class AutoSetupTest {
    private static AutoSetup.Seen top(long pos, double d, boolean walled) {
        return new AutoSetup.Seen(pos, d, false, walled);
    }

    private static AutoSetup.Seen sub(long pos, double d) {
        return new AutoSetup.Seen(pos, d, true, false);
    }

    @Test
    void sortsTheClub() {
        AutoSetup.Result r = AutoSetup.plan(List.of(top(1, 5, false), top(2, 16, false), top(3, 20, false), top(4, 6, true), sub(5, 3)));
        assertEquals(FLOOR, r.zones().get(1L));
        assertEquals(FLOOR, r.zones().get(2L), "16 is not 12 beyond 5");
        assertEquals(DELAY, r.zones().get(3L));
        assertEquals(ROOM, r.zones().get(4L));
        assertFalse(r.zones().containsKey(5L), "subwoofers are not assigned, they are always SUBS");
        assertEquals(4, r.tops());
        assertEquals(1, r.subs());
        assertEquals(1, r.rooms());
        assertEquals(1, r.delays());
        assertEquals(80f, r.crossover(), "one sub: a lower corner");
        assertTrue(r.align());
    }

    @Test
    void walledSpeakersAreNotTheNearest() {
        AutoSetup.Result r = AutoSetup.plan(List.of(top(1, 2, true), top(2, 30, false)));
        assertEquals(ROOM, r.zones().get(1L));
        assertEquals(FLOOR, r.zones().get(2L), "the only clear top is the reference, not a delay");
    }

    @Test
    void onlyWalledTopsMeanNoDelay() {
        AutoSetup.Result r = AutoSetup.plan(List.of(top(1, 2, true), top(2, 40, true)));
        assertEquals(0, r.delays());
        assertEquals(2, r.rooms());
    }

    @Test
    void aPlainClubNeedsNoAlignment() {
        AutoSetup.Result r = AutoSetup.plan(List.of(top(1, 4, false), top(2, 6, false), sub(3, 3), sub(4, 3)));
        assertFalse(r.align());
        assertEquals(100f, r.crossover());
    }

    @Test
    void noSpeakersAtAll() {
        AutoSetup.Result r = AutoSetup.plan(List.of());
        assertEquals(0, r.tops());
        assertEquals(100f, r.crossover());
        assertFalse(r.align());
    }

    @Test
    void applyMakesItRight() {
        AmpSettings s = new AmpSettings();
        s.power = false;
        s.set(ZONE_LOW, FLOOR, 9f);
        s.set(ZONE_DELAY, ROOM, 40f);
        s.set(ZONE_INVERT, SUBS, 1f);
        s.set(ZONE_LIMIT, DELAY, -10f);
        s.set(ZONE_MUTE, FLOOR, 1f);
        s.set(ZONE_HPF, FLOOR, 80f);
        s.assign.put(9L, new Assignment(DELAY, true));
        AutoSetup.apply(s, AutoSetup.plan(List.of(top(1, 5, false), top(2, 7, true), sub(3, 2))));
        assertTrue(s.power);
        assertEquals(CLUB, s.preset);
        assertEquals(80f, s.crossover);
        assertTrue(s.align);
        assertEquals(0f, s.zones[FLOOR].low);
        assertEquals(0f, s.zones[FLOOR].hpf);
        assertEquals(0f, s.zones[ROOM].delayMs);
        assertFalse(s.zones[SUBS].invert);
        assertEquals(0f, s.zones[DELAY].limit);
        assertFalse(s.zones[FLOOR].mute);
        assertEquals(30f, s.zones[SUBS].hpf, "the CLUB preset's sub high-pass");
        assertFalse(s.assign.containsKey(9L), "Auto Setup re-detects everything");
        assertEquals(new Assignment(ROOM, false), s.assign.get(2L));
    }

    @Test
    void sortingFreshSpeakersDoesNotDependOnTheirOrder() {
        // The far one comes first in position order; it must still be a delay behind the near one.
        var far = top(1, 20, false);
        var near = top(2, 5, false);
        assertEquals(DELAY, AutoSetup.sortFresh(List.of(far, near), Double.POSITIVE_INFINITY).get(1L));
        assertEquals(FLOOR, AutoSetup.sortFresh(List.of(far, near), Double.POSITIVE_INFINITY).get(2L));
        assertEquals(DELAY, AutoSetup.sortFresh(List.of(far), 5).get(1L), "against an already sorted top");
    }
}
