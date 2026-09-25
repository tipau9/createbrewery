package com.createbrewery.drunk;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

/**
 * Listens to one song's 16-bit PCM, chunk by chunk (see {@link MusicPulse}), in 20 ms slices:
 * <ul>
 *   <li>the kick: the bass (below about 150 Hz) jumping up, nearly as hard as the hardest jump of
 *       the last second. Rises, not levels, so it still finds the kick in loud modern masters whose
 *       bass never lets up, and measured against the kicks themselves, so fast techno with a kick
 *       on every beat and a rolling bassline in between still comes out as kick, kick, kick;</li>
 *   <li>the level: how loud the song is right now, against its own loudest (so quiet songs count too);</li>
 *   <li>the hats: the same kind of jump in the highs - hi-hats, claps, snares.</li>
 * </ul>
 */
final class KickDetector {
    static final double SLICE = 0.02;
    static final int KICK = 0, LEVEL = 1, HATS = 2;

    private static final int SUBS = 4;

    private float low1, low2, last, alpha = -1f, peak = 0.02f;
    /** The last three 5 ms loudnesses of the bass and of the highs, newest first. */
    private final float[] bassWas = new float[3], highWas = new float[3];
    private final Onsets kicks = new Onsets(), hats = new Onsets();

    /** Per slice: [KICK], [LEVEL] and [HATS], each 0..1. */
    float[][] slices(AudioFormat format, ByteBuffer pcm) {
        pcm.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int channels = Math.max(1, format.getChannels());
        float rate = format.getSampleRate();
        if (alpha < 0f) alpha = (float) (1.0 - Math.exp(-2.0 * Math.PI * 150.0 / rate));
        int perSlice = Math.max(SUBS, (int) (rate * SLICE));
        int n = pcm.remaining() / (2 * channels) / perSlice;
        float[][] out = new float[3][n];
        float fade = (float) Math.exp(-SLICE / 8.0); // the loudest point is forgotten over some seconds
        for (int s = 0; s < n; s++) {
            double allE = 0;
            float kickRise = 0f, hatRise = 0f;
            // In 5 ms steps: how much louder the last 10 ms are than the 10 ms before. A kick hits
            // within a few milliseconds; bass notes swelling in and two low notes beating against
            // each other rise much more slowly, so they stay small here.
            for (int sub = 0; sub < SUBS; sub++) {
                int frames = (sub + 1) * perSlice / SUBS - sub * perSlice / SUBS;
                double bassE = 0, highE = 0;
                for (int f = 0; f < frames; f++) {
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
                kickRise = Math.max(kickRise, rise(bassWas, (float) Math.sqrt(bassE / frames)));
                hatRise = Math.max(hatRise, rise(highWas, (float) Math.sqrt(highE / frames)));
            }
            out[KICK][s] = kicks.hit(kickRise);
            out[HATS][s] = hats.hit(hatRise);
            float all = (float) Math.sqrt(allE / perSlice);
            peak = Math.max(all, peak * fade);
            out[LEVEL][s] = all < 0.003f ? 0f : all / peak;
        }
        return out;
    }

    /** The last 10 ms against the 10 ms before, then remembers this 5 ms step. */
    private static float rise(float[] was, float now) {
        float rise = (now + was[0]) / 2f - (was[1] + was[2]) / 2f;
        was[2] = was[1];
        was[1] = was[0];
        was[0] = now;
        return rise;
    }

    /**
     * Jumps that stand out: either clearly above the usual jumps of the last second, or nearly as
     * big as the biggest of them. The second rule is for dense beats - four kicks a second push
     * the usual jump up so far that the first rule alone would miss them.
     */
    private static final class Onsets {
        private final ArrayDeque<Float> history = new ArrayDeque<>();
        private float sum, sumSq;

        float hit(float rise) {
            rise = Math.max(0f, rise);
            int size = Math.max(1, history.size());
            float mean = sum / size, std = (float) Math.sqrt(Math.max(0f, sumSq / size - mean * mean));
            float biggest = rise;
            for (float old : history) biggest = Math.max(biggest, old);
            float threshold = Math.min(mean + 1.5f * std, 0.35f * biggest) + 0.004f;
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
