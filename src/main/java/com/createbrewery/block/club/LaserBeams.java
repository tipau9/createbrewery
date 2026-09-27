package com.createbrewery.block.club;

/** Beam geometry, kept free of Minecraft classes so it can be unit-tested. */
final class LaserBeams {
    private LaserBeams() {}

    /**
     * The world direction of a beam fanned {@code yawOffsetDeg} away from the facing normal
     * {@code (nx, ny, nz)}: sideways (around Y) for wall-mounted projectors, in the X/Y plane for
     * ceiling and floor ones.
     */
    static double[] direction(int nx, int ny, int nz, double yawOffsetDeg) {
        double rad = Math.toRadians(yawOffsetDeg);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        if (ny != 0) {
            return new double[]{sin, ny * cos, 0};
        }
        return new double[]{nx * cos - nz * sin, 0, nx * sin + nz * cos};
    }
}
