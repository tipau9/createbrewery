package com.createbrewery.drunk;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;

import java.nio.IntBuffer;

/**
 * Real audio effects for what drugs do to hearing, through OpenAL's effects extension: every sound
 * is also sent through one effect slot, and the slot's effect and loudness follow the strongest
 * drug. Lachgas wah-wahs (autowah), Keta and the DMT waiting room are a vast hall (reverb), the DMT
 * crack rings metallic (ring modulator), LSD flanges, mushrooms chorus, MDMA brings up the bass.
 *
 * <p>Sound Physics Remastered uses sends 0-3 and owns each source's direct filter, so with it we
 * take the first send after its four and leave the direct filter alone. The context is asked for
 * more sends when it is made ({@link #moreSends}); how many there are is logged once.
 */
public final class DrugAudio {
    private DrugAudio() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    /** True with Sound Physics Remastered: it owns sends 0-3 and the direct filter. */
    static final boolean PHYSICS = ModList.get() != null && ModList.get().isLoaded("sound_physics_remastered");
    private static final int WAH = 0, HALL = 1, RING = 2, FLANGE = 3, CHORUS = 4, BASS = 5;
    private static final float[] LOUDNESS = {1f, 1f, 0.7f, 0.8f, 0.8f, 0.6f};

    private static long context;
    private static volatile int slot = -1, send = -1;
    private static int[] effects;
    private static int current = -1;
    private static boolean failed;
    private static IntBuffer attributes;
    private static java.lang.reflect.Field channelSource;

    static void init() {
        NeoForge.EVENT_BUS.addListener(DrugAudio::onSource);
        NeoForge.EVENT_BUS.addListener(DrugAudio::onStream);
    }

    /** From the Library mixin: the context attributes, with room for our send after the others. */
    public static IntBuffer moreSends(IntBuffer in) {
        int[] was = new int[in == null ? 0 : in.remaining()];
        if (in != null) in.duplicate().get(was);
        int[] now = Intoxication.withSends(was, EXTEfx.ALC_MAX_AUXILIARY_SENDS, 6);
        attributes = BufferUtils.createIntBuffer(now.length).put(now).flip();
        return attributes;
    }

