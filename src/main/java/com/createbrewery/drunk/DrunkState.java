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
    /** 0..1 how used the body is to alcohol. Survives sobering up and death. */
    public float tolerance;
    /** Game time the tolerance was last brought up to date; sober time since then wears it off. */
    public long toleranceTime;

    /** Server-only bookkeeping: whether the client last saw a non-zero state. */
    transient boolean clientSawAlcohol;
    /** Server-only: load on the heart from drugs (see Pharmacology#heartLoad). Not saved: it rebuilds in seconds. */
    public transient float heart;
    /** Server-only: body heat above normal from MDMA and meth (see Pharmacology#heatStep). */
    public transient float heat;
    /** Server-only: seconds awake on meth, towards psychosis. */
    public transient int awake;
    /** Server-only: game time of the last drink, to spot two players clinking glasses. */
    transient long lastDrinkTime = Long.MIN_VALUE / 2;

    public DrunkState() {}

    public DrunkState(float blood, float stomach, float peak, float tolerance, long toleranceTime) {
        this.blood = blood;
        this.stomach = stomach;
        this.peak = peak;
        this.tolerance = tolerance;
        this.toleranceTime = toleranceTime;
    }

    /** Client copy: the client needs the tolerance for how drunk things look, not its clock. */
    private DrunkState(float blood, float stomach, float peak, float tolerance) {
        this(blood, stomach, peak, tolerance, 0L);
    }

    public boolean hasAlcohol() {
        return blood > 0f || stomach > 0f;
    }

    /** The blood level as this body feels it (see {@link Intoxication#felt}). */
    public float felt() {
        return Intoxication.felt(blood, tolerance);
    }

    public float total() {
        return blood + stomach;
    }

    public boolean isEmpty() {
        return blood <= 0f && stomach <= 0f && peak <= 0f && tolerance <= 0f;
    }

    public static final Codec<DrunkState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.FLOAT.fieldOf("blood").forGetter(s -> s.blood),
        Codec.FLOAT.fieldOf("stomach").forGetter(s -> s.stomach),
        Codec.FLOAT.fieldOf("peak").forGetter(s -> s.peak),
        Codec.FLOAT.optionalFieldOf("tolerance", 0f).forGetter(s -> s.tolerance),
        Codec.LONG.optionalFieldOf("tolerance_time", 0L).forGetter(s -> s.toleranceTime)
    ).apply(i, DrunkState::new));

    public static final StreamCodec<ByteBuf, DrunkState> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, s -> s.blood,
        ByteBufCodecs.FLOAT, s -> s.stomach,
        ByteBufCodecs.FLOAT, s -> s.peak,
        ByteBufCodecs.FLOAT, s -> s.tolerance,
        DrunkState::new);
}
