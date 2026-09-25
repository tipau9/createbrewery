package com.createbrewery.drugs;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * LSD, Zauberpilze and Meskalin (Peyote), server side. The client draws the trip itself from
 * these effects (DrunkClient: the Trip channel of the shader, synaesthesia, the breathing view).
 *
 * <p>All three are classic serotonergic psychedelics: the body barely notices, the mind does.
 * They come up slowly, last for hours, and share one tolerance: after a trip the next dose of
 * any of them does little for about a day. Set and setting decide how it goes - tripping in the
 * dark, hurt, or with monsters around can turn it into a bad trip.
 *
 * <ul>
 *   <li>LSD: long, clear, electric colours; strongest visuals, no body load at all.</li>
 *   <li>Pilze: shorter, organic and green, the stomach turns during the come-up, and giggles.</li>
 *   <li>Meskalin: slowest and longest, warm desert colours, throwing up early is almost a rite.</li>
 *   <li>DMT: smoked, and within seconds a complete breakthrough - the world folds away into
 *       hyperspace and beings appear ("Maschinenelfen"). A minute later it is over. No tolerance.</li>
 * </ul>
 */
public final class Psychedelics {
    private Psychedelics() {}

    public static final int LSD_TICKS = 14400;
    public static final int SHROOM_TICKS = 7200;
    public static final int MESCALINE_TICKS = 14400;
    /** About a day of tolerance after a trip ends. */
    public static final int TOLERANCE_TICKS = 24000;
    public static final int BAD_TRIP_TICKS = 1200;
    /** A real breakthrough lasts ~15 minutes: here 45 seconds. */
    public static final int DMT_TICKS = 900;
    /** Three doses at most: from the third LSD tab on, the ego dissolves. */
    private static final int MAX_LEVEL = 2;

    public static Holder<MobEffect> effect(DrugServer.Kind kind) {
        return switch (kind) {
            case SHROOMS -> ModEffects.SHROOM_TRIP;
            case MESCALINE -> ModEffects.MESCALINE_TRIP;
            default -> ModEffects.LSD_TRIP;
        };
    }

    private static int ticks(DrugServer.Kind kind) {
        return switch (kind) {
            case SHROOMS -> SHROOM_TICKS;
            case MESCALINE -> MESCALINE_TICKS;
            default -> LSD_TICKS;
        };
    }

    /** 0..1 how hard the trip hits right now, all three together. */
    public static float tripping(LivingEntity entity) {
        return Math.min(1f, DrugEffect.felt(entity, ModEffects.LSD_TRIP) + DrugEffect.felt(entity, ModEffects.SHROOM_TRIP)
            + DrugEffect.felt(entity, ModEffects.MESCALINE_TRIP));
    }

    public static void take(Player player, DrugServer.Kind kind) {
        if (kind == DrugServer.Kind.DMT) {
            // Held in the lungs as long as possible, then gone. A second hit only prolongs it.
            player.addEffect(new MobEffectInstance(ModEffects.BREAKTHROUGH, DMT_TICKS,
                player.hasEffect(ModEffects.BREAKTHROUGH) ? 1 : 0, false, false, true));
            return;
        }
        Holder<MobEffect> trip = effect(kind);
        MobEffectInstance before = player.getEffect(trip);
        if (player.hasEffect(ModEffects.PSY_TOLERANCE) && before == null) {
            // Tolerant: it starts at half strength and only fades from there.
            DrugEffect drug = (DrugEffect) trip.value();
            player.addEffect(new MobEffectInstance(trip, drug.fade() / 2, 0, false, false, true));
            return;
        }
        int level = before == null ? 0 : Math.min(MAX_LEVEL, before.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(trip, ticks(kind), level, false, false, true));
    }

    /** Every second while tripping: the body (stomach) and the setting (bad trips). */
    public static void tripTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        float shroom = DrugEffect.strength(player, ModEffects.SHROOM_TRIP);
        float mesc = DrugEffect.strength(player, ModEffects.MESCALINE_TRIP);
        // The come-up turns the stomach: mushrooms a little, peyote a lot.
        float sick = (comingUp(player, ModEffects.SHROOM_TRIP) ? 0.012f : 0f)
            + (comingUp(player, ModEffects.MESCALINE_TRIP) ? 0.035f : 0f);
        if (player.getRandom().nextFloat() < sick) DrunkServer.vomit(player);
        // Mushrooms giggle, like weed.
        if (player.getRandom().nextFloat() < 0.02f * shroom + 0.005f * mesc) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GIGGLE.get(),
                SoundSource.PLAYERS, 1.0f, 1.05f + player.getRandom().nextFloat() * 0.15f);
        }
        // Set and setting: dark, hurt, or hunted - the trip can turn.
        float trip = tripping(player);
        if (!player.hasEffect(ModEffects.BAD_TRIP) && !player.hasEffect(ModEffects.CALM) // Xanax: no fear
            && player.getRandom().nextFloat() < 0.012f * trip * badSetting(player)) {
            player.addEffect(new MobEffectInstance(ModEffects.BAD_TRIP, BAD_TRIP_TICKS, 0, false, false, true));
        }
        // A heroic dose at its peak: the self dissolves.
        MobEffectInstance lsd = player.getEffect(ModEffects.LSD_TRIP);
        if (lsd != null && lsd.getAmplifier() >= MAX_LEVEL && DrugEffect.strength(player, ModEffects.LSD_TRIP) > 0.9f) {
            DrugServer.award(player, "ego_tod", "dissolved");
        }
    }

    /** Bad trip, every second: the heart speeds up with the fear, and it feeds on the setting. */
    public static void badTripTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        // Going somewhere bright and safe helps it pass, just like in real life.
        if (badSetting(player) == 0f) {
            com.createbrewery.event.BreweryCommonEvents.reduceDuration(player, ModEffects.BAD_TRIP, 20);
        }
    }

    /** DMT, every second: at the peak you meet them. */
    public static void breakthroughTick(LivingEntity entity, int level) {
        if (DrugEffect.strength(entity, ModEffects.BREAKTHROUGH) > 0.8f) DrugServer.award(entity, "maschinenelfen", "met");
    }

    private static boolean comingUp(Player player, Holder<MobEffect> trip) {
        MobEffectInstance instance = player.getEffect(trip);
        if (instance == null || !(trip.value() instanceof DrugEffect drug)) return false;
        return drug.total() - instance.getDuration() < drug.onset();
    }

    /** 0: a good place to trip. Up to 3: dark, hurt and hunted all at once. */
    public static float badSetting(Player player) {
        float bad = 0f;
        if (player.level().getMaxLocalRawBrightness(player.blockPosition()) < 6) bad += 1f;
        if (player.getHealth() < player.getMaxHealth() * 0.5f) bad += 1f;
        if (!player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(10.0),
            e -> e instanceof Enemy).isEmpty()) bad += 1f;
        return bad;
    }

    /** When a trip ends: a day of tolerance for all three. */
    public static void expired(LivingEntity entity, MobEffectInstance instance) {
        if (instance.is(ModEffects.LSD_TRIP) || instance.is(ModEffects.SHROOM_TRIP) || instance.is(ModEffects.MESCALINE_TRIP)) {
            entity.addEffect(new MobEffectInstance(ModEffects.PSY_TOLERANCE, TOLERANCE_TICKS, 0, false, false, true));
        }
    }
}
