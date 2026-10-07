package com.createbrewery.drunk;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drugs.DrugServer;
import com.createbrewery.drugs.Hallucinations;
import com.createbrewery.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Handles all hallucinatory visual and auditory phenomena on the client:
 * - Paranoia footsteps creeping up from behind and auditory phantoms
 * - Peripheral shadow figures in the corner of the eye
 * - Closed-eye visionary patterns (CEVs)
 * - Level stage rendering of hallucinations (GeckoLib shadow people, crawlers, machine elves)
 */
public final class HallucinationClient {
    private HallucinationClient() {}

    public static final ResourceLocation SHADOW_FIGURE =
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "textures/misc/shadow_figure.png");

    // Footsteps & auditory phantoms
    static int nextFootstep = 400;
    static int nextPhantomStep = 0;
    private static Vec3 phantomAt;
    private static float phantomYaw;
    private static int phantomSteps;
    private static Phantom phantomSound;

    // Peripheral shadow figure
    private static float shadowYaw, shadowAlpha;
    private static int shadowAge = -1;
    private static boolean shadowGoing;

    // Closed-eye visions (CEVs)
    static float eyes;
    private static int stillTicks;
    private static boolean eyesHinted, eyesSeen;

    public static void reset() {
        nextFootstep = 400;
        nextPhantomStep = 0;
        phantomAt = null;
        phantomSound = null;
        shadowAge = -1;
        shadowAlpha = 0f;
        shadowGoing = false;
        stillTicks = 0;
        eyesHinted = false;
        eyesSeen = false;
        eyes = 0f;
    }

    private static final class Phantom extends SimpleSoundInstance {
        Phantom(net.minecraft.sounds.SoundEvent sound, SoundSource source, float volume, float pitch, LocalPlayer player, Vec3 at) {
            super(sound, source, volume, pitch, player.getRandom(), at.x, at.y, at.z);
        }
    }

    public static void tick(Minecraft mc, LocalPlayer player) {
        paranoia(mc, player);
        closedEyes(mc, player);
    }

    /**
     * Paranoia: from the second joint, with Koks, high in the dark, or in a bad trip. Footsteps creep up from
     * behind, or something hisses - nobody is there, and it stops the moment you turn round.
     * In the dark a figure stands at the edge of the view, and dissolves when you look at it.
     */
    public static void paranoia(Minecraft mc, LocalPlayer player) {
        boolean dark = DrunkClient.dark(player);
        boolean paranoid = player.hasEffect(ModEffects.WEED_HIGH)
            && (DrugServer.joints(player) > 1f || player.hasEffect(ModEffects.COKE_HIGH) || (dark && DrunkClient.high > 0.3f))
            || DrunkClient.bad > 0.3f;
        if (phantomAt != null) {
            if (Math.abs(Mth.wrapDegrees(player.getYRot() - phantomYaw)) > 110f) {
                if (phantomSound != null) mc.getSoundManager().stop(phantomSound);
                phantomAt = null;
            } else if (phantomSteps > 0 && player.tickCount >= nextPhantomStep) {
                var below = net.minecraft.core.BlockPos.containing(phantomAt).below();
                var step = player.level().getBlockState(below).getSoundType().getStepSound();
                phantomSound = new Phantom(step, SoundSource.PLAYERS, 0.35f, 0.85f + player.getRandom().nextFloat() * 0.15f, player, phantomAt);
                mc.getSoundManager().play(phantomSound);
                phantomAt = phantomAt.add(player.position().subtract(phantomAt).normalize().scale(0.35));
                nextPhantomStep = player.tickCount + 9 + player.getRandom().nextInt(4);
                if (--phantomSteps == 0) phantomAt = null;
            } else if (phantomSteps == 0 && phantomSound != null && !mc.getSoundManager().isActive(phantomSound)) {
                phantomAt = null;
            }
        }
        if (paranoid && phantomAt == null && player.tickCount >= nextFootstep) {
            Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
            phantomAt = player.position().subtract(look.scale(4.0));
            phantomYaw = player.getYRot();
            if (player.getRandom().nextFloat() < 0.25f) {
                phantomSteps = 0;
                phantomSound = new Phantom(SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 0.15f, 0.5f, player,
                    player.position().subtract(look.scale(2.0)));
                mc.getSoundManager().play(phantomSound);
            } else {
                phantomSteps = 3 + player.getRandom().nextInt(3);
                nextPhantomStep = player.tickCount;
            }
            nextFootstep = player.tickCount + (dark ? 200 : 300) + player.getRandom().nextInt(dark ? 300 : 500);
        } else if (!paranoid) {
            nextFootstep = Math.max(nextFootstep, player.tickCount + 200);
        }

        if (shadowAge < 0 && paranoid && dark && player.getRandom().nextFloat() < 1f / 700f
            && !(Hallucinations.GEO && Hallucinations.shadow(player, 62f))) {
            shadowYaw = player.getYRot() + (player.getRandom().nextBoolean() ? 62f : -62f);
            shadowAge = 0;
            shadowGoing = false;
        }
        if (shadowAge >= 0) {
            shadowAge++;
            float rel = Mth.wrapDegrees(shadowYaw - player.getYRot());
            if (Math.abs(rel) < 40f || Math.abs(rel) > 100f || shadowAge > 140 || !paranoid) shadowGoing = true;
            shadowAlpha = shadowGoing ? shadowAlpha - 1f / 12f : Math.min(1f, shadowAlpha + 1f / 30f);
            if (shadowGoing && shadowAlpha <= 0f) {
                shadowAlpha = 0f;
                shadowAge = -1;
            }
        }
    }

    public static void drawShadow(LocalPlayer player, GuiGraphics g, int w, int h) {
        if (shadowAge < 0 || shadowAlpha <= 0f) return;
        float rel = Mth.wrapDegrees(shadowYaw - player.getYRot());
        int fh = (int) (h * 0.55f), fw = fh / 2;
        int x = rel > 0 ? w - fw * 2 / 3 : -fw / 3;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, shadowAlpha * 0.5f * DrunkClient.screen());
        g.blit(SHADOW_FIGURE, x, (h - fh) / 2 + h / 12, fw, fh, 0f, 0f, 64, 128, 64, 128);
        g.setColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    public static void closedEyes(Minecraft mc, LocalPlayer player) {
        boolean still = player.isShiftKeyDown() && player.input.forwardImpulse == 0f && player.input.leftImpulse == 0f
            && mc.screen == null;
        stillTicks = still ? stillTicks + 1 : 0;
        eyes += ((DrunkClient.trip > 0.15f && stillTicks > 30 ? 1f : 0f) - eyes) * 0.15f;
        if (DrunkClient.trip < 0.05f) eyesHinted = eyesSeen = false;
        if (DrunkClient.trip > 0.35f && !eyesHinted && player.tickCount % 200 == 0) {
            eyesHinted = true;
            player.displayClientMessage(Component.translatable("createbrewery.thought.trip.eyes_hint")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GREEN), true);
        }
        if (eyes > 0.9f && !eyesSeen) {
            eyesSeen = true;
            player.displayClientMessage(Component.translatable("createbrewery.thought.trip.eyes_seen")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GREEN), true);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Hallucinations.render(event);
    }
}
