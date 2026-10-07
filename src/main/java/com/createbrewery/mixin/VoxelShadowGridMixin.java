package com.createbrewery.mixin;

import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL21C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Veil's voxel shadow grid uploads a 64³ texture with glTexImage3D but trusts whatever pixel-unpack
 * state the last mod left behind. A bound pixel-unpack buffer, or a row length, image height or skip
 * from someone else's upload, makes the driver read far past Veil's 256 KB buffer, and Nvidia's
 * driver crashes on it (nvoglv64.dll, EXCEPTION_ACCESS_VIOLATION). Around both of Veil's uploads the
 * state is set to plain defaults, and put back as it was afterwards. Skipped when Veil is not there.
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.light.VoxelShadowGrid", remap = false)
public abstract class VoxelShadowGridMixin {
    private static final int[] STATE = {
        GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING, GL12C.GL_UNPACK_ROW_LENGTH, GL12C.GL_UNPACK_SKIP_ROWS,
        GL12C.GL_UNPACK_SKIP_PIXELS, GL12C.GL_UNPACK_IMAGE_HEIGHT, GL12C.GL_UNPACK_SKIP_IMAGES,
        GL12C.GL_UNPACK_ALIGNMENT, GL12C.GL_UNPACK_SWAP_BYTES};
    private static final int[] SAVED = new int[STATE.length];

    @Inject(method = {"ensureTexture", "uploadBuffer"}, at = @At("HEAD"), require = 0)
    private static void createbrewery$cleanUnpack(CallbackInfo ci) {
        for (int i = 0; i < STATE.length; i++) SAVED[i] = GL12C.glGetInteger(STATE[i]);
        GL21C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_SKIP_ROWS, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_SKIP_PIXELS, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_SKIP_IMAGES, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_ALIGNMENT, 4);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_SWAP_BYTES, 0);
    }

    @Inject(method = {"ensureTexture", "uploadBuffer"}, at = @At("RETURN"), require = 0)
    private static void createbrewery$restoreUnpack(CallbackInfo ci) {
        GL21C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, SAVED[0]);
        for (int i = 1; i < STATE.length; i++) GL12C.glPixelStorei(STATE[i], SAVED[i]);
    }
}
