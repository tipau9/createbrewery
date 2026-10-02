package com.createbrewery.block.club;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rack's Auto Setup: which zone each speaker plays, and a crossover, alignment and levels that
 * suit the room. A speaker behind a wall from the booth is in a side room; one far behind the
 * nearest clear speaker is a delay tower; the rest face the dance floor.
 */
public final class AutoSetup {
    private AutoSetup() {}

    /** Blocks further back than the nearest clear speaker that make a speaker a delay. */
    public static final double DELAY_BEYOND = 12;

    /** A linked speaker as the rack sees it: where, how far from the booth, a sub, behind a wall. */
    public record Seen(long pos, double distance, boolean sub, boolean walled) {}

    /** The tops' zones by position, and what else was decided; counts for the chat line. */
    public record Result(Map<Long, Integer> zones, float crossover, boolean align, int tops, int subs, int rooms, int delays) {}

    /** One speaker's zone, given the distance of the nearest top with a clear line to the booth. */
    public static int zoneOf(Seen s, double nearestClear) {
        if (s.sub()) return AmpSettings.SUBS;
        if (s.walled()) return AmpSettings.ROOM;
        return s.distance() > nearestClear + DELAY_BEYOND ? AmpSettings.DELAY : AmpSettings.FLOOR;
    }

    /** Zones for newly linked tops, judged against the nearest clear top of the whole batch (and {@code nearestKnown}), not in the order they come. */
    public static Map<Long, Integer> sortFresh(List<Seen> fresh, double nearestKnown) {
        double nearest = nearestKnown;
        for (Seen s : fresh) if (!s.walled()) nearest = Math.min(nearest, s.distance());
        Map<Long, Integer> zones = new HashMap<>();
        for (Seen s : fresh) zones.put(s.pos(), zoneOf(s, nearest));
        return zones;
    }

    public static Result plan(List<Seen> seen) {
        double nearest = Double.POSITIVE_INFINITY;
        for (Seen s : seen) if (!s.sub() && !s.walled()) nearest = Math.min(nearest, s.distance());
        Map<Long, Integer> zones = new HashMap<>();
        int tops = 0, subs = 0, rooms = 0, delays = 0;
        for (Seen s : seen) {
            int z = zoneOf(s, nearest);
            if (z == AmpSettings.SUBS) {
                subs++;
                continue;
            }
            tops++;
            zones.put(s.pos(), z);
            if (z == AmpSettings.ROOM) rooms++;
            if (z == AmpSettings.DELAY) delays++;
        }
        return new Result(zones, subs == 1 ? 80f : 100f, rooms + delays > 0, tops, subs, rooms, delays);
    }

    /** Writes a plan in: zones, crossover, alignment, the CLUB preset on neutral channels, power on. */
    public static void apply(AmpSettings s, Result r) {
        s.assign.clear();
        r.zones().forEach((pos, zone) -> s.assign.put(pos, new AmpSettings.Assignment(zone, false)));
        for (AmpSettings.Zone z : s.zones) {
            z.low = z.mid = z.high = z.hpf = z.delayMs = z.limit = 0;
            z.invert = z.mute = false;
        }
        s.applyPreset(AmpSettings.CLUB);
        s.crossover = r.crossover();
        s.align = r.align();
        s.power = true;
    }
}
