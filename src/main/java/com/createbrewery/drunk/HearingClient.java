package com.createbrewery.drunk;

import com.createbrewery.compat.CuriosCompat;
import com.createbrewery.effect.ModEffects;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import java.nio.ShortBuffer;

/**
 * Your ears, on the client. Measures the bass you stand in (from {@link MusicPulse#subBass}) and
 * sends it to the server once a second ({@link HearingDose}); while the server says your ears ring
 * ({@link ModEffects#TINNITUS}) everything sounds dull - the club's speakers through a real 800 Hz
 * low-pass ({@link Emitter}), the game's own sounds through OpenAL's filter - and a quiet 6 kHz
 * whistle plays in your head. Earplugs make it all 15 % quieter and keep the dose and the crackle
 * of overdriven boxes out.
 */
public final class HearingClient {
    private HearingClient() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 0..1: comes on over a second, goes over two. Read on the sound thread too. */
    static volatile float tinnitus;
    /** True while the local player wears earplugs. */
    static volatile boolean plugs;
    private static boolean wasPlugged, failed;
    private static float taken;
    private static int ticks;
    private static long context;
    /** The whistle's source and buffer, and the dull filter for game sounds (without Sound Physics); -1 none. */
    private static int ring = -1, ringBuffer = -1;
    private static volatile int filter = -1;

    static void init() {
        NeoForge.EVENT_BUS.addListener(HearingClient::onTick);
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean ringing = player != null && player.hasEffect(ModEffects.TINNITUS);
        tinnitus = Math.max(0f, Math.min(1f, tinnitus + (ringing ? 0.05f : -0.025f)));
        plugs = player != null && CuriosCompat.earplugs(player);
        if (player != null && !mc.isPaused() && !plugs) taken += MusicPulse.subBass() / 20f;
        if (++ticks >= 20) {
            ticks = 0;
            if (taken > 0.001f && mc.getConnection() != null && mc.getConnection().hasChannel(HearingDose.TYPE)) {
                PacketDistributor.sendToServer(new HearingDose(taken));
            }
            taken = 0f;
        }
        if (failed) return;
        try {
            audio(mc);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.warn("Tinnitus audio unavailable", e);
        }
    }

    private static void audio(Minecraft mc) {
        long now = ALC10.alcGetCurrentContext();
        if (now == 0L) return;
        // A new context (another device, a sound reload): what was made died with the old one.
        if (now != context) {
            context = now;
            ring = ringBuffer = filter = -1;
            if (!DrugAudio.PHYSICS && ALC10.alcIsExtensionPresent(ALC10.alcGetContextsDevice(now), "ALC_EXT_EFX")) {
                int f = EXTEfx.alGenFilters();
                EXTEfx.alFilteri(f, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
                filter = f;
            }
        }
        if (filter >= 0) EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, Hearing.gainHf(tinnitus));

        // The game sets the listener's gain to the master volume when that slider moves; plugged, it is set here each tick.
        float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        if (plugs) AL10.alListenerf(AL10.AL_GAIN, master * Hearing.PLUGGED);
        else if (wasPlugged) AL10.alListenerf(AL10.AL_GAIN, master);
        wasPlugged = plugs;

        float gain = tinnitus * Hearing.RING_GAIN;
        if (gain > 0f && (ring < 0 || !AL10.alIsSource(ring))) makeRing();
        if (ring < 0) return;
        AL10.alSourcef(ring, AL10.AL_GAIN, gain);
        boolean on = AL10.alGetSourcei(ring, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
        if (gain > 0f && !on) AL10.alSourcePlay(ring);
        else if (gain <= 0f && on) AL10.alSourceStop(ring);
    }

    /** The whistle: one second looped, in your head, the same wherever you look. */
    private static void makeRing() {
        int rate = 48000;
        short[] samples = Hearing.ring(rate);
        ShortBuffer pcm = MemoryUtil.memAllocShort(samples.length).put(samples).flip();
        try {
            ringBuffer = AL10.alGenBuffers();
            AL10.alBufferData(ringBuffer, AL10.AL_FORMAT_MONO16, pcm, rate);
        } finally {
            MemoryUtil.memFree(pcm);
        }
        ring = AL10.alGenSources();
        AL10.alSourcei(ring, AL10.AL_BUFFER, ringBuffer);
        AL10.alSourcei(ring, AL10.AL_LOOPING, AL10.AL_TRUE);
        AL10.alSourcei(ring, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(ring, AL10.AL_POSITION, 0f, 0f, 0f);
        AL10.alSourcef(ring, AL10.AL_ROLLOFF_FACTOR, 0f);
    }

    /**
     * Sound thread, as a game sound starts (without Sound Physics, which owns the direct filter and
     * is dulled in its own mixin): ringing ears hear it dull.
     */
    // ponytail: a sound already playing when the ringing starts or stops keeps what it had; re-attach live sources if that is noticed.
    static void attach(int source) {
        int f = filter;
        if (f >= 0) AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, tinnitus > 0.01f ? f : EXTEfx.AL_FILTER_NULL);
    }

    /** For the Sound Physics mixin: what is left of the highs. */
    public static float gainHf() {
        return Hearing.gainHf(tinnitus);
    }
}
