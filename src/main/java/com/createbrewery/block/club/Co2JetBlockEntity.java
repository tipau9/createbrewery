package com.createbrewery.block.club;

import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The valve of a CO2 cannon. Opening it slams the pressure up (a surge a little above the steady
 * flow), closing it lets the jet collapse over a few ticks. While there is pressure the nozzle
 * pours out a tight, fast column of cold vapour that flares into a cone, and the roar follows the
 * pressure. No saved state: the valve is just the block's POWERED.
 */
public class Co2JetBlockEntity extends BlockEntity {

    /** Particles per tick at full pressure: enough for an unbroken column. */
    private static final int FLOW = 7;

    private float pressure;
    private int open;
    /** The roar loop playing on this client (a {@code Co2RoarSound}), typed Object so the server never loads it. */
    private Object roar;

    public Co2JetBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        boolean on = state.getValue(Co2JetBlock.POWERED);
        Direction facing = state.getValue(Co2JetBlock.FACING);
        if (!level.isClientSide) {
            // Keeps cooling whoever steps into a jet that is already running.
            if (on && level.getGameTime() % 10 == 0) Co2JetBlock.chill(level, pos, facing);
            return;
        }

        boolean wasOn = open > 0;
        open = on ? open + 1 : 0;
        // Snaps open with a surge, settles to the steady flow; closed, the jet dies within a few ticks.
        float target = on ? (open < 5 ? 1.25f : 1f) : 0f;
        pressure += (target - pressure) * (on ? 0.6f : 0.4f);
        if (pressure < 0.02f) pressure = 0f;

        if (open == 1) level.playLocalSound(pos, ModSounds.CO2_START.get(), SoundSource.BLOCKS, 1.6f, 0.95f + level.random.nextFloat() * 0.1f, false);
        if (wasOn && !on) {
            level.playLocalSound(pos, ModSounds.CO2_STOP.get(), SoundSource.BLOCKS, 1.2f, 1f, false);
        }
        if (pressure > 0f) roar = Co2RoarSound.keep(roar, this);
        else roar = null;

        // Nozzle against a wall: the gas just hisses out of the sides.
        if (pressure <= 0f || Co2JetBlock.blocks(level, pos.relative(facing))) return;
        emit(level, pos, facing, level.random);
    }

    private void emit(Level level, BlockPos pos, Direction facing, RandomSource random) {
        Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 nozzle = Vec3.atCenterOf(pos).add(dir.scale(0.55));
        int n = Math.round(FLOW * Math.min(1f, pressure));
        for (int i = 0; i < n; i++) {
            // Exit speed ~25 m/s at full pressure; the cone is about 8 degrees wide.
            double speed = (1.15 + random.nextDouble() * 0.35) * pressure;
            Vec3 v = dir.scale(speed).add(
                random.nextGaussian() * 0.07 * speed,
                random.nextGaussian() * 0.07 * speed,
                random.nextGaussian() * 0.07 * speed);
            // Spread along this tick's stretch of the jet, so it reads as one column and not a string of beads.
            Vec3 at = nozzle.add(v.scale(random.nextDouble()));
            level.addParticle(ModParticles.CO2.get(), at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    /** 0 closed .. about 1.25 in the surge right after the valve opens. */
    public float pressure() {
        return pressure;
    }
}
