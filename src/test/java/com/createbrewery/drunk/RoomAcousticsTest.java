package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** A closet is short and tight, a hall long, the open air dry. */
class RoomAcousticsTest {
    private static RoomAcoustics.Params room(double d) {
        double[] dist = new double[16];
        boolean[] hit = new boolean[16];
        Arrays.fill(dist, d);
        Arrays.fill(hit, true);
        return RoomAcoustics.estimate(dist, hit);
    }

    @Test
    void aHallRingsLongerThanACloset() {
        assertTrue(room(3).decayTime() < room(15).decayTime());
    }

    @Test
    void decayGrowsWithTheRoomAndStaysInRange() {
        float last = 0f;
        for (double d = 0; d <= 24; d += 0.5) {
            RoomAcoustics.Params p = room(d);
            assertTrue(p.decayTime() >= last - 1e-6f, "shrank at " + d);
            assertTrue(p.decayTime() >= 0.1f && p.decayTime() <= 3.5f, "out of range at " + d + ": " + p.decayTime());
            last = p.decayTime();
        }
    }

    @Test
    void standingInsideABlockStaysFiniteAndInRange() {
        RoomAcoustics.Params p = room(0);
        for (float v : new float[] {p.decayTime(), p.density(), p.diffusion(), p.lateGain(), p.reflectionsGain()}) {
            assertTrue(Float.isFinite(v) && v >= 0f, "bad value " + v);
        }
        assertTrue(p.decayTime() <= 0.3f, "a closet should be short");
    }

    @Test
    void openAirIsDry() {
        double[] dist = new double[16];
        boolean[] hit = new boolean[16];
        Arrays.fill(dist, RoomAcoustics.MAX_RAY);
        assertEquals(0f, RoomAcoustics.estimate(dist, hit).lateGain());
    }

    @Test
    void outdoorsWithOnlyTheGroundHitIsNearlyDry() {
        double[] dist = new double[16];
        boolean[] hit = new boolean[16];
        Arrays.fill(dist, RoomAcoustics.MAX_RAY);
        for (int i = 12; i < 16; i++) { // the four downward rays meet the ground
            dist[i] = 1.7;
            hit[i] = true;
        }
        assertTrue(RoomAcoustics.estimate(dist, hit).lateGain() < 0.15f);
        assertTrue(room(8).lateGain() > 1f, "a closed room keeps its full late reverb");
    }

    @Test
    void lerpMovesEveryFieldTowardsTheTarget() {
        RoomAcoustics.Params a = room(3), b = room(15);
        RoomAcoustics.Params half = RoomAcoustics.lerp(a, b, 0.5);
        assertEquals((a.decayTime() + b.decayTime()) / 2, half.decayTime(), 1e-5);
        assertEquals(b.decayTime(), RoomAcoustics.lerp(a, b, 1).decayTime(), 1e-6);
        assertEquals(a.decayTime(), RoomAcoustics.lerp(a, b, 0).decayTime(), 1e-6);
    }
}
