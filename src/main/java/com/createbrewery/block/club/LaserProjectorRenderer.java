package com.createbrewery.block.club;

import com.createbrewery.particle.ModParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
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
        float drop = be.getDrop();
        float beat = be.getBeat();
        float kickBoost = Math.max(Math.max(be.getKick() * 0.8f, drop * 1.2f), beat);

        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 startWorld = center.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.46));

        float[][] beams = aim(be, partialTick, beat, drop);
        // Through haze the beams stand out; without any they look as they always did.
        haze = 1f + 0.8f * HazerBlockEntity.hazeAt(level, center);

        VertexConsumer v = buffers.getBuffer(ClubRenderTypes.GLOW);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        Matrix4f m = pose.last().pose();

        for (int i = 0; i < beams.length; i++) {
            // Colour: rainbow runs through the beams, so a fan shows the whole spectrum.
            int c = be.getColor();
            if (c == -1) {
                float hue = ((be.getTicks() + partialTick) * 0.02f + (float) i / beams.length * 0.6f) % 1.0f;
                c = Mth.hsvToRgb(hue, 0.95f, 1.0f);
            }
            float r = ((c >> 16) & 0xFF) / 255f;
            float g = ((c >> 8) & 0xFF) / 255f;
            float b = (c & 0xFF) / 255f;

            double[] d = LaserBeams.direction(facing.getStepX(), facing.getStepY(), facing.getStepZ(), beams[i][0], beams[i][1]);
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
                if (level.random.nextFloat() < (0.08f + kickBoost * 0.15f) * 3f / Math.max(3, beams.length)) {
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

    /**
     * Where every beam points right now, as {yaw, pitch} in degrees off the facing. The pattern clock
     * ({@code phase}) runs faster on kicks, so the whole show speeds up with the music.
     */
    static float[][] aim(LaserProjectorBlockEntity be, float partialTick, float beat, float drop) {
        float t = be.getPhase(partialTick);
        return switch (be.getPattern()) {
            // One beam drawing a figure-of-eight over the crowd
            case BEAM -> new float[][]{{40f * Mth.sin(t), 22f * Mth.sin(t * 1.7f + 1f)}};
            // A 5-beam sheet swinging side to side and bobbing
            case SWEEP -> {
                float yaw = 38f * Mth.sin(t * 0.8f), pitch = 12f * Mth.sin(t * 0.5f);
                float spread = 8f + 4f * beat;
                float[][] out = new float[5][];
                for (int i = 0; i < 5; i++) out[i] = new float[]{yaw + (i - 2) * spread, pitch};
                yield out;
            }
            // A 9-beam fan spinning around the lens; it opens up on every beat
            case FAN -> {
                float turn = t * 0.6f, spread = 6f + 6f * beat + 2f * Mth.sin(t * 0.3f);
                float cos = Mth.cos(turn), sin = Mth.sin(turn), tilt = 10f * Mth.sin(t * 0.4f);
                float[][] out = new float[9][];
                for (int i = 0; i < 9; i++) {
                    float o = (i - 4) * spread;
                    out[i] = new float[]{o * cos, o * sin + tilt};
                }
                yield out;
            }
            // Beams jumping to new spots on every beat; the drop brings all twelve
            case BURST -> {
                int n = drop > 0.3f ? LaserProjectorBlockEntity.MAX_BEAMS : 6;
                float[][] out = new float[n][];
                for (int i = 0; i < n; i++) out[i] = new float[]{be.chaseYaw(i, partialTick), be.chasePitch(i, partialTick)};
                yield out;
            }
            // Twelve beams on a turning cone: a tunnel of light in the haze, wider on the beat
            case TUNNEL -> {
                float radius = 16f + 8f * beat + 5f * Mth.sin(t * 0.3f);
                float[][] out = new float[12][];
                for (int i = 0; i < 12; i++) {
                    float a = Mth.TWO_PI * i / 12 + t * 1.2f;
                    out[i] = new float[]{radius * Mth.cos(a), radius * Mth.sin(a)};
                }
                yield out;
            }
        };
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

    /** 1 in clear air, up to 1.8 in a full haze; set per projector before its beams (render thread). */
    private static float haze = 1f;

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
        float glowAlpha = Math.min(1f, (0.55f + boost * 0.35f) * haze);
        glowW *= haze;

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
    static void renderImpactDot(VertexConsumer v, Matrix4f m, Vec3 hit, Direction face,
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
