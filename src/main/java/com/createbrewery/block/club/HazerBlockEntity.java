package com.createbrewery.block.club;

import com.createbrewery.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
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
        if (running && level.getGameTime() % 6 == 0) {
            // A thin wisp out of the nozzle: the haze itself is too fine to see.
            Direction facing = state.getValue(HazerBlock.FACING);
            Vec3 out = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.45)).add(0, -0.15, 0);
            Vec3 v = Vec3.atLowerCornerOf(facing.getNormal()).scale(0.06);
            level.addParticle(ModParticles.SMOKE.get(), out.x, out.y, out.z, v.x, 0.005, v.z);
        }
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
