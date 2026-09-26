package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.particle.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * MDMA and Meth, server side.
 *
 * <p>MDMA (Ecstasy): warm, open, everyone is your friend (hearts over everyone near you), music
 * is everything, the jaw clenches. The danger is heat: dancing on it drives the body temperature
 * up, and past a point it overheats (Hitzschlag). Water and rest cool it. Days later, the
 * serotonin is gone: the Tiefpunkt. Until then another pill does only half as much. Sneak up close to anyone for a hug - yes, even a creeper.
 * Coming up the stomach turns; rolling it hurts less, you can dance all night and cannot sleep.
 *
 * <p>Meth (Crystal): like Koks, but for half a day - fast, tireless, no hunger, no sleep, and by
 * far the hardest on the heart. Awake long enough on it, the mind starts to slip (Psychose): the
 * shadow people at the edge of the view. Skin picking. And the crash is long.
 */
public final class Stimulants {
    private Stimulants() {}

    public static final int MDMA_TICKS = 7200;
    public static final int METH_TICKS = 14400;
    public static final int PSYCHOSIS_TICKS = 1200;
    /** Seconds awake on meth before psychosis can set in (in game about a sleepless night and a day). */
    public static final int PSYCHOSIS_AFTER = 600;
    private static final int MAX_MDMA = 2;
    private static final int MAX_METH = 3;

