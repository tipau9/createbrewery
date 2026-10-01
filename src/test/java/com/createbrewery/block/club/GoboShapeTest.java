package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoboShapeTest {
    private static void assertInside(List<float[]> shapes, float radius) {
        for (float[] poly : shapes) {
            assertTrue(poly.length >= 6 && poly.length % 2 == 0, "a polygon needs at least 3 points");
            for (float v : poly) assertTrue(Float.isFinite(v));
            for (int i = 0; i < poly.length; i += 2) {
                assertTrue(Math.hypot(poly[i], poly[i + 1]) <= radius * 1.001f, "point outside the spot");
            }
        }
    }

    @Test
    void circleStarDotsAndBarHaveTheirShapes() {
        assertEquals(1, GoboShape.shapes(0, 0f, 1f).size());
        assertEquals(24, GoboShape.shapes(0, 0f, 1f).get(0).length, "a 12-gon");
        assertEquals(20, GoboShape.shapes(1, 0f, 1f).get(0).length, "a 10-point star");
        assertEquals(3, GoboShape.shapes(2, 0f, 1f).size(), "three dots");
        assertEquals(8, GoboShape.shapes(3, 0f, 1f).get(0).length, "a bar is a quad");
    }

    @Test
    void everyShapeStaysInsideItsRadiusWhateverTheRotation() {
        for (int gobo = 0; gobo < DmxProgram.GOBOS; gobo++) {
            for (float rot = 0; rot < 6.3f; rot += 0.7f) {
                assertInside(GoboShape.shapes(gobo, rot, 0.5f), 0.5f);
            }
        }
    }

    @Test
    void anUnknownGoboFallsBackToTheCircle() {
        assertEquals(GoboShape.shapes(0, 0f, 1f).get(0).length, GoboShape.shapes(99, 0f, 1f).get(0).length);
        assertEquals(GoboShape.shapes(0, 0f, 1f).get(0).length, GoboShape.shapes(-2, 0f, 1f).get(0).length);
    }

    @Test
    void rotationActuallyTurnsTheBar() {
        float[] a = GoboShape.shapes(3, 0f, 1f).get(0), b = GoboShape.shapes(3, 1f, 1f).get(0);
        assertNotEquals(a[0], b[0], 1e-4);
    }
}
