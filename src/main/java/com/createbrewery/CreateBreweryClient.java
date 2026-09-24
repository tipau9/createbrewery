package com.createbrewery;

import com.createbrewery.effect.ModEffects;
import com.createbrewery.ponder.BreweryPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

public class CreateBreweryClient {
    private static boolean shaderActive = false;
    private static final ResourceLocation BLUR_SHADER = ResourceLocation.withDefaultNamespace("shaders/post/blur.json");

    public static void onClientSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new BreweryPonderPlugin());
        NeoForge.EVENT_BUS.addListener(CreateBreweryClient::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(CreateBreweryClient::onComputeFov);
        NeoForge.EVENT_BUS.addListener(CreateBreweryClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(CreateBreweryClient::onRenderGui);
    }

    private static int getIntoxicationStage(Player player) {
        MobEffectInstance inebriation = player.getEffect(ModEffects.INEBRIATION);
        if (inebriation != null) {
            return inebriation.getAmplifier() + 1; // 1, 2, 3, 4+
        }
        if (player.hasEffect(ModEffects.HANGOVER)) return 4;
        if (player.hasEffect(ModEffects.DELIRIUM)) return 3;
        if (player.hasEffect(ModEffects.STUMBLE)) return 2;
        if (player.hasEffect(ModEffects.HICCUPS)) return 1;
        return 0;
    }

    /**
     * 1. Camera Sway & Seegang: Scales progressively with each beer!
     */
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.isPaused()) {
            int stage = getIntoxicationStage(mc.player);
            if (stage > 0) {
                float tick = mc.player.tickCount + (float) event.getPartialTick();

                // Roll: 2.5 deg (1 beer) up to 14 deg (4+ beers)
                float maxRoll = switch (stage) {
                    case 1 -> 2.8f;
                    case 2 -> 6.0f;
                    case 3 -> 9.5f;
                    default -> 14.0f;
                };
                float roll = (float) Math.sin(tick * 0.055f) * maxRoll;
                event.setRoll(event.getRoll() + roll);

                // Drowsy pitch & yaw drift only starting at Stage 2+
                if (stage >= 2) {
                    float driftFactor = (stage == 2) ? 1.0f : ((stage == 3) ? 2.2f : 3.5f);
                    float pitchDrift = (float) Math.sin(tick * 0.04f) * (1.2f * driftFactor);
                    float yawDrift = (float) Math.cos(tick * 0.035f) * (1.5f * driftFactor);
                    event.setPitch(event.getPitch() + pitchDrift);
                    event.setYaw(event.getYaw() + yawDrift);
                }
            }
        }
    }

    /**
     * 2. Dynamic FOV Pulsing: Throbbing lens zoom, scaling with alcohol level.
     */
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            int stage = getIntoxicationStage(player);
            if (stage > 0) {
                float pulseIntensity = switch (stage) {
                    case 1 -> 0.03f; // barely noticeable
                    case 2 -> 0.07f;
                    case 3 -> 0.12f;
                    default -> 0.17f; // heavy throbbing headache
                };
                float tick = player.tickCount;
                float pulse = (float) Math.sin(tick * 0.08f) * pulseIntensity;
                event.setNewFovModifier(event.getNewFovModifier() + pulse);
            }
        }
    }

    /**
     * 3. Post-Processing Blur Shader: Kicks in at Stage 3 (3. Bier) and Stage 4 (Kater).
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int stage = getIntoxicationStage(mc.player);
            boolean needsShader = (stage >= 3);

            if (needsShader && !shaderActive) {
                mc.gameRenderer.loadEffect(BLUR_SHADER);
                shaderActive = true;
            } else if (!needsShader && shaderActive) {
                mc.gameRenderer.shutdownEffect();
                shaderActive = false;
            }
        } else if (shaderActive) {
            shaderActive = false;
        }
    }

    /**
     * 4. Heavy Eyelids Overlay: Starts drooping gently at Stage 2, deeply at Stage 3 & 4.
     */
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui) {
            int stage = getIntoxicationStage(mc.player);
            if (stage >= 2) {
                GuiGraphics graphics = event.getGuiGraphics();
                int width = graphics.guiWidth();
                int height = graphics.guiHeight();

                float tick = mc.player.tickCount;
                float eyelidPhase = (float) ((Math.sin(tick * 0.045f) + 1.0f) * 0.5f);

                int baseHeight = (stage == 2) ? 10 : ((stage == 3) ? 18 : 28);
                int swayHeight = (stage == 2) ? 12 : ((stage == 3) ? 22 : 35);
                int eyelidHeight = (int) (baseHeight + eyelidPhase * swayHeight);

                // Top eyelid (gradient to transparent)
                graphics.fillGradient(0, 0, width, eyelidHeight, 0xD0080402, 0x00080402);
                // Bottom eyelid
                graphics.fillGradient(0, height - eyelidHeight, width, height, 0x00080402, 0xD0080402);
            }
        }
    }
}
