package com.createbrewery.drunk;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

/**
 * Listens to one song's 16-bit PCM, chunk by chunk (see {@link MusicPulse}), in 20 ms slices:
 * <ul>
 *   <li>the kick: the bass (below about 150 Hz) jumping up, clearly more than it has been jumping
 *       over the last second. Rises, not levels, so it still finds the kick in loud modern masters
 *       whose bass never lets up;</li>
 *   <li>the level: how loud the song is right now, against its own loudest (so quiet songs count too);</li>
 *   <li>the hats: the same kind of jump in the highs - hi-hats, claps, snares.</li>
 * </ul>
 */
final class KickDetector {
    static final double SLICE = 0.02;
    static final int KICK = 0, LEVEL = 1, HATS = 2;

    private float low1, low2, last, alpha = -1f, lastBass, lastHigh, peak = 0.02f;
    private final Onsets kicks = new Onsets(), hats = new Onsets();

    /** Per slice: [KICK], [LEVEL] and [HATS], each 0..1. */
    float[][] slices(AudioFormat format, ByteBuffer pcm) {
        pcm.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int channels = Math.max(1, format.getChannels());
        float rate = format.getSampleRate();
        if (alpha < 0f) alpha = (float) (1.0 - Math.exp(-2.0 * Math.PI * 150.0 / rate));
        int perSlice = Math.max(1, (int) (rate * SLICE));
        int n = pcm.remaining() / (2 * channels) / perSlice;
        float[][] out = new float[3][n];
        float fade = (float) Math.exp(-SLICE / 8.0); // the loudest point is forgotten over some seconds
        for (int s = 0; s < n; s++) {
            double bassE = 0, highE = 0, allE = 0;
            for (int f = 0; f < perSlice; f++) {
                float mono = 0f;
                for (int c = 0; c < channels; c++) mono += pcm.getShort() / 32768f;
                mono /= channels;
                // Two low-passes in a row: much less of the mids leaks into the bass.
                low1 += alpha * (mono - low1);
                low2 += alpha * (low1 - low2);
                float high = mono - last;
                last = mono;
                bassE += low2 * low2;
                highE += high * high;
                allE += mono * mono;
            }
            float bass = (float) Math.sqrt(bassE / perSlice), high = (float) Math.sqrt(highE / perSlice);
            float all = (float) Math.sqrt(allE / perSlice);
            out[KICK][s] = kicks.hit(bass - lastBass);
            out[HATS][s] = hats.hit(high - lastHigh);
            lastBass = bass;
            lastHigh = high;
            peak = Math.max(all, peak * fade);
            out[LEVEL][s] = all < 0.003f ? 0f : all / peak;
        }
        return out;
    }

    /** Jumps that stand out from the last second's jumps. */
    private static final class Onsets {
        private final ArrayDeque<Float> history = new ArrayDeque<>();
        private float sum, sumSq;

        float hit(float rise) {
            rise = Math.max(0f, rise);
            int size = Math.max(1, history.size());
            float mean = sum / size, std = (float) Math.sqrt(Math.max(0f, sumSq / size - mean * mean));
            float threshold = mean + 1.5f * std + 0.004f;
            history.addLast(rise);
            sum += rise;
            sumSq += rise * rise;
            if (history.size() > (int) (1.0 / SLICE)) {
                float old = history.removeFirst();
                sum -= old;
                sumSq -= old * old;
            }
            return Math.max(0f, Math.min(1f, (rise - threshold) / threshold));
        }
    }
}
