package com.createbrewery.drunk;

/**
 * What the room around the listener does to sound, from how far sixteen rays travel before they
 * hit something: the mean free distance gives a volume and a surface (Sabine: RT60 = 0.161 V / A),
 * rays that find nothing mean open air, which has no reverb. The numbers are OpenAL EFX reverb
 * settings. Free of Minecraft and OpenAL classes.
 */
final class RoomAcoustics {
    private RoomAcoustics() {}

    /** Rays are cast this far (blocks); one that finds nothing counts as this long and as open air. */
    static final double MAX_RAY = 24.0;
    /** Average absorption of walls (stone and wood, little carpet). */
    static final double ABSORPTION = 0.25;

    /** EFX units: decay in seconds, the others 0..1 (gains 0..10 and 0..3.16). */
    record Params(float decayTime, float density, float diffusion, float lateGain, float reflectionsGain) {}

    static Params estimate(double[] dist, boolean[] hit) {
        int n = dist.length, misses = 0;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            if (!hit[i]) misses++;
            sum += hit[i] ? Math.min(dist[i], MAX_RAY) : MAX_RAY;
        }
        double d = Math.max(1.0, sum / n);
        double open = (double) misses / n;
        double volume = 4.0 / 3 * Math.PI * d * d * d, surface = 4 * Math.PI * d * d;
        double rt60 = 0.161 * volume / (surface * ABSORPTION);
        double decay = clamp(rt60, 0.2, 3.5) * (1 - 0.7 * open);
        double dry = (1 - open) * (1 - open);
        return new Params((float) Math.max(0.1, decay), 1f, (float) clamp(1 - d / 60, 0.6, 1), (float) (1.26 * dry), (float) (0.3 * dry));
    }

    static Params lerp(Params a, Params b, double k) {
        return new Params(mix(a.decayTime(), b.decayTime(), k), mix(a.density(), b.density(), k), mix(a.diffusion(), b.diffusion(), k),
            mix(a.lateGain(), b.lateGain(), k), mix(a.reflectionsGain(), b.reflectionsGain(), k));
    }

    private static float mix(float a, float b, double k) {
        return (float) (a + (b - a) * k);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
