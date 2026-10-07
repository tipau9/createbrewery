package com.createbrewery.block.club;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaserBeamDirectionTest {

    /** The six facing normals: down, up, north, south, west, east. */
    private static final int[][] FACINGS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    @Test
    void straightBeamLeavesThroughTheLens() {
        for (int[] n : FACINGS) {
            double[] d = LaserBeams.direction(n[0], n[1], n[2], 0);
            for (int i = 0; i < 3; i++) assertEquals(n[i], d[i], 1e-9);
        }
    }

    @Test
    void fannedBeamsStayUnitLengthAndFanSideways() {
        for (int[] n : FACINGS) {
            double[] d = LaserBeams.direction(n[0], n[1], n[2], 24);
            assertEquals(1, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
            assertEquals(Math.cos(Math.toRadians(24)), d[0] * n[0] + d[1] * n[1] + d[2] * n[2], 1e-9);
            // Wall lasers fan out level, never up into the ceiling.
            if (n[1] == 0) assertEquals(0, d[1], 1e-9);
        }
    }

    @Test
    void pannedAndTiltedBeamsStayUnitLengthAndInFront() {
        for (int[] n : FACINGS) {
            double[] d = LaserBeams.direction(n[0], n[1], n[2], 30, -20);
            assertEquals(1, Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-9);
            assertEquals(Math.cos(Math.toRadians(30)) * Math.cos(Math.toRadians(20)), d[0] * n[0] + d[1] * n[1] + d[2] * n[2], 1e-9);
            // Tilting a wall laser moves it up/down.
            if (n[1] == 0) assertEquals(Math.sin(Math.toRadians(-20)), d[1], 1e-9);
            // Even a wild swing stays well in front of the mount.
            double[] wild = LaserBeams.direction(n[0], n[1], n[2], 170, -170);
            assertEquals(true, wild[0] * n[0] + wild[1] * n[1] + wild[2] * n[2] > 0.15);
        }
    }

    @Test
    void aimAtFindsThePanAndTiltBack() {
        int[][] facings = {{0, -1, 0}, {0, 1, 0}, {1, 0, 0}, {0, 0, -1}};
        for (int[] n : facings) {
            for (double pan = -60; pan <= 60; pan += 20) {
                for (double tilt = -60; tilt <= 60; tilt += 20) {
                    double[] d = LaserBeams.direction(n[0], n[1], n[2], pan, tilt);
                    double[] a = LaserBeams.aimAt(n[0], n[1], n[2], d[0] * 3, d[1] * 3, d[2] * 3);
                    assertEquals(pan, a[0], 1e-6, "pan, facing " + java.util.Arrays.toString(n));
                    assertEquals(tilt, a[1], 1e-6, "tilt, facing " + java.util.Arrays.toString(n));
                }
            }
        }
    }
}
