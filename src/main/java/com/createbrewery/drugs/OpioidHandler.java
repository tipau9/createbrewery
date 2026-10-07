package com.createbrewery.drugs;

import com.createbrewery.effect.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Handles server-side logic for opioids, sedatives, and overdose emergencies:
 * - Heroin (nodding, intense analgesia, respiratory depression)
 * - Xanax (anxiolytic calm, memory gaps, blackout potentiation)
 * - Naloxone (immediate opioid receptor antagonism, rebound withdrawal)
 * - Respiratory depression and suffocation event handling
 */
public final class OpioidHandler {
    private OpioidHandler() {}

    public static void take(Player player, DrugServer.Kind kind) {
        Opioids.take(player, kind);
    }

    public static void body(Player player) {
        Opioids.body(player);
    }

    public static void expired(LivingEntity entity, MobEffectInstance instance) {
        if (instance.is(ModEffects.NALOXONE) && entity.hasEffect(ModEffects.NOD) && entity instanceof Player player) {
            DrugServer.think(player, "createbrewery.thought.opioid.back", 0xC8A060);
        }
    }

    public static void onBreathe(LivingBreatheEvent event) {
        LivingEntity entity = event.getEntity();
        boolean choking = entity.hasEffect(ModEffects.ASPIRATION);
        if ((choking || entity.hasEffect(ModEffects.RESPIRATORY_DEPRESSION) || DissociativeHandler.hypoxic(entity))
            && !DrugServer.helped(entity)) {
            event.setCanBreathe(false);
            event.setConsumeAirAmount(entity.getAirSupply() > 0 ? (choking ? 5 : 2) : 0);
        }
    }

    public static void onDrown(LivingDrownEvent event) {
        if (event.getEntity().hasEffect(ModEffects.ASPIRATION)
            || event.getEntity().hasEffect(ModEffects.RESPIRATORY_DEPRESSION)
            || DissociativeHandler.hypoxic(event.getEntity())) {
            event.setDrowning(false);
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().hasEffect(ModEffects.NOD)) {
            event.setAmount(event.getAmount() * Opioids.painFactor(event.getEntity()));
        }
        if (event.getEntity().hasEffect(ModEffects.ROLLING)) {
            event.setAmount(event.getAmount() * (1f - 0.3f * DrugEffect.felt(event.getEntity(), ModEffects.ROLLING)));
        }
    }
}
