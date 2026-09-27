package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import com.createbrewery.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SubwooferBlockEntity extends BlockEntity {

    public static final Set<BlockPos> ACTIVE_SUBS = ConcurrentHashMap.newKeySet();

    private float conePulse = 0f;
    private boolean kickLatched = false;

    public SubwooferBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!state.getValue(SubwooferBlock.ACTIVE)) {
            ACTIVE_SUBS.remove(pos);
            conePulse = 0f;
            return;
        }

        ACTIVE_SUBS.add(pos);

        if (level.isClientSide) {
            float kick = MusicPulse.kick();
            if (kick > 0.38f && !kickLatched) {
                conePulse = 1.0f;
                kickLatched = true;

                // Puff subtle air pressure wave from the front cone
                Direction facing = state.getValue(SubwooferBlock.FACING);
                double px = pos.getX() + 0.5 + facing.getStepX() * 0.52;
                double py = pos.getY() + 0.5 + facing.getStepY() * 0.52;
                double pz = pos.getZ() + 0.5 + facing.getStepZ() * 0.52;

                level.addParticle(ModParticles.SMOKE.get(), px, py, pz,
                    facing.getStepX() * 0.08, 0.01, facing.getStepZ() * 0.08);
            } else if (kick < 0.22f) {
                kickLatched = false;
            }

            conePulse = Math.max(0f, conePulse - 0.22f);
        }
    }

    public float getConePulse() {
        return conePulse;
    }

    public static float getSubwooferBassShake(net.minecraft.world.entity.player.Player player) {
        if (player == null || ACTIVE_SUBS.isEmpty()) return 0f;
        float kick = MusicPulse.kick();
        if (kick < 0.1f) return 0f;

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        float totalShake = 0f;
        for (BlockPos pos : ACTIVE_SUBS) {
            double distSq = pos.distToCenterSqr(px, py, pz);
            if (distSq < 144.0) { // within 12 blocks
                double dist = Math.sqrt(distSq);
                float falloff = (float) Math.max(0.0, 1.0 - (dist / 12.0));
                totalShake += kick * falloff * falloff;
            }
        }
        return Math.min(1.5f, totalShake);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ACTIVE_SUBS.remove(worldPosition);
    }
}
