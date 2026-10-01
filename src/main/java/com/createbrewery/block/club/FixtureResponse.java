package com.createbrewery.block.club;

/**
 * How one club fixture answers the console: the dimmer curve and the lamp's inertia, a colour that
 * fades instead of snapping, and a moving head's motors (limited speed and acceleration). One
 * instance per fixture, stepped once per client tick. Free of Minecraft classes (unit-tested).
 */
final class FixtureResponse {
    /** Between linear and the square law of real dimmers. */
    static final float DIMMER_EXPONENT = 1.6f;
    /** Degrees per tick (20 ticks a second): 180 and 120 degrees a second; and how hard a motor may speed up or brake. */
    static final float PAN_SPEED = 9f, TILT_SPEED = 6f, ACCEL = 1.5f;
    /** At most this much a tick when flashing lights are hidden (the same as {@code DmxProgram.GLIDE}). */
    private static final float GLIDE = 0.1f;

    /** Ticks a lamp takes to rise to, and fall from, 63 percent of a step. */
    record Lamp(float riseTicks, float fallTicks) {
        static final Lamp LED = new Lamp(1, 2), PAR = new Lamp(1, 2), HEAD = new Lamp(2, 3), TUNGSTEN = new Lamp(2, 7);
    }

    float pan, tilt;
    private float panVel, tiltVel;
    private float out;
    private float r, g, b;
    private boolean colored;

    static float curve(float level) {
        float l = level < 0 ? 0 : level > 1 ? 1 : level;
        return (float) Math.pow(l, DIMMER_EXPONENT);
    }

    /** Photosensitivity: every colour fade lasts at least this many ticks, so a chase step cannot flash. */
    static final float CALM_FADE = 20f;

    static float fade(float fadeTicks, boolean noFlashing) {
        return noFlashing ? Math.max(fadeTicks, CALM_FADE) : fadeTicks;
    }

    /** One tick of a colour (0xRRGGBB) toward {@code target}, no state: for the LED bar's pixels. Always moves at least a step. */
    static int blend(int current, int target, float fadeTicks) {
        float k = 1f - (float) Math.exp(-1f / Math.max(0.01f, fadeTicks));
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int c = current >> shift & 255, t = target >> shift & 255;
            float step = (t - c) * k;
            if (t != c && Math.abs(step) < 1f) step = Math.signum(t - c);
            out |= Math.max(0, Math.min(255, Math.round(c + step))) << shift;
        }
        return out;
    }

    /** One tick: the lamp's output intensity for the console level {@code level}. */
    float dimmer(Lamp lamp, float level) {
        float target = curve(level);
        float ticks = target > out ? lamp.riseTicks() : lamp.fallTicks();
        out += (target - out) * (1f - (float) Math.exp(-1f / Math.max(0.01f, ticks)));
        if (Math.abs(target - out) < 0.001f) out = target;
        return out;
    }

    /** One tick: the colour (0xRRGGBB) fading toward {@code target} over about {@code fadeTicks}; 0 is instant. */
    int color(int target, float fadeTicks) {
        float tr = (target >> 16 & 255) / 255f, tg = (target >> 8 & 255) / 255f, tb = (target & 255) / 255f;
        if (!colored || fadeTicks <= 0f) {
            r = tr;
            g = tg;
            b = tb;
            colored = true;
        } else {
            float k = 1f - (float) Math.exp(-1f / fadeTicks);
            r += (tr - r) * k;
            g += (tg - g) * k;
            b += (tb - b) * k;
        }
        return Math.round(r * 255f) << 16 | Math.round(g * 255f) << 8 | Math.round(b * 255f);
    }

    /** One tick of both motors toward the target angles (degrees off the facing). */
    void motor(float panTarget, float tiltTarget) {
        float[] p = axis(pan, panVel, panTarget, PAN_SPEED);
        pan = p[0];
        panVel = p[1];
        float[] t = axis(tilt, tiltVel, tiltTarget, TILT_SPEED);
        tilt = t[0];
        tiltVel = t[1];
    }

    private static float[] axis(float pos, float vel, float target, float maxSpeed) {
        float err = target - pos;
        // The speed from which braking at ACCEL stops exactly on the target.
        float desired = Math.signum(err) * Math.min(maxSpeed, (float) Math.sqrt(2f * ACCEL * Math.abs(err)));
        vel += Math.max(-ACCEL, Math.min(ACCEL, desired - vel));
        pos += vel;
        // Close and slow: arrived (a discrete motor would otherwise ring around the target).
        if (Math.abs(target - pos) < ACCEL && Math.abs(vel) < 2f * ACCEL) {
            pos = target;
            vel = 0f;
        }
        return new float[] {pos, vel};
    }

    /** Photosensitivity: a level moves at most a tenth a tick toward {@code target}. */
    static float glide(float current, float target) {
        return current + Math.max(-GLIDE, Math.min(GLIDE, target - current));
    }
}
