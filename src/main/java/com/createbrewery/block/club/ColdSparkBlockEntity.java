package com.createbrewery.block.club;

import com.createbrewery.particle.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The spark fountain's show, client only: see {@link ColdSparkBlock}. */
public class ColdSparkBlockEntity extends BlockEntity implements ConsoleLinked {

    private final ConsoleLinkData link = new ConsoleLinkData();

    @Override
    public ConsoleLinkData consoleLink() {
        return link;
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        link.save(tag);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        link.load(tag);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(registries);
        link.save(tag);
        return tag;
    }
    /** A drop sets it off for this long: about four seconds, like a programmed burst. */
    private static final int BURST = 80;
    /** Sparks per tick at full flow. */
    private static final int FLOW = 9;
    /** Gravity of a {@code cold_spark} particle, blocks per tick squared. */
    private static final double GRAVITY = 0.04;

    private int burst;
    /** The fan spins up and down: the fountain grows and dies over half a second. */
    private float flow;

    public ColdSparkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    void clientTick(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(ColdSparkBlock.AUTO) && ClubStates.at(level, pos, link.booth(level)).dropEdge) burst = BURST;
        if (burst > 0) burst--;
        boolean on = (state.getValue(Co2JetBlock.POWERED) || burst > 0) && link.gate(level) >= 0.05f;
        float before = flow;
        flow += ((on ? 1f : 0f) - flow) * 0.25f;
        if (flow < 0.03f) flow = 0f;
        if (before == 0f && flow > 0f) {
            level.playLocalSound(pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5f, 1.8f, false);
        }
        if (flow <= 0f) return;

        Direction facing = state.getValue(Co2JetBlock.FACING);
        if (Co2JetBlock.blocks(level, pos.relative(facing))) return;
        // The fizz of the granules, and the fan.
        if (level.getGameTime() % 4 == 0) {
            level.playLocalSound(pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.2f * flow, 1.6f + level.random.nextFloat() * 0.3f, false);
        }
        emit(level, pos, facing, state.getValue(ColdSparkBlock.HEIGHT), level.random);
    }

    private void emit(Level level, BlockPos pos, Direction facing, int height, RandomSource random) {
        Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 nozzle = Vec3.atCenterOf(pos).add(dir.scale(0.5));
        // Straight up it climbs {@code height} blocks: v = sqrt(2 g h). Sideways or down the same speed.
        double speed = Math.sqrt(2 * GRAVITY * height) * Math.sqrt(flow);
        int n = Math.round(FLOW * flow);
        for (int i = 0; i < n; i++) {
            double s = speed * (0.85 + random.nextDouble() * 0.25);
            Vec3 v = dir.scale(s).add(random.nextGaussian() * 0.035, random.nextGaussian() * 0.035, random.nextGaussian() * 0.035);
            level.addParticle(ModParticles.COLD_SPARK.get(), nozzle.x, nozzle.y, nozzle.z, v.x, v.y, v.z);
        }
    }
}
