package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import com.createbrewery.particle.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class LaserProjectorRenderer implements BlockEntityRenderer<LaserProjectorBlockEntity> {

    public LaserProjectorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(LaserProjectorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!be.getBlockState().getValue(LaserProjectorBlock.ACTIVE)) return;

        Level level = be.getLevel();
        if (level == null) return;

        Direction facing = be.getBlockState().getValue(LaserProjectorBlock.FACING);
        BlockPos pos = be.getBlockPos();

        // Music reactivity
        float kick = MusicPulse.kick();
        float drop = MusicPulse.drop();
        float kickBoost = Math.max(kick * 0.8f, drop * 1.2f);

        // Color resolution
        int c = be.getColor();
        float r, g, b;
        if (c == -1) {
            // Rainbow prism cycle
            float hue = ((be.getTicks() + partialTick) * 0.02f) % 1.0f;
            int rgb = Mth.hsvToRgb(hue, 0.95f, 1.0f);
            r = ((rgb >> 16) & 0xFF) / 255f;
            g = ((rgb >> 8) & 0xFF) / 255f;
            b = (rgb & 0xFF) / 255f;
        } else {
            r = ((c >> 16) & 0xFF) / 255f;
            g = ((c >> 8) & 0xFF) / 255f;
            b = (c & 0xFF) / 255f;
        }

        // Base raycast origin in world space (front lens of projector)
        Vec3 startWorld = Vec3.atCenterOf(pos).add(
            facing.getStepX() * 0.46,
            facing.getStepY() * 0.46,
            facing.getStepZ() * 0.46
        );

        LaserPattern pattern = be.getPattern();

        // Calculate beam angles
        float[] beamYawOffsets;
        if (pattern == LaserPattern.FAN) {
            beamYawOffsets = new float[]{-24f, -12f, 0f, 12f, 24f};
        } else if (pattern == LaserPattern.BURST) {
            if (drop > 0.25f || kick > 0.45f) {
                beamYawOffsets = new float[]{-22f, -11f, 0f, 11f, 22f};
            } else {
                beamYawOffsets = new float[]{0f};
            }
        } else if (pattern == LaserPattern.SWEEP) {
            float sweep;
            if (MusicPulse.playing()) {
                sweep = Mth.sin((be.getTicks() + partialTick) * 0.18f) * 26f;
            } else {
                sweep = Mth.sin((be.getTicks() + partialTick) * 0.08f) * 20f;
            }
            beamYawOffsets = new float[]{sweep};
        } else {
            // SINGLE BEAM
            beamYawOffsets = new float[]{0f};
        }

        VertexConsumer v = buffers.getBuffer(RenderType.lightning());

        for (float yawDeg : beamYawOffsets) {
            // Compute beam direction in world coordinates
            Vec3 dir = getBeamDirection(facing, yawDeg);
            Vec3 maxEnd = startWorld.add(dir.scale(42.0));

            // Clip against blocks in the world
            BlockHitResult hit = level.clip(new ClipContext(startWorld, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null));
            Vec3 endWorld = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : maxEnd;
            double beamLength = startWorld.distanceTo(endWorld);

            // Relative vector from block origin (0.5, 0.5, 0.5) to start and end
            Vec3 relStart = startWorld.subtract(Vec3.atCenterOf(pos));
            Vec3 relEnd = endWorld.subtract(Vec3.atCenterOf(pos));

            // Render laser beam cylinder/cross-quads
            renderBeamSegment(pose, v, relStart, relEnd, r, g, b, kickBoost);

            // Impact dot & sparks
            if (hit.getType() != HitResult.Type.MISS) {
                renderImpactDot(pose, v, relEnd, r, g, b, kickBoost);
                if (level.random.nextFloat() < 0.08f + kickBoost * 0.15f) {
                    level.addParticle(ModParticles.CHEERS_SPARK.get(),
                        endWorld.x - dir.x * 0.05,
                        endWorld.y - dir.y * 0.05,
                        endWorld.z - dir.z * 0.05,
                        (level.random.nextDouble() - 0.5) * 0.04,
                        0.02 + level.random.nextDouble() * 0.04,
                        (level.random.nextDouble() - 0.5) * 0.04);
                }
            }
        }
    }

    private static Vec3 getBeamDirection(Direction facing, float yawOffsetDeg) {
        float rad = yawOffsetDeg * Mth.DEG_TO_RAD;
        Vec3 base = Vec3.atLowerCornerOf(facing.getNormal());

        if (facing.getAxis().isVertical()) {
            // If facing UP or DOWN, rotate in XZ plane
            return new Vec3(Mth.sin(rad), base.y, Mth.cos(rad)).normalize();
        } else {
            // Horizontal facing: rotate horizontally around Y axis
            double cos = Mth.cos(rad);
            double sin = Mth.sin(rad);
            double x = base.x * cos - base.z * sin;
            double z = base.x * sin + base.z * cos;
            return new Vec3(x, base.y, z).normalize();
        }
    }

    private static void renderBeamSegment(PoseStack pose, VertexConsumer v, Vec3 start, Vec3 end,
                                          float r, float g, float b, float boost) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);

        Matrix4f m = pose.last().pose();

        Vec3 diff = end.subtract(start);
        float len = (float) diff.length();
        if (len < 0.01f) {
            pose.popPose();
            return;
        }

        Vec3 norm = diff.normalize();

        // Generate orthogonal vectors for beam cross-section
        Vec3 up = Math.abs(norm.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = norm.cross(up).normalize();
        up = right.cross(norm).normalize();

        float coreW = 0.016f + boost * 0.010f;
        float glowW = 0.055f + boost * 0.050f;
        float glowAlpha = 0.55f + boost * 0.35f;

        // 1. Inner intense White Core
        drawBeamQuad(v, m, start, end, right.scale(coreW), 1f, 1f, 1f, 0.95f);
        drawBeamQuad(v, m, start, end, up.scale(coreW), 1f, 1f, 1f, 0.95f);

        // 2. Outer Vibrant Colored Glow Sheath
        drawBeamQuad(v, m, start, end, right.scale(glowW), r, g, b, glowAlpha);
        drawBeamQuad(v, m, start, end, up.scale(glowW), r, g, b, glowAlpha);

        pose.popPose();
    }

    private static void drawBeamQuad(VertexConsumer v, Matrix4f m, Vec3 s, Vec3 e, Vec3 offset,
                                     float r, float g, float b, float a) {
        float x0 = (float) (s.x - offset.x), y0 = (float) (s.y - offset.y), z0 = (float) (s.z - offset.z);
        float x1 = (float) (s.x + offset.x), y1 = (float) (s.y + offset.y), z1 = (float) (s.z + offset.z);
        float x2 = (float) (e.x + offset.x), y2 = (float) (e.y + offset.y), z2 = (float) (e.z + offset.z);
        float x3 = (float) (e.x - offset.x), y3 = (float) (e.y - offset.y), z3 = (float) (e.z - offset.z);

        v.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        v.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        v.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        v.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
    }

    private static void renderImpactDot(PoseStack pose, VertexConsumer v, Vec3 hitPos,
                                        float r, float g, float b, float boost) {
        Matrix4f m = pose.last().pose();
        float radius = 0.05f + boost * 0.04f;
        float hx = (float) hitPos.x, hy = (float) hitPos.y, hz = (float) hitPos.z;

        // Bright impact cross / flare
        v.addVertex(m, hx - radius, hy - radius, hz).setColor(1f, 1f, 1f, 0.9f);
        v.addVertex(m, hx + radius, hy - radius, hz).setColor(1f, 1f, 1f, 0.9f);
        v.addVertex(m, hx + radius, hy + radius, hz).setColor(1f, 1f, 1f, 0.9f);
        v.addVertex(m, hx - radius, hy + radius, hz).setColor(1f, 1f, 1f, 0.9f);

        // Glow ring
        float glowRad = radius * 2.2f;
        v.addVertex(m, hx - glowRad, hy - glowRad, hz).setColor(r, g, b, 0.6f);
        v.addVertex(m, hx + glowRad, hy - glowRad, hz).setColor(r, g, b, 0.6f);
        v.addVertex(m, hx + glowRad, hy + glowRad, hz).setColor(r, g, b, 0.6f);
        v.addVertex(m, hx - glowRad, hy + glowRad, hz).setColor(r, g, b, 0.6f);
    }
}
