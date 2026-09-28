package com.createbrewery.block.club;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import static com.createbrewery.block.club.StrobeLightRenderer.glow;
import static com.createbrewery.block.club.StrobeLightRenderer.quad;

/** Beams through the haze and glowing lenses; the housings are block models. Client only. */
public class FixtureRenderer implements BlockEntityRenderer<FixtureBlockEntity> {

    public FixtureRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FixtureBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float lv = be.level(partialTick);
        if (lv < 0.02f) return;
        Direction facing = be.getBlockState().getValue(FixtureBlock.FACING);
        int c = be.color();
        float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
        VertexConsumer v = buffers.getBuffer(ClubRenderTypes.GLOW);
        Vec3 dir = be.direction(facing, partialTick);
        Vec3 lens = be.lens(facing).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
        Vec3 side = Math.abs(facing.getStepY()) > 0 ? new Vec3(1, 0, 0) : new Vec3(-facing.getStepZ(), 0, facing.getStepX());
        float len = be.beam();

        switch (be.kind()) {
            case PAR -> {
                cone(v, pose, lens, dir, len, 0.22f, 0.22f + len * 0.2f, r, g, b, 0.22f * lv);
                lensGlow(v, pose, lens, 0.35f, r, g, b, lv);
            }
            case MOVING_HEAD -> {
                // A tight beam with a hot core, and its spot on whatever it hits.
                cone(v, pose, lens, dir, len, 0.07f, 0.07f + len * 0.035f, r, g, b, 0.35f * lv);
                cone(v, pose, lens, dir, len, 0.03f, 0.03f + len * 0.01f, 1f, 1f, 1f, 0.3f * lv);
                lensGlow(v, pose, lens, 0.25f, r, g, b, lv);
                BlockHitResult hit = be.hit();
                if (hit != null) {
                    pose.pushPose();
                    Matrix4f m = pose.last().pose();
                    Vec3 at = hit.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
                    LaserProjectorRenderer.renderImpactDot(v, m, at, hit.getDirection(), r, g, b, lv * 6f);
                    pose.popPose();
                }
            }
            case BLINDER -> {
                // Two lamps side by side; they light the crowd rather than draw beams.
                for (int i = -1; i <= 1; i += 2) {
                    Vec3 at = lens.add(side.scale(i * 0.22));
                    cone(v, pose, at, dir, Math.min(len, 6f), 0.18f, 1.6f, r, g, b, 0.18f * lv);
                    lensGlow(v, pose, at, 0.5f + lv * 0.5f, r, g, b, lv);
                }
            }
            case LED_BAR -> {
                for (int i = 0; i < 8; i++) {
                    float p = lv * be.pixel(i);
                    if (p < 0.02f) continue;
                    Vec3 at = lens.add(side.scale((i - 3.5) / 8.0 * 0.9));
                    cone(v, pose, at, dir, Math.min(len, 8f), 0.05f, 0.05f + Math.min(len, 8f) * 0.08f, r, g, b, 0.18f * p);
                    lensGlow(v, pose, at, 0.12f, r, g, b, p);
                }
            }
        }
    }

    /** A soft cone of light from {@code from} along {@code dir}, fading out toward its end. */
    private static void cone(VertexConsumer v, PoseStack pose, Vec3 from, Vec3 dir, float len, float r0, float r1,
                             float r, float g, float b, float a) {
        if (len < 0.05f || a < 0.005f) return;
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z));
        Matrix4f m = pose.last().pose();
        int n = 10;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            quad(v, m, c0 * r0, 0, s0 * r0, c1 * r0, 0, s1 * r0, c1 * r1, len, s1 * r1, c0 * r1, len, s0 * r1, r, g, b, a, 0f);
        }
        pose.popPose();
    }

    /** A glowing disc on the lens, turned to the camera. */
    private static void lensGlow(VertexConsumer v, PoseStack pose, Vec3 at, float size, float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        Matrix4f m = pose.last().pose();
        glow(v, m, size * 0.4f, 1f, 1f, 1f, a);
        glow(v, m, size, r, g, b, a * 0.6f);
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(FixtureBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(FixtureBlockEntity.RANGE);
    }

    @Override
    public boolean shouldRenderOffScreen(FixtureBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
