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

    /** The raw pulse as heard, 0..1 (smooth, for things that follow it continuously) and the raw drop. */
    float kick, drop;
    /**
     * True for exactly one tick on a beat; its running count; 0..1 through the beat; the punch that
     * dies before the next kick. When the kick drops out (a breakdown) the beat runs on by itself
     * at the learnt tempo, like a flywheel, for {@link #FLYWHEEL_BEATS} beats and then fades out;
     * the punch only ever follows real kicks.
     */
    boolean beat;
    int beatIndex;
    float beatPhase, env;
    /** 1 while the beat runs on without a kick, fading to 0 after {@link #FLYWHEEL_BEATS} (8 bars) and 4 more bars. */
    float flywheel;
    static final int FLYWHEEL_BEATS = 32, FLYWHEEL_FADE = 16;
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

    private boolean kickLatched, dropLatched, ranOn;
    private double sinceKick = 99, sinceBeat = 99;

    void update(float dt, float kick, float drop, float tension, boolean playing, double period, Mixer mixer, boolean noFlashing) {
        this.kick = kick;
        this.drop = drop;
        this.playing = playing;
        this.noFlashing = noFlashing;
        this.period = Math.max(0.2, period);

        boolean kicked = kick > BEAT_ON && !kickLatched;
        if (kicked) {
            kickLatched = true;
            // A late kick right after the flywheel already counted this beat only pulls it back in phase.
            beat = !(ranOn && sinceBeat < 0.35 * this.period);
            if (beat) beatIndex++;
            sinceKick = 0;
            sinceBeat = 0;
            ranOn = false;
        } else {
            if (kick < BEAT_OFF) kickLatched = false;
            sinceKick += dt;
            sinceBeat += dt;
            beat = false;
        }
        flywheel = playing && sinceKick < 99
            ? clamp((float) ((FLYWHEEL_BEATS + FLYWHEEL_FADE - sinceKick / this.period) / FLYWHEEL_FADE)) : 0f;
        if (!kicked && flywheel > 0f && sinceBeat >= this.period) {
            beat = true;
            beatIndex++;
            sinceBeat -= this.period;
            ranOn = true;
        }
        beatPhase = flywheel > 0f ? (float) Math.min(1.0, sinceBeat / this.period) : (float) Math.min(1.0, sinceKick / this.period);
        env = (float) Math.exp(-sinceKick * 7);

        dropEdge = drop > DROP_ON && !dropLatched;
        if (dropEdge) dropLatched = true;
        else if (drop < DROP_OFF) dropLatched = false;
        dropLevel = Math.max(drop, dropLevel * (float) Math.exp(-dt * 1.5));

        breakdown = playing && sinceKick > Math.max(2.0, 4 * this.period);

        bassCut = Math.max(clamp((0.47f - mixer.lowEq()) / 0.47f), Math.max(0f, mixer.filter()));
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
