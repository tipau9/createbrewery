package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.particle.ModParticles;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Handles server-side logic for stimulants:
 * - Cocaine (intense focus, appetite suppression, nosebleeds, craving, crash)
 * - Methamphetamine / Punding (repetitive action focus, sleep inhibition)
 * - MDMA / Ecstasy coordination
 * - CK-Mix interactions (Coke + Ketamine)
 */
public final class StimulantHandler {
    private StimulantHandler() {}

    public static final int COKE_TICKS = 3600;
    private static final int MAX_LEVEL = 3;

    private static final ResourceKey<DamageType> NOSEBLEED = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "nosebleed"));

    private static final String[] CRAVING = {
        "Nur noch eine Line…", "Eine noch, dann hör ich auf.", "Wo krieg ich jetzt noch was her?",
        "Ohne ist alles so flach.", "Ich bin so unruhig."
    };

    private static final String[] PUND = {
        "Noch einen. Perfekt. Noch einen.", "Genau so. Jeder gleich. Wunderschön.",
        "Ich könnte das ewig machen.", "Ordnung. Endlich Ordnung."
    };

    public static void take(Player player, DrugServer.Kind kind) {
        if (kind == DrugServer.Kind.MDMA || kind == DrugServer.Kind.METH) {
            Stimulants.take(player, kind);
            return;
        }
        if (kind != DrugServer.Kind.COKE) return;

        MobEffectInstance before = player.getEffect(ModEffects.COKE_HIGH);
        int level = before == null ? 0 : Math.min(MAX_LEVEL, before.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(ModEffects.COKE_HIGH, DrugEffect.doseTicks(before, ModEffects.COKE_HIGH, COKE_TICKS), level, false, false, true));

        DrunkServer.state(player).cokeHabit = Math.min(1f, DrunkServer.state(player).cokeHabit + 0.06f);
        player.removeEffect(ModEffects.COKE_CRASH);
        player.removeEffect(ModEffects.CRAVING);

        if (player.level() instanceof ServerLevel serverLevel) {
            float bleed = DrugServer.mixed(player) ? 0.35f : level >= 2 ? 0.08f * (level - 1) : 0f;
            if (player.getRandom().nextFloat() < bleed) nosebleed(player, serverLevel);
        }
    }

    public static void cokeTick(LivingEntity entity, int level) {
        if (entity instanceof Player player && DrugEffect.strength(entity, ModEffects.COKE_HIGH) > 0.3f) {
            player.getFoodData().setExhaustion(0f);
        }
        checkMix(entity);
    }

    public static void cravingTick(LivingEntity entity, int level) {
        if (entity instanceof Player player && player.getRandom().nextFloat() < 1f / 40f) {
            DrugServer.think(player, CRAVING[player.getRandom().nextInt(CRAVING.length)], 0xB0B0C8);
        }
    }

    public static void crashTick(LivingEntity entity, int level) {
        if (entity instanceof Player player) player.causeFoodExhaustion(0.4f);
    }

    public static void checkMix(LivingEntity entity) {
        if (DrugServer.mixed(entity)) {
            entity.addEffect(new MobEffectInstance(ModEffects.CK_MIX, 60, 0, false, false, true));
        }
    }

    public static void mixTick(LivingEntity entity, int level) {
        if (entity.tickCount % 40 != 0) return;
        if (entity instanceof Player player && player.level() instanceof ServerLevel serverLevel
            && player.getRandom().nextFloat() < 0.05f) {
            nosebleed(player, serverLevel);
        }
    }

    public static void nosebleed(Player player, ServerLevel level) {
        player.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(NOSEBLEED)), 1f);
        var look = player.getLookAngle();
        level.sendParticles(ModParticles.NOSEBLEED.get(), player.getX() + look.x * 0.25, player.getEyeY() - 0.15,
            player.getZ() + look.z * 0.25, 6, 0.03, 0.02, 0.03, 0.0);
    }

    public static void expired(LivingEntity entity, MobEffectInstance instance, float worse) {
        if (instance.is(ModEffects.COKE_HIGH)) {
            entity.addEffect(new MobEffectInstance(ModEffects.COKE_CRASH, (int) ((1200 + 600 * instance.getAmplifier()) * worse), 0));
            boolean hooked = entity instanceof Player p && DrunkServer.state(p).cokeHabit >= 0.3f;
            if (instance.getAmplifier() >= 1 || hooked) {
                entity.addEffect(new MobEffectInstance(ModEffects.CRAVING, 2400 * Math.max(1, instance.getAmplifier()), 0));
            }
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide || !player.hasEffect(ModEffects.TWEAK)) return;
        DrunkState s = DrunkServer.state(player);
        Block block = event.getState().getBlock();
        if (block == s.pundBlock) {
            s.pundStreak++;
            if (s.pundStreak % 6 == 0) {
                player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SPEED, 200, 1, false, false, true));
                DrugServer.think(player, PUND[player.getRandom().nextInt(PUND.length)], 0xA0E0FF);
            }
        } else {
            if (s.pundStreak >= 12) {
                player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 200, 0, false, false, true));
                DrugServer.think(player, "Nein, nein, NEIN. Ich war noch nicht fertig. Das war noch nicht fertig.", 0xE08080);
            }
            s.pundBlock = block;
            s.pundStreak = 1;
        }
    }

    public static void onSleep(CanPlayerSleepEvent event) {
        if (event.getEntity().hasEffect(ModEffects.COKE_HIGH) || event.getEntity().hasEffect(ModEffects.TWEAK)
            || event.getEntity().hasEffect(ModEffects.ROLLING)) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }
}
