package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceMaskTest {
    private static short[] voice(float amplitude) {
        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (amplitude * 32767 * Math.sin(2 * Math.PI * 220 * i / 48000.0));
        return pcm;
    }

    private static double rms(short[] pcm) {
        double sum = 0;
        for (short s : pcm) sum += (double) s * s;
        return Math.sqrt(sum / pcm.length) / 32768;
    }

    /** RMS of the last of {@code frames} frames of a talker at {@code amplitude}. */
    private static double heard(float amplitude, float music, double distance, float sensitivity) {
        VoiceMask mask = new VoiceMask();
        short[] pcm = null;
        for (int frame = 0; frame < 30; frame++) {
            pcm = voice(amplitude);
            mask.process(pcm, music, distance, sensitivity);
        }
        return rms(pcm);
    }

    @Test
    void normalTalkFarAwayInLoudClubIsDrownedOut() {
        double before = rms(voice(0.1f));
        assertTrue(heard(0.1f, 1f, 8, 1f) < before * 0.2, "still " + heard(0.1f, 1f, 8, 1f));
    }

    @Test
    void shoutingCutsThrough() {
        double before = rms(voice(0.5f));
        assertTrue(heard(0.5f, 1f, 8, 1f) > before * 0.95);
    }

    @Test
    void untouchedWhenCloseQuietOrOff() {
        for (float[] c : new float[][] {{1f, 1.5f, 1f}, {0.1f, 8f, 1f}, {1f, 8f, 0f}}) {
            VoiceMask mask = new VoiceMask();
            for (int frame = 0; frame < 10; frame++) {
                short[] pcm = voice(0.1f), before = pcm.clone();
                mask.process(pcm, c[0], c[1], c[2]);
                assertArrayEquals(before, pcm);
            }
        }
    }
}
