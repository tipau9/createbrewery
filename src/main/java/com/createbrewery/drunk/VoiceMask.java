package com.createbrewery.drunk;

/**
 * Other players' voices in a loud club, as you hear them over Simple Voice Chat (see
 * compat/voice/BreweryVoicePlugin): the music drowns out whoever talks at a normal level more than
 * two blocks away, so you have to shout (the Lombard effect). Drowned-out speech loses its lows,
 * its highs and most of its level - scraps come through, not silence. Behind a door and a wall the
 * music is quiet enough to talk again. Microphones differ a lot in level, so each talker is
 * measured against their own usual level: shouting means louder than you normally talk. One per
 * talker, since the filters and that level are theirs; plain Java, no Minecraft.
 */
public final class VoiceMask {
    private static final double RATE = 48000;
    /** Below this heard music level (0..1, walls counted) nothing is drowned out: a quiet bar, the toilets. */
    static final float QUIET = 0.15f;
    /** Voice RMS (0..1) that cuts through music at full level, at sensitivity 1. Normal talk is ~0.03-0.1. */
    static final float SHOUT = 0.2f;
    /** Closer than this (blocks) you hear each other anyway: talking into an ear. Fully drowned out from twice this. */
    static final double EAR = 2.0;
    /** A usual talking level: each talker's own is scaled to this. */
    static final float NORMAL = 0.06f;
    /** Per spoken frame: how slowly a talker's usual level is learned (~40 s of talking). */
    private static final float LEARN = 0.0005f;
    /** How far drowned-out speech is turned down (-20 dB). */
    private static final float FLOOR = 0.1f;
    /** Frames quieter than this are pauses: they say nothing about how loud someone talks. */
    private static final float PAUSE = 0.005f;
    /** Per 20 ms frame: how quickly the drowning follows a talker getting louder or quieter. */
    private static final float EASE = 0.3f;

    private final DeckFx.Biquad lows = new DeckFx.Biquad(), highs = new DeckFx.Biquad();
    private float drowned, usual = NORMAL;

    public VoiceMask() {
        lows.highPass(300, RATE, Math.sqrt(0.5));
        highs.lowPass(3000, RATE, Math.sqrt(0.5));
    }

    /**
     * 0 heard clearly .. 1 drowned out: a voice at RMS {@code voice}, {@code distance} blocks away,
     * under music heard at {@code music}. {@code sensitivity} is the client config's (0 off).
     */
    static float drowned(float voice, float music, double distance, float sensitivity) {
        if (sensitivity <= 0 || music <= QUIET || distance <= EAR) return 0;
        float needed = SHOUT * sensitivity * (music - QUIET) / (1 - QUIET);
        float far = (float) Math.min(1, (distance - EAR) / EAR);
        return Math.max(0, 1 - voice / needed) * far;
    }

    /** Changes {@code pcm} (16-bit, 48 kHz, one frame) in place. */
    public void process(short[] pcm, float music, double distance, float sensitivity) {
        if (pcm.length == 0) return;
        double sum = 0;
        for (short s : pcm) sum += (double) s * s;
        float rms = (float) (Math.sqrt(sum / pcm.length) / 32768);
        float from = drowned;
        if (rms > PAUSE) {
            usual = Math.max(0.01f, Math.min(0.3f, usual + (rms - usual) * LEARN));
            drowned += (drowned(rms * NORMAL / usual, music, distance, sensitivity) - drowned) * EASE;
        }
        if (from < 0.001f && drowned < 0.001f) {
            drowned = 0;
            return;
        }
        for (int i = 0; i < pcm.length; i++) {
            // Glided across the frame, so a change never clicks.
            float d = from + (drowned - from) * i / pcm.length;
            double x = pcm[i] / 32768.0;
            double band = highs.run(lows.run(x));
            double y = (x + (band - x) * d) * (1 - (1 - FLOOR) * d);
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(y * 32768)));
        }
    }
}
