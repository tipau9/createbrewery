package com.createbrewery.block.club;

import java.util.Arrays;

/**
 * The amp rack's meters, per zone: the loudest that left the amps (dBFS) and how much the
 * limiters are taking off (dB). A new high shows at once, is held a second, then falls 20 dB a
 * second, like the LEDs on a real rack. Fed by MusicPulse each frame, client side.
 */
public final class AmpMeters {
    public static final float FLOOR_DB = -60f;
    private static final float HOLD = 1f, FALL = 20f;
    private static final int N = AmpSettings.ZONES;

    private final float[] peakIn = new float[N], reductionIn = new float[N];
    private final float[] peakDb = new float[N], limitDb = new float[N], peakHold = new float[N], limitHold = new float[N];

    public AmpMeters() {
        Arrays.fill(peakDb, FLOOR_DB);
        Arrays.fill(reductionIn, 1f);
    }

    /** One speaker this frame: its peak (linear) and its limiter's lowest gain (1 = none). */
    public void add(int zone, float peak, float reduction) {
        if (zone < 0 || zone >= N) return;
        peakIn[zone] = Math.max(peakIn[zone], peak);
        reductionIn[zone] = Math.min(reductionIn[zone], reduction);
    }

    /** Once a frame, after every speaker was added. */
    public void step(float dt) {
        for (int z = 0; z < N; z++) {
            float p = peakIn[z] > 1e-3f ? Math.max(FLOOR_DB, (float) (20 * Math.log10(peakIn[z]))) : FLOOR_DB;
            float l = reductionIn[z] < 1f ? (float) (-20 * Math.log10(Math.max(reductionIn[z], 1e-4f))) : 0f;
            peakDb[z] = follow(peakDb[z], p, peakHold, z, dt, FLOOR_DB);
            limitDb[z] = follow(limitDb[z], l, limitHold, z, dt, 0f);
            peakIn[z] = 0f;
            reductionIn[z] = 1f;
        }
    }

    private static float follow(float shown, float now, float[] hold, int z, float dt, float floor) {
        if (now >= shown) {
            hold[z] = HOLD;
            return now;
        }
        if (hold[z] > 0) {
            hold[z] -= dt;
            return shown;
        }
        return Math.max(Math.max(now, floor), shown - FALL * dt);
    }

    public float peakDb(int zone) {
        return peakDb[zone];
    }

    public float limitDb(int zone) {
        return limitDb[zone];
    }

    public float worstLimit() {
        float w = 0f;
        for (float l : limitDb) w = Math.max(w, l);
        return w;
    }
}
