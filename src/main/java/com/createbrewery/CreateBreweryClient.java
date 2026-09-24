package com.createbrewery;

import com.createbrewery.effect.ModEffects;
import com.createbrewery.ponder.BreweryPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

public class CreateBreweryClient {
    public static void onClientSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new BreweryPonderPlugin());
        NeoForge.EVENT_BUS.addListener(CreateBreweryClient::onComputeCameraAngles);
    }

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
                float roll = (float) Math.sin(tick * 0.06f) * (8.0f * intensity);
                event.setRoll(event.getRoll() + roll);
            }
        }
    }
}