    /** Each tick: the drug strengths, 0..1. Sets which effect the slot plays and how loud. */
    static void tick(float wah, float hall, float ring, float lsd, float shrooms, float roll) {
        if (failed) return;
        try {
            long now = ALC10.alcGetCurrentContext();
            if (now == 0L) return;
            // A new context (another device, a sound reload) needs everything made again.
            if (now != context || slot >= 0 && !EXTEfx.alIsAuxiliaryEffectSlot(slot)) setUp(now);
            if (slot < 0) return;
            float[] w = {wah, hall, ring, lsd, shrooms, roll};
            int best = 0;
            for (int i = 1; i < w.length; i++) if (w[i] > w[best]) best = i;
            // Only switch for a clearly stronger drug, so two close ones do not flicker back and forth.
            if (best != current && (current < 0 || w[current] < 0.02f || w[best] > w[current] + 0.1f)) {
                EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effects[best]);
                current = best;
            }
            EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, Math.min(1f, w[current] * LOUDNESS[current]));
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.warn("Drug audio effects unavailable", e);
        }
    }

    private static void setUp(long now) {
        context = now;
        slot = -1;
        current = -1;
        long device = ALC10.alcGetContextsDevice(now);
        if (!ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
            failed = true;
            LOGGER.warn("Drug audio: no ALC_EXT_EFX, effects off");
            return;
        }
        int sends = ALC10.alcGetInteger(device, EXTEfx.ALC_MAX_AUXILIARY_SENDS);
        int free = PHYSICS ? 4 : 0;
        LOGGER.info("Drug audio: {} aux sends, Sound Physics {}", sends, PHYSICS ? "on (sends 0-3 are its)" : "off");
        if (sends <= free) {
            LOGGER.warn("Drug audio: no free aux send, effects off");
            return;
        }
        effects = new int[6];
        effects[WAH] = effect(EXTEfx.AL_EFFECT_AUTOWAH);
        EXTEfx.alEffectf(effects[WAH], EXTEfx.AL_AUTOWAH_ATTACK_TIME, 0.04f);
        EXTEfx.alEffectf(effects[WAH], EXTEfx.AL_AUTOWAH_RELEASE_TIME, 0.25f);
        EXTEfx.alEffectf(effects[WAH], EXTEfx.AL_AUTOWAH_RESONANCE, 900f);
        EXTEfx.alEffectf(effects[WAH], EXTEfx.AL_AUTOWAH_PEAK_GAIN, 20f);
        effects[HALL] = effect(EXTEfx.AL_EFFECT_REVERB);
        EXTEfx.alEffectf(effects[HALL], EXTEfx.AL_REVERB_DECAY_TIME, 9f);
        EXTEfx.alEffectf(effects[HALL], EXTEfx.AL_REVERB_DENSITY, 1f);
        EXTEfx.alEffectf(effects[HALL], EXTEfx.AL_REVERB_DIFFUSION, 1f);
        EXTEfx.alEffectf(effects[HALL], EXTEfx.AL_REVERB_GAIN, 0.6f);
        EXTEfx.alEffectf(effects[HALL], EXTEfx.AL_REVERB_LATE_REVERB_GAIN, 2.5f);
        EXTEfx.alEffectf(effects[HALL], EXTEfx.AL_REVERB_LATE_REVERB_DELAY, 0.09f);
        effects[RING] = effect(EXTEfx.AL_EFFECT_RING_MODULATOR);
        EXTEfx.alEffectf(effects[RING], EXTEfx.AL_RING_MODULATOR_FREQUENCY, 440f);
        EXTEfx.alEffectf(effects[RING], EXTEfx.AL_RING_MODULATOR_HIGHPASS_CUTOFF, 600f);
        effects[FLANGE] = effect(EXTEfx.AL_EFFECT_FLANGER);
        EXTEfx.alEffectf(effects[FLANGE], EXTEfx.AL_FLANGER_RATE, 0.2f);
        EXTEfx.alEffectf(effects[FLANGE], EXTEfx.AL_FLANGER_DEPTH, 1f);
        EXTEfx.alEffectf(effects[FLANGE], EXTEfx.AL_FLANGER_FEEDBACK, 0.6f);
        effects[CHORUS] = effect(EXTEfx.AL_EFFECT_CHORUS);
        EXTEfx.alEffectf(effects[CHORUS], EXTEfx.AL_CHORUS_RATE, 0.3f);
        EXTEfx.alEffectf(effects[CHORUS], EXTEfx.AL_CHORUS_DEPTH, 0.7f);
        EXTEfx.alEffectf(effects[CHORUS], EXTEfx.AL_CHORUS_FEEDBACK, 0.3f);
        effects[BASS] = effect(EXTEfx.AL_EFFECT_EQUALIZER);
        EXTEfx.alEffectf(effects[BASS], EXTEfx.AL_EQUALIZER_LOW_GAIN, 7.9f);
        EXTEfx.alEffectf(effects[BASS], EXTEfx.AL_EQUALIZER_LOW_CUTOFF, 180f);
        EXTEfx.alEffectf(effects[BASS], EXTEfx.AL_EQUALIZER_MID1_GAIN, 0.2f);
        EXTEfx.alEffectf(effects[BASS], EXTEfx.AL_EQUALIZER_MID2_GAIN, 0.2f);
        EXTEfx.alEffectf(effects[BASS], EXTEfx.AL_EQUALIZER_HIGH_GAIN, 0.2f);
        int made = EXTEfx.alGenAuxiliaryEffectSlots();
        EXTEfx.alAuxiliaryEffectSlotf(made, EXTEfx.AL_EFFECTSLOT_GAIN, 0f);
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) LOGGER.warn("Drug audio: OpenAL error {} while setting up effects", error);
        send = free;
        slot = made;
    }

    private static int effect(int type) {
        int e = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(e, EXTEfx.AL_EFFECT_TYPE, type);
        return e;
    }

    private static void onSource(PlaySoundSourceEvent event) {
        attach(event.getChannel());
    }

    private static void onStream(PlayStreamingSourceEvent event) {
        attach(event.getChannel());
    }

    /** On the sound thread: every new sound also goes through our slot. Sources are reused, so always set. */
    private static void attach(Channel channel) {
        int s = slot;
        if (s < 0) return;
        try {
            if (channelSource == null) {
                channelSource = Channel.class.getDeclaredField("source");
                channelSource.setAccessible(true);
            }
            AL11.alSource3i(channelSource.getInt(channel), EXTEfx.AL_AUXILIARY_SEND_FILTER, s, send, EXTEfx.AL_FILTER_NULL);
        } catch (ReflectiveOperationException | RuntimeException e) {
            slot = -1;
            LOGGER.warn("Drug audio: could not attach a sound, effects off", e);
        }
    }
}
