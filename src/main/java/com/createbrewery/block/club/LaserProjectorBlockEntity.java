package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LaserProjectorBlockEntity extends BlockEntity {

    private int color = 0x00FF66; // Neon green default
    private LaserPattern pattern = LaserPattern.BEAM;
    private int ticks = 0;

    // Client-side motion (not saved): the pattern clock runs faster on kicks, chase beams jump on beats.
    static final int MAX_BEAMS = 12;
    private float phase, prevPhase;
    private float beat;
    private boolean kickLatched;
    private final float[] chase = new float[MAX_BEAMS * 2];
    private final float[] prevChase = new float[MAX_BEAMS * 2];
    private final float[] chaseTarget = new float[MAX_BEAMS * 2];

    public LaserProjectorBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Client only (see the block's ticker). */
    public void tick() {
        ticks++;
        float kick = MusicPulse.kickNear(worldPosition);
        prevPhase = phase;
        phase += 0.04f + kick * 0.12f + MusicPulse.dropNear(worldPosition) * 0.08f;

        boolean hit = kick > 0.38f && !kickLatched;
        if (hit) kickLatched = true;
        else if (kick < 0.20f) kickLatched = false;
        beat = hit ? 1f : beat * 0.8f;

        // Chase: new spots on every beat, or every second while nothing plays.
        if (hit || (!MusicPulse.playingNear(worldPosition) && ticks % 20 == 0)) {
            for (int i = 0; i < chaseTarget.length; i++) {
                chaseTarget[i] = (level.random.nextFloat() - 0.5f) * (i % 2 == 0 ? 80f : 50f);
            }
        }
        System.arraycopy(chase, 0, prevChase, 0, chase.length);
        for (int i = 0; i < chase.length; i++) chase[i] += (chaseTarget[i] - chase[i]) * 0.35f;
    }

    public float getPhase(float partialTick) {
        return prevPhase + (phase - prevPhase) * partialTick;
    }

    /** 1 on a beat, fading out over a few ticks. */
    public float getBeat() {
        return beat;
    }

    /** Where chase beam {@code i} points: {yaw, pitch} in degrees. */
    public float chaseYaw(int i, float partialTick) {
        return prevChase[i * 2] + (chase[i * 2] - prevChase[i * 2]) * partialTick;
    }

    public float chasePitch(int i, float partialTick) {
        return prevChase[i * 2 + 1] + (chase[i * 2 + 1] - prevChase[i * 2 + 1]) * partialTick;
    }

    public int getTicks() {
        return ticks;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public LaserPattern getPattern() {
        return pattern;
    }

    public void setPattern(LaserPattern pattern) {
        this.pattern = pattern;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Color", color);
        tag.putString("Pattern", pattern.getSerializedName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Color")) {
            color = tag.getInt("Color");
        }
        if (tag.contains("Pattern")) {
            String p = tag.getString("Pattern");
            for (LaserPattern lp : LaserPattern.values()) {
                if (lp.getSerializedName().equalsIgnoreCase(p)) {
                    pattern = lp;
                    break;
                }
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}
