package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import com.createbrewery.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A subwoofer: with an amp rack on its booth it plays the lows (see MusicPulse); its cone and the camera shake follow the kick. */
public class SubwooferBlockEntity extends SpeakerBlockEntity {

    /** Client only: in singleplayer the integrated server ticks the same class and must not add to it. */
    public static final Set<BlockPos> ACTIVE_SUBS = ConcurrentHashMap.newKeySet();

    private float conePulse = 0f;
    private boolean kickLatched = false;

    public SubwooferBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) return;
        if (!state.getValue(SubwooferBlock.ACTIVE)) {
            ACTIVE_SUBS.remove(pos);
            conePulse = 0f;
            return;
        }

        ACTIVE_SUBS.add(pos);

        float kick = MusicPulse.kickNear(pos);
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

    /** Switched on, and not muted by redstone. */
    public boolean isActive() {
        return getBlockState().getValue(SubwooferBlock.ACTIVE);
    }

    public float getConePulse() {
        return conePulse;
    }

    public static float getSubwooferBassShake(net.minecraft.world.entity.player.Player player) {
        if (player == null) return 0f;
        float subShake = 0f;
        for (BlockPos pos : ACTIVE_SUBS) {
            double distSq = pos.distToCenterSqr(player.getX(), player.getY(), player.getZ());
            if (distSq >= 324.0) continue; // within 18 blocks
            // Positions left behind by another dimension (its chunks are not always unloaded one by one).
            if (!(player.level().getBlockEntity(pos) instanceof SubwooferBlockEntity)) {
                ACTIVE_SUBS.remove(pos);
                continue;
            }
            // Each sub pushes the kick of the music playing near it, not of whatever the player hears.
            float kick = MusicPulse.kickNear(pos);
            if (kick < 0.1f) continue;
            float falloff = (float) Math.max(0.0, 1.0 - Math.sqrt(distSq) / 18.0);
            subShake += kick * falloff * falloff;
        }
        float musicShake = MusicPulse.getMusicBassShake(player);
        return Math.min(1.5f, Math.max(subShake, musicShake));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) ACTIVE_SUBS.remove(worldPosition);
    }
}
