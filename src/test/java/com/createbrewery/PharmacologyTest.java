package com.createbrewery;

import com.createbrewery.drugs.Pharmacology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PharmacologyTest {

    @Test
    void aDoseComesUpHoldsAndFades() {
        assertEquals(0f, Pharmacology.strength(0, 3600, 300, 900), 1e-6f);
        assertTrue(Pharmacology.strength(150, 3450, 300, 900) < 0.6f);
        assertEquals(1f, Pharmacology.strength(1500, 2100, 300, 900), 1e-6f);
        assertTrue(Pharmacology.strength(3150, 450, 300, 900) < 0.6f);
    }

    @Test
    void everyFurtherDoseFeelsLikeLess() {
        float previous = 0f, gain = Float.MAX_VALUE;
        for (int amp = 0; amp <= 3; amp++) {
            float felt = Pharmacology.dosesFelt(amp);
            assertTrue(felt - previous < gain, "dose " + amp + " added more than the one before");
            gain = felt - previous;
            previous = felt;
        }
        assertEquals(1f, Pharmacology.dosesFelt(3), 1e-6f);
    }

    @Test
    void theHeartBreaksWhereItShould() {
        // One line alone is fine, even sprinting.
        assertTrue(Pharmacology.heartLoad(0, 1, -1, 0, 0, 0, true, false) < Pharmacology.HEART_RACING);
        // Three lines race the heart; four lines while sprinting can kill.
        assertTrue(Pharmacology.heartLoad(2, 1, -1, 0, 0, 0, false, false) >= Pharmacology.HEART_RACING);
        assertTrue(Pharmacology.heartLoad(3, 1, -1, 0, 0, 0, true, false) >= Pharmacology.HEART_CRITICAL);
        // Cocaethylene: three lines on 1.3 per mille are worse than three lines sober.
        assertTrue(Pharmacology.heartLoad(2, 1, -1, 0, 0, 1.3f, false, false)
            > Pharmacology.heartLoad(2, 1, -1, 0, 0, 0, false, false) + 0.3f);
        // Weed or Keta alone never threaten the heart; alcohol alone adds nothing here.
        assertTrue(Pharmacology.heartLoad(-1, 0, 3, 1, 1, 2.5f, true, true) < Pharmacology.HEART_RACING);
        // CK is harder on it than the Koks alone.
        assertTrue(Pharmacology.heartLoad(1, 1, 1, 1, 0, 0, false, false)
            > Pharmacology.heartLoad(1, 1, -1, 0, 0, 0, false, false) + 0.4f);
        assertEquals(0f, Pharmacology.heartAttackChance(1.2f));
        assertTrue(Pharmacology.heartAttackChance(2.5f) > Pharmacology.heartAttackChance(2.0f));
    }

    @Test
    void stimulantsLoadTheHeart() {
        // One pill is fine; meth stacks hard, and heat makes it worse.
        assertTrue(Pharmacology.stimulantLoad(1, -1, 0, 0) < Pharmacology.HEART_RACING);
        assertTrue(Pharmacology.stimulantLoad(0, 2, 1, 0) >= Pharmacology.HEART_RACING);
        assertTrue(Pharmacology.stimulantLoad(0, 2, 1, 1.5f) > Pharmacology.stimulantLoad(0, 2, 1, 0) + 0.4f);
    }

    @Test
    void dancingOnMdmaOverheats() {
        // Four minutes of dancing on a full pill in the heat: overheated. Standing still: never.
        float dancing = 0f, resting = 0f;
        for (int s = 0; s < 240; s++) {
            dancing = Pharmacology.heatStep(dancing, 1f, true, true, false);
            resting = Pharmacology.heatStep(resting, 1f, false, false, false);
        }
        assertTrue(dancing >= Pharmacology.OVERHEATED, "dancing reached " + dancing);
        assertTrue(resting < Pharmacology.OVERHEATED, "resting reached " + resting);
        // A swim cools you down within seconds.
        float swim = dancing;
        for (int s = 0; s < 20; s++) swim = Pharmacology.heatStep(swim, 1f, false, false, true);
        assertTrue(swim < dancing - 1f);
    }

    @Test
    void heroinWithBenzosStopsTheBreath() {
        float f = Pharmacology.BREATH_FAILING;
        assertTrue(Pharmacology.breathLoad(0, 1f, 0f, 0f, 0f) < f, "one shot");
        assertTrue(Pharmacology.breathLoad(1, 1f, 0f, 0f, 0f) < f, "two shots");
        assertTrue(Pharmacology.breathLoad(2, 1f, 0f, 0f, 0f) >= f, "three shots");
        assertTrue(Pharmacology.breathLoad(0, 1f, 1f, 0f, 0f) >= f, "one shot with Xanax");
        assertTrue(Pharmacology.breathLoad(-1, 0f, 1f, 1.3f, 0f) < f, "Xanax and three beers, no opioid");
    }

    @Test
    void breathToleranceHelpsButNeverFully() {
        float naive = Pharmacology.breathLoad(1, 1f, 0f, 0f, 0f, 0f);
        float used = Pharmacology.breathLoad(1, 1f, 0f, 0f, 0f, 1f);
        assertTrue(used < naive, "tolerance did nothing");
        // Three shots: deadly for a fresh (or rested) breath, just survivable for a used one -
        // until a drink comes on top.
        assertTrue(Pharmacology.breathLoad(2, 1f, 0f, 0f, 0f, 0f) >= Pharmacology.BREATH_FAILING);
        assertTrue(Pharmacology.breathLoad(2, 1f, 0f, 0f, 0f, 1f) < Pharmacology.BREATH_FAILING);
        assertTrue(Pharmacology.breathLoad(2, 1f, 0f, 0.5f, 0f, 1f) >= Pharmacology.BREATH_FAILING);
    }

    @Test
    void seizuresOnlyAfterRealUse() {
        assertEquals(0f, Pharmacology.seizureChance(0.3f), 1e-6f);
        assertTrue(Pharmacology.seizureChance(0.5f) > 0f);
        assertTrue(Pharmacology.seizureChance(1f) > Pharmacology.seizureChance(0.5f));
    }

    @Test
    void mushroomsComeInWaves() {
        assertEquals(1f, Pharmacology.shroomWave(0), 1e-6f);
        assertEquals(0.7f, Pharmacology.shroomWave(1200), 1e-5f);
        assertEquals(1f, Pharmacology.shroomWave(2400), 1e-5f);
    }
}
