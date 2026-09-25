package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropDetectorTest {
    private static final float DT = 1f / 60f;

    /** Plays a stretch of a track at 60 frames a second; returns how many drops were heard. */
    private static int play(DropDetector d, double[] clock, double seconds, boolean kicks, float level, float hats) {
        int drops = 0;
        double beat = 60.0 / 128.0;
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
}
