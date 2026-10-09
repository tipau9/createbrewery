package com.createbrewery.block.club;

import com.createbrewery.CreateBrewery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * What a booth has learnt about a record by playing it, kept on the record itself so the library
 * shows it wherever the record goes.
 *
 * @param bpm the record's own tempo (pitch fader at 0)
 */
public record TrackInfo(float bpm) {
    /** The tempos a booth believes; outside them the beat detector locked onto half or double time. */
    public static final float MIN_BPM = 60f, MAX_BPM = 200f;

    public static final Codec<TrackInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.FLOAT.fieldOf("bpm").forGetter(TrackInfo::bpm)
    ).apply(i, TrackInfo::new));
    public static final StreamCodec<ByteBuf, TrackInfo> STREAM_CODEC = ByteBufCodecs.FLOAT.map(TrackInfo::new, TrackInfo::bpm);

    private static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateBrewery.MOD_ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TrackInfo>> TRACK_INFO =
        COMPONENTS.registerComponentType("track_info", b -> b.persistent(CODEC).networkSynchronized(STREAM_CODEC));

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }

    /** The record's own tempo from a beat heard every {@code beatPeriodSeconds} while played at {@code pitch}. */
    public static float baseBpm(double beatPeriodSeconds, float pitch) {
        return (float) (60.0 / beatPeriodSeconds / pitch);
    }
}
