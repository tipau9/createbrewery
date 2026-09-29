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

    /**
     * Techno at the given tempo: a punchy kick on every beat, a rolling bassline on the three
     * sixteenths between (ducked under the kick), open hi-hats on the off-beat.
     */
    private static double techno(double time, double bpm) {
        double beat = 60.0 / bpm, sixteenth = beat / 4;
        double inBeat = time % beat, inSixteenth = time % sixteenth;
        // Kick: the pitch falls from 120 to 50 Hz, fast decay.
        double kick = 0.7 * Math.exp(-inBeat * 18) * Math.sin(2 * Math.PI * (50 * inBeat + 70 * (1 - Math.exp(-inBeat * 30)) / 30));
        double bass = (int) (inBeat / sixteenth) == 0 ? 0 : 0.3 * Math.exp(-inSixteenth * 20) * Math.sin(2 * Math.PI * 65 * inSixteenth);
        double offBeat = (time + beat / 2) % beat;
        double hat = offBeat < 0.03 ? 0.2 * Math.sin(time * 1.3e5) * Math.sin(time * 7.7e4) : 0;
        return kick + bass + hat;
    }

    private static void assertTechno(double bpm) {
        float[] kick = listen(time -> techno(time, bpm))[KICK];
        double beat = 60.0 / bpm, slice = KickDetector.SLICE;
        for (double at = Math.ceil(1.0 / beat) * beat; at < 1.95; at += beat) {
            int s = (int) ((at - 1.0) / slice);
            assertTrue(Math.max(kick[s], kick[Math.min(s + 1, kick.length - 1)]) > 0.5f, bpm + " bpm: kick at " + at + ": " + kick[s]);
            // The bass notes on the sixteenths between are not kicks.
            for (int n = 1; n < 4; n++) {
                int b = (int) ((at + n * beat / 4 - 1.0) / slice);
                if (b + 1 < kick.length) assertTrue(Math.max(kick[b], kick[b + 1]) < 0.5f, bpm + " bpm: bass taken for a kick at " + (at + n * beat / 4) + ": " + kick[b] + " " + kick[b + 1]);
            }
        }
    }

    @Test
    void findsTechnoKicks() {
        assertTechno(130);
        assertTechno(145);
    }

    @Test
    void findsHardTechnoKicks() {
        assertTechno(165);
        assertTechno(180);
    }

    /** A calm pad without drums: no kicks at all, but still loud enough to see. */
    @Test
    void padHasNoKicksButALevel() {
        float[][] out = listen(time -> 0.2 * Math.sin(2 * Math.PI * 220 * time) + 0.15 * Math.sin(2 * Math.PI * 330 * time));
        for (float kick : out[KICK]) assertTrue(kick < 0.1f, "no kick in a pad: " + kick);
        assertTrue(out[LEVEL][30] > 0.3f, "the pad is heard: " + out[LEVEL][30]);
    }

    @Test
    void theTraceHasOneLinePerSliceOnTheSongClock() {
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        KickDetector detector = new KickDetector();
        java.io.StringWriter text = new java.io.StringWriter();
        detector.trace = new java.io.PrintWriter(text);
        ByteBuffer pcm = ByteBuffer.allocate((int) RATE * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < RATE; i++) pcm.putShort((short) (drum(i / RATE) * 32767));
        pcm.flip();
        int slices = detector.slices(format, pcm)[KICK].length;
        String[] lines = text.toString().split("\\R");
        assertTrue(lines.length == slices, lines.length + " lines for " + slices + " slices");
        assertTrue(lines[1].startsWith("0.02,") && lines[1].split(",").length == KickDetector.TRACE_HEADER.split(",").length, lines[1]);
    }
}
