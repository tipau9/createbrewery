package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugPose;
import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A line the way it is done: phone out of the pocket, the powder chopped into a line on the
 * screen with a card, card swapped for a straw, head down, one long sniff, head back, phone
 * away. {@link PoseAnimation} moves the body through the phases below; this layer draws the
 * phone, the card, the straw and the line in the hands, and plays the sniff at the right moment.
 */
final class SniffProps extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Phase ends, as progress 0..1 of the action: phone out, chopping, card for straw, sniffing, head back, phone away. */
    static final float OUT = 0.12f, CHOP = 0.45f, SWAP = 0.55f, SNIFF = 0.8f, BACK = 0.88f;
    /** The moment the line goes up the nose (the server doses then too). */
    private static final float SNIFFED = DrugPose.SNIFF_AT;
    /** The view the player had before their own line; the camera turns round to watch it. */
    private static net.minecraft.client.CameraType before;

    private static final Set<DrugPose.Action> HEARD = Collections.newSetFromMap(new WeakHashMap<>());

    private SniffProps(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new SniffProps(renderer));
        }
    }

    /** Client tick: the sniff and the puff of powder, once per line, for everyone in sight. */
    static void tick(Minecraft mc) {
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        // Your own line: the camera swings round to the front to watch it (the body is not drawn
        // in first person), and back to how it was once the phone is away.
        DrugPose.Action own = mc.player == null ? null : DrugPose.ACTING.get(mc.player.getId());
        boolean watching = own != null && own.kind() == DrugPose.SNIFF && own.progress(now) < 0.97f
            && net.neoforged.fml.ModList.get().isLoaded("playeranimator");
        if (watching && before == null) {
            before = mc.options.getCameraType();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (!watching && before != null) {
            mc.options.setCameraType(before);
            before = null;
        }
        DrugPose.ACTING.forEach((id, action) -> {
            if (action.kind() != DrugPose.SNIFF || action.progress(now) < SNIFFED || !HEARD.add(action)) return;
            Entity entity = mc.level.getEntity(id);
            if (entity == null) return;
            mc.level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), ModSounds.SNIFF.get(), SoundSource.PLAYERS,
                1f, 0.9f + entity.getRandom().nextFloat() * 0.2f, false);
            Vec3 look = entity.getLookAngle();
            for (int i = 0; i < 6; i++) {
                mc.level.addParticle(ModParticles.POWDER.get(), entity.getX() + look.x * 0.3, entity.getEyeY() - 0.15,
                    entity.getZ() + look.z * 0.3, (entity.getRandom().nextFloat() - 0.5f) * 0.02f, 0.01, (entity.getRandom().nextFloat() - 0.5f) * 0.02f);
            }
        });
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        DrugPose.Action action = DrugPose.ACTING.get(player.getId());
        if (action == null || action.kind() != DrugPose.SNIFF || player.isInvisible()) return;
        float p = action.progress(System.currentTimeMillis());
        if (p < 0.04f || p > 0.96f) return; // still in the pocket
        float lit = Math.max(LightTexture.block(light), LightTexture.sky(light)) / 15f * 0.85f + 0.15f;
        VertexConsumer v = buffers.getBuffer(RenderType.debugQuads());

        // In arm space, in blocks: the arm hangs along +y from the shoulder, the hand ends at
        // y = 0.625, and -z is the side that faces up once the arm is raised forward.
        pose.pushPose();
        getParentModel().leftArm.translateAndRotate(pose);
        Matrix4f m = pose.last().pose();
        // The phone: dark, flat in the palm, reaching past the fingers.
        box(v, m, -0.04f, 0.45f, -0.175f, 0.2f, 0.82f, -0.13f, 0.1f, 0.1f, 0.12f, lit);
        // The screen, lit on its own, and the line on it (both faces: whichever turns up).
        for (float z : new float[] {-0.176f, -0.129f}) {
            quad(v, m, -0.025f, 0.47f, 0.185f, 0.8f, z, 0.25f, 0.4f, 0.75f, 0.6f + 0.4f * lit);
            float left = p < SNIFFED - 0.02f ? 1f : 1f - Math.min(1f, (p - SNIFFED + 0.02f) / 0.06f);
            if (left > 0f && p > OUT) quad(v, m, 0.065f, 0.52f, 0.095f, 0.52f + 0.22f * left, z + (z < -0.15f ? -0.001f : 0.001f), 0.95f, 0.95f, 0.95f, 1f);
        }
        pose.popPose();

        pose.pushPose();
        getParentModel().rightArm.translateAndRotate(pose);
        m = pose.last().pose();
        if (p > OUT && p < CHOP) {
            // The card, a thin edge held in the fingers.
            box(v, m, -0.13f, 0.6f, -0.14f, 0.06f, 0.74f, -0.13f, 0.15f, 0.35f, 0.8f, lit);
        } else if (p > SWAP - 0.03f && p < BACK) {
            // The straw: a thin silver tube out of the fist.
            box(v, m, -0.05f, 0.58f, -0.15f, -0.025f, 0.84f, -0.125f, 0.8f, 0.82f, 0.85f, lit);
        }
        pose.popPose();
    }

    /** A box from (x0, y0, z0) to (x1, y1, z1), each face shaded a little differently. */
    private static void box(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float b, float lit) {
        quad(v, m, x0, y0, x1, y1, z0, r, g, b, lit);
        quad(v, m, x0, y0, x1, y1, z1, r, g, b, lit * 0.8f);
        float s = lit * 0.9f;
        vertex(v, m, x0, y0, z0, r, g, b, s); vertex(v, m, x0, y1, z0, r, g, b, s); vertex(v, m, x0, y1, z1, r, g, b, s); vertex(v, m, x0, y0, z1, r, g, b, s);
        vertex(v, m, x1, y0, z0, r, g, b, s); vertex(v, m, x1, y1, z0, r, g, b, s); vertex(v, m, x1, y1, z1, r, g, b, s); vertex(v, m, x1, y0, z1, r, g, b, s);
        s = lit * 0.7f;
        vertex(v, m, x0, y0, z0, r, g, b, s); vertex(v, m, x1, y0, z0, r, g, b, s); vertex(v, m, x1, y0, z1, r, g, b, s); vertex(v, m, x0, y0, z1, r, g, b, s);
        vertex(v, m, x0, y1, z0, r, g, b, s); vertex(v, m, x1, y1, z0, r, g, b, s); vertex(v, m, x1, y1, z1, r, g, b, s); vertex(v, m, x0, y1, z1, r, g, b, s);
    }

    private static void quad(VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, float z,
                             float r, float g, float b, float lit) {
        vertex(v, m, x0, y0, z, r, g, b, lit);
        vertex(v, m, x0, y1, z, r, g, b, lit);
        vertex(v, m, x1, y1, z, r, g, b, lit);
        vertex(v, m, x1, y0, z, r, g, b, lit);
    }

    private static void vertex(VertexConsumer v, Matrix4f m, float x, float y, float z, float r, float g, float b, float lit) {
        v.addVertex(m, x, y, z).setColor(r * lit, g * lit, b * lit, 1f);
    }
}
