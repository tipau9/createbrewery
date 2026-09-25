package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KickDetectorTest {
    /** Two seconds of 120 bpm: a 60 Hz kick every half second, quiet hi-hat noise in between. */
    @Test
    void findsTheKicks() {
        float rate = 44100f;
        AudioFormat format = new AudioFormat(rate, 16, 1, true, false);
        KickDetector detector = new KickDetector();
        float[] kick = null;
        java.util.Random noise = new java.util.Random(1);
        for (int second = 0; second < 2; second++) {
            ByteBuffer pcm = ByteBuffer.allocate((int) rate * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < rate; i++) {
                double inBeat = (i % (rate / 2)) / rate;
                double drum = inBeat < 0.08 ? 0.8 * Math.sin(2 * Math.PI * 60 * inBeat) : 0;
                pcm.putShort((short) ((drum + 0.05 * (noise.nextDouble() - 0.5)) * 32767));
            }
            pcm.flip();
            kick = detector.slices(format, pcm);
        }
        assertTrue(kick[1] > 0.5f, "kick on the beat: " + kick[1]);
        assertTrue(kick[26] > 0.5f, "kick on the off-beat: " + kick[26]);
        assertTrue(kick[12] < 0.1f, "no kick between beats: " + kick[12]);
        assertTrue(kick[40] < 0.1f, "no kick between beats: " + kick[40]);
    }
}
