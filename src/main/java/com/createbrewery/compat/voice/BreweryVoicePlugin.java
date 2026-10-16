package com.createbrewery.compat.voice;

import com.createbrewery.CreateBrewery;
import com.createbrewery.Config;
import com.createbrewery.drunk.MusicPulse;
import com.createbrewery.drunk.VoiceFx;
import com.createbrewery.drunk.VoiceMask;
import com.mojang.logging.LogUtils;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import com.createbrewery.block.club.MicrophoneBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple Voice Chat (found by its annotation, only when it is installed): your microphone goes
 * through {@link VoiceFx} before it is sent, so the others hear the state you are in. And whoever
 * talks into a club microphone is heard out of its booth's speakers too. In a loud club, the others
 * have to shout to be heard ({@link VoiceMask}).
 */
@ForgeVoicechatPlugin
public class BreweryVoicePlugin implements VoicechatPlugin {
    private final VoiceFx fx = new VoiceFx();
    /** One channel per talker and speaker (two voices on one channel would garble), reused while they talk. */
    private final Map<String, LocationalAudioChannel> channels = new ConcurrentHashMap<>();
    /** Client: per talker, how the music drowns their voice out. */
    private final Map<UUID, VoiceMask> masks = new ConcurrentHashMap<>();
    /** How far a voice from a club speaker carries, in blocks. */
    private static final float PA_DISTANCE = 48f;

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
        // Players only: a club microphone comes out of the speakers as a locational sound and is meant to cut through.
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, event -> {
            short[] pcm = event.getRawAudio();
            double distance = MusicPulse.distanceTo(event.getEntityId());
            if (pcm == null || distance < 0) return;
            float sensitivity = Config.CLIENT_SPEC.isLoaded() ? Config.VOICE_MASKING.get().floatValue() : 1f;
            masks.computeIfAbsent(event.getEntityId(), id -> new VoiceMask()).process(pcm, MusicPulse.heardLevel(), distance, sensitivity);
            event.setRawAudio(pcm);
        });
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophone);
        // A new voice chat server (a world reopened): the old channels and routes belong to the last one.
        registration.registerEvent(de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent.class, event -> {
            channels.clear();
            MicrophoneBlockEntity.ROUTES.clear();
        });
    }

    /** Voice chat thread: at a mic, the voice also goes out of the booth's speakers. */
    private void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null) return;
        UUID talker = sender.getPlayer().getUuid();
        MicrophoneBlockEntity.Route route = MicrophoneBlockEntity.ROUTES.get(talker);
        if (route == null) return;
        if (route.until() < System.currentTimeMillis()) {
            // Walked away from the mic: let go of the channels.
            MicrophoneBlockEntity.ROUTES.remove(talker, route);
            channels.entrySet().removeIf(e -> {
                if (e.getKey().startsWith(talker.toString())) {
                    e.getValue().flush();
                    return true;
                }
                return false;
            });
            return;
        }
        var api = event.getVoicechat();
        byte[] opus = event.getPacket().getOpusEncodedData();
        for (Vec3 speaker : route.speakers()) {
            String key = talker + "@" + route.level().dimension().location() + "@" + speaker;
            channels.computeIfPresent(key, (k, c) -> c.isClosed() ? null : c);
            LocationalAudioChannel channel = channels.computeIfAbsent(key, k -> {
                LocationalAudioChannel c = api.createLocationalAudioChannel(UUID.randomUUID(), api.fromServerLevel(route.level()),
                    api.createPosition(speaker.x, speaker.y, speaker.z));
                if (c == null) return null;
                c.setDistance(PA_DISTANCE);
                // Real-time stage monitoring: the talker hears their amplified voice from the speakers too
                return c;
            });
            if (channel != null) channel.send(opus);
        }
    }
}
