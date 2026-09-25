package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.DoubleUnaryOperator;

import static com.createbrewery.drunk.KickDetector.KICK;
import static com.createbrewery.drunk.KickDetector.LEVEL;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KickDetectorTest {
    private static final float RATE = 44100f;

    /** Feeds two one-second chunks of the given wave; returns the slices of the second. */
    private static float[][] listen(DoubleUnaryOperator wave) {
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        KickDetector detector = new KickDetector();
        float[][] out = null;
        for (int second = 0; second < 2; second++) {
            ByteBuffer pcm = ByteBuffer.allocate((int) RATE * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < RATE; i++) pcm.putShort((short) (wave.applyAsDouble(second + i / RATE) * 32767));
            pcm.flip();
            out = detector.slices(format, pcm);
        }
        return out;
    }

    /** 120 bpm: a 60 Hz kick on every half second. */
    private static double drum(double time) {
        double inBeat = time % 0.5;
        return inBeat < 0.08 ? 0.5 * Math.sin(2 * Math.PI * 60 * inBeat) : 0;
    }

    private static void assertKicks(float[] kick) {
        assertTrue(Math.max(kick[0], kick[1]) > 0.5f, "kick on the beat: " + kick[0] + " " + kick[1]);
        assertTrue(Math.max(kick[25], kick[26]) > 0.5f, "kick on the off-beat: " + kick[25] + " " + kick[26]);
        assertTrue(kick[12] < 0.1f && kick[40] < 0.1f, "no kick between beats: " + kick[12] + " " + kick[40]);
    }

    @Test
    void findsTheKicks() {
        java.util.Random noise = new java.util.Random(1);
        assertKicks(listen(time -> drum(time) + 0.05 * (noise.nextDouble() - 0.5))[KICK]);
    }

    /** Loud modern masters: a bassline that never stops, and the kick on top of it. */
    @Test
    void findsTheKicksOverAStandingBass() {
        assertKicks(listen(time -> drum(time) + 0.4 * Math.sin(2 * Math.PI * 50 * time))[KICK]);
    }

    /** A calm pad without drums: no kicks at all, but still loud enough to see. */
    @Test
    void padHasNoKicksButALevel() {
        float[][] out = listen(time -> 0.2 * Math.sin(2 * Math.PI * 220 * time) + 0.15 * Math.sin(2 * Math.PI * 330 * time));
        for (float kick : out[KICK]) assertTrue(kick < 0.1f, "no kick in a pad: " + kick);
        assertTrue(out[LEVEL][30] > 0.3f, "the pad is heard: " + out[LEVEL][30]);
    }
}
