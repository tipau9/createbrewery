package com.createbrewery.block.club;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Extends RenderType only to reach its protected render state shards; never instantiated. */
final class ClubRenderTypes extends RenderType {
    private ClubRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /**
     * Vanilla's lightning (additive, unlit) without depth writes: beams and glows brighten what is
     * behind them but never hide the fog, glass or water drawn after them.
     */
    static final RenderType GLOW = create("createbrewery_glow", DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS, 1536, false, true,
        CompositeState.builder()
            .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
            .setWriteMaskState(COLOR_WRITE)
            .setTransparencyState(LIGHTNING_TRANSPARENCY)
            .setOutputState(WEATHER_TARGET)
            .createCompositeState(false));
}
