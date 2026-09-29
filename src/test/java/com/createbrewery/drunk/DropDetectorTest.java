package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropDetectorTest {
    private static final float DT = 1f / 60f;

    /** Plays a stretch of a track at 60 frames a second; returns how many drops were heard. */
    private static int play(DropDetector d, double[] clock, double seconds, boolean kicks, float level, float hats) {
        return play(d, clock, seconds, kicks ? BEAT : 0, level, hats);
    }

    private static final double BEAT = 60.0 / 128.0;

    /** Same, with a kick every {@code beat} seconds (0: none). */
    private static int play(DropDetector d, double[] clock, double seconds, double beat, float level, float hats) {
        int drops = 0;
        boolean kicks = beat > 0;
        for (double end = clock[0] + seconds; clock[0] < end; clock[0] += DT) {
            boolean onBeat = kicks && (clock[0] % beat) < 0.05;
            d.hear(onBeat ? 1f : 0f, level, hats, true, clock[0], DT);
            if (d.takeDrop()) drops++;
        }
        return drops;
    }

    @Test
    void buildUpThenDrop() {
        DropDetector d = new DropDetector();
        double[] clock = {0};
        play(d, clock, 30, true, 0.8f, 0.3f);
        assertEquals(128, 60.0 / d.period(), 3, "tempo learnt");
        // The kick drops out, a riser climbs, a breath of silence...
        for (int i = 0; i < 10; i++) play(d, clock, 1, false, 0.3f + 0.05f * i, 0.2f + 0.05f * i);
        assertTrue(d.tension > 0.8f, "build-up heard: " + d.tension);
        play(d, clock, 0.3, false, 0.05f, 0f);
        // ...and the kick is back.
        assertEquals(1, play(d, clock, 5, true, 0.9f, 0.3f), "one drop");
        assertEquals(0, d.tension, 1e-6, "build-up over");
    }

    @Test
    void aShortBreakIsNoDrop() {
        DropDetector d = new DropDetector();
        double[] clock = {0};
        play(d, clock, 20, true, 0.8f, 0.3f);
        play(d, clock, 1.8, false, 0.7f, 0.3f); // one bar without the kick
        assertEquals(0, play(d, clock, 5, true, 0.8f, 0.3f));
    }

    @Test
    void aSongWithoutDrumsNeverDrops() {
        DropDetector d = new DropDetector();
        double[] clock = {0};
        assertEquals(0, play(d, clock, 90, false, 0.5f, 0.1f));
        assertTrue(d.tension < 0.1f, "no endless build-up: " + d.tension);
    }

    @Test
    void aKickRollIntoThePauseIsABuildUp() {
        DropDetector d = new DropDetector();
        double[] clock = {0};
        play(d, clock, 30, true, 0.8f, 0.3f);
        // The kick doubles up for four bars while a riser climbs, never leaving...
        for (int i = 0; i < 8; i++) assertEquals(0, play(d, clock, 1, BEAT / 2, 0.5f + 0.05f * i, 0.3f + 0.05f * i));
        // ...one bar of breath...
        play(d, clock, 4 * BEAT, false, 0.05f, 0f);
        // ...and the drop.
        assertEquals(1, play(d, clock, 5, true, 0.9f, 0.3f), "one drop");
    }

    @Test
    void aLongBreakdownStillDrops() {
        DropDetector d = new DropDetector();
        double[] clock = {0};
        play(d, clock, 30, true, 0.8f, 0.3f);
        play(d, clock, 45, false, 0.4f, 0.1f); // a trance breakdown, three quarters of a minute
        assertEquals(1, play(d, clock, 5, true, 0.9f, 0.3f), "one drop");
    }

    @Test
    void aStrayKickInTheBreakdownIsNotTheDrop() {
        DropDetector d = new DropDetector();
        double[] clock = {0};
        play(d, clock, 30, true, 0.8f, 0.3f);
        play(d, clock, 8, false, 0.4f, 0.1f);
        // One lone thump...
        d.hear(1f, 0.5f, 0.1f, true, clock[0], DT);
        clock[0] += DT;
        float flash = 0f;
        for (int i = 0; i < 6 / DT; i++, clock[0] += DT) {
            d.hear(0f, 0.4f, 0.1f, true, clock[0], DT);
            flash = Math.max(flash, d.drop);
            assertTrue(!d.takeDrop(), "no drop without a second kick");
        }
        assertEquals(0f, flash, 1e-6, "not even a flash");
        assertTrue(d.tension > 0.3f, "build-up goes on: " + d.tension);
        // ...and the real drop is still heard, not blocked for a quarter of a minute.
        assertEquals(1, play(d, clock, 5, true, 0.9f, 0.3f), "the real drop");
    }

    @Test
    void aSteadyGrooveNeverDrops() {
        DropDetector d = new DropDetector();
        double[] clock = {0};
        // A normal track: steady kick, the level wandering about.
        for (int i = 0; i < 60; i++) assertEquals(0, play(d, clock, 1, true, 0.6f + 0.3f * (i % 3) / 2f, 0.2f + 0.2f * (i % 2)));
        assertEquals(0, play(d, clock, 1.5, false, 0.7f, 0.3f), "short break");
        assertEquals(0, play(d, clock, 5, true, 0.8f, 0.3f), "no drop after a short break");
    }

    @Test
    void aBeatWithoutGrooveNeverDrops() {
        // Hip-hop or a broken beat: kicks come and go, gaps of a beat up to several seconds.
        java.util.Random r = new java.util.Random(7);
        DropDetector d = new DropDetector();
        int drops = 0;
        double t = 0, nextKick = 0;
        for (int i = 0; i < 180 / DT; i++, t += DT) {
            boolean k = t >= nextKick;
            if (k) nextKick = t + (r.nextFloat() < 0.3f ? 2.0 + 4.0 * r.nextFloat() : 0.3 + 0.6 * r.nextFloat());
            d.hear(k ? 1f : 0f, 0.3f + 0.6f * r.nextFloat(), 0.4f * r.nextFloat(), true, t, DT);
            if (d.takeDrop()) drops++;
        }
        assertEquals(0, drops, "no drops in three minutes");
    }
}
