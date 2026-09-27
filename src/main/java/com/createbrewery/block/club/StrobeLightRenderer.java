package com.createbrewery.block.club;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

public class StrobeLightRenderer implements BlockEntityRenderer<StrobeLightBlockEntity> {

    public StrobeLightRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(StrobeLightBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float intensity = be.getFlashIntensity(partialTick);
        if (intensity < 0.04f) return;

        Direction facing = be.getBlockState().getValue(StrobeLightBlock.FACING);

        pose.pushPose();
        // Move to block center
        pose.translate(0.5, 0.5, 0.5);

        // Rotate towards facing direction (North is -Z)
        switch (facing) {
            case NORTH -> {}
            case SOUTH -> pose.mulPose(Axis.YP.rotationDegrees(180f));
            case WEST -> pose.mulPose(Axis.YP.rotationDegrees(90f));
            case EAST -> pose.mulPose(Axis.YP.rotationDegrees(-90f));
            case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90f));
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90f));
        }

        // Move to front of lens
        pose.translate(0, 0, -0.46);

        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();

        // 1. Intense Xenon Flash Flare Billboard
        float s = 0.35f + intensity * 0.45f;
        float alpha = Math.min(1f, intensity * 1.2f);
        quad(v, m, -s, -s, s, s, 0f, 0.95f, 0.98f, 1.0f, alpha);

        // 2. Light Cone projecting forward (stage haze / beam effect)
        float coneLen = -1.2f - intensity * 1.6f;
        float coneSpread = 0.45f + intensity * 0.35f;
        float coneAlpha = intensity * 0.3f;

        coneQuad(v, m, -s * 0.4f, s * 0.4f, -coneSpread, coneSpread, 0f, coneLen, 0.92f, 0.96f, 1f, coneAlpha);
        coneQuad(v, m, s * 0.4f, -s * 0.4f, coneSpread, -coneSpread, 0f, coneLen, 0.92f, 0.96f, 1f, coneAlpha);

        pose.popPose();
    }

    private static void quad(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z,
                             float r, float g, float b, float a) {
        v.addVertex(m, x0, y0, z).setColor(r, g, b, a);
        v.addVertex(m, x0, y1, z).setColor(r, g, b, a);
        v.addVertex(m, x1, y1, z).setColor(r, g, b, a);
        v.addVertex(m, x1, y0, z).setColor(r, g, b, a);
    }

    private static void coneQuad(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z0, float z1,
                                 float r, float g, float b, float a) {
        v.addVertex(m, x0, -0.1f, z0).setColor(r, g, b, a);
        v.addVertex(m, x0, 0.1f, z0).setColor(r, g, b, a);
        v.addVertex(m, x1, 0.3f, z1).setColor(r, g, b, 0f);
        v.addVertex(m, x1, -0.3f, z1).setColor(r, g, b, 0f);
    }
}
