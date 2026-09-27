package com.createbrewery.block.club;

import com.createbrewery.ModBlockEntities;
import com.createbrewery.drunk.MusicPulse;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StrobeLightBlockEntity extends BlockEntity {

    private float flashIntensity = 0f;
    private float prevFlashIntensity = 0f;
    private boolean kickLatched = false;
    private int strobeCounter = 0;

    public StrobeLightBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) {
            // Server-side: ensure LIT property updates periodically if needed, or leave to client
            return;
        }

        prevFlashIntensity = flashIntensity;
        StrobeMode mode = state.getValue(StrobeLightBlock.MODE);
        boolean powered = state.getValue(StrobeLightBlock.POWERED);

        float target = 0f;
        switch (mode) {
            case BEAT -> {
                // React to bass kicks via MusicPulse
                float kick = MusicPulse.kick();
                if (kick > 0.38f && !kickLatched) {
                    flashIntensity = 1.0f;
                    kickLatched = true;
                } else if (kick < 0.20f) {
                    kickLatched = false;
                }
                // Decay flash intensity rapidly (snappy xenon burst)
                target = 0f;
            }
            case STROBE -> {
                strobeCounter++;
                if (strobeCounter % 3 == 0) {
                    flashIntensity = 1.0f;
                }
                target = 0f;
            }
            case REDSTONE -> {
                if (powered) {
                    target = 1.0f;
                } else {
                    target = 0f;
                }
            }
        }

        // Smooth or fast fade
        if (mode == StrobeMode.REDSTONE) {
            flashIntensity = target;
        } else {
            flashIntensity = Math.max(0f, flashIntensity - 0.28f);
        }
    }

    public float getFlashIntensity(float partialTicks) {
        return prevFlashIntensity + (flashIntensity - prevFlashIntensity) * partialTicks;
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
