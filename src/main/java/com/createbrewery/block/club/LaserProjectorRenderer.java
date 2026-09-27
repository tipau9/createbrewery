package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import com.createbrewery.particle.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import static com.createbrewery.block.club.StrobeLightRenderer.quad;

public class LaserProjectorRenderer implements BlockEntityRenderer<LaserProjectorBlockEntity> {

    /** How far a beam reaches before it fades into the air. */
    static final double RANGE = 42.0;

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
        if (c == -1) {
            // Rainbow prism cycle
            float hue = ((be.getTicks() + partialTick) * 0.02f) % 1.0f;
            c = Mth.hsvToRgb(hue, 0.95f, 1.0f);
        }
        float r = ((c >> 16) & 0xFF) / 255f;
        float g = ((c >> 8) & 0xFF) / 255f;
        float b = (c & 0xFF) / 255f;

        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 startWorld = center.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.46));

        LaserPattern pattern = be.getPattern();
        float[] beamYawOffsets = switch (pattern) {
            case FAN -> new float[]{-24f, -12f, 0f, 12f, 24f};
            case BURST -> drop > 0.25f || kick > 0.45f ? new float[]{-22f, -11f, 0f, 11f, 22f} : new float[]{0f};
            case SWEEP -> new float[]{MusicPulse.playing()
                ? Mth.sin((be.getTicks() + partialTick) * 0.18f) * 26f
                : Mth.sin((be.getTicks() + partialTick) * 0.08f) * 20f};
            case BEAM -> new float[]{0f};
        };

        VertexConsumer v = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        Matrix4f m = pose.last().pose();

        for (float yawDeg : beamYawOffsets) {
            double[] d = LaserBeams.direction(facing.getStepX(), facing.getStepY(), facing.getStepZ(), yawDeg);
            Vec3 dir = new Vec3(d[0], d[1], d[2]);
            BlockHitResult hit = level.clip(new ClipContext(startWorld, startWorld.add(dir.scale(RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            boolean hitBlock = hit.getType() != HitResult.Type.MISS;
            Vec3 endWorld = hit.getLocation();

            // Everything below is relative to the block centre (the pose is translated there).
            Vec3 relStart = startWorld.subtract(center);
            Vec3 relEnd = endWorld.subtract(center);
            renderBeam(v, m, relStart, relEnd, r, g, b, kickBoost);

            if (hitBlock) {
                renderImpactDot(v, m, relEnd, hit.getDirection(), r, g, b, kickBoost);
                if (level.random.nextFloat() < 0.08f + kickBoost * 0.15f) {
                    level.addParticle(ModParticles.CHEERS_SPARK.get(),
                        endWorld.x - dir.x * 0.05, endWorld.y - dir.y * 0.05, endWorld.z - dir.z * 0.05,
                        (level.random.nextDouble() - 0.5) * 0.04,
                        0.02 + level.random.nextDouble() * 0.04,
                        (level.random.nextDouble() - 0.5) * 0.04);
                }
            }
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(LaserProjectorBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(RANGE);
    }

    @Override
    public boolean shouldRenderOffScreen(LaserProjectorBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    private static void renderBeam(VertexConsumer v, Matrix4f m, Vec3 start, Vec3 end,
                                   float r, float g, float b, float boost) {
        Vec3 diff = end.subtract(start);
        if (diff.lengthSqr() < 1e-4) return;
        Vec3 norm = diff.normalize();

        // Two orthogonal vectors across the beam, for the crossed planes
        Vec3 up = Math.abs(norm.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = norm.cross(up).normalize();
        up = right.cross(norm).normalize();

        float coreW = 0.016f + boost * 0.010f;
        float glowW = 0.055f + boost * 0.050f;
        float glowAlpha = 0.55f + boost * 0.35f;

        // Hot white core inside a coloured sheath
        beamPlane(v, m, start, end, right.scale(coreW), 1f, 1f, 1f, 0.95f);
        beamPlane(v, m, start, end, up.scale(coreW), 1f, 1f, 1f, 0.95f);
        beamPlane(v, m, start, end, right.scale(glowW), r, g, b, glowAlpha);
        beamPlane(v, m, start, end, up.scale(glowW), r, g, b, glowAlpha);
    }

    private static void beamPlane(VertexConsumer v, Matrix4f m, Vec3 s, Vec3 e, Vec3 o,
                                  float r, float g, float b, float a) {
        quad(v, m,
            (float) (s.x - o.x), (float) (s.y - o.y), (float) (s.z - o.z),
            (float) (s.x + o.x), (float) (s.y + o.y), (float) (s.z + o.z),
            (float) (e.x + o.x), (float) (e.y + o.y), (float) (e.z + o.z),
            (float) (e.x - o.x), (float) (e.y - o.y), (float) (e.z - o.z),
            r, g, b, a, a);
    }

    /** A bright dot lying flat on the face the beam hit, lifted off it a hair against z-fighting. */
    private static void renderImpactDot(VertexConsumer v, Matrix4f m, Vec3 hit, Direction face,
                                        float r, float g, float b, float boost) {
        Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 u = face.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 w = n.cross(u);
        Vec3 c = hit.add(n.scale(0.01));
        float radius = 0.05f + boost * 0.04f;
        dot(v, m, c, u.scale(radius), w.scale(radius), 1f, 1f, 1f, 0.9f);
        dot(v, m, c.add(n.scale(0.002)), u.scale(radius * 2.2), w.scale(radius * 2.2), r, g, b, 0.6f);
    }

    private static void dot(VertexConsumer v, Matrix4f m, Vec3 c, Vec3 u, Vec3 w,
                            float r, float g, float b, float a) {
        quad(v, m,
            (float) (c.x - u.x - w.x), (float) (c.y - u.y - w.y), (float) (c.z - u.z - w.z),
            (float) (c.x + u.x - w.x), (float) (c.y + u.y - w.y), (float) (c.z + u.z - w.z),
            (float) (c.x + u.x + w.x), (float) (c.y + u.y + w.y), (float) (c.z + u.z + w.z),
            (float) (c.x - u.x + w.x), (float) (c.y - u.y + w.y), (float) (c.z - u.z + w.z),
            r, g, b, a, a);
    }
}
