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
        Vec3 lens = be.lens(facing, partialTick).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
        Vec3 side = Math.abs(facing.getStepY()) > 0 ? new Vec3(1, 0, 0) : new Vec3(-facing.getStepZ(), 0, facing.getStepX());
        float len = be.beam();
        // Through haze the beams stand out (where the beam is, not only at the fixture); without any they look as they always did.
        Vec3 lensWorld = be.lens(facing, partialTick);
        haze = hazeAlong(be.getLevel(), lensWorld, dir, len);

        switch (be.kind()) {
            case PAR -> {
                cone(v, pose, lens, dir, len, 0.22f, 0.22f + len * 0.2f, r, g, b, 0.22f * lv);
                float glare = glare(lensWorld, dir, 0.97f);
                lensGlow(v, pose, lens, 0.35f * (1f + 2.5f * glare), r, g, b, lv * (1f + glare));
                BlockHitResult wash = be.hit();
                if (wash != null) {
                    pose.pushPose();
                    Vec3 at = wash.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
                    softSpot(v, pose.last().pose(), at, wash.getDirection(), r, g, b, 0.25f * lv, 0.22f + len * 0.2f);
                    pose.popPose();
                }
            }
            case MOVING_HEAD -> {
                // Zoom: 0 narrow, 1 normal (as before), 2 wide.
                float zoomK = be.zoom() == 0 ? 0.6f : be.zoom() == 2 ? 1.8f : 1f;
                float glare = 0f;
                for (int bm = 0; bm < be.beamCount(); bm++) {
                    Vec3 bdir = be.beamDirection(bm, partialTick);
                    float blen = be.beamLength(bm);
                    haze = hazeAlong(be.getLevel(), lensWorld, bdir, blen);
                    glare = Math.max(glare, glare(lensWorld, bdir, be.zoom() == 0 ? 0.995f : be.zoom() == 2 ? 0.975f : 0.99f));
                    // A tight beam with a hot core, and its spot on whatever it hits.
                    cone(v, pose, lens, bdir, blen, 0.07f, 0.07f + blen * 0.035f * zoomK, r, g, b, 0.35f * lv);
                    cone(v, pose, lens, bdir, blen, 0.03f, 0.03f + blen * 0.01f * zoomK, 1f, 1f, 1f, 0.3f * lv);
                    BlockHitResult hit = be.beamHit(bm);
                    if (hit != null) {
                        pose.pushPose();
                        Matrix4f m = pose.last().pose();
                        Vec3 at = hit.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
                        if (be.gobo() == 0) {
                            LaserProjectorRenderer.renderImpactDot(v, m, at, hit.getDirection(), r, g, b, lv * 6f);
                        } else {
                            goboSpot(v, m, at, hit.getDirection(), r, g, b, lv, be.gobo(), be.goboRotation(), 0.18f + 0.1f * zoomK);
                        }
                        pose.popPose();
                    }
                }
                lensGlow(v, pose, lens, 0.25f * (1f + 3f * glare), r, g, b, lv * (1f + glare));
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
                    int pc = be.pixelColor(i);
                    float pr = (pc >> 16 & 255) / 255f, pg = (pc >> 8 & 255) / 255f, pb = (pc & 255) / 255f;
                    Vec3 at = lens.add(side.scale((i - 3.5) / 8.0 * 0.9));
                    cone(v, pose, at, dir, Math.min(len, 8f), 0.05f, 0.05f + Math.min(len, 8f) * 0.08f, pr, pg, pb, 0.18f * p);
                    lensGlow(v, pose, at, 0.12f, pr, pg, pb, p);
                }
                BlockHitResult bar = be.hit();
                if (bar != null) {
                    pose.pushPose();
                    Vec3 at = bar.getLocation().subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
                    softSpot(v, pose.last().pose(), at, bar.getDirection(), r, g, b, 0.2f * lv, 0.6f);
                    pose.popPose();
                }
            }
        }
    }

    /** 1 in clear air, up to 2.5 in a full haze; set per beam before it is drawn (render thread). */
    private static float haze = 1f;

    /** How much a beam stands out: the thickest haze at its lens, middle or end. */
    private static float hazeAlong(net.minecraft.world.level.Level level, Vec3 from, Vec3 dir, float len) {
        float h = HazerBlockEntity.hazeAt(level, from);
        h = Math.max(h, HazerBlockEntity.hazeAt(level, from.add(dir.scale(len * 0.5))));
        h = Math.max(h, HazerBlockEntity.hazeAt(level, from.add(dir.scale(len))));
        return 1f + 1.5f * h;
    }

    /**
     * 0..1, how straight the camera looks into a beam from {@code lens} along {@code dir}: 0 outside
     * the cone whose half-angle has cosine {@code edge}, 1 right down its middle. Makes the lens flare.
     */
    private static float glare(Vec3 lens, Vec3 dir, float edge) {
        Vec3 to = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(lens);
        double d = to.length();
        if (d < 0.5) return 0f;
        double cos = to.scale(1 / d).dot(dir);
        return cos <= edge ? 0f : (float) ((cos - edge) / (1 - edge));
    }

    /** A soft cone of light from {@code from} along {@code dir}, fading out toward its end. */
    private static void cone(VertexConsumer v, PoseStack pose, Vec3 from, Vec3 dir, float len, float r0, float r1,
                             float r, float g, float b, float a) {
        a = Math.min(1f, a * haze);
        if (len < 0.05f || a < 0.005f) return;
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z));
        Matrix4f m = pose.last().pose();
        int n = 16;
        // Four nested cones: a bright core inside fading skirts and a faint halo, so the edge of the beam is soft.
        float[] scale = {1.5f, 1f, 0.6f, 0.3f}, share = {0.1f, 0.33f, 0.32f, 0.25f};
        // A short beam that hits a wall still reaches it; a long one fades out into the air.
        float reach = 0.35f * Math.max(0f, 1f - len / (float) FixtureBlockEntity.RANGE);
        for (int layer = 0; layer < scale.length; layer++) {
            float q0 = r0 * scale[layer], q1 = r1 * scale[layer], al = a * share[layer];
            for (int i = 0; i < n; i++) {
                float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                quad(v, m, c0 * q0, 0, s0 * q0, c1 * q0, 0, s1 * q0, c1 * q1, len, s1 * q1, c0 * q1, len, s0 * q1, r, g, b, al, al * reach);
            }
        }
        pose.popPose();
    }

    /** A gobo's shape on the surface that was hit, filled, bright in the middle. */
    private static void goboSpot(VertexConsumer v, Matrix4f m, Vec3 hit, Direction face, float r, float g, float b, float lv,
                                 int gobo, float rotation, float radius) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 u = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 w = n.cross(u);
        Vec3 c = hit.add(n.scale(0.01));
        for (float[] poly : GoboShape.shapes(gobo, rotation, radius)) {
            fan(v, m, c, u, w, poly, 1f, 1f, 1f, 0.7f * lv, 0.2f * lv);
            fan(v, m, c.add(n.scale(0.002)), u, w, poly, r, g, b, 0.9f * lv, 0.4f * lv);
        }
    }

    /** The polygon as a triangle fan around its middle, alpha {@code aMid} there and {@code aEdge} at the rim. */
    private static void fan(VertexConsumer v, Matrix4f m, Vec3 c, Vec3 u, Vec3 w, float[] poly, float r, float g, float b, float aMid, float aEdge) {
        int count = poly.length / 2;
        float mx = 0, my = 0;
        for (int i = 0; i < count; i++) {
            mx += poly[i * 2] / count;
            my += poly[i * 2 + 1] / count;
        }
        Vec3 mid = c.add(u.scale(mx)).add(w.scale(my));
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            Vec3 p0 = c.add(u.scale(poly[i * 2])).add(w.scale(poly[i * 2 + 1]));
            Vec3 p1 = c.add(u.scale(poly[j * 2])).add(w.scale(poly[j * 2 + 1]));
            quad(v, m, (float) mid.x, (float) mid.y, (float) mid.z, (float) mid.x, (float) mid.y, (float) mid.z,
                (float) p1.x, (float) p1.y, (float) p1.z, (float) p0.x, (float) p0.y, (float) p0.z, r, g, b, aMid, aEdge);
        }
    }

    /** A soft round patch of light on the surface a wash hit; {@code alpha} in the middle, none at the rim. */
    private static void softSpot(VertexConsumer v, Matrix4f m, Vec3 hit, Direction face, float r, float g, float b, float alpha, float radius) {
        if (alpha < 0.01f) return;
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 u = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 w = n.cross(u);
        Vec3 c = hit.add(n.scale(0.012));
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = Mth.TWO_PI * i / seg, a1 = Mth.TWO_PI * (i + 1) / seg;
            Vec3 p0 = c.add(u.scale(Math.cos(a0) * radius)).add(w.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(u.scale(Math.cos(a1) * radius)).add(w.scale(Math.sin(a1) * radius));
            quad(v, m, (float) c.x, (float) c.y, (float) c.z, (float) c.x, (float) c.y, (float) c.z,
                (float) p1.x, (float) p1.y, (float) p1.z, (float) p0.x, (float) p0.y, (float) p0.z, r, g, b, alpha, 0f);
        }
    }

    /** A glowing disc on the lens, turned to the camera. */
    private static void lensGlow(VertexConsumer v, PoseStack pose, Vec3 at, float size, float r, float g, float b, float a) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        Matrix4f m = pose.last().pose();
        glow(v, m, size * 0.4f, 1f, 1f, 1f, Math.min(1f, a));
        glow(v, m, size, r, g, b, Math.min(1f, a * 0.6f));
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
