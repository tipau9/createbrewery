package com.createbrewery.drunk;

/**
 * What an emitter's source needs to stay in step with its song, decided once a frame: nothing, a
 * slightly different playing speed, or a restart - and a restart at most every quarter second, so
 * a stalled frame cannot turn into a storm of clicks. Free of Minecraft and OpenAL classes.
 */
final class SyncPolicy {
    enum Action { KEEP, SERVO, RESTART }

    record Decision(Action action, float pitch, String reason) {}

    /** Under this (seconds) it is in step; up to {@link #DRIFT_HARD} the speed is nudged; over it, restart. */
    static final double DRIFT_SOFT = 0.02, DRIFT_HARD = 0.25;
    /** Seconds between restarts. */
    static final double COOLDOWN = 0.25;
    /** The most the playing speed is moved to pull a drift back. */
    static final float SERVO_RANGE = 0.02f;

    private double lastRestart = Double.NEGATIVE_INFINITY;

    /**
     * @param running the source is playing or paused
     * @param queued  buffers still queued on it
     * @param here    the song frame it is playing, {@code target} the one it should be at
     * @param now     seconds, any clock that only goes forward
     */
    Decision decide(boolean running, int queued, long here, long target, double rate, double now) {
        double ahead = (here - target) / rate;
        float pitch = Math.abs(ahead) < DRIFT_SOFT ? 1f
            : (float) Math.max(1 - SERVO_RANGE, Math.min(1 + SERVO_RANGE, 1 - ahead * 4));
        boolean stopped = !running, starved = running && queued == 0, drifted = Math.abs(ahead) > DRIFT_HARD;
        if ((stopped || starved || drifted) && now - lastRestart >= COOLDOWN) {
            lastRestart = now;
            return new Decision(Action.RESTART, 1f, stopped ? "stopped" : starved ? "starved" : "drift");
        }
        return new Decision(pitch == 1f ? Action.KEEP : Action.SERVO, pitch, "");
    }
}
