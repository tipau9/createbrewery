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

/**
 * The eyes of someone on drugs, as others see them (from {@link DrugPose#eyes}): red when stoned,
 * heavy lids on the nod, a glassy shine on MDMA, Koks or meth. A tint over the eye rows of the
 * face (rows 3-4), not drawn pupils - skins put their eyes too differently for that.
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

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        DrugPose drug = DrugPose.SEEN.get(player.getId());
        if (drug == null || drug.eyes() == 0 || player.isInvisible() || !getParentModel().head.visible) return;
        // Unlit colour: dimmed by hand with the light where the player stands.
        float lit = Math.max(LightTexture.block(light), LightTexture.sky(light)) / 15f * 0.85f + 0.15f;
        float r, g, b, a, top = -5f, bottom = -3f;
        switch (drug.eyes()) {
            case DrugPose.EYES_RED -> { r = 0.85f; g = 0.12f; b = 0.1f; a = 0.45f; }
            // Heavy lids: the upper eye row goes dark.
            case DrugPose.EYES_LIDS -> { r = 0.12f; g = 0.07f; b = 0.06f; a = 0.6f; bottom = -4f; }
            default -> { r = 1f; g = 1f; b = 1f; a = 0.3f; lit = Math.max(lit, 0.7f); }
        }
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        Matrix4f m = pose.last().pose();
        // In head space, in pixels / 16: the face is at z = -4, its top at y = -8.
        float z = -4.05f / 16f, x0 = -3f / 16f, x1 = 3f / 16f, y0 = top / 16f, y1 = bottom / 16f;
        VertexConsumer v = buffers.getBuffer(RenderType.debugQuads());
        v.addVertex(m, x0, y0, z).setColor(r * lit, g * lit, b * lit, a);
        v.addVertex(m, x0, y1, z).setColor(r * lit, g * lit, b * lit, a);
        v.addVertex(m, x1, y1, z).setColor(r * lit, g * lit, b * lit, a);
        v.addVertex(m, x1, y0, z).setColor(r * lit, g * lit, b * lit, a);
        pose.popPose();
    }
}
