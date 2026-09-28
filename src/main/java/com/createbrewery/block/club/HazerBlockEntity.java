package com.createbrewery.block.club;

import com.createbrewery.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The haze a {@link HazerBlock} has put into the air around it. Client only. */
public class HazerBlockEntity extends BlockEntity {
    /** Out to here the haze is as thick as at the hazer; thinning out to nothing at {@link #REACH}. */
    private static final double FULL = 8, REACH = 24;
    /** Seconds to fill the room, and to clear it again. */
    private static final float FILL_TIME = 30f, CLEAR_TIME = 60f;

    /** Client: every hazer with haze in the air. */
    private static final Map<BlockPos, HazerBlockEntity> HAZY = new ConcurrentHashMap<>();

    /** 0 clear air .. 1 the room full of haze. */
    private float fill;

    public HazerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    void clientTick(Level level, BlockPos pos, BlockState state) {
        boolean running = HazerBlock.running(state);
        fill = Math.max(0f, Math.min(1f, fill + (running ? 1f / (FILL_TIME * 20) : -1f / (CLEAR_TIME * 20))));
        if (fill > 0f) HAZY.put(pos.immutable(), this);
        else HAZY.remove(pos);
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || !player.position().closerThan(Vec3.atCenterOf(pos), REACH + 16)) return;
        Direction facing = state.getValue(HazerBlock.FACING);
        Vec3 ahead = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 nozzle = Vec3.atCenterOf(pos).add(ahead.scale(0.45)).add(0, -0.15, 0);
        var random = level.random;
        if (running) {
            // A thin, fast jet that fans out and slows within a few blocks.
            for (int i = 0; i < 2; i++) {
                double speed = 0.2 + random.nextDouble() * 0.08;
                level.addParticle(ModParticles.HAZE.get(), nozzle.x, nozzle.y, nozzle.z,
                    ahead.x * speed + (random.nextDouble() - 0.5) * 0.05,
                    0.01 + (random.nextDouble() - 0.5) * 0.03,
                    ahead.z * speed + (random.nextDouble() - 0.5) * 0.05);
            }
        }
        // The veil already in the air: faint patches through the room, floor to ceiling and out to its
        // walls, reaching further across it as it fills - the room's shape, not a ball round the hazer.
        // ponytail: ~650 live patches at full haze; fewer if fill-rate bites on weak GPUs.
        if (fill > 0.02f && random.nextFloat() < fill * 1.3f) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 flat = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 from = nozzle.add(0, 0.8, 0);
            Vec3 col = inside(level, from, from.add(flat.scale(2 + 20 * fill * Math.sqrt(random.nextDouble()))), flat, player);
            double floor = inside(level, col, col.add(0, -4, 0), new Vec3(0, -1, 0), player).y;
            double ceiling = inside(level, col, col.add(0, 6, 0), new Vec3(0, 1, 0), player).y;
            Vec3 at = new Vec3(col.x, floor + random.nextDouble() * Math.max(0, ceiling - floor), col.z);
            if (at.distanceToSqr(nozzle) > 1) level.addParticle(ModParticles.HAZE.get(), at.x, at.y, at.z, 0, 0, 0);
        }
    }

    /** How far from {@code from} towards {@code to} is open air: stops a block short of a wall. */
    private static Vec3 inside(Level level, Vec3 from, Vec3 to, Vec3 dir, net.minecraft.world.entity.Entity player) {
        HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (hit.getType() == HitResult.Type.MISS) return to;
        Vec3 back = hit.getLocation().subtract(dir.scale(0.8));
        return back.subtract(from).dot(dir) > 0 ? back : from;
    }

    /** How thick the haze is at {@code at}, 0..1: what makes a beam through it show. */
    public static float hazeAt(Level level, Vec3 at) {
        float haze = 0f;
        for (Map.Entry<BlockPos, HazerBlockEntity> e : HAZY.entrySet()) {
            // Left behind by another dimension (its chunks are not always unloaded one by one).
            if (level.getBlockEntity(e.getKey()) != e.getValue()) {
                HAZY.remove(e.getKey());
                continue;
            }
            double d = Math.sqrt(e.getKey().distToCenterSqr(at));
            float near = (float) Math.max(0, Math.min(1, (REACH - d) / (REACH - FULL)));
            haze = Math.max(haze, e.getValue().fill * near);
        }
        return haze;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) HAZY.remove(worldPosition);
    }
}
