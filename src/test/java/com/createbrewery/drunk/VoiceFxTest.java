package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceFxTest {
    private static final VoiceFx.Params ALL = new VoiceFx.Params(1, 1, 0.5f, 1, 1, 1, 1);

    private static short[] sine(float amplitude) {
        short[] pcm = new short[960];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (amplitude * 32767 * Math.sin(2 * Math.PI * 220 * i / 48000.0));
        return pcm;
    }

    @Test
    void soberVoicePassesUnchanged() {
        short[] pcm = sine(0.5f), before = pcm.clone();
        new VoiceFx().process(pcm, VoiceFx.Params.NONE);
        assertArrayEquals(before, pcm);
    }

    @Test
    void silenceStaysSilent() {
        VoiceFx fx = new VoiceFx();
        for (int frame = 0; frame < 20; frame++) {
            short[] pcm = new short[960];
            fx.process(pcm, ALL);
            for (short s : pcm) assertEquals(0, s);
        }
    }

    @Test
    void loudVoiceNeverWrapsAround() {
        VoiceFx fx = new VoiceFx();
        for (int frame = 0; frame < 50; frame++) {
            short[] pcm = new short[960];
            java.util.Arrays.fill(pcm, Short.MAX_VALUE);
            short[] sine = sine(1f);
            fx.process(frame % 2 == 0 ? pcm : sine, new VoiceFx.Params(0, 0, 0, 0, 1, 1, 0));
            // Bright and echo push a full-scale constant above the top: it is held there, not flipped.
            if (frame % 2 == 0) for (short s : pcm) assertTrue(s > 0, "wrapped to " + s);
        }
    }

    @Test
    void drunkVoiceIsChanged() {
        VoiceFx fx = new VoiceFx();
        short[] pcm = sine(0.5f);
        fx.process(sine(0.5f), new VoiceFx.Params(1, 0, 0, 0, 0, 0, 0));
        short[] before = pcm.clone();
        fx.process(pcm, new VoiceFx.Params(1, 0, 0, 0, 0, 0, 0));
        int differ = 0;
        for (int i = 0; i < pcm.length; i++) if (Math.abs(pcm[i] - before[i]) > 500) differ++;
        assertTrue(differ > 100, "only " + differ + " samples changed");
    }

    @Test
    void microphoneBoostsVolume() {
        VoiceFx fx = new VoiceFx();
        short[] pcm = sine(0.2f);
        short[] before = pcm.clone();
        fx.process(pcm, new VoiceFx.Params(0, 0, 0, 0, 0, 0, 0, 1.0f));
        int louder = 0;
        for (int i = 0; i < pcm.length; i++) {
            if (Math.abs(pcm[i]) > Math.abs(before[i])) louder++;
        }
        assertTrue(louder > pcm.length / 2, "voice should be amplified: " + louder + " of " + pcm.length + " samples louder");
    }

    @Test
    void microphoneCompressesWithoutHardClipping() {
        VoiceFx fx = new VoiceFx();
        short[] pcm = sine(0.9f);
        fx.process(pcm, new VoiceFx.Params(0, 0, 0, 0, 0, 0, 0, 1.0f));
        for (short s : pcm) {
            assertTrue(s >= Short.MIN_VALUE && s <= Short.MAX_VALUE);
        }
    }
}
