package com.createbrewery;

import com.createbrewery.effect.ModEffects;
import com.createbrewery.ponder.BreweryPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
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

    /**
     * 1. Camera Sway & Seegang: Roll side-to-side, pitch nodding, and wandering yaw gaze.
     */
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.isPaused()) {
            boolean hasStumble = mc.player.hasEffect(ModEffects.STUMBLE);
            boolean hasHangover = mc.player.hasEffect(ModEffects.HANGOVER);
            boolean hasDelirium = mc.player.hasEffect(ModEffects.DELIRIUM);

            if (hasStumble || hasHangover || hasDelirium) {
                float intensity = 1.0f;
                if (hasStumble) intensity += 0.5f;
                if (hasDelirium) intensity += 0.5f;

                float tick = mc.player.tickCount + (float) event.getPartialTick();
                // Roll sway (ocean ship rolling)
                float roll = (float) Math.sin(tick * 0.06f) * (8.5f * intensity);
                event.setRoll(event.getRoll() + roll);

                // Drowsy pitch nodding
                float pitchDrift = (float) Math.sin(tick * 0.04f) * (2.5f * intensity);
                event.setPitch(event.getPitch() + pitchDrift);

                // Wandering eyes yaw drift
                float yawDrift = (float) Math.cos(tick * 0.035f) * (3.2f * intensity);
                event.setYaw(event.getYaw() + yawDrift);
            }
        }
    }

    /**
     * 2. Dynamic FOV Pulsing: Throbbing tunnel vision (like headache pressure / adrenaline).
     */
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            boolean hasStumble = player.hasEffect(ModEffects.STUMBLE);
            boolean hasHangover = player.hasEffect(ModEffects.HANGOVER);
            boolean hasDelirium = player.hasEffect(ModEffects.DELIRIUM);
            boolean hasHiccups = player.hasEffect(ModEffects.HICCUPS);

            if (hasStumble || hasHangover || hasDelirium || hasHiccups) {
                float tick = player.tickCount;
                float pulse = (float) Math.sin(tick * 0.08f) * 0.12f;
                event.setNewFovModifier(event.getNewFovModifier() + pulse);
            }
        }
    }

    /**
     * 3. Post-Processing Blur Shader: Out-of-focus vision when hungover or delirious.
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            boolean needsShader = mc.player.hasEffect(ModEffects.HANGOVER) || mc.player.hasEffect(ModEffects.DELIRIUM);
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
     * 4. Heavy Eyelids & Vignette Overlay: Dark translucent eyelids drooping down from top and bottom.
     */
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui) {
            boolean hasHangover = mc.player.hasEffect(ModEffects.HANGOVER);
            boolean hasStumble = mc.player.hasEffect(ModEffects.STUMBLE);
            boolean hasDelirium = mc.player.hasEffect(ModEffects.DELIRIUM);

            if (hasHangover || hasStumble || hasDelirium) {
                GuiGraphics graphics = event.getGuiGraphics();
                int width = graphics.guiWidth();
                int height = graphics.guiHeight();

                float tick = mc.player.tickCount;
                // Eyelids breathing/drooping down & up
                float eyelidPhase = (float) ((Math.sin(tick * 0.045f) + 1.0f) * 0.5f);
                int eyelidHeight = (int) (16 + eyelidPhase * 28);

                // Top eyelid (solid dark fading to transparent)
                graphics.fillGradient(0, 0, width, eyelidHeight, 0xD0080402, 0x00080402);
                // Bottom eyelid
                graphics.fillGradient(0, height - eyelidHeight, width, height, 0x00080402, 0xD0080402);
            }
        }
    }
}
