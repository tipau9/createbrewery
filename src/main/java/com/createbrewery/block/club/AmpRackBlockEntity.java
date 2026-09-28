package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A booth's amp rack: the crossover that sends the lows to its subwoofers and the rest to its
 * speakers, and the gain of each side. Linked to the booth like a speaker (see MusicPulse).
 */
public class AmpRackBlockEntity extends SpeakerBlockEntity {
    public static final float MIN_CROSSOVER = 60f, MAX_CROSSOVER = 200f;

    /** The crossover's corner in Hz; the gain knobs 0..1 as DeckFx.eqGain reads them (0.5 = unity). */
    private float crossover = 100f, subGain = 0.5f, topGain = 0.5f;

    public AmpRackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public float getCrossover() {
        return crossover;
    }

    public float getSubGain() {
        return subGain;
    }

    public float getTopGain() {
        return topGain;
    }

    public void setCrossover(float hz) {
        crossover = Math.max(MIN_CROSSOVER, Math.min(MAX_CROSSOVER, hz));
        changed();
    }

    public void setSubGain(float knob) {
        subGain = Math.max(0f, Math.min(1f, knob));
        changed();
    }

    public void setTopGain(float knob) {
        topGain = Math.max(0f, Math.min(1f, knob));
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("Crossover", crossover);
        tag.putFloat("SubGain", subGain);
        tag.putFloat("TopGain", topGain);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Crossover")) crossover = tag.getFloat("Crossover");
        if (tag.contains("SubGain")) subGain = tag.getFloat("SubGain");
        if (tag.contains("TopGain")) topGain = tag.getFloat("TopGain");
    }
}
