package com.createbrewery.drunk;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

/**
 * Finds the kick drum in one song's 16-bit PCM, chunk by chunk (see {@link MusicPulse}). Each
 * chunk is split into 20 ms slices; a slice is a kick when its bass (roughly below 150 Hz) is
 * clearly louder than the last second's average.
 */
final class KickDetector {
    static final double SLICE = 0.02;

    private float lowPass, alpha = -1f;
    private final ArrayDeque<Float> history = new ArrayDeque<>();
    private float historySum;

    /** 0..1 per slice: how hard it kicks there. */
    float[] slices(AudioFormat format, ByteBuffer pcm) {
        pcm.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int channels = Math.max(1, format.getChannels());
        float rate = format.getSampleRate();
        if (alpha < 0f) alpha = (float) (1.0 - Math.exp(-2.0 * Math.PI * 150.0 / rate));
        int perSlice = Math.max(1, (int) (rate * SLICE));
        float[] kick = new float[pcm.remaining() / (2 * channels) / perSlice];
        int historyMax = (int) (1.0 / SLICE);
        for (int s = 0; s < kick.length; s++) {
            double energy = 0;
            for (int f = 0; f < perSlice; f++) {
                float mono = 0f;
                for (int c = 0; c < channels; c++) mono += pcm.getShort() / 32768f;
                lowPass += alpha * (mono / channels - lowPass);
                energy += lowPass * lowPass;
            }
            float bass = (float) Math.sqrt(energy / perSlice);
            float mean = history.isEmpty() ? bass : historySum / history.size();
            // Not just louder than before, and not just silence.
            kick[s] = bass < 0.01f ? 0f : Math.max(0f, Math.min(1f, (bass / (mean + 1e-4f) - 1.25f) * 1.5f));
            history.addLast(bass);
            historySum += bass;
            if (history.size() > historyMax) historySum -= history.removeFirst();
        }
        return kick;
    }
}
