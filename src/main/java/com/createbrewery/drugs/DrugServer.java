package com.createbrewery.drugs;

import com.createbrewery.Config;
import com.createbrewery.CreateBrewery;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/**
 * Koks and Keta, server side. Registered on NeoForge.EVENT_BUS.
 *
 * <p>Koks (stimulant): faster, stronger at mining, no hunger, no sleep, racing heart. Too much -
 * a third line while still high - risks a heart attack, and together with alcohol far more so
 * (the body makes cocaethylene, which is harder on the heart than either). It also masks how
 * drunk you are, so you drink more. Then the crash: tired, slow, grey, hungry.
 *
 * <p>Keta (dissociative): slow motion, the world far away, pain dulled. Too much tips into the
 * K-Loch, where the body barely obeys. With alcohol both depress the body together: blackouts
 * and vomiting become far likelier. Afterwards: dazed.
 *
 * <p>Both together ("CK"): Keta raises heart rate and blood pressure as well, so the heart is
 * strained even at small doses and beats irregularly; Koks masks the dissociation, so it feels
 * clearer than it is and the next dose comes sooner; orientation flips (left and right swap for
 * moments); two powders up the same nose make it bleed; and the comedown is longer and harder.
 */
public final class DrugServer {
    private DrugServer() {}

    public static final int COKE_TICKS = 3600;
    public static final int KETA_TICKS = 2400;
    public static final int K_HOLE_TICKS = 600;
    /** Doses stack up to this amplifier. */
    private static final int MAX_LEVEL = 3;

