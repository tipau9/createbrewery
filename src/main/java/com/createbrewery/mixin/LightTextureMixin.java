package com.createbrewery.mixin;

import com.createbrewery.block.club.StrobeFlash;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A strobe flash lights the room: for that tick every block-light level of the lightmap is pushed
 * towards full, so the dark club shows up lit, with its real shading, and drops back to dark.
 * Not required: without it the strobe still washes the screen (StrobeFlash's glare).
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    // ordinal 1: the block-light brightness (ordinal 0 is sky light).
    @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LightTexture;getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F",
        ordinal = 1), require = 0)
    private float createbrewery$strobeFlash(float brightness) {
        return Mth.lerp(StrobeFlash.light(), brightness, 1f);
    }
}
