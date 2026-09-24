package com.createbrewery;

import com.createbrewery.drunk.Intoxication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntoxicationTest {

    @Test
    void oneBeerIsFullyAbsorbedWithinFifteenSeconds() {
        float stomach = Intoxication.PER_BEER, blood = 0f;
        for (int i = 0; i < 300; i++) {
            float a = Intoxication.absorbed(stomach);
            stomach -= a;
            blood += a;
        }
        assertEquals(0f, stomach, 1e-4f);
        assertEquals(Intoxication.PER_BEER, blood, 1e-4f);
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
    void oneBeerWearsOffInTwoAndAHalfMinutes() {
        assertEquals(3000, Intoxication.ticksUntilSober(Intoxication.PER_BEER, 0f), 1);
        assertEquals(3000, Intoxication.ticksUntilSober(0f, Intoxication.PER_BEER), 1);
        assertEquals(0, Intoxication.ticksUntilSober(0f, 0f));
    }

    @Test
    void eachBeerCrossesTheNextThreshold() {
        float[] thresholds = { Intoxication.TIPSY, Intoxication.MERRY, Intoxication.DRUNK,
            Intoxication.WASTED, Intoxication.SMASHED, Intoxication.BLACKOUT };
        for (int beer = 1; beer <= thresholds.length; beer++) {
            float bac = beer * Intoxication.PER_BEER;
            assertTrue(bac >= thresholds[beer - 1], "beer " + beer + " should reach threshold " + thresholds[beer - 1]);
            assertTrue((beer - 1) * Intoxication.PER_BEER < thresholds[beer - 1], "beer " + (beer - 1) + " should stay below it");
        }
        assertTrue(7 * Intoxication.PER_BEER >= Intoxication.POISONING, "the 7th beer poisons");
        assertTrue(6 * Intoxication.PER_BEER < Intoxication.POISONING, "six beers do not");
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
    void firstBeersAreAlreadyFelt() {
        assertTrue(Intoxication.visualIntensity(2 * Intoxication.PER_BEER) > 0.45f);
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
        assertEquals(0, Intoxication.hangoverTicks(1.0f));
        assertTrue(Intoxication.hangoverTicks(Intoxication.HANGOVER_PEAK) > 0);
        assertTrue(Intoxication.hangoverTicks(3.0f) > Intoxication.hangoverTicks(1.5f));
    }
}
