package com.createbrewery.mixin;

import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL21C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Veil's voxel shadow grid uploads a 64³ texture with glTexImage3D but trusts whatever pixel-unpack
 * state the last mod left behind. A bound pixel-unpack buffer, or a row length, image height or skip
 * from someone else's upload, makes the driver read far past Veil's 256 KB buffer, and Nvidia's
 * driver crashes on it (nvoglv64.dll, EXCEPTION_ACCESS_VIOLATION). Around Veil's uploads the state
 * is set to plain defaults, and put back as it was afterwards. Skipped when Veil is not there.
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.light.VoxelShadowGrid", remap = false)
public abstract class VoxelShadowGridMixin {
    @Shadow
    private static int textureId;

    private static final int[] CREATEBREWERY_STATE = {
        GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING, GL12C.GL_UNPACK_ROW_LENGTH, GL12C.GL_UNPACK_SKIP_ROWS,
        GL12C.GL_UNPACK_SKIP_PIXELS, GL12C.GL_UNPACK_IMAGE_HEIGHT, GL12C.GL_UNPACK_SKIP_IMAGES,
        GL12C.GL_UNPACK_ALIGNMENT, GL12C.GL_UNPACK_SWAP_BYTES};
    private static final int[] CREATEBREWERY_SAVED = new int[CREATEBREWERY_STATE.length];
    /** Whether the state was cleaned on the way in, so it is put back on the way out. */
    private static boolean createbrewery$cleaned, createbrewery$logged;

    // ensureTexture runs every frame but only uploads once, while the texture does not exist yet:
    // only then is the state touched (each glGet can stall the driver).
    @Inject(method = "ensureTexture", at = @At("HEAD"), require = 0)
    private static void createbrewery$beforeCreate(CallbackInfo ci) {
        if (textureId != 0) return;
        createbrewery$clean();
        if (!createbrewery$logged) {
            createbrewery$logged = true;
            LogUtils.getLogger().info("Create Brewery: cleaned pixel-unpack state before Veil's voxel shadow grid upload");
        }
    }

    // Veil binds its own unpack buffer inside uploadBuffer, after this: clean at the head, not at the call.
    @Inject(method = "uploadBuffer", at = @At("HEAD"), require = 0)
    private static void createbrewery$beforeUpload(CallbackInfo ci) {
        createbrewery$clean();
    }

    @Inject(method = {"ensureTexture", "uploadBuffer"}, at = @At("RETURN"), require = 0)
    private static void createbrewery$after(CallbackInfo ci) {
        if (!createbrewery$cleaned) return;
        createbrewery$cleaned = false;
        GL21C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, CREATEBREWERY_SAVED[0]);
        for (int i = 1; i < CREATEBREWERY_STATE.length; i++) GL12C.glPixelStorei(CREATEBREWERY_STATE[i], CREATEBREWERY_SAVED[i]);
    }

    private static void createbrewery$clean() {
        for (int i = 0; i < CREATEBREWERY_STATE.length; i++) CREATEBREWERY_SAVED[i] = GL12C.glGetInteger(CREATEBREWERY_STATE[i]);
        createbrewery$cleaned = true;
        GL21C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_ROW_LENGTH, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_SKIP_ROWS, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_SKIP_PIXELS, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_IMAGE_HEIGHT, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_SKIP_IMAGES, 0);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_ALIGNMENT, 4);
        GL12C.glPixelStorei(GL12C.GL_UNPACK_SWAP_BYTES, 0);
    }
}
