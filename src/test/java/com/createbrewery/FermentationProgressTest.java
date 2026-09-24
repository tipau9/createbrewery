package com.createbrewery;

import com.createbrewery.recipe.FermentationProgress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FermentationProgressTest {

    private static final int DAY = 24000;

    @Test
    void notStartedIsZero() {
        assertEquals(0f, FermentationProgress.progress(-1L, 5000L, DAY));
    }

    @Test
    void halfwayThroughIsOneHalf() {
        assertEquals(0.5f, FermentationProgress.progress(1000L, 1000L + DAY / 2, DAY), 0.001f);
    }

    @Test
    void completeAtExactDuration() {
        assertEquals(1f, FermentationProgress.progress(1000L, 1000L + DAY, DAY));
    }

    // Review Focus #1: world time moved backwards (/time set, backup restore)
    @Test
    void negativeElapsedClampsToZero() {
        assertEquals(0f, FermentationProgress.progress(9000L, 1000L, DAY));
    }

    // Review Focus #4: player away for thousands of in-game days
    @Test
    void enormousElapsedSaturatesAtOne() {
        assertEquals(1f, FermentationProgress.progress(0L, Long.MAX_VALUE, DAY));
    }

    @Test
    void zeroOrNegativeDurationIsImmediatelyComplete() {
        assertEquals(1f, FermentationProgress.progress(0L, 10L, 0));
        assertEquals(1f, FermentationProgress.progress(0L, 10L, -5));
    }

    @Test
    void neverReturnsNaN() {
        assertFalse(Float.isNaN(FermentationProgress.progress(0L, 0L, 0)));
        assertFalse(Float.isNaN(FermentationProgress.progress(-1L, -1L, DAY)));
    }
}
