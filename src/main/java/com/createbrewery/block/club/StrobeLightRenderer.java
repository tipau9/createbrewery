package com.createbrewery.block.club;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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

        Direction facing = be.getBlockState().getValue(StrobeLightBlock.FACING);
        VertexConsumer v = buffers.getBuffer(ClubRenderTypes.GLOW);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        // Direction#getRotation turns +Y onto the facing, so the cone below is drawn along +Y.
        pose.mulPose(facing.getRotation());
        Matrix4f m = pose.last().pose();
        // A light cone thrown forward into the haze: two crossed planes, fading out
        float y = 0.47f;
        float len = y + 1.5f + intensity * 2f;
        float near = 0.3f, far = 0.6f + intensity * 0.5f;
        float ca = intensity * 0.25f;
        quad(v, m, -near, y, 0, near, y, 0, far, len, 0, -far, len, 0, 0.92f, 0.96f, 1f, ca, 0f);
        quad(v, m, 0, y, -near, 0, y, near, 0, len, far, 0, len, -far, 0.92f, 0.96f, 1f, ca, 0f);
        pose.popPose();

        // The flash itself: a round glow on the lens, always turned to the camera, hot white in the
        // middle and fading to nothing at the rim (the blend is additive, so it only ever brightens).
        // Pulled towards the camera, so the wall or ceiling it hangs on does not slice the halo.
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 lens = new Vec3(0.5 + facing.getStepX() * 0.5, 0.5 + facing.getStepY() * 0.5, 0.5 + facing.getStepZ() * 0.5);
        Vec3 toCamera = camera.getPosition().subtract(Vec3.atLowerCornerOf(be.getBlockPos()).add(lens));
        Vec3 halo = lens.add(toCamera.normalize().scale(Math.min(1.5, toCamera.length() * 0.5)));
        pose.pushPose();
        pose.translate(halo.x, halo.y, halo.z);
        pose.mulPose(camera.rotation());
        m = pose.last().pose();
        float power = be.getBlockState().getValue(StrobeLightBlock.BRIGHTNESS) / 3f;
        glow(v, m, (0.3f + intensity * 0.2f) * power, 1f, 1f, 1f, intensity);
        glow(v, m, (1.2f + intensity * 1.8f) * power, 0.85f, 0.92f, 1f, Math.min(1f, intensity * 0.55f * power));
        pose.popPose();
    }

    /** A disc of radius {@code r} facing +Z, alpha {@code a} in the centre and 0 at the rim. */
    private static void glow(VertexConsumer v, Matrix4f m, float r, float red, float green, float blue, float a) {
        int n = 16;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float x0 = Mth.cos(a0) * r, y0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, y1 = Mth.sin(a1) * r;
            // A triangle as a quad with the centre twice.
            quad(v, m, 0, 0, 0, 0, 0, 0, x0, y0, 0, x1, y1, 0, red, green, blue, a, 0f);
        }
    }

    @Override
    public AABB getRenderBoundingBox(StrobeLightBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(9);
    }

    /**
     * A quad seen from both sides (the glow render type culls back faces). The first two
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
