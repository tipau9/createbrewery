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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * MDMA and Meth, server side.
 *
 * <p>MDMA (Ecstasy): warm, open, everyone is your friend (hearts over everyone near you), music
 * is everything, the jaw clenches. The danger is heat: dancing on it drives the body temperature
 * up, and past a point it overheats (Hitzschlag). Water and rest cool it. Days later, the
 * serotonin is gone: the Tiefpunkt.
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
        player.addEffect(new MobEffectInstance(high, mdma ? MDMA_TICKS : METH_TICKS, level, false, false, true));
        // Another dose pushes the comedown back.
        player.removeEffect(mdma ? ModEffects.COMEDOWN : ModEffects.METH_CRASH);
    }

    /** MDMA, every second: love for everyone around, and the jaw grinding. */
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
            s.heat = Pharmacology.heatStep(s.heat, rolling + 0.5f * tweak, player.isSprinting(), hot, player.isInWaterOrRain());
            if (s.heat >= Pharmacology.OVERHEATED) {
                player.addEffect(new MobEffectInstance(ModEffects.HYPERTHERMIA, 40, 0, false, false, true));
            }
        }
        if (player.hasEffect(ModEffects.TWEAK)) {
            s.awake++;
            if (s.awake > PSYCHOSIS_AFTER && !player.hasEffect(ModEffects.PSYCHOSIS) && player.getRandom().nextFloat() < 0.01f) {
                player.addEffect(new MobEffectInstance(ModEffects.PSYCHOSIS, PSYCHOSIS_TICKS, 0, false, false, true));
            }
        } else if (player.isSleeping()) {
            s.awake = 0;
        }
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
        } else if (instance.is(ModEffects.TWEAK)) {
            entity.addEffect(new MobEffectInstance(ModEffects.METH_CRASH, 3600 + 1800 * instance.getAmplifier(), 0));
        }
    }
}
