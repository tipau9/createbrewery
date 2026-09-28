package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/** Client-side flash state only; the mode lives in the block state. */
public class StrobeLightBlockEntity extends BlockEntity {

    /** STROBE mode fires every this many ticks: 20 / 3 = ~6.7 flashes a second. */
    static final int STROBE_PERIOD = 3;

    private float flashIntensity = 0f;
    private float prevFlashIntensity = 0f;
    private boolean kickLatched = false;
    private int strobeCounter = 0;
    /** The Veil room light (a {@code LightRenderHandle}), typed Object so Veil stays optional. */
    private Object roomLight;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean veil = ModList.get().isLoaded("veil");

    public StrobeLightBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Client only (see the block's ticker). */
    public void tick(BlockState state) {
        prevFlashIntensity = flashIntensity;
        int power = state.getValue(StrobeLightBlock.BRIGHTNESS);
        // A xenon burst: full on, dark again two ticks later, so there are real dark frames between
        // flashes. From "Extreme" up it is a single hard frame: on, then black.
        flashIntensity = Math.max(0f, flashIntensity - (power >= 4 ? 1f : 0.5f));

        switch (state.getValue(StrobeLightBlock.MODE)) {
            case BEAT -> {
                float kick = MusicPulse.kickNear(worldPosition);
                if (kick > 0.38f && !kickLatched) {
                    flashIntensity = 1f;
                    kickLatched = true;
                } else if (kick < 0.20f) {
                    kickLatched = false;
                }
            }
            case STROBE -> {
                // "Blinding" fires every other tick: 10 flashes a second.
                if (++strobeCounter % (power == 5 ? 2 : STROBE_PERIOD) == 0) flashIntensity = 1f;
            }
            case REDSTONE -> flashIntensity = state.getValue(StrobeLightBlock.POWERED) ? 1f : 0f;
        }

        boolean steady = state.getValue(StrobeLightBlock.MODE) == StrobeMode.REDSTONE;
        if (level != null) StrobeFlash.offer(level, worldPosition, state.getValue(StrobeLightBlock.FACING), flashIntensity, power, steady);

        if (veil) {
            try {
                roomLight = StrobeRoomLight.update(roomLight, worldPosition, state.getValue(StrobeLightBlock.FACING),
                    flashIntensity * (0.4f + 0.3f * power), steady);
            } catch (RuntimeException | LinkageError e) {
                veil = false;
                LOGGER.warn("Veil strobe light unavailable", e);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        // Broken or chunk unloaded (clearAllBlockEntities calls this); only the client ever has a light.
        if (roomLight != null) {
            StrobeRoomLight.free(roomLight);
            roomLight = null;
        }
    }

    /** Leaving the world; called by DrunkClient once there is no player. */
    public static void clearRoomLights() {
        if (veil) StrobeRoomLight.clearAll();
    }

    public float getFlashIntensity(float partialTicks) {
        return prevFlashIntensity + (flashIntensity - prevFlashIntensity) * partialTicks;
    }
}
