package com.createbrewery;

import com.createbrewery.drunk.Intoxication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntoxicationTest {

    @Test
    void absorptionPeaksGraduallyLikeARealDrink() {
        float stomach = Intoxication.PER_BEER, blood = 0f;
        int halfIn = -1, allIn = -1;
        for (int i = 1; i <= 4000; i++) {
            float a = Intoxication.absorbed(stomach);
            stomach -= a;
            blood += a;
            if (halfIn < 0 && blood >= Intoxication.PER_BEER / 2) halfIn = i;
            if (allIn < 0 && stomach <= 0f) allIn = i;
        }
        // Half of a beer is in the blood after ~15 MC minutes, nearly all after an MC hour.
        assertTrue(halfIn > 200 && halfIn < 300, "half absorbed at " + halfIn);
        assertTrue(allIn > 800 && allIn < 2500, "fully absorbed at " + allIn);
    }

    @Test
    void fullStomachSlowsAbsorption() {
        assertTrue(Intoxication.absorbed(1f, 20) < Intoxication.absorbed(1f, 0));
        assertEquals(Intoxication.absorbed(1f, 0) / 2f, Intoxication.absorbed(1f, 20), 1e-6f);
    }

    @Test
    void absorptionNeverOvershootsTheStomach() {
        assertEquals(0f, Intoxication.absorbed(0f));
        assertEquals(0f, Intoxication.absorbed(-1f));
        assertEquals(0.0001f, Intoxication.absorbed(0.0001f), 1e-7f);
    }

    @Test
    void eliminationClampsAtZero() {
        assertEquals(0f, Intoxication.eliminated(0f));
        assertEquals(0f, Intoxication.eliminated(Intoxication.ELIMINATE_PER_TICK / 2f));
        assertTrue(Intoxication.eliminated(1f) < 1f);
    }

    @Test
    void oneBeerWearsOffInAboutTwoMinecraftHours() {
        assertEquals(2000, Intoxication.ticksUntilSober(Intoxication.PER_BEER, 0f), 1);
        assertEquals(2000, Intoxication.ticksUntilSober(0f, Intoxication.PER_BEER), 1);
        assertEquals(0, Intoxication.ticksUntilSober(0f, 0f));
    }

    @Test
    void thresholdsNeedRealisticAmountsOfBeer() {
        float[] thresholds = { Intoxication.TIPSY, Intoxication.MERRY, Intoxication.DRUNK,
            Intoxication.WASTED, Intoxication.SMASHED, Intoxication.BLACKOUT, Intoxication.POISONING };
        for (int i = 1; i < thresholds.length; i++) assertTrue(thresholds[i] > thresholds[i - 1]);
        assertTrue(Intoxication.PER_BEER >= Intoxication.TIPSY, "one beer makes you tipsy");
        assertTrue(3 * Intoxication.PER_BEER >= Intoxication.MERRY, "three make you merry");
        assertTrue(4 * Intoxication.PER_BEER < Intoxication.DRUNK, "four are not yet drunk");
        assertTrue(10 * Intoxication.PER_BEER < Intoxication.POISONING, "ten do not poison");
    }

    @Test
    void visualIntensityIsClampedAndMonotonic() {
        assertEquals(0f, Intoxication.visualIntensity(0f));
        assertEquals(0f, Intoxication.visualIntensity(Intoxication.TIPSY));
        assertEquals(1f, Intoxication.visualIntensity(Intoxication.BLACKOUT));
        assertEquals(1f, Intoxication.visualIntensity(10f));
        float prev = -1f;
        for (float b = 0f; b <= 4f; b += 0.1f) {
            float v = Intoxication.visualIntensity(b);
            assertTrue(v >= prev);
            prev = v;
        }
    }

    @Test
    void hangoverSetsInOnTheWayDown() {
        assertFalse(Intoxication.hangoverStarts(0.2f, 0f, 0.5f), "too little for a hangover");
        assertFalse(Intoxication.hangoverStarts(0.6f, 0f, 1.2f), "still drunk");
        assertFalse(Intoxication.hangoverStarts(0.2f, 0.3f, 1.2f), "still absorbing");
        assertTrue(Intoxication.hangoverStarts(0.2f, 0f, 1.2f));
        assertEquals(0, Intoxication.hangoverLevel(1.2f));
        assertEquals(1, Intoxication.hangoverLevel(2.5f));
    }

    @Test
    void goodMoodOnlyForTheFirstBeers() {
        assertFalse(Intoxication.inGoodMood(0.1f));
        assertTrue(Intoxication.inGoodMood(0.5f));
        assertTrue(Intoxication.inGoodMood(1.0f));
        assertFalse(Intoxication.inGoodMood(1.5f));
    }

    @Test
    void beerGogglesOnlyInThePartyZone() {
        assertEquals(0f, Intoxication.mood(0.05f));
        assertEquals(1f, Intoxication.mood(0.7f), 1e-6f);
        assertTrue(Intoxication.mood(1.25f) > 0f && Intoxication.mood(1.25f) < 1f);
        assertEquals(0f, Intoxication.mood(2.0f));
    }

    @Test
    void firstBeersAreAlreadyFelt() {
        assertTrue(Intoxication.visualIntensity(3 * Intoxication.PER_BEER) > 0.45f);
    }

    @Test
    void poisoningHurtsMoreTheHigherItGoes() {
        assertEquals(0f, Intoxication.poisonDamage(3.0f));
        assertTrue(Intoxication.poisonDamage(Intoxication.POISONING) >= 1f);
        assertTrue(Intoxication.poisonDamage(4.0f) > Intoxication.poisonDamage(3.5f));
    }

    @Test
    void slurStagesFollowTheThresholds() {
        assertEquals(0, Intoxication.slurStage(0.5f));
        assertEquals(1, Intoxication.slurStage(1.0f));
        assertEquals(2, Intoxication.slurStage(1.5f));
        assertEquals(3, Intoxication.slurStage(2.5f));
    }

    @Test
    void onlyAHeavySessionEarnsAHangover() {
        assertEquals(0, Intoxication.hangoverTicks(0.5f));
        assertTrue(Intoxication.hangoverTicks(Intoxication.HANGOVER_PEAK) > 0);
        assertTrue(Intoxication.hangoverTicks(3.0f) > Intoxication.hangoverTicks(1.5f));
    }
}
