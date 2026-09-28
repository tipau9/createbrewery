package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behind a wall the kick must come through and the vocals must not. */
class WallFilterTest {
    private static final double RATE = 44100;

    /** Level in dB of a sine at {@code hz} after the filter, against the same sine unfiltered. */
    private static double through(double hz, double muffle, double walls) {
        WallFilter filter = new WallFilter(RATE);
        filter.set(muffle, walls);
        int n = (int) RATE;
        float[] in = new float[n], out = new float[n];
        for (int i = 0; i < n; i++) in[i] = (float) Math.sin(2 * Math.PI * hz * i / RATE);
        filter.process(in, 0, n, out);
        double pin = 0, pout = 0;
        for (int i = n / 2; i < n; i++) { // past the filter settling in
            pin += in[i] * in[i];
            pout += out[i] * out[i];
        }
        return 10 * Math.log10(pout / pin);
    }

    @Test
    void clearIsTransparentAcrossTheSong() {
        for (double hz : new double[] {50, 1000, 8000}) {
            assertTrue(through(hz, 0, 0) > -1.0, hz + " Hz lost in the open");
        }
    }

    @Test
    void oneWallKeepsTheKickAndKillsTheVocals() {
        double kick = through(55, 1, 1), vocal = through(2000, 1, 1);
        assertTrue(kick > -12, "kick through one wall: " + kick + " dB");
        assertTrue(vocal < -45, "vocals through one wall: " + vocal + " dB");
    }

    @Test
    void moreWallsAreQuieterAndDuller() {
        assertTrue(through(55, 1, 3) < through(55, 1, 1) - 6, "three walls not quieter than one");
        assertTrue(WallFilter.cutoff(1, 3) < WallFilter.cutoff(1, 1), "three walls not duller than one");
    }
}
