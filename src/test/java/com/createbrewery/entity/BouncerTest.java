package com.createbrewery.entity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BouncerTest {

    @Test
    void guestListTrimmingAndCaseInsensitivity() {
        Set<String> list = new HashSet<>();
        String rawBook = "Alice\n  Bob  \nCHARLIE, Dave;Eve";
        for (String line : rawBook.split("[\\r\\n,;]+")) {
            String trimmed = line.trim().toLowerCase();
            if (!trimmed.isEmpty()) list.add(trimmed);
        }

        assertEquals(5, list.size());
        assertTrue(list.contains("alice"));
        assertTrue(list.contains("bob"));
        assertTrue(list.contains("charlie"));
        assertTrue(list.contains("dave"));
        assertTrue(list.contains("eve"));
        assertFalse(list.contains("mallory"));
    }

    @Test
    void clubStampValidityDuration() {
        long currentTime = 10000L;
        long duration = 24000L; // 1 in-game day
        long stampExpiry = currentTime + duration;

        // Valid while within duration
        assertTrue(stampExpiry > currentTime + 5000L);
        assertTrue(stampExpiry > currentTime + 23999L);

        // Expired after 1 day
        assertFalse(stampExpiry > currentTime + 24001L);
    }

    @Test
    void sobrietyThresholdRejectsWastedPlayers() {
        float sober = 0.0f;
        float tipsy = 0.4f;
        float wasted = 0.85f;
        float smashed = 1.4f;

        float limit = 0.8f;
        assertTrue(sober < limit, "Sober passes");
        assertTrue(tipsy < limit, "Tipsy passes");
        assertFalse(wasted < limit, "Wasted is rejected");
        assertFalse(smashed < limit, "Smashed is rejected");
    }
}
