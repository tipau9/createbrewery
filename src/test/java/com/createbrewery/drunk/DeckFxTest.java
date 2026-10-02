package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeckFxTest {
    private static final double RATE = 48000;

    /** RMS of the last half second of a sine at {@code hz} through {@code fx}, over the input's. */
    private static double gain(DeckFx fx, double hz) {
        int n = (int) RATE, piece = 1024;
        float[] buf = new float[piece];
        double in = 0, out = 0;
        for (int at = 0; at < n; at += piece) {
            for (int i = 0; i < piece; i++) buf[i] = (float) Math.sin(2 * Math.PI * hz * (at + i) / RATE);
            double sumIn = 0;
            for (float v : buf) sumIn += v * v;
            fx.process(buf, piece);
            if (at >= n / 2) {
                in += sumIn;
                for (float v : buf) out += v * v;
            }
        }
        return Math.sqrt(out / in);
    }

    private static double db(double g) {
        return 20 * Math.log10(g);
    }

    @Test
    void flatIsTransparent() {
        for (double hz : new double[] {50, 1000, 8000}) {
            DeckFx fx = new DeckFx(RATE);
            fx.set(0.5f, 0.5f, 0.5f, 0f, DeckFx.NONE, 0f, 0.5);
            assertEquals(0, db(gain(fx, hz)), 0.1, hz + " Hz");
        }
    }

    @Test
    void killsRemoveTheirBand() {
        DeckFx lowKill = new DeckFx(RATE);
        lowKill.set(0f, 0.5f, 0.5f, 0f, DeckFx.NONE, 0f, 0.5);
        assertTrue(db(gain(lowKill, 50)) < -20, "the kick survived a low kill");
        assertTrue(db(gain(lowKill, 5000)) > -1, "a low kill took the highs too");

        DeckFx highKill = new DeckFx(RATE);
        highKill.set(0.5f, 0.5f, 0f, 0f, DeckFx.NONE, 0f, 0.5);
        assertTrue(db(gain(highKill, 10000)) < -20, "the hats survived a high kill");
        assertTrue(db(gain(highKill, 60)) > -1, "a high kill took the bass too");
    }

    @Test
    void filterSweepsBothWays() {
        DeckFx lp = new DeckFx(RATE);
        lp.set(0.5f, 0.5f, 0.5f, -0.9f, DeckFx.NONE, 0f, 0.5);
        assertTrue(db(gain(lp, 5000)) < -30, "low-pass let the highs through");
        DeckFx hp = new DeckFx(RATE);
        hp.set(0.5f, 0.5f, 0.5f, 0.9f, DeckFx.NONE, 0f, 0.5);
        assertTrue(db(gain(hp, 60)) < -30, "high-pass let the bass through");
    }

    @Test
    void echoRingsOutAfterTheSendCloses() {
        DeckFx fx = new DeckFx(RATE);
        fx.set(0.5f, 0.5f, 0.5f, 0f, DeckFx.ECHO, 1f, 0.5);
        float[] buf = new float[1024];
        fx.process(buf, buf.length); // the send glides open over the first block
        buf[0] = 1f;
        fx.process(buf, buf.length);
        // Send closed, silence in: the half-beat repeats still come.
        fx.set(0.5f, 0.5f, 0.5f, 0f, DeckFx.ECHO, 0f, 0.5);
        double tail = 0;
        for (int p = 0; p < 40; p++) {
            buf = new float[1024];
            fx.process(buf, buf.length);
            for (float v : buf) tail = Math.max(tail, Math.abs(v));
        }
        assertTrue(tail > 0.1, "no echo tail: " + tail);
    }

    /** RMS of the last half second of a sine through a crossover side (null: both sides summed), over the input's. */
    private static double crossover(Boolean lowPass, double hz) {
        DeckFx.Crossover lp = new DeckFx.Crossover(RATE), hp = new DeckFx.Crossover(RATE);
        lp.set(true, 100);
        hp.set(false, 100);
        int n = (int) RATE, piece = 1024;
        float[] a = new float[piece], b = new float[piece];
        double in = 0, out = 0;
        for (int at = 0; at < n; at += piece) {
            for (int i = 0; i < piece; i++) a[i] = b[i] = (float) Math.sin(2 * Math.PI * hz * (at + i) / RATE);
            double sumIn = 0;
            for (float v : a) sumIn += v * v;
            lp.process(a, piece);
            hp.process(b, piece);
            if (at < n / 2) continue;
            in += sumIn;
            for (int i = 0; i < piece; i++) {
                double y = lowPass == null ? a[i] + b[i] : lowPass ? a[i] : b[i];
                out += y * y;
            }
        }
        return Math.sqrt(out / in);
    }

    @Test
    void crossoverSplitsAndSumsFlat() {
        assertTrue(db(crossover(true, 1000)) < -20, "the subs got the mids");
        assertTrue(db(crossover(false, 40)) < -20, "the tops got the sub bass");
        for (double hz : new double[] {40, 100, 250, 1000}) {
            assertEquals(0, db(crossover(null, hz)), 0.2, "sum at " + hz + " Hz");
        }
    }

    @Test
    void limiterHoldsPeaksUnderTheCeilingAndLetsQuietThrough() {
        DeckFx.Limiter lim = new DeckFx.Limiter(RATE);
        float[] quiet = new float[1024];
        for (int i = 0; i < quiet.length; i++) quiet[i] = (float) (0.5 * Math.sin(i * 0.05));
        float[] copy = quiet.clone();
        lim.process(quiet, quiet.length);
        assertArrayEquals(copy, quiet, "limited a signal under the ceiling");
        assertEquals(1f, lim.takeReduction());

        float[] loud = new float[4800];
        for (int i = 0; i < loud.length; i++) loud[i] = (float) (2.0 * Math.sin(i * 0.05));
        lim.process(loud, loud.length);
        for (float v : loud) assertTrue(Math.abs(v) <= DeckFx.Limiter.CEILING + 1e-6, "over the ceiling: " + v);
        assertTrue(lim.takeReduction() < 0.5f, "a +6 dB signal hardly limited");
    }

    @Test
    void soundFliesAndTheServoWalksItBackInStep() {
        // 34.3 blocks away is a tenth of a second; a delay tower's alignment adds on top.
        assertEquals(0.1, DeckFx.propagation(0, 34.3), 1e-9);
        assertEquals(0.15, DeckFx.propagation(0.05, 34.3), 1e-9);
        assertEquals(1f, DeckFx.servo(0.0005), "in step, yet the speed moved");
        assertTrue(DeckFx.servo(0.01) < 1f && DeckFx.servo(-0.01) > 1f, "the servo pushes the wrong way");
        assertEquals(0.97f, DeckFx.servo(1), 1e-6, "more than a 3 % shift");
        // Walked out of step by 20 ms, it is back within a second of frames.
        double ahead = 0.02, dt = 0.05;
        for (int i = 0; i < 20; i++) ahead += (DeckFx.servo(ahead) - 1) * dt;
        assertTrue(Math.abs(ahead) < 0.002, "still " + ahead + " s out of step");
    }

    @Test
    void loopsWrapBackToTheirStart() {
        assertEquals(500, DeckFx.loopFrame(500, 1000, 300));
        assertEquals(1000, DeckFx.loopFrame(1300, 1000, 300));
        assertEquals(1150, DeckFx.loopFrame(1750, 1000, 300));
    }

    @Test
    void propagationCalculatesSpeedOfSoundFlight() {
        double alignDelay = 0.05; // 50 ms delay tower
        double distance = 343.0; // 343 blocks away
        double totalProp = DeckFx.propagation(alignDelay, distance);
        assertEquals(1.05, totalProp, 1e-4, "acoustic flight should be alignDelay + distance / SPEED_OF_SOUND");
    }

    @Test
    void peakLimiterRestrictsOverload() {
        DeckFx.Limiter limiter = new DeckFx.Limiter(44100.0);
        float[] buffer = new float[] {0.5f, 0.8f, 1.5f, 2.0f, 3.0f, 0.5f};
        limiter.process(buffer, buffer.length);

        for (int i = 0; i < buffer.length; i++) {
            assertTrue(Math.abs(buffer[i]) <= DeckFx.Limiter.CEILING + 0.01f,
                "limiter failed to keep sample " + i + " (" + buffer[i] + ") below ceiling");
        }

        float reduction = limiter.takeReduction();
        assertTrue(reduction < 0.5f, "gain reduction should have been triggered for 3.0f peak");
        assertEquals(1.0f, limiter.takeReduction(), "subsequent take should be reset to unity");
    }
}
