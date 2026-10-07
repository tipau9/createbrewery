package com.createbrewery.block.club;

/** Beam geometry, kept free of Minecraft classes so it can be unit-tested. */
final class LaserBeams {
    private LaserBeams() {}

    /** How far a beam may swing away from the facing, so ceiling and floor projectors never shine into their mount. */
    static final double MAX_DEFLECTION = 65.0;

    static double[] direction(int nx, int ny, int nz, double yawOffsetDeg) {
        return direction(nx, ny, nz, yawOffsetDeg, 0);
    }

    /**
     * The world direction of a beam panned {@code yawDeg} and tilted {@code pitchDeg} away from the
     * facing normal {@code (nx, ny, nz)}. Pan is sideways (around Y) for wall-mounted projectors and
     * along X for ceiling and floor ones; tilt is up/down on walls and along Z on ceilings and floors.
     * Both are clamped to {@link #MAX_DEFLECTION}.
     */
    static double[] direction(int nx, int ny, int nz, double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(clamp(yawDeg)), pitch = Math.toRadians(clamp(pitchDeg));
        double forward = Math.cos(yaw) * Math.cos(pitch), side = Math.sin(yaw) * Math.cos(pitch), lift = Math.sin(pitch);
        // right/up frame around the facing
        double rx, rz, ux, uy, uz;
        if (ny != 0) {
            rx = 1; rz = 0;
            ux = 0; uy = 0; uz = 1;
        } else {
            rx = -nz; rz = nx;
            ux = 0; uy = 1; uz = 0;
        }
        return new double[]{
            nx * forward + rx * side + ux * lift,
            ny * forward + uy * lift,
            nz * forward + rz * side + uz * lift
        };
    }

    /**
     * The {pan, tilt} in degrees that point a beam from facing {@code (nx, ny, nz)} along {@code (dx, dy, dz)}:
     * the inverse of {@link #direction(int, int, int, double, double)} (which clamps it).
     */
    static double[] aimAt(int nx, int ny, int nz, double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) return new double[] {0, 0};
        dx /= len;
        dy /= len;
        dz /= len;
        double side, lift;
        if (ny != 0) {
            side = dx;
            lift = dz;
        } else {
            side = -nz * dx + nx * dz;
            lift = dy;
        }
        double forward = dx * nx + dy * ny + dz * nz;
        return new double[] {Math.toDegrees(Math.atan2(side, forward)), Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, lift))))};
    }

    private static double clamp(double deg) {
        return Math.max(-MAX_DEFLECTION, Math.min(MAX_DEFLECTION, deg));
    }
}
