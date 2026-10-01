package com.createbrewery.block.club;

/**
 * What the club's music and its DJ are doing right now, worked out once a tick for a whole rig:
 * one place that turns raw pulse numbers and the mixer into show events, so every light and effect
 * answers the same beat, drop and breakdown. Free of Minecraft classes (unit-tested); see
 * {@link ClubStates} for who advances which instance.
 */
final class ClubState {
    static final float BEAT_ON = 0.38f, BEAT_OFF = 0.20f, DROP_ON = 0.6f, DROP_OFF = 0.2f;

    /** The loudest deck's mixer: low EQ knob 0..1 (0.5 = flat), filter -1 (low-pass closed) .. 0 .. 1 (high-pass), loop running. */
    record Mixer(float lowEq, float filter, boolean looping) {
        static final Mixer NONE = new Mixer(0.5f, 0f, false);
    }

    /** True for exactly one tick on a kick; its running count; 0..1 through the beat; the punch that dies before the next kick. */
    boolean beat;
    int beatIndex;
    float beatPhase, env;
    /** True for one tick when a drop lands; then 1 decaying over a second or two. */
    boolean dropEdge;
    float dropLevel;
    /** Music but no kick for a few beats. */
    boolean breakdown, playing, noFlashing;
    /** 0..1 build-up: the tension, sharpened by the mixer while a build-up is already running. */
    float buildUp;
    /** 0..1: low EQ killed or high-pass swept up. */
    float bassCut;
    /** 0..1: low-pass swept closed. */
    float filterClosed;
    /** Seconds per beat, at least 0.2. */
    double period = 0.5;

    private boolean kickLatched, dropLatched;
    private double sinceKick = 99;

    void update(float dt, float kick, float drop, float tension, boolean playing, double period, Mixer mixer, boolean noFlashing) {
        this.playing = playing;
        this.noFlashing = noFlashing;
        this.period = Math.max(0.2, period);

        beat = kick > BEAT_ON && !kickLatched;
        if (beat) {
            kickLatched = true;
            beatIndex++;
            sinceKick = 0;
        } else {
            if (kick < BEAT_OFF) kickLatched = false;
            sinceKick += dt;
        }
        beatPhase = (float) Math.min(1.0, sinceKick / this.period);
        env = (float) Math.exp(-sinceKick * 7);

        dropEdge = drop > DROP_ON && !dropLatched;
        if (dropEdge) dropLatched = true;
        else if (drop < DROP_OFF) dropLatched = false;
        dropLevel = Math.max(drop, dropLevel * (float) Math.exp(-dt * 1.5));

        breakdown = playing && sinceKick > Math.max(2.0, 4 * this.period);

        bassCut = Math.max(clamp((0.5f - mixer.lowEq()) * 2f), Math.max(0f, mixer.filter()));
        filterClosed = Math.max(0f, -mixer.filter());
        // The mixer only sharpens a build-up that is already running: a bass kill on its own must not strobe.
        buildUp = tension > 0.05f ? clamp(tension + 0.3f * Math.max(bassCut, filterClosed)) : tension;
    }

    /** The mixer of the deck the crowd hears: the loudest of the playing ones, {@link Mixer#NONE} when none plays. */
    static Mixer loudestMixer(boolean[] playing, float[] gain, float[] lowEq, float[] filter, boolean[] looping) {
        int best = -1;
        for (int d = 0; d < playing.length; d++) {
            if (playing[d] && (best < 0 || gain[d] > gain[best])) best = d;
        }
        return best < 0 ? Mixer.NONE : new Mixer(lowEq[best], filter[best], looping[best]);
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
