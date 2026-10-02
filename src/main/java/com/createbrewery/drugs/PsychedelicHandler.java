package com.createbrewery.drugs;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Handles server-side logic and lifecycle events for psychedelics:
 * - LSD (geometric hallucinations, time dilation, recursion)
 * - Psilocybin / Magic Mushrooms (organic shifts, emotional warmth)
 * - Mescaline / Peyote (desert hues, sacred geometry)
 * - DMT (waiting room, breakthrough entities, machine elves)
 */
public final class PsychedelicHandler {
    private PsychedelicHandler() {}

    public static void take(Player player, DrugServer.Kind kind) {
        Psychedelics.take(player, kind);
    }

    public static void expired(LivingEntity entity, MobEffectInstance instance) {
        Psychedelics.expired(entity, instance);
    }
}
