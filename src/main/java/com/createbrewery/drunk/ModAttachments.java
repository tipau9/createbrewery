package com.createbrewery.drunk;

import com.createbrewery.CreateBrewery;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    private ModAttachments() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CreateBrewery.MOD_ID);

    /**
     * Synced only to the player it belongs to - nobody else's client renders their drunkenness.
     * Saved only while there is something to save, so sober players carry no extra NBT.
     */
    public static final Supplier<AttachmentType<DrunkState>> DRUNK = ATTACHMENTS.register("drunk",
        () -> AttachmentType.builder(() -> new DrunkState())
            .serialize(DrunkState.CODEC, s -> !s.isEmpty())
            .sync((holder, to) -> holder == to, DrunkState.STREAM_CODEC)
            .build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
