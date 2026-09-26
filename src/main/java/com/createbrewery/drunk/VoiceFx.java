package com.createbrewery.drunk;

/**
 * Your own voice on drugs, as the others hear it over Simple Voice Chat (see
 * compat/voice/BreweryVoicePlugin): the microphone's 16-bit PCM at 48 kHz, changed frame by frame.
 * Plain Java, no Minecraft - the client sets {@link #params} every tick.
 * <ul>
 *   <li>Alcohol: a wobbling pitch (vibrato) and duller - slurred.</li>
 *   <li>Lachgas: tremolo and a wah that sweeps with the throb.</li>
 *   <li>Heroin, Xanax: muffled, quieter.</li>
 *   <li>Keta: echo, far away.</li>
 *   <li>Koks, meth: louder, sharper.</li>
 *   <li>DMT, the other side: a ring modulator, not quite human.</li>
 * </ul>
 */
public final class VoiceFx {
    /** Strengths 0..1; {@code wahPulse} is where in its sweep the wah is. */
    public record Params(float drunk, float wah, float wahPulse, float dull, float echo, float bright, float ring) {
        public static final Params NONE = new Params(0, 0, 0, 0, 0, 0, 0);

        boolean none() {
            return drunk < 0.01f && wah < 0.01f && dull < 0.01f && echo < 0.01f && bright < 0.01f && ring < 0.01f;
        }
    }

    public static volatile Params params = Params.NONE;

    private static final float RATE = 48000f;
    private final float[] delay = new float[48000];
    private int write;
    private long samples;
    private float lowDull, lowBright, bandLow, bandBand;

    /** Changes {@code pcm} in place. */
    public void process(short[] pcm, Params p) {
        if (p.none()) {
            samples += pcm.length;
            return;
        }
        float dullCut = coefficient(8000f - 6000f * Math.max(p.drunk * 0.6f, p.dull));
        float brightCut = coefficient(2000f);
        float wahFreq = 400f + 1600f * p.wahPulse;
        float f = (float) (2 * Math.sin(Math.PI * wahFreq / RATE));
        for (int i = 0; i < pcm.length; i++, samples++) {
            float t = samples / RATE;
            float x = pcm[i] / 32768f;
            delay[write] = x;
            float y = x;

            // Slurred: read a little behind, the lag wobbling four times a second.
            if (p.drunk > 0.01f) {
                float lag = (0.006f + 0.004f * p.drunk * (float) Math.sin(2 * Math.PI * 4.0 * t)) * RATE;
                y = y + (read(lag) - y) * p.drunk;
            }
            // Muffled (heroin, Xanax, and some of the alcohol).
            lowDull += dullCut * (y - lowDull);
            y = y + (lowDull - y) * Math.min(1f, Math.max(p.dull, p.drunk * 0.6f));
            y *= 1f - 0.4f * p.dull;
            // Wah: a band that sweeps; tremolo on top.
            if (p.wah > 0.01f) {
                float high = y - bandLow - 0.35f * bandBand;
                bandBand += f * high;
                bandLow += f * bandBand;
                y = y + (2.5f * bandBand - y) * p.wah;
                y *= 1f - 0.5f * p.wah * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * 6.0 * t));
            }
            // Echo: a quarter second back, feeding into itself.
            if (p.echo > 0.01f) {
                y += 0.6f * p.echo * read(0.25f * RATE);
                delay[write] = x + 0.45f * p.echo * read(0.25f * RATE);
            }
            // Sharper and louder: the highs lifted.
            lowBright += brightCut * (y - lowBright);
            y = (y + p.bright * (y - lowBright)) * (1f + 0.3f * p.bright);
            // Not quite human.
            if (p.ring > 0.01f) y = y + (y * (float) Math.sin(2 * Math.PI * 70.0 * t) - y) * p.ring;

            write = (write + 1) % delay.length;
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(y * 32768f)));
        }
    }

    private float read(float back) {
        float pos = write - back;
        while (pos < 0) pos += delay.length;
        int a = (int) pos;
        float frac = pos - a;
        return delay[a % delay.length] * (1 - frac) + delay[(a + 1) % delay.length] * frac;
    }

    /** One-pole low-pass coefficient for {@code cutoff} Hz. */
    private static float coefficient(float cutoff) {
        return (float) (1 - Math.exp(-2 * Math.PI * Math.max(50f, cutoff) / RATE));
    }
}
