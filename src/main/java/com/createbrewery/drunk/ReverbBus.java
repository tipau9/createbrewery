package com.createbrewery.drunk;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * The one reverb the club's speakers share: an OpenAL EFX auxiliary slot with an (EAX) reverb
 * effect, set from {@link RoomAcoustics}. Each emitter sends to it through a low-pass filter whose
 * gain says how much of its sound reaches the room. Without EFX nothing here does anything.
 * Render thread only.
 */
final class ReverbBus {
    private ReverbBus() {}

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** The share of the send a subwoofer's band keeps (reverb on bass is mud); the rest follows the room's size ({@link RoomAcoustics#sendFor}). */
    static final float LOW_FACTOR = 0.3f;

    private static int slot = -1, effect = -1;
    private static boolean eax, failed;
    private static float amount, room = 1f;
    private static double lastApply = -1;

    /** The slot to send to, or 0 when there is none. */
    static int slot() {
        return slot > 0 ? slot : 0;
    }

    /** Makes the slot if it is missing; false, quietly, when this OpenAL has no EFX. */
    static boolean ensure() {
        if (failed) return false;
        try {
            if (slot > 0 && EXTEfx.alIsAuxiliaryEffectSlot(slot)) return true;
            long context = ALC10.alcGetCurrentContext();
            if (context == 0 || !ALC10.alcIsExtensionPresent(ALC10.alcGetContextsDevice(context), "ALC_EXT_EFX")) {
                failed = true;
                return false;
            }
            AL10.alGetError();
            slot = EXTEfx.alGenAuxiliaryEffectSlots();
            effect = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
            eax = AL10.alGetError() == AL10.AL_NO_ERROR;
            if (!eax) EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) throw new IllegalStateException("no reverb effect");
            lastApply = -1;
            return true;
        } catch (Throwable e) {
            LOGGER.info("Club reverb off: {}", e.toString());
            failed = true;
            release();
            return false;
        }
    }

    /** Sets the room and how much of it is heard ({@code wet} 0 keeps the slot but sends nothing). At most ten times a second. */
    static void apply(RoomAcoustics.Params p, float wet, double now) {
        if (slot <= 0) return;
        amount = wet;
        room = RoomAcoustics.sendFor(p);
        if (now - lastApply < 0.1) return;
        lastApply = now;
        try {
            if (eax) {
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, p.decayTime());
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, p.density());
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, p.diffusion());
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAIN, 0.32f);
                // Smeared, not metallic, and from everywhere: a club's room is all around you.
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_MODULATION_DEPTH, 0.3f);
                EXTEfx.alEffectfv(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_PAN, new float[3]);
                EXTEfx.alEffectfv(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_PAN, new float[3]);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, p.lateGain());
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, p.reflectionsGain());
            } else {
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, p.decayTime());
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DENSITY, p.density());
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DIFFUSION, p.diffusion());
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, 0.32f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, p.lateGain());
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, p.reflectionsGain());
            }
            // The slot only takes the new settings when the effect is loaded into it again.
            EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
            AL10.alGetError(); // the game must not log an EFX hiccup as its own error
        } catch (Throwable ignored) {}
    }

    /** The send gain for an emitter of {@code band} behind {@code wallLoss} (1 in the open). */
    static float send(int band, float wallLoss) {
        return Math.min(1f, room * amount * wallLoss * (band == Emitter.LOW ? LOW_FACTOR : 1f));
    }

    static int newFilter() {
        try {
            int filter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            AL10.alGetError();
            return filter;
        } catch (Throwable e) {
            return -1;
        }
    }

    static void setFilterGain(int filter, float gain) {
        try {
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
            AL10.alGetError();
        } catch (Throwable ignored) {}
    }

    static void deleteFilter(int filter) {
        try {
            EXTEfx.alDeleteFilters(filter);
            AL10.alGetError();
        } catch (Throwable ignored) {}
    }

    /** Frees the slot. Only call with no emitter left: OpenAL Soft refuses to delete a slot sources still send to. */
    static void release() {
        try {
            if (slot > 0) {
                EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, 0);
                EXTEfx.alDeleteAuxiliaryEffectSlots(slot);
            }
            if (effect > 0) EXTEfx.alDeleteEffects(effect);
            AL10.alGetError();
        } catch (Throwable ignored) {}
        slot = -1;
        effect = -1;
    }
}
