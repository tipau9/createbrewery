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

    // Config-sync bug: FERMENTATION_DURATION_MULTIPLIER is a COMMON config, which NeoForge
    // never syncs to clients, so a client recomputing the scaled duration from its own config
    // can disagree with the server. resolveScaledDuration is the pure decision of which value
    // wins; the actual network round trip needs a real client and can't run in a GameTest
    // (GameTestServer has no ClientLevel, so level.isClientSide is never true there) - that
    // part is deferred to manual verification, noted in the task report.

    @Test
    void serverAlwaysUsesItsOwnComputation() {
        // Even if some synced value is present, the server is authoritative and ignores it.
        assertEquals(10000, FermentationProgress.resolveScaledDuration(false, 5000, 10000));
        assertEquals(10000, FermentationProgress.resolveScaledDuration(false, FermentationProgress.NO_SYNCED_DURATION, 10000));
    }

    @Test
    void clientPrefersSyncedValueOverItsOwnConfigMismatch() {
        // The exact bug: client's local multiplier disagrees with the server's (locallyComputed
        // stands in for "what the client would compute from its own, unsynced config").
        assertEquals(5000, FermentationProgress.resolveScaledDuration(true, 5000, 10000));
    }

    @Test
    void clientFallsBackBeforeFirstSync() {
        assertEquals(10000,
            FermentationProgress.resolveScaledDuration(true, FermentationProgress.NO_SYNCED_DURATION, 10000));
    }
}
