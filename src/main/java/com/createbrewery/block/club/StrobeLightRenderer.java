package com.createbrewery.block.club;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public class StrobeLightRenderer implements BlockEntityRenderer<StrobeLightBlockEntity> {

    public StrobeLightRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(StrobeLightBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float intensity = be.getFlashIntensity(partialTick);
        if (intensity < 0.04f) return;
        // Photosensitivity: vanilla's "Hide Lightning Flashes" also stops the strobe (a steady redstone light stays).
        if (Minecraft.getInstance().options.hideLightningFlash().get()
            && be.getBlockState().getValue(StrobeLightBlock.MODE) != StrobeMode.REDSTONE) return;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // Direction#getRotation turns +Y onto the facing, so everything below is drawn along +Y.
        pose.mulPose(be.getBlockState().getValue(StrobeLightBlock.FACING).getRotation());

        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();

        // 1. The xenon flare on the lens
        float s = 0.35f + intensity * 0.45f;
        float a = Math.min(1f, intensity * 1.2f);
        float y = 0.47f;
        quad(v, m, -s, y, -s, s, y, -s, s, y, s, -s, y, s, 0.95f, 0.98f, 1f, a, a);

        // 2. A light cone thrown forward into the haze: two crossed planes, fading out
        float len = y + 1.2f + intensity * 1.6f;
        float near = s * 0.4f, far = 0.45f + intensity * 0.35f;
        float ca = intensity * 0.3f;
        quad(v, m, -near, y, 0, near, y, 0, far, len, 0, -far, len, 0, 0.92f, 0.96f, 1f, ca, 0f);
        quad(v, m, 0, y, -near, 0, y, near, 0, len, far, 0, len, -far, 0.92f, 0.96f, 1f, ca, 0f);

        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(StrobeLightBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(4);
    }

    /**
     * A quad seen from both sides ({@code RenderType.lightning()} culls back faces). The first two
     * corners get alpha {@code a0}, the last two {@code a1}, for fading beams.
     */
    static void quad(VertexConsumer v, Matrix4f m,
                     float x0, float y0, float z0, float x1, float y1, float z1,
                     float x2, float y2, float z2, float x3, float y3, float z3,
                     float r, float g, float b, float a0, float a1) {
        v.addVertex(m, x0, y0, z0).setColor(r, g, b, a0);
        v.addVertex(m, x1, y1, z1).setColor(r, g, b, a0);
        v.addVertex(m, x2, y2, z2).setColor(r, g, b, a1);
        v.addVertex(m, x3, y3, z3).setColor(r, g, b, a1);

        v.addVertex(m, x3, y3, z3).setColor(r, g, b, a1);
        v.addVertex(m, x2, y2, z2).setColor(r, g, b, a1);
        v.addVertex(m, x1, y1, z1).setColor(r, g, b, a0);
        v.addVertex(m, x0, y0, z0).setColor(r, g, b, a0);
    }
}
