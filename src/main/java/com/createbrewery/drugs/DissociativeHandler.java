package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Handles server-side logic for dissociatives:
 * - Ketamine (K-hole, anesthesia, motor decoupling, bladder wear)
 * - Nitrous Oxide / Lachgas (wah-wah giggles, hypoxia, B12 depletion)
 */
public final class DissociativeHandler {
    private DissociativeHandler() {}

    public static final int KETA_TICKS = 2400;
    public static final int K_HOLE_TICKS = 600;
    public static final int WAH_TICKS = 300;
    private static final int MAX_LEVEL = 3;

    private static final ResourceKey<DamageType> BLADDER = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "keta_bladder"));

    private static final String[] NUMB = {
        "createbrewery.thought.dissociative.numb.0", "createbrewery.thought.dissociative.numb.1",
        "createbrewery.thought.dissociative.numb.2"
    };

    public static void take(Player player, DrugServer.Kind kind) {
        if (kind == DrugServer.Kind.LACHGAS) {
            MobEffectInstance before = player.getEffect(ModEffects.WAH);
            player.addEffect(new MobEffectInstance(ModEffects.WAH, DrugEffect.doseTicks(before, ModEffects.WAH, WAH_TICKS),
                before == null ? 0 : Math.min(MAX_LEVEL, before.getAmplifier() + 1), false, false, true));
            DrunkServer.state(player).b12 = Math.min(1f, DrunkServer.state(player).b12 + 0.05f);
            return;
        }

        if (kind != DrugServer.Kind.KETA) return;

        DrunkServer.state(player).bladder = Math.min(1f, DrunkServer.state(player).bladder + 0.06f);
        MobEffectInstance before = player.getEffect(ModEffects.KETA_HIGH);
        int level = before == null ? 0 : Math.min(MAX_LEVEL, before.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(ModEffects.KETA_HIGH, DrugEffect.doseTicks(before, ModEffects.KETA_HIGH, KETA_TICKS), level, false, false, true));

        int holeAt = player.hasEffect(ModEffects.WEED_HIGH) ? 1 : 2;
        if (level >= holeAt) {
            MobEffectInstance hole = player.getEffect(ModEffects.K_HOLE);
            player.addEffect(new MobEffectInstance(ModEffects.K_HOLE, DrugEffect.doseTicks(hole, ModEffects.K_HOLE, K_HOLE_TICKS),
                hole == null ? 0 : 1, false, false, true));
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            float bleed = DrugServer.mixed(player) ? 0.35f : level >= 2 ? 0.08f * (level - 1) : 0f;
            if (player.getRandom().nextFloat() < bleed) StimulantHandler.nosebleed(player, serverLevel);
        }
    }

    public static void ketaTick(LivingEntity entity, int level) {
        StimulantHandler.checkMix(entity);
        if (!(entity instanceof Player player)) return;
        float blood = DrunkServer.state(player).blood;
        float strength = DrugEffect.strength(player, ModEffects.KETA_HIGH);
        if (blood >= Intoxication.MERRY && player.getRandom().nextFloat() < (0.02f + 0.02f * level) * strength) {
            DrunkServer.blackout(player);
        }
        if (blood >= Intoxication.TIPSY && player.getRandom().nextFloat() < 0.015f * strength) {
            DrunkServer.vomit(player);
        }
    }

    public static void wahTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        float strength = DrugEffect.strength(player, ModEffects.WAH);
        if (player.getRandom().nextFloat() < 0.25f * strength) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GIGGLE.get(),
                SoundSource.PLAYERS, 1.0f, 1.1f + player.getRandom().nextFloat() * 0.2f);
        }
        if (player.getAirSupply() <= 0) DrunkServer.blackout(player);
        if (DrunkServer.state(player).blood >= Intoxication.TIPSY) {
            player.addEffect(new MobEffectInstance(ModEffects.STUMBLE, 40, 0, false, false, true));
        }
    }

    public static boolean hypoxic(LivingEntity entity) {
        MobEffectInstance wah = entity.getEffect(ModEffects.WAH);
        return wah != null && wah.getAmplifier() >= 2;
    }

    public static void wear(Player player) {
        DrunkState s = DrunkServer.state(player);
        if (s.b12 > 0f) {
            s.b12 = Math.max(0f, s.b12 - 0.0002f);
            if (s.b12 >= 0.35f) {
                player.addEffect(new MobEffectInstance(ModEffects.NUMBNESS, 60, s.b12 >= 0.7f ? 1 : 0, false, false, true));
                if (s.b12 >= 0.7f) player.addEffect(new MobEffectInstance(ModEffects.STUMBLE, 40, 0, false, false, false));
                if (player.getRandom().nextFloat() < 1f / 180f) DrugServer.think(player, NUMB[player.getRandom().nextInt(NUMB.length)], 0xA0B8C8);
            }
        }
        if (s.bladder > 0f) {
            s.bladder = Math.max(0f, s.bladder - 0.00005f);
            if (s.bladder >= 0.25f && player.getRandom().nextFloat() < 0.006f * s.bladder) {
                if (s.bladder >= 0.6f) {
                    player.hurt(new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(BLADDER)), 1f);
                    DrugServer.think(player, "createbrewery.thought.dissociative.blood", 0xD06060);
                } else {
                    DrugServer.think(player, "createbrewery.thought.dissociative.again", 0xC8C890);
                }
            }
        }
    }

    public static void expired(LivingEntity entity, MobEffectInstance instance, float worse) {
        if (instance.is(ModEffects.KETA_HIGH) || instance.is(ModEffects.K_HOLE)) {
            entity.addEffect(new MobEffectInstance(ModEffects.DAZED, (int) (1200 * worse), 0));
        }
    }
}
