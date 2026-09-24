package com.createbrewery.sound;

import com.createbrewery.CreateBrewery;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Our own sounds (assets/createbrewery/sounds.json + sounds/*.ogg). */
public final class ModSounds {
    private ModSounds() {}

    private static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, CreateBrewery.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GLASS_CLINK = sound("glass_clink");
    public static final DeferredHolder<SoundEvent, SoundEvent> HICCUP = sound("hiccup");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = sound("heartbeat");
    public static final DeferredHolder<SoundEvent, SoundEvent> BEER_OPEN = sound("beer_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> EAR_RINGING = sound("ear_ringing");
    public static final DeferredHolder<SoundEvent, SoundEvent> SNIFF = sound("sniff");
    public static final DeferredHolder<SoundEvent, SoundEvent> COUGH = sound("cough");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIGGLE = sound("giggle");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
