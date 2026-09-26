package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Heroin and Xanax, server side.
 *
 * <p>Heroin: a rush, then warm and far away, nodding off again and again; pain barely reaches
 * you. What kills is the breath: every shot suppresses it further, and Xanax, alcohol or Keta on
 * top multiply that (Pharmacology#breathLoad). When it stops, the air runs out like under water
 * - a friend crouched next to you breathing for you keeps you alive. Shot after shot the body
 * gets used to it; then without it comes the Entzug: sick, hungry, shaking.
 *
 * <p>Xanax: calm. Fear is gone - it ends a bad trip or a psychosis. With alcohol, the memory
 * goes (Filmriss), and with heroin, the breath.
 */
public final class Opioids {
    private Opioids() {}

    public static final int HEROIN_TICKS = 4800;
    public static final int XANAX_TICKS = 6000;
    /** From this dependence on, going without means withdrawal. */
    public static final float DEPENDENT = 0.5f;
    private static final int MAX_LEVEL = 2;

    private static final ResourceKey<DamageType> OVERDOSE = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "overdose"));

    public static void take(Player player, DrugServer.Kind kind) {
        boolean heroin = kind == DrugServer.Kind.HEROIN;
        var high = heroin ? ModEffects.NOD : ModEffects.CALM;
        MobEffectInstance before = player.getEffect(high);
        int level = before == null ? 0 : Math.min(MAX_LEVEL, before.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(high, DrugEffect.doseTicks(before, high, heroin ? HEROIN_TICKS : XANAX_TICKS), level, false, false, true));
        if (heroin) {
            DrunkState s = DrunkServer.state(player);
            s.dependence = Math.min(1f, s.dependence + 0.2f);
            s.breathTolerance = Math.min(1f, s.breathTolerance + 0.08f);
            player.removeEffect(ModEffects.WITHDRAWAL);
        } else {
            DrunkServer.state(player).benzo = Math.min(1f, DrunkServer.state(player).benzo + 0.12f);
            calm(player);
        }
    }

    private static void calm(LivingEntity entity) {
        entity.removeEffect(ModEffects.BAD_TRIP);
        entity.removeEffect(ModEffects.PSYCHOSIS);
    }

    /** Xanax, every second: no fear, and with alcohol the memory switches off. */
    public static void calmTick(LivingEntity entity, int level) {
        calm(entity);
        if (entity instanceof Player player && DrunkServer.state(player).blood >= Intoxication.MERRY
            && player.getRandom().nextFloat() < 0.02f * (level + 1) * DrugEffect.felt(player, ModEffects.CALM)) {
            DrunkServer.blackout(player);
        }
    }

    /** Entzug, every second: ravenous and sick. */
    public static void withdrawalTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        player.causeFoodExhaustion(0.3f);
        if (player.getRandom().nextFloat() < 0.01f) DrunkServer.vomit(player);
    }

    /** Atemlähmung, every second: once the air is gone, it hurts - unless someone breathes for you. */
    public static void breathTick(LivingEntity entity, int level) {
        if (entity.getAirSupply() <= 0 && !DrugServer.helped(entity)) {
            entity.hurt(new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(OVERDOSE)), 2f);
        }
    }

    /** Every second, for every player: the breath, and dependence wearing off or turning into withdrawal. */
    public static void body(Player player) {
        DrunkState s = DrunkServer.state(player);
        MobEffectInstance nod = player.getEffect(ModEffects.NOD);
        boolean hole = player.hasEffect(ModEffects.K_HOLE);
        float breath = Pharmacology.breathLoad(nod == null ? -1 : nod.getAmplifier(), DrugEffect.strength(player, ModEffects.NOD),
            DrugEffect.felt(player, ModEffects.CALM), s.blood,
            hole ? 1f : DrugEffect.strength(player, ModEffects.KETA_HIGH), s.breathTolerance)
            + (Mixes.speedball(player) ? 0.3f : 0f);
        if (breath >= Pharmacology.BREATH_FAILING) {
            player.addEffect(new MobEffectInstance(ModEffects.RESPIRATORY_DEPRESSION, 60, 0, false, false, true));
        }
        // The first times the stomach turns, in the minute after the shot.
        if (nod != null && s.dependence < 0.45f && HEROIN_TICKS - nod.getDuration() < 1200 && player.getRandom().nextFloat() < 0.01f) {
            DrunkServer.vomit(player);
        }
        // Used to it: the high wears thin (both sides read this, see DrugEffect#felt).
        int habit = s.dependence >= 0.75f ? 2 : s.dependence >= 0.5f ? 1 : s.dependence >= 0.3f ? 0 : -1;
        if (habit >= 0) player.addEffect(new MobEffectInstance(ModEffects.OPIOID_HABIT, 60, habit, false, false, false));
        // Naloxon on a habit: the whole withdrawal at once.
        if (player.hasEffect(ModEffects.NALOXONE) && s.dependence >= 0.3f) {
            player.addEffect(new MobEffectInstance(ModEffects.WITHDRAWAL, 60, 1, false, false, true));
        }
        // Without heroin the breath forgets its tolerance within minutes - long before the habit goes.
        if (nod == null) s.breathTolerance = Math.max(0f, s.breathTolerance - 0.004f);
        benzo(player, s);
        if (nod != null || s.dependence <= 0f) return;
        if (s.dependence >= DEPENDENT) {
            player.addEffect(new MobEffectInstance(ModEffects.WITHDRAWAL, 60, 0, false, false, true));
        }
        // Clean, the body slowly forgets: a full habit takes about an in-game day.
        s.dependence = Math.max(0f, s.dependence - 0.001f);
    }

    /**
     * Xanax, every second: used to it and without it, the brain overshoots - a seizure can come.
     * Taking it again stops that; clean long enough, the body settles.
     */
    private static void benzo(Player player, DrunkState s) {
        if (s.benzo <= 0f || player.hasEffect(ModEffects.CALM)) return;
        s.benzo = Math.max(0f, s.benzo - 0.0004f);
        if (!player.hasEffect(ModEffects.SEIZURE) && player.getRandom().nextFloat() < Pharmacology.seizureChance(s.benzo)) {
            seize(player);
        }
    }

    public static final int SEIZURE_TICKS = 100;

    /** A seizure: five seconds of convulsions, then dazed and hurt. */
    public static void seize(Player player) {
        player.addEffect(new MobEffectInstance(ModEffects.SEIZURE, SEIZURE_TICKS, 0, false, false, true));
        DrugPose.act(player, DrugPose.SEIZURE, SEIZURE_TICKS);
        player.setSprinting(false);
    }

    private static final ResourceKey<DamageType> SEIZURE = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "seizure"));

    /** Seizure, every second: thrashing against whatever is there. */
    public static void seizureTick(LivingEntity entity, int level) {
        entity.hurt(new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(SEIZURE)), 1f);
    }

    public static final int NALOXONE_TICKS = 1200;

    /**
     * Naloxon: the breath comes back at once, the heroin does nothing for a minute. With a habit
     * that is the whole withdrawal in one go; and if there is still a lot of heroin in the body,
     * it comes back once the naloxon is gone.
     */
    public static void naloxone(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(ModEffects.NALOXONE, NALOXONE_TICKS, 0, false, false, true));
        entity.removeEffect(ModEffects.RESPIRATORY_DEPRESSION);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), com.createbrewery.sound.ModSounds.SNIFF.get(),
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.3f);
    }

    /** Heroin dulls pain: hits land softer while high. */
    public static float painFactor(LivingEntity entity) {
        return 1f - 0.5f * DrugEffect.felt(entity, ModEffects.NOD);
    }
}
