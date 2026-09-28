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
    void loopsWrapBackToTheirStart() {
        assertEquals(500, DeckFx.loopFrame(500, 1000, 300));
        assertEquals(1000, DeckFx.loopFrame(1300, 1000, 300));
        assertEquals(1150, DeckFx.loopFrame(1750, 1000, 300));
    }
}
