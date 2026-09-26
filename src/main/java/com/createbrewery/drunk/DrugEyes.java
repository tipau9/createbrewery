package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * The eyes of someone on drugs, as others see them (from {@link DrugPose#eyes}): red when stoned,
 * heavy lids on the nod, a glassy shine on MDMA, Koks or meth. A tint over the eye pixels of
 * that skin, found by {@link EyeFinder}.
 */
final class DrugEyes extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private DrugEyes(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new DrugEyes(renderer));
        }
    }

    /** The eyes found on each skin (EyeFinder), by texture. */
    private static final Map<ResourceLocation, EyeFinder.Eyes> FOUND = new HashMap<>();

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        DrugPose drug = DrugPose.SEEN.get(player.getId());
        if (drug == null || drug.eyes() == 0 || player.isInvisible() || !getParentModel().head.visible) return;
        EyeFinder.Eyes eyes = FOUND.computeIfAbsent(player.getSkin().texture(), DrugEyes::find);
        if (eyes == null) eyes = EyeFinder.USUAL; // not downloaded yet: asked again next frame
        // Unlit colour: dimmed by hand with the light where the player stands.
        float lit = Math.max(LightTexture.block(light), LightTexture.sky(light)) / 15f * 0.85f + 0.15f;
        float r, g, b, a;
        switch (drug.eyes()) {
            case DrugPose.EYES_RED -> { r = 0.85f; g = 0.12f; b = 0.1f; a = 0.5f; }
            // Heavy lids: only the upper row of the eyes goes dark.
            case DrugPose.EYES_LIDS -> { r = 0.12f; g = 0.07f; b = 0.06f; a = 0.65f; }
            default -> { r = 1f; g = 1f; b = 1f; a = 0.3f; lit = Math.max(lit, 0.7f); }
        }
        int top = 8;
        for (int i = 0; i < 64; i++) if (eyes.at(i % 8, i / 8)) top = Math.min(top, i / 8);
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        Matrix4f m = pose.last().pose();
        VertexConsumer v = buffers.getBuffer(RenderType.debugQuads());
        for (int y = 0; y < 8; y++) {
            if (drug.eyes() == DrugPose.EYES_LIDS && y != top) continue;
            for (int x = 0; x < 8; x++) {
                if (!eyes.at(x, y)) continue;
                // In head space, in pixels / 16: the face at z = -4 (the hat layer 0.5 further out),
                // its top-left pixel (as seen from the front) at x = -4, y = -8.
                float z = (eyes.onHat(x, y) ? -4.55f : -4.05f) / 16f;
                float x0 = (x - 4) / 16f, x1 = (x - 3) / 16f, y0 = (y - 8) / 16f, y1 = (y - 7) / 16f;
                v.addVertex(m, x0, y0, z).setColor(r * lit, g * lit, b * lit, a);
                v.addVertex(m, x0, y1, z).setColor(r * lit, g * lit, b * lit, a);
                v.addVertex(m, x1, y1, z).setColor(r * lit, g * lit, b * lit, a);
                v.addVertex(m, x1, y0, z).setColor(r * lit, g * lit, b * lit, a);
            }
        }
        pose.popPose();
    }

    /** Reads the skin back from the GPU (render thread) and finds its eyes; the usual place if it cannot, null if not there yet. */
    private static EyeFinder.Eyes find(ResourceLocation skin) {
        try {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skin);
            RenderSystem.bindTexture(texture.getId());
            int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w == 0) return null;
            if (w != 64 || h != 64) return EyeFinder.USUAL;
            int[] argb = new int[64 * 64];
            try (NativeImage image = new NativeImage(64, 64, false)) {
                image.downloadTexture(0, false);
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 64; x++) {
                        int abgr = image.getPixelRGBA(x, y);
                        argb[y * 64 + x] = abgr & 0xFF00FF00 | (abgr & 0xFF) << 16 | (abgr >> 16) & 0xFF;
                    }
                }
            }
            return EyeFinder.find(argb);
        } catch (RuntimeException e) {
            return EyeFinder.USUAL;
        }
    }
}
