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
}
