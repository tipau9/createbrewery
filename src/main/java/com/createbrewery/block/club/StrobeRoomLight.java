package com.createbrewery.block.club;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.HashSet;
import java.util.Set;

/**
 * A strobe's flash lighting up the room, through Veil (which comes along with Sable / Create
 * Aeronautics). Only touched when Veil is loaded (see {@link StrobeLightBlockEntity}), so it stays
 * optional. With an Iris shaderpack Veil's lights are skipped - then only the lens flashes.
 * Client and render thread only.
 */
final class StrobeRoomLight {
    private StrobeRoomLight() {}

    private static final Set<LightRenderHandle<PointLightData>> ALL = new HashSet<>();

    /**
     * Sets the strobe's light to {@code brightness} ({@code steady}: REDSTONE mode, not flashing); returns the handle to keep. Nothing is
     * allocated until the first flash, and once there it stays (dark between flashes): adding and
     * freeing Veil lights recompiles shaders, which would hitch on every beat.
     */
    static Object update(Object handle, BlockPos pos, Direction facing, float brightness, boolean steady) {
        return update(handle, pos, facing, brightness, steady, 0xE8F0FF);
    }

    /** The same in any colour (club fixtures). */
    @SuppressWarnings("unchecked")
    static Object update(Object handle, BlockPos pos, Direction facing, float brightness, boolean steady, int color) {
        // Photosensitivity: "Hide Lightning Flashes" keeps the room dark too (a steady redstone light stays).
        if (!steady && Minecraft.getInstance().options.hideLightningFlash().get()) brightness = 0f;
        LightRenderHandle<PointLightData> light = (LightRenderHandle<PointLightData>) handle;
        if (light == null || !light.isValid()) {
            if (brightness <= 0f) return null;
            light = VeilRenderSystem.renderer().getLightRenderer().addLight(new PointLightData());
            // Cold xenon white, a little in front of the lens so the housing does not shade it.
            light.getLightData()
                .setPosition(pos.getX() + 0.5 + facing.getStepX(), pos.getY() + 0.5 + facing.getStepY(), pos.getZ() + 0.5 + facing.getStepZ())
                .setRadius(20f);
            ALL.add(light);
        }
        light.getLightData().setColor(color).setBrightness(brightness * 3.5f);
        light.markDirty();
        return light;
    }

    static void free(Object handle) {
        if (handle instanceof LightRenderHandle<?> light) {
            // Already gone if clearAll ran first.
            if (ALL.remove(light)) light.free();
        }
    }

    /** Leaving the world: chunks are not always unloaded one by one, so drop whatever is left. */
    static void clearAll() {
        ALL.forEach(LightRenderHandle::free);
        ALL.clear();
    }
}
