package com.createbrewery.drunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's alcohol, in per-mille. The single source of truth for every drunk symptom:
 * the server mutates it in place every tick and syncs it to the owning client, which derives
 * all of its visuals from {@link #blood}. Not copied on death - dying sobers you up.
 */
public final class DrunkState {
    /** Blood alcohol, ‰. What every symptom reads. */
    public float blood;
    /** Alcohol drunk but not yet absorbed, ‰. */
    public float stomach;
    /** Highest blood level of the current session; decides the hangover. */
    public float peak;

    /** Server-only bookkeeping: whether the client last saw a non-zero state. */
    transient boolean clientSawAlcohol;

    public DrunkState() {}

    public DrunkState(float blood, float stomach, float peak) {
        this.blood = blood;
        this.stomach = stomach;
        this.peak = peak;
    }

    public float total() {
        return blood + stomach;
    }

    public boolean isEmpty() {
        return blood <= 0f && stomach <= 0f && peak <= 0f;
    }

    public static final Codec<DrunkState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.FLOAT.fieldOf("blood").forGetter(s -> s.blood),
        Codec.FLOAT.fieldOf("stomach").forGetter(s -> s.stomach),
        Codec.FLOAT.fieldOf("peak").forGetter(s -> s.peak)
    ).apply(i, DrunkState::new));

    public static final StreamCodec<ByteBuf, DrunkState> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, s -> s.blood,
        ByteBufCodecs.FLOAT, s -> s.stomach,
        ByteBufCodecs.FLOAT, s -> s.peak,
        DrunkState::new);
}
