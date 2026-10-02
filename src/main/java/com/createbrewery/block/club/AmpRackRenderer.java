package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

/**
 * The amp rack's front LEDs, seen without opening it: POWER green (amber while it fades up or
 * down), SIGNAL blue on the kick, LIMIT red while the amps are limiting.
 */
public class AmpRackRenderer implements BlockEntityRenderer<AmpRackBlockEntity> {
    // ponytail: LED spots placed by eye on amp_rack_front.png; move LED_X / LED_Y if the texture changes.
    private static final float[] LED_X = {-0.32f, -0.22f, -0.12f};
    private static final float LED_Y = 0.36f, SIZE = 0.035f, FRONT = 0.445f;

    public AmpRackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(AmpRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockPos booth = rack.getBooth();
        AmpSettings s = rack.settings();
        boolean ramping = booth != null && MusicPulse.ramping(booth);
        float signal = s.power ? MusicPulse.kickNear(rack.getBlockPos()) : 0f;
        AmpMeters m = booth == null ? null : MusicPulse.meters(booth);
        boolean limit = m != null && m.worstLimit() >= 1f;
        if (!s.power && !ramping && signal <= 0.05f && !limit) return;

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-rack.getBlockState().getValue(SpeakerBlock.FACING).toYRot()));
        Matrix4f mat = pose.last().pose();
        VertexConsumer v = buffers.getBuffer(ClubRenderTypes.GLOW);
        if (ramping) led(v, mat, LED_X[0], 1f, 0.6f, 0.1f, 0.9f);
        else if (s.power) led(v, mat, LED_X[0], 0.2f, 1f, 0.3f, 0.9f);
        if (signal > 0.05f) led(v, mat, LED_X[1], 0.2f, 0.5f, 1f, Math.min(1f, 0.3f + signal));
        if (limit) led(v, mat, LED_X[2], 1f, 0.15f, 0.1f, 0.95f);
        pose.popPose();
    }

    /** A small square on the front face (local +Z after the turn). */
    private static void led(VertexConsumer v, Matrix4f m, float x, float r, float g, float b, float a) {
        float x0 = x - SIZE, x1 = x + SIZE, y0 = LED_Y - SIZE, y1 = LED_Y + SIZE;
        v.addVertex(m, x0, y0, FRONT).setColor(r, g, b, a);
        v.addVertex(m, x1, y0, FRONT).setColor(r, g, b, a);
        v.addVertex(m, x1, y1, FRONT).setColor(r, g, b, a);
        v.addVertex(m, x0, y1, FRONT).setColor(r, g, b, a);
    }
}