    private static final ResourceKey<DamageType> HEART_ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "heart_attack"));
    private static final ResourceKey<DamageType> NOSEBLEED = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "nosebleed"));

    public enum Kind { COKE, KETA }

    public static DamageSource heartAttack(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(HEART_ATTACK));
    }

    /** Both at once: Koks and Keta (or the K-Loch) active together. */
    public static boolean mixed(LivingEntity entity) {
        return entity.hasEffect(ModEffects.COKE_HIGH)
            && (entity.hasEffect(ModEffects.KETA_HIGH) || entity.hasEffect(ModEffects.K_HOLE));
    }

    /** Whether drugs are enabled in the config (off: the items do nothing). */
    public static boolean enabled() {
        return !Config.SPEC.isLoaded() || Config.ENABLE_DRUGS.get();
    }

    /** A line goes up the nose. Stacks with what is still active. */
    public static void take(Player player, Kind kind) {
        Holder<MobEffect> high = kind == Kind.COKE ? ModEffects.COKE_HIGH : ModEffects.KETA_HIGH;
        MobEffectInstance before = player.getEffect(high);
        int level = before == null ? 0 : Math.min(MAX_LEVEL, before.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(high, kind == Kind.COKE ? COKE_TICKS : KETA_TICKS, level, false, false, true));
        if (kind == Kind.COKE) player.removeEffect(ModEffects.COKE_CRASH); // a new line pushes the crash back
        if (kind == Kind.KETA && level >= 2) player.addEffect(new MobEffectInstance(ModEffects.K_HOLE, K_HOLE_TICKS, 0, false, false, true));

        if (player.level() instanceof ServerLevel level1) {
            // The other powder is still in there: the nose gives up now and then.
            if (mixed(player) && player.getRandom().nextFloat() < 0.35f) nosebleed(player, level1);
            Vec3 look = player.getLookAngle();
            level1.sendParticles(ModParticles.POWDER.get(), player.getX() + look.x * 0.3, player.getEyeY() - 0.1,
                player.getZ() + look.z * 0.3, 8, 0.06, 0.04, 0.06, 0.01);
            level1.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SNIFF.get(),
                SoundSource.PLAYERS, 1.0f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        }
    }

    private static void nosebleed(Player player, ServerLevel level) {
        player.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(NOSEBLEED)), 1f);
        Vec3 look = player.getLookAngle();
        level.sendParticles(ModParticles.NOSEBLEED.get(), player.getX() + look.x * 0.25, player.getEyeY() - 0.15,
            player.getZ() + look.z * 0.25, 6, 0.03, 0.02, 0.03, 0.0);
    }

    /** Keeps the CK-Mix indicator up while both are active; its own tick does the heart strain. */
    private static void checkMix(LivingEntity entity) {
        if (mixed(entity)) entity.addEffect(new MobEffectInstance(ModEffects.CK_MIX, 60, 0, false, false, true));
    }

    /** Heart under Koks and Keta at once: at risk from the first dose, far more with alcohol. */
    public static void mixTick(LivingEntity entity, int level) {
        if (entity.tickCount % 40 != 0) return;
        int doses = amp(entity, ModEffects.COKE_HIGH) + amp(entity, ModEffects.KETA_HIGH) + 2;
        float blood = entity instanceof Player p ? DrunkServer.state(p).blood : 0f;
        float chance = 0.04f * doses + (blood >= Intoxication.TIPSY ? 0.08f : 0f);
        if (entity.getRandom().nextFloat() < chance) {
            entity.hurt(heartAttack(entity.level()), 3f + doses + (blood >= Intoxication.TIPSY ? 2f : 0f));
        }
        // Nosebleeds keep coming back while both are in the system.
        if (entity instanceof Player player && player.level() instanceof ServerLevel level1
            && player.getRandom().nextFloat() < 0.05f) {
            nosebleed(player, level1);
        }
    }

    private static int amp(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    // ---- ticks, called from the effects every 20 ticks ----

    public static void cokeTick(LivingEntity entity, int level) {
        if (entity instanceof Player player) player.getFoodData().setExhaustion(0f); // no appetite
        checkMix(entity);
        if (entity.tickCount % 40 != 0) return;
        float blood = entity instanceof Player p ? DrunkServer.state(p).blood : 0f;
        // Heart attack: from a third line on, or earlier with alcohol in the blood (cocaethylene).
        float chance = 0f;
        if (level >= 2) chance = 0.12f + 0.08f * (level - 2);
        if (blood >= Intoxication.TIPSY && level >= 1) chance += 0.08f;
        if (blood >= Intoxication.DRUNK) chance += 0.1f;
        if (chance > 0f && entity.getRandom().nextFloat() < chance) {
            float damage = 4f + level * 2f + (blood >= Intoxication.TIPSY ? 3f : 0f);
            entity.hurt(heartAttack(entity.level()), damage);
        }
    }

    public static void crashTick(LivingEntity entity, int level) {
        if (entity instanceof Player player) player.causeFoodExhaustion(0.4f); // ravenous
    }

    public static void ketaTick(LivingEntity entity, int level) {
        checkMix(entity);
        if (!(entity instanceof Player player)) return;
        float blood = DrunkServer.state(player).blood;
        // Keta and alcohol together: the body switches off.
        if (blood >= Intoxication.MERRY && player.getRandom().nextFloat() < 0.02f + 0.02f * level) {
            DrunkServer.blackout(player);
        }
        if (blood >= Intoxication.TIPSY && player.getRandom().nextFloat() < 0.015f) DrunkServer.vomit(player);
    }

    // ---- events ----

    /** Every high ends in something: the crash after Koks, a daze after Keta. */
    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        LivingEntity entity = event.getEntity();
        if (instance == null || entity.level().isClientSide) return;
        // After a CK session everything lasts half again as long.
        float worse = entity.hasEffect(ModEffects.CK_MIX) ? 1.5f : 1f;
        if (instance.is(ModEffects.COKE_HIGH)) {
            entity.addEffect(new MobEffectInstance(ModEffects.COKE_CRASH, (int) ((1200 + 600 * instance.getAmplifier()) * worse), 0));
        } else if (instance.is(ModEffects.KETA_HIGH) || instance.is(ModEffects.K_HOLE)) {
            entity.addEffect(new MobEffectInstance(ModEffects.DAZED, (int) (1200 * worse), 0));
        }
    }

    /** Wide awake on Koks: no sleeping. */
    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (event.getEntity().hasEffect(ModEffects.COKE_HIGH)) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    /** Keta dulls pain. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasEffect(ModEffects.K_HOLE)) event.setAmount(event.getAmount() * 0.5f);
        else if (entity.hasEffect(ModEffects.KETA_HIGH)) event.setAmount(event.getAmount() * 0.7f);
    }
}
