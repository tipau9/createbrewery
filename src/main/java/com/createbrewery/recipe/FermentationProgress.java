package com.createbrewery.recipe;

public final class FermentationProgress {

    public static final long NOT_STARTED = -1L;

    private FermentationProgress() {}

    /**
     * Fraction of a fermentation batch completed, always within [0, 1].
     *
     * Derived from world time rather than a tick counter so that batches
     * continue through chunk unload. That means this must tolerate a world
     * clock that jumps, including backwards.
     */
    public static float progress(long startedAt, long now, int duration) {
        if (startedAt == NOT_STARTED) return 0f;
        if (duration <= 0) return 1f;

        long elapsed = now - startedAt;
        if (elapsed <= 0) return 0f;          // clock moved backwards, or same tick
        if (elapsed >= duration) return 1f;   // saturate before the float divide

        return (float) elapsed / (float) duration;
    }

    /** Sentinel meaning "no synced duration has been received yet". */
    public static final int NO_SYNCED_DURATION = -1;

    /**
     * Chooses which scaled duration a block entity should treat as authoritative.
     *
     * Common configs are never synced to clients by NeoForge (only server-controlled ones
     * are), so a client that recomputes the scaled duration from its own copy of the config
     * can disagree with the server whenever the fermentation duration multiplier isn't 1.0.
     * Once the server has sent a synced value, the client must prefer it over anything it
     * would compute locally; the server always uses its own live computation.
     */
    public static int resolveScaledDuration(boolean clientSide, int syncedDuration, int locallyComputed) {
        if (clientSide && syncedDuration != NO_SYNCED_DURATION) return syncedDuration;
        return locallyComputed;
    }
}
