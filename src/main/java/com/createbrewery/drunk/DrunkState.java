package com.createbrewery.drunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A player's alcohol, in per-mille. The single source of truth for every drunk symptom:
 * the server mutates it in place every tick and syncs it to the owning client, which derives
 * all of its visuals from {@link #blood}. On death only what the body keeps is copied (see DrunkServer#onClone).
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
    /** 0..1 how used the body is to heroin; from Opioids#DEPENDENT on, going without is withdrawal. */
    public float dependence;
    /** 0..1 how used the breath is to heroin. Grows far slower than {@link #dependence}, and is gone far sooner. */
    public float breathTolerance;
    /** 0..1 how used the body is to Xanax; stopping from high up risks a seizure. */
    public float benzo;
    /** 0..1 vitamin B12 used up by Lachgas: numb feet, a clumsy gait. Heals slowly. */
    public float b12;
    /** 0..1 how damaged the bladder is by Keta. Heals very slowly. */
    public float bladder;
    /** 0..1 how used the body is to weed, Koks and meth: regular use, the high wears thin. Clean, it wears off. */
    public float weedHabit, cokeHabit, methHabit;

    /** Server-only bookkeeping: whether the client last saw a non-zero state. */
    transient boolean clientSawAlcohol;
    /** Server-only: load on the heart from drugs (see Pharmacology#heartLoad). Not saved: it rebuilds in seconds. */
    public transient float heart;
    /** Server-only: body heat above normal from MDMA and meth (see Pharmacology#heatStep). Saved, so logging out is no cure. */
    public float heat;
    /** Server-only: seconds awake on meth, towards psychosis. Saved. */
    public int awake;
    /** Server-only: meth punding - the block mined over and over, and how many in a row. */
    public transient net.minecraft.world.level.block.Block pundBlock;
    public transient int pundStreak;
    /** Server-only: water drunk on MDMA and not yet got rid of, towards water poisoning. Saved. */
    public float water;
    /** Server-only: game time of the last drink, to spot two players clinking glasses. */
    transient long lastDrinkTime = Long.MIN_VALUE / 2;

    public DrunkState() {}

    public DrunkState(float blood, float stomach, float peak, float tolerance, long toleranceTime, float dependence) {
        this(blood, stomach, peak, tolerance, toleranceTime);
        this.dependence = dependence;
    }

    private DrunkState(float blood, float stomach, float peak, float tolerance, long toleranceTime, float dependence,
                       float breathTolerance, float benzo, float b12, float bladder) {
        this(blood, stomach, peak, tolerance, toleranceTime, dependence);
        this.breathTolerance = breathTolerance;
        this.benzo = benzo;
        this.b12 = b12;
        this.bladder = bladder;
    }

    private DrunkState(float blood, float stomach, float peak, float tolerance, long toleranceTime, float dependence,
                       float breathTolerance, float benzo, float b12, float bladder, float weedHabit, float cokeHabit, float methHabit,
                       Body body) {
        this(blood, stomach, peak, tolerance, toleranceTime, dependence, breathTolerance, benzo, b12, bladder);
        this.weedHabit = weedHabit;
        this.cokeHabit = cokeHabit;
        this.methHabit = methHabit;
        this.heat = body.heat;
        this.water = body.water;
        this.awake = body.awake;
    }

    /** The saved server-side body state, nested so {@link #CODEC} stays under RecordCodecBuilder's 16-field cap. */
    private record Body(float heat, float water, int awake) {
        static final Body NONE = new Body(0f, 0f, 0);
        static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("heat", 0f).forGetter(Body::heat),
            Codec.FLOAT.optionalFieldOf("water", 0f).forGetter(Body::water),
            Codec.INT.optionalFieldOf("awake", 0).forGetter(Body::awake)
        ).apply(i, Body::new));
    }

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
        return blood <= 0f && stomach <= 0f && peak <= 0f && tolerance <= 0f && dependence <= 0f
            && breathTolerance <= 0f && benzo <= 0f && b12 <= 0f && bladder <= 0f
            && weedHabit <= 0f && cokeHabit <= 0f && methHabit <= 0f && heat <= 0f && water <= 0f && awake <= 0;
    }

    public static final Codec<DrunkState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.FLOAT.fieldOf("blood").forGetter(s -> s.blood),
        Codec.FLOAT.fieldOf("stomach").forGetter(s -> s.stomach),
        Codec.FLOAT.fieldOf("peak").forGetter(s -> s.peak),
        Codec.FLOAT.optionalFieldOf("tolerance", 0f).forGetter(s -> s.tolerance),
        Codec.LONG.optionalFieldOf("tolerance_time", 0L).forGetter(s -> s.toleranceTime),
        Codec.FLOAT.optionalFieldOf("dependence", 0f).forGetter(s -> s.dependence),
        Codec.FLOAT.optionalFieldOf("breath_tolerance", 0f).forGetter(s -> s.breathTolerance),
        Codec.FLOAT.optionalFieldOf("benzo", 0f).forGetter(s -> s.benzo),
        Codec.FLOAT.optionalFieldOf("b12", 0f).forGetter(s -> s.b12),
        Codec.FLOAT.optionalFieldOf("bladder", 0f).forGetter(s -> s.bladder),
        Codec.FLOAT.optionalFieldOf("weed_habit", 0f).forGetter(s -> s.weedHabit),
        Codec.FLOAT.optionalFieldOf("coke_habit", 0f).forGetter(s -> s.cokeHabit),
        Codec.FLOAT.optionalFieldOf("meth_habit", 0f).forGetter(s -> s.methHabit),
        Body.CODEC.optionalFieldOf("body", Body.NONE).forGetter(s -> new Body(s.heat, s.water, s.awake))
    ).apply(i, DrunkState::new));

    public static final StreamCodec<ByteBuf, DrunkState> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, s -> s.blood,
        ByteBufCodecs.FLOAT, s -> s.stomach,
        ByteBufCodecs.FLOAT, s -> s.peak,
        ByteBufCodecs.FLOAT, s -> s.tolerance,
        DrunkState::new);
}
