package com.createbrewery.drunk;

import com.createbrewery.ModItems;
import com.createbrewery.drugs.DrugPose;
import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * Ultra-detailed first-person sniffing choreography:
 * 1. Left hand brings out smartphone (dark body, glowing screen).
 * 2. Right hand brings out Ziploc baggie, tilts over screen, taps/pours powder -> round powder pile forms.
 * 3. Right hand brings out credit card, chops 3x across screen -> pile morphs into a clean straight line.
 * 4. Right hand brings out straw/rolled bill, tip touches line start -> sniffs along the line!
 *    Straw glides smoothly along the line, line progressively disappears behind it, sniff sound plays, particles puff!
 * 5. Straw and phone are tucked away back into pockets. Only then does the high kick in!
 */
public final class SniffFirstPerson {
    private SniffFirstPerson() {}

    private static DrugPose.Action lastAction = null;
    private static boolean soundPlayed = false;
    private static int lastParticleTick = -1;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(SniffFirstPerson::onRenderHand);
    }

    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.getCameraType() != CameraType.FIRST_PERSON) return;

        DrugPose.Action action = DrugPose.ACTING.get(player.getId());
        if (action == null || action.kind() != DrugPose.SNIFF) {
            lastAction = null;
            soundPlayed = false;
            return;
        }

        // Cancel default hand rendering so vanilla floating hand/held item doesn't interfere
        event.setCanceled(true);

        // Render entire 2-handed scene once during MAIN_HAND event
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            renderScene(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), player, action);
        }
    }

    private static void renderScene(PoseStack pose, MultiBufferSource buffers, int light,
                                    LocalPlayer player, DrugPose.Action action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = System.currentTimeMillis();
        float p = action.progress(now);
        if (p < 0.01f || p > 0.99f) return;

        if (action != lastAction) {
            lastAction = action;
            soundPlayed = false;
        }

        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        float lit = Math.max(LightTexture.block(light), LightTexture.sky(light)) / 15f * 0.85f + 0.15f;

        // Line endpoints on the phone screen (phone coordinates)
        final float LINE_X = 0.012f;
        final float LINE_Y0 = -0.065f;
        final float LINE_Y1 = 0.065f;

        // -------------------------------------------------------------
        // Sound and Particles
        // -------------------------------------------------------------
        // Powder sprinkle particles during pouring (Phase 2)
        if (p >= DrugPose.SNIFF_POUR_START + 0.02f && p <= DrugPose.SNIFF_POUR_END - 0.01f) {
            if (player.tickCount != lastParticleTick && player.getRandom().nextFloat() < 0.45f) {
                lastParticleTick = player.tickCount;
                var look = player.getLookAngle();
                mc.level.addParticle(ModParticles.POWDER.get(),
                    player.getX() + look.x * 0.38 + (player.getRandom().nextFloat() - 0.5) * 0.03,
                    player.getEyeY() - 0.22,
                    player.getZ() + look.z * 0.38 + (player.getRandom().nextFloat() - 0.5) * 0.03,
                    0, -0.01, 0);
            }
        }

        // Sniff sound and straw particle puffs (Phase 4)
        if (p >= DrugPose.SNIFF_LINE_START && p <= DrugPose.SNIFF_LINE_END) {
            if (!soundPlayed) {
                soundPlayed = true;
                mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    ModSounds.SNIFF.get(), SoundSource.PLAYERS, 1.0f, 0.95f + player.getRandom().nextFloat() * 0.1f, false);
            }
            if (player.tickCount != lastParticleTick) {
                lastParticleTick = player.tickCount;
                var look = player.getLookAngle();
                for (int i = 0; i < 2; i++) {
                    mc.level.addParticle(ModParticles.POWDER.get(),
                        player.getX() + look.x * 0.35 + (player.getRandom().nextFloat() - 0.5) * 0.04,
                        player.getEyeY() - 0.26 + (player.getRandom().nextFloat() - 0.5) * 0.02,
                        player.getZ() + look.z * 0.35 + (player.getRandom().nextFloat() - 0.5) * 0.04,
                        (player.getRandom().nextFloat() - 0.5) * 0.01, 0.015, (player.getRandom().nextFloat() - 0.5) * 0.01);
                }
            }
        }

        // -------------------------------------------------------------
        // Easing calculations
        // -------------------------------------------------------------
        // Left hand / Phone entry and exit easing
        float phoneEnter = Mth.clamp(p / 0.12f, 0f, 1f);
        phoneEnter = phoneEnter * phoneEnter * (3f - 2f * phoneEnter); // smoothstep
        float phoneExit = p < DrugPose.SNIFF_TUCK ? 1f : Mth.clamp(1f - (p - DrugPose.SNIFF_TUCK) / (1f - DrugPose.SNIFF_TUCK), 0f, 1f);
        phoneExit = phoneExit * phoneExit * (3f - 2f * phoneExit);
        float phoneVisibility = Math.min(phoneEnter, phoneExit);
        float phoneYOffset = (1f - phoneVisibility) * -0.65f;

        // -------------------------------------------------------------
        // 1. RENDER LEFT HAND & PHONE
        // -------------------------------------------------------------
        pose.pushPose();
        // Position phone comfortable in front-left of view
        pose.translate(-0.12f, -0.22f + phoneYOffset, -0.38f);
        // Tilt phone up towards player's eyes
        pose.mulPose(Axis.XP.rotationDegrees(38f));
        pose.mulPose(Axis.YP.rotationDegrees(14f));
        pose.mulPose(Axis.ZP.rotationDegrees(-6f));

        // Draw Left Arm holding phone
        pose.pushPose();
        pose.translate(-0.12f, -0.25f, 0.05f);
        pose.mulPose(Axis.XP.rotationDegrees(20f));
        pose.mulPose(Axis.YP.rotationDegrees(-25f));
        pose.mulPose(Axis.ZP.rotationDegrees(15f));
        pose.scale(0.85f, 0.85f, 0.85f);
        renderer.renderLeftHand(pose, buffers, light, player);
        pose.popPose();

        // Draw Phone Body & Screen (fetch quads AFTER arm rendering ends its batch)
        VertexConsumer phoneQuads = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f pm = pose.last().pose();
        // Phone chassis (metallic dark titanium slate)
        box(phoneQuads, pm, -0.075f, -0.13f, -0.006f, 0.075f, 0.13f, 0.006f, 0.11f, 0.11f, 0.13f, lit);
        // Phone bevel highlight rim
        box(phoneQuads, pm, -0.073f, -0.128f, -0.007f, 0.073f, 0.128f, -0.006f, 0.22f, 0.22f, 0.25f, lit);
        // AMOLED Screen surface
        quad(phoneQuads, pm, -0.068f, -0.122f, 0.068f, 0.122f, -0.0075f, 0.04f, 0.04f, 0.06f, lit);
        // Notch dot
        quad(phoneQuads, pm, -0.008f, 0.112f, 0.008f, 0.118f, -0.0077f, 0.01f, 0.01f, 0.01f, lit);

        // -------------------------------------------------------------
        // Powder & Line on Phone Screen
        // -------------------------------------------------------------
        float powderZ = -0.0082f;

        // Phase 2: Powder Pile growing from baggie
        if (p >= DrugPose.SNIFF_POUR_START && p < DrugPose.SNIFF_CARD_START) {
            float pourT = Mth.clamp((p - DrugPose.SNIFF_POUR_START) / 0.10f, 0f, 1f);
            float r = 0.024f * pourT;
            // Draw round powder pile
            circle(phoneQuads, pm, LINE_X, 0f, r, powderZ, 0.96f, 0.96f, 0.98f, lit);
            circle(phoneQuads, pm, LINE_X, 0f, r * 0.6f, powderZ - 0.0005f, 1f, 1f, 1f, lit);
        }
        // Phase 3: Card chops pile into a clean line
        else if (p >= DrugPose.SNIFF_CARD_START && p < DrugPose.SNIFF_LINE_START) {
            float chopT = Mth.clamp((p - DrugPose.SNIFF_CARD_START) / (DrugPose.SNIFF_CARD_END - DrugPose.SNIFF_CARD_START), 0f, 1f);
            // Pile morphs from circular to long line
            float halfWidth = Mth.lerp(chopT, 0.022f, 0.0055f);
            float halfLength = Mth.lerp(chopT, 0.022f, (LINE_Y1 - LINE_Y0) * 0.5f);
            float cy = (LINE_Y0 + LINE_Y1) * 0.5f;
            quad(phoneQuads, pm, LINE_X - halfWidth, cy - halfLength, LINE_X + halfWidth, cy + halfLength, powderZ, 0.97f, 0.97f, 0.99f, lit);
        }
        // Phase 4: Sniffing along the line (line shrinks as straw consumes it!)
        else if (p >= DrugPose.SNIFF_LINE_START && p <= DrugPose.SNIFF_LINE_END) {
            float sniffProgress = Mth.clamp((p - DrugPose.SNIFF_LINE_START) / (DrugPose.SNIFF_LINE_END - DrugPose.SNIFF_LINE_START), 0f, 1f);
            float currentY = LINE_Y0 + sniffProgress * (LINE_Y1 - LINE_Y0);
            // Only draw remaining part of the line from currentY to LINE_Y1
            if (currentY < LINE_Y1) {
                quad(phoneQuads, pm, LINE_X - 0.0055f, currentY, LINE_X + 0.0055f, LINE_Y1, powderZ, 0.97f, 0.97f, 0.99f, lit);
            }
        }
        pose.popPose();

        // -------------------------------------------------------------
        // 2. RENDER RIGHT HAND & ACTIVE PROP
        // -------------------------------------------------------------

        // PHASE 2: Ziploc Baggie (Pouring)
        if (p >= 0.12f && p < DrugPose.SNIFF_CARD_START) {
            float bagEnter = Mth.clamp((p - 0.12f) / 0.06f, 0f, 1f);
            float bagExit = p < 0.28f ? 1f : Mth.clamp(1f - (p - 0.28f) / 0.06f, 0f, 1f);
            float bagVis = Math.min(bagEnter, bagExit);
            float bagYOff = (1f - bagVis) * -0.55f;

            // Tilt & Shake over phone
            boolean pouring = p >= DrugPose.SNIFF_POUR_START && p <= DrugPose.SNIFF_POUR_END;
            float shake = pouring ? Mth.sin((p - DrugPose.SNIFF_POUR_START) * 65f) * 0.008f : 0f;

            pose.pushPose();
            pose.translate(0.04f + shake, -0.08f + bagYOff + shake, -0.34f);
            pose.mulPose(Axis.XP.rotationDegrees(42f));
            pose.mulPose(Axis.YP.rotationDegrees(-28f));
            pose.mulPose(Axis.ZP.rotationDegrees(-35f + (pouring ? -25f : 0f)));

            // Draw Right Arm holding baggie
            pose.pushPose();
            pose.translate(0.08f, -0.22f, 0.08f);
            pose.mulPose(Axis.XP.rotationDegrees(25f));
            pose.mulPose(Axis.YP.rotationDegrees(20f));
            pose.mulPose(Axis.ZP.rotationDegrees(-15f));
            pose.scale(0.85f, 0.85f, 0.85f);
            renderer.renderRightHand(pose, buffers, light, player);
            pose.popPose();

            // Render Ziploc Baggie item
            pose.pushPose();
            pose.translate(-0.02f, 0.02f, -0.04f);
            pose.scale(0.22f, 0.22f, 0.22f);
            pose.mulPose(Axis.ZP.rotationDegrees(180f));
            mc.getItemRenderer().renderStatic(new ItemStack(ModItems.KOKS.get()),
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, light, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
            pose.popPose();

            pose.popPose();
        }

        // PHASE 3: Credit Card (Chopping into Line)
        else if (p >= DrugPose.SNIFF_CARD_START && p < DrugPose.SNIFF_STRAW_START) {
            float cardEnter = Mth.clamp((p - DrugPose.SNIFF_CARD_START) / 0.05f, 0f, 1f);
            float cardExit = p < DrugPose.SNIFF_CARD_END ? 1f : Mth.clamp(1f - (p - DrugPose.SNIFF_CARD_END) / 0.05f, 0f, 1f);
            float cardVis = Math.min(cardEnter, cardExit);
            float cardYOff = (1f - cardVis) * -0.55f;

            // 3 chopping motions
            float chopCycle = Mth.sin((p - DrugPose.SNIFF_CARD_START) * 38f);
            float chopMotion = Math.max(0f, chopCycle) * 0.025f;

            pose.pushPose();
            pose.translate(0.02f, -0.14f + cardYOff + chopMotion, -0.36f);
            pose.mulPose(Axis.XP.rotationDegrees(35f));
            pose.mulPose(Axis.YP.rotationDegrees(-18f));
            pose.mulPose(Axis.ZP.rotationDegrees(22f));

            // Draw Right Arm holding card
            pose.pushPose();
            pose.translate(0.08f, -0.22f, 0.06f);
            pose.mulPose(Axis.XP.rotationDegrees(20f));
            pose.mulPose(Axis.YP.rotationDegrees(15f));
            pose.mulPose(Axis.ZP.rotationDegrees(-10f));
            pose.scale(0.85f, 0.85f, 0.85f);
            renderer.renderRightHand(pose, buffers, light, player);
            pose.popPose();

            // Draw Credit Card prop (Navy blue card with gold stripe & gold chip)
            VertexConsumer cardQuads = buffers.getBuffer(RenderType.debugQuads());
            Matrix4f cm = pose.last().pose();
            box(cardQuads, cm, -0.045f, -0.005f, -0.028f, 0.045f, 0.005f, 0.028f, 0.12f, 0.22f, 0.45f, lit);
            // Magnetic stripe / gold chip
            box(cardQuads, cm, -0.046f, -0.006f, -0.01f, 0.046f, 0.006f, 0.005f, 0.85f, 0.72f, 0.22f, lit);
            pose.popPose();
        }

        // PHASE 4: Straw / Rolled Bill (Tracking along the Line & Snorting)
        else if (p >= DrugPose.SNIFF_STRAW_START && p < DrugPose.SNIFF_TUCK) {
            float strawEnter = Mth.clamp((p - DrugPose.SNIFF_STRAW_START) / 0.05f, 0f, 1f);
            float strawExit = p < 0.82f ? 1f : Mth.clamp(1f - (p - 0.82f) / 0.05f, 0f, 1f);
            float strawVis = Math.min(strawEnter, strawExit);
            float strawYOff = (1f - strawVis) * -0.55f;

            // Travel along the line in sync with sniffing progress
            float sniffProgress = Mth.clamp((p - DrugPose.SNIFF_LINE_START) / (DrugPose.SNIFF_LINE_END - DrugPose.SNIFF_LINE_START), 0f, 1f);
            float currentLineY = LINE_Y0 + sniffProgress * (LINE_Y1 - LINE_Y0);

            // First-person straw tracking coordinates (world space in front of phone)
            float strawTravelX = LINE_X * 0.9f;
            float strawTravelY = currentLineY * 0.8f;

            pose.pushPose();
            // Align straw right above phone screen and sweep along the line
            pose.translate(-0.06f + strawTravelX, -0.20f + strawTravelY + strawYOff, -0.37f);
            // Angled from player's face down into the phone screen
            pose.mulPose(Axis.XP.rotationDegrees(48f));
            pose.mulPose(Axis.YP.rotationDegrees(12f));
            pose.mulPose(Axis.ZP.rotationDegrees(-15f));

            // Draw Right Arm holding straw
            pose.pushPose();
            pose.translate(0.08f, -0.22f, 0.08f);
            pose.mulPose(Axis.XP.rotationDegrees(30f));
            pose.mulPose(Axis.YP.rotationDegrees(18f));
            pose.mulPose(Axis.ZP.rotationDegrees(-12f));
            pose.scale(0.85f, 0.85f, 0.85f);
            renderer.renderRightHand(pose, buffers, light, player);
            pose.popPose();

            // Draw Straw Prop (Silver cylinder / rolled banknote tube)
            VertexConsumer strawQuads = buffers.getBuffer(RenderType.debugQuads());
            Matrix4f sm = pose.last().pose();
            cylinder(strawQuads, sm, 0f, -0.07f, 0.045f, 0.0075f, 0.12f, 0.82f, 0.85f, 0.88f, lit);
            // Inner hollow tip
            cylinder(strawQuads, sm, 0f, -0.071f, 0.045f, 0.0055f, 0.004f, 0.2f, 0.2f, 0.25f, lit);

            pose.popPose();
        }
    }

    // -------------------------------------------------------------
    // Primitive Geometry Helpers
    // -------------------------------------------------------------
    private static void box(VertexConsumer v, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float b, float lit) {
        quad(v, m, x0, y0, x1, y1, z0, r, g, b, lit);
        quad(v, m, x0, y0, x1, y1, z1, r, g, b, lit * 0.85f);
        float s = lit * 0.9f;
        vertex(v, m, x0, y0, z0, r, g, b, s); vertex(v, m, x0, y1, z0, r, g, b, s); vertex(v, m, x0, y1, z1, r, g, b, s); vertex(v, m, x0, y0, z1, r, g, b, s);
        vertex(v, m, x1, y0, z0, r, g, b, s); vertex(v, m, x1, y1, z0, r, g, b, s); vertex(v, m, x1, y1, z1, r, g, b, s); vertex(v, m, x1, y0, z1, r, g, b, s);
        s = lit * 0.75f;
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

    private static void circle(VertexConsumer v, Matrix4f m, float cx, float cy, float radius, float z,
                               float r, float g, float b, float lit) {
        int segments = 12;
        float step = (float) (Math.PI * 2.0 / segments);
        for (int i = 0; i < segments; i += 2) {
            float a0 = i * step;
            float a1 = (i + 1) * step;
            float a2 = (i + 2) * step;
            vertex(v, m, cx, cy, z, r, g, b, lit);
            vertex(v, m, cx + Mth.cos(a0) * radius, cy + Mth.sin(a0) * radius, z, r, g, b, lit);
            vertex(v, m, cx + Mth.cos(a1) * radius, cy + Mth.sin(a1) * radius, z, r, g, b, lit);
            vertex(v, m, cx + Mth.cos(a2) * radius, cy + Mth.sin(a2) * radius, z, r, g, b, lit);
        }
    }

    private static void cylinder(VertexConsumer v, Matrix4f m, float cx, float cy, float cz,
                                 float radius, float height, float r, float g, float b, float lit) {
        int segments = 8;
        float step = (float) (Math.PI * 2.0 / segments);
        for (int i = 0; i < segments; i++) {
            float a0 = i * step;
            float a1 = (i + 1) * step;
            float x0 = cx + Mth.cos(a0) * radius;
            float z0 = cz + Mth.sin(a0) * radius;
            float x1 = cx + Mth.cos(a1) * radius;
            float z1 = cz + Mth.sin(a1) * radius;
            float sideLit = lit * (0.75f + 0.25f * Mth.cos(a0));
            vertex(v, m, x0, cy, z0, r, g, b, sideLit);
            vertex(v, m, x0, cy + height, z0, r, g, b, sideLit);
            vertex(v, m, x1, cy + height, z1, r, g, b, sideLit);
            vertex(v, m, x1, cy, z1, r, g, b, sideLit);
        }
    }

    private static void vertex(VertexConsumer v, Matrix4f m, float x, float y, float z, float r, float g, float b, float lit) {
        v.addVertex(m, x, y, z).setColor(r * lit, g * lit, b * lit, 1f);
    }
}
