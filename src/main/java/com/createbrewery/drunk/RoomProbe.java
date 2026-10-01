package com.createbrewery.drunk;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Listens to the room: sixteen rays from the listener's eye (eight round, four up and four down at
 * 45 degrees) four times a second, turned into reverb settings by {@link RoomAcoustics} and eased
 * over about a second, so walking through a door changes the sound gradually. Render thread only.
 */
final class RoomProbe {
    static final int RAYS = 16;
    private static final double EVERY = 0.25, EASE_SECONDS = 0.7;
    private static final Vec3[] DIRS = new Vec3[RAYS];

    static {
        int i = 0;
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            DIRS[i++] = new Vec3(Math.cos(a), 0, Math.sin(a));
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI * 2 * k / 4 + Math.PI / 4;
            DIRS[i++] = new Vec3(Math.cos(a), 1, Math.sin(a)).normalize();
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.PI * 2 * k / 4 + Math.PI / 4;
            DIRS[i++] = new Vec3(Math.cos(a), -1, Math.sin(a)).normalize();
        }
    }

    private double last = -1;
    private RoomAcoustics.Params target, eased;

    void update(Level level, Entity listener, double now, float dt) {
        if (now - last >= EVERY) {
            last = now;
            Vec3 eye = listener.getEyePosition();
            double[] dist = new double[RAYS];
            boolean[] hit = new boolean[RAYS];
            for (int i = 0; i < RAYS; i++) {
                Vec3 to = eye.add(DIRS[i].scale(RoomAcoustics.MAX_RAY));
                BlockHitResult r = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, listener));
                hit[i] = r.getType() != HitResult.Type.MISS;
                dist[i] = hit[i] ? r.getLocation().distanceTo(eye) : RoomAcoustics.MAX_RAY;
            }
            target = RoomAcoustics.estimate(dist, hit);
            if (eased == null) eased = target;
        }
        if (eased != null) eased = RoomAcoustics.lerp(eased, target, 1 - Math.exp(-dt / EASE_SECONDS));
    }

    /** The room as eased so far; null before the first look. */
    RoomAcoustics.Params params() {
        return eased;
    }

    /** Nothing plays any more: the next song looks at the room afresh. */
    void reset() {
        last = -1;
        target = null;
        eased = null;
    }
}
