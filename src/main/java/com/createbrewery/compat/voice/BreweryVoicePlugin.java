package com.createbrewery.compat.voice;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drunk.VoiceFx;
import com.mojang.logging.LogUtils;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/**
 * Simple Voice Chat (found by its annotation, only when it is installed): your microphone goes
 * through {@link VoiceFx} before it is sent, so the others hear the state you are in.
 */
@ForgeVoicechatPlugin
public class BreweryVoicePlugin implements VoicechatPlugin {
    private final VoiceFx fx = new VoiceFx();

    @Override
    public String getPluginId() {
        return CreateBrewery.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        LogUtils.getLogger().info("Drug voice: Simple Voice Chat on");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientSoundEvent.class, event -> {
            short[] pcm = event.getRawAudio();
            fx.process(pcm, VoiceFx.params);
            event.setRawAudio(pcm);
        });
    }
}