    private static final ResourceKey<DamageType> HYPERTHERMIA = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "hyperthermia"));
    private static final ResourceKey<DamageType> SKIN_PICKING = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "skin_picking"));

    public static void take(Player player, DrugServer.Kind kind) {
        boolean mdma = kind == DrugServer.Kind.MDMA;
        var high = mdma ? ModEffects.ROLLING : ModEffects.TWEAK;
        MobEffectInstance before = player.getEffect(high);
        int level = before == null ? 0 : Math.min(mdma ? MAX_MDMA : MAX_METH, before.getAmplifier() + 1);
        // The serotonin is still used up from the last roll: the magic is gone - it starts at half
        // strength and only fades from there.
        boolean spent = mdma && before == null && (player.hasEffect(ModEffects.COMEDOWN_PENDING) || player.hasEffect(ModEffects.COMEDOWN));
        int ticks = spent ? ((DrugEffect) high.value()).fade() / 2
            : DrugEffect.doseTicks(before, high, mdma ? DrugServer.stomach(player, high, MDMA_TICKS) : METH_TICKS);
        player.addEffect(new MobEffectInstance(high, ticks, level, false, false, true));
        if (spent) DrugServer.think(player, "Irgendwie… nicht wie letztes Mal.", 0xB08AB0);
        // Another dose pushes the comedown back (and stills the craving, for now).
        player.removeEffect(mdma ? ModEffects.COMEDOWN : ModEffects.METH_CRASH);
        if (!mdma) {
            player.removeEffect(ModEffects.CRAVING);
            DrunkServer.state(player).methHabit = Math.min(1f, DrunkServer.state(player).methHabit + 0.08f);
        }
    }

    /** MDMA, every second: love for everyone around, the jaw grinding, and hugs. */
    public static void rollTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player) || !(player.level() instanceof ServerLevel server)) return;
        float felt = DrugEffect.felt(player, ModEffects.ROLLING);
        if (player.getRandom().nextFloat() < 0.6f * felt) {
            for (LivingEntity other : server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6.0),
                e -> e != player)) {
                server.sendParticles(ParticleTypes.HEART, other.getX(), other.getY() + other.getBbHeight() + 0.3,
                    other.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            }
        }
        if (player.getRandom().nextFloat() < 0.06f * felt) {
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GRINDSTONE_USE,
                SoundSource.PLAYERS, 0.25f, 1.8f + player.getRandom().nextFloat() * 0.2f);
        }
        // Coming up, the stomach turns for a moment.
        if (Psychedelics.comingUp(player, ModEffects.ROLLING) && player.getRandom().nextFloat() < 0.02f) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, false));
        }
        // Dancing all night: sprinting barely tires.
        if (player.isSprinting() && felt > 0.3f) {
            player.getFoodData().setExhaustion(Math.max(0f, player.getFoodData().getExhaustionLevel() - 0.3f * felt));
        }
        // Sneaking right up to someone is a hug: warmth for both, and hearts everywhere.
        if (player.isShiftKeyDown() && felt > 0.2f) {
            for (LivingEntity other : server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.8), e -> e != player)) {
                server.sendParticles(ParticleTypes.HEART, other.getX(), other.getY() + other.getBbHeight() * 0.8, other.getZ(),
                    5, 0.4, 0.3, 0.4, 0.0);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CAT_PURR, SoundSource.PLAYERS, 0.8f, 1.1f);
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
                other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
                if (other instanceof Creeper) DrugServer.award(player, "kuschelmonster", "hugged");
                break;
            }
        }
    }

    /** Meth, every second: no hunger, and after a while the skin itches until it bleeds. */
    public static void tweakTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        float strength = DrugEffect.strength(player, ModEffects.TWEAK);
        if (strength > 0.3f) player.getFoodData().setExhaustion(0f);
        if (player.level() instanceof ServerLevel server && player.getRandom().nextFloat() < 0.006f * (level + 1) * strength) {
            player.hurt(new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SKIN_PICKING)), 1f);
            server.sendParticles(ModParticles.NOSEBLEED.get(), player.getX(), player.getY() + 1.0, player.getZ(),
                4, 0.2, 0.2, 0.2, 0.0);
        }
    }

    public static void hyperthermiaTick(LivingEntity entity, int level) {
        entity.hurt(new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(HYPERTHERMIA)), 1f);
    }

    /**
     * Every second, for every player: body heat follows MDMA and meth, sprinting (dancing) and a
     * hot place; water brings it down. Meth also counts the time awake towards psychosis.
     */
    public static void body(Player player) {
        DrunkState s = DrunkServer.state(player);
        float rolling = DrugEffect.felt(player, ModEffects.ROLLING);
        float tweak = DrugEffect.strength(player, ModEffects.TWEAK);
        if (rolling > 0f || tweak > 0f || s.heat > 0f) {
            boolean hot = player.level().dimensionType().ultraWarm()
                || player.level().getBiome(player.blockPosition()).value().getBaseTemperature() > 1.0f;
            s.heat = Pharmacology.heatStep(s.heat, rolling * (Mixes.dehydrating(player) ? 1.6f : 1f) + 0.5f * tweak, player.isSprinting(), hot, player.isInWaterOrRain());
            if (s.heat >= Pharmacology.OVERHEATED) {
                player.addEffect(new MobEffectInstance(ModEffects.HYPERTHERMIA, 40, 0, false, false, true));
            }
        }
        water(player, s);
        if (player.hasEffect(ModEffects.TWEAK)) {
            s.awake++;
            if (s.awake > PSYCHOSIS_AFTER && !player.hasEffect(ModEffects.PSYCHOSIS) && !player.hasEffect(ModEffects.CALM) && player.getRandom().nextFloat() < 0.01f) {
                player.addEffect(new MobEffectInstance(ModEffects.PSYCHOSIS, PSYCHOSIS_TICKS, 0, false, false, true));
            }
        } else if (player.isSleeping()) {
            s.awake = 0;
        }
    }

    /**
     * MDMA makes the body hold on to water. Drinking to cool down is right, but what goes in on
     * it stays in: past about four drinks the blood's salt thins (water poisoning) - headache,
     * sick, confused, and further on a seizure. Most MDMA deaths are this, not drying out.
     */
    private static void water(Player player, DrunkState s) {
        if (s.water <= 0f) return;
        s.water = Math.max(0f, s.water - (player.hasEffect(ModEffects.ROLLING) ? 0.0005f : 0.004f));
        if (s.water < 1f) return;
        player.addEffect(new MobEffectInstance(ModEffects.HYPONATREMIA, 60, s.water >= 1.5f ? 1 : 0, false, false, true));
        if (player.getRandom().nextFloat() < 0.01f) {
            DrugServer.think(player, WATER[player.getRandom().nextInt(WATER.length)], 0x7AB0E0);
        }
        if (s.water >= 1.5f && !player.hasEffect(ModEffects.SEIZURE) && player.getRandom().nextFloat() < 0.02f) Opioids.seize(player);
    }

    private static final String[] WATER = {"Mein Kopf… als würd er platzen.", "Mir ist so schlecht. Noch mehr Wasser?",
        "Wo… bin ich nochmal?", "Ich hab doch genug getrunken… oder?"};

    /** Wasservergiftung, every second: sick to the stomach. */
    public static void waterTick(LivingEntity entity, int level) {
        if (entity.getRandom().nextFloat() < 0.04f * (level + 1)) {
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, false));
        }
        if (entity instanceof Player player && player.getRandom().nextFloat() < 0.01f * (level + 1)) DrunkServer.vomit(player);
    }

    /** Water on MDMA: it goes in, and stays. */
    public static void drank(Player player) {
        if (player.hasEffect(ModEffects.ROLLING) || player.hasEffect(ModEffects.COMEDOWN)) DrunkServer.state(player).water += 0.25f;
    }

    /** Salt and sugar: the blood's salt back, and cool. */
    public static void electrolytes(Player player) {
        DrunkState s = DrunkServer.state(player);
        s.water = 0f;
        TanCompat.electrolytes(player);
        player.removeEffect(ModEffects.HYPONATREMIA);
        cool(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** A drink cools you down. */
    public static void cool(Player player) {
        DrunkState s = DrunkServer.state(player);
        s.heat = Math.max(0f, s.heat - 0.4f);
    }

    /** Heart load on top of Pharmacology#heartLoad. */
    public static float heartLoad(Player player) {
        MobEffectInstance meth = player.getEffect(ModEffects.TWEAK);
        return Pharmacology.stimulantLoad(DrugEffect.felt(player, ModEffects.ROLLING),
            meth == null ? -1 : meth.getAmplifier(), DrugEffect.strength(player, ModEffects.TWEAK),
            DrunkServer.state(player).heat);
    }

    /** The comedowns: the serotonin is gone after MDMA, the body is empty after meth. */
    public static void expired(LivingEntity entity, MobEffectInstance instance) {
        if (instance.is(ModEffects.ROLLING)) {
            entity.addEffect(new MobEffectInstance(ModEffects.COMEDOWN, 2400 + 1200 * instance.getAmplifier(), 0));
            // And the real low comes later: about two days on, the serotonin is at its lowest.
            entity.addEffect(new MobEffectInstance(ModEffects.COMEDOWN_PENDING, 36000 + entity.getRandom().nextInt(12000), 0, false, false, false));
        } else if (instance.is(ModEffects.COMEDOWN_PENDING)) {
            entity.addEffect(new MobEffectInstance(ModEffects.COMEDOWN, 3600, 0));
            if (entity instanceof Player player) DrugServer.think(player, "Warum bin ich so… leer? Grundlos. Einfach leer.", 0x8A8AB0);
        } else if (instance.is(ModEffects.TWEAK)) {
            entity.addEffect(new MobEffectInstance(ModEffects.METH_CRASH, 3600 + 1800 * instance.getAmplifier(), 0));
            // Meth grips hardest: the craving comes after every run, not just a binge.
            entity.addEffect(new MobEffectInstance(ModEffects.CRAVING, 4800 + 2400 * instance.getAmplifier(), 0));
        }
    }
}
