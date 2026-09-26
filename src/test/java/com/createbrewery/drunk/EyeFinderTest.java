package com.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EyeFinderTest {
    private static final int SKIN = 0xFFE0B090, HAIR = 0xFF201810, WHITE = 0xFFFFFFFF, IRIS = 0xFF3050C0;

    /** A face: hair in rows 0-1 (and 2 at the sides), eyes in row 4 at x 1-2 and 5-6, skin elsewhere. */
    private static int[] skin() {
        int[] argb = new int[64 * 64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int c = y < 2 || (y == 2 && (x == 0 || x == 7)) ? HAIR : SKIN;
                if (y == 4 && (x == 1 || x == 5)) c = WHITE;
                if (y == 4 && (x == 2 || x == 6)) c = IRIS;
                argb[(8 + y) * 64 + 8 + x] = c;
            }
        }
        return argb;
    }

    @Test
    void findsTheEyesWhereTheSkinHasThem() {
        EyeFinder.Eyes eyes = EyeFinder.find(skin());
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                boolean eye = y == 4 && (x == 1 || x == 2 || x == 5 || x == 6);
                assertEquals(eye, eyes.at(x, y), "pixel " + x + "," + y);
            }
        }
        assertFalse(eyes.onHat(1, 4));
    }

    @Test
    void eyesOnTheHatLayerCount() {
        int[] argb = skin();
        argb[(8 + 3) * 64 + 40 + 1] = WHITE; // a highlight above the left eye, on the hat
        argb[(8 + 3) * 64 + 40 + 5] = WHITE;
        EyeFinder.Eyes eyes = EyeFinder.find(argb);
        assertTrue(eyes.at(1, 3) && eyes.onHat(1, 3));
        assertTrue(eyes.at(1, 4) && !eyes.onHat(1, 4));
    }

    @Test
    void aBlankFaceGetsTheUsualPlace() {
        int[] argb = new int[64 * 64];
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) argb[(8 + y) * 64 + 8 + x] = SKIN;
        assertEquals(EyeFinder.USUAL, EyeFinder.find(argb));
        assertTrue(EyeFinder.USUAL.at(1, 3) && EyeFinder.USUAL.at(6, 4) && !EyeFinder.USUAL.at(0, 3));
    }
}
