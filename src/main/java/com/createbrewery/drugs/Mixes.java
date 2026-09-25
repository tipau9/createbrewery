package com.createbrewery.drugs;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Named mixes, on top of the shared heart and breath loads. Each shows as its own effect while
 * both are in the body (like the CK-Mix), which is also what their advancements listen for.
 * What each mix does lives where that drug is handled; this only spots them.
 *
 * <ul>
 *   <li>Candyflip (a trip + MDMA): warmer and stronger, and the fear stays away.</li>
 *   <li>Speedball (heroin + Koks or Crystal): the stimulant hides the nodding while both loads
 *       grow - and when it wears off first, the heroin hits all at once.</li>
 *   <li>Nachgelegt (a trip + weed): the trip gets stronger, and bad trips twice as likely.</li>
 *   <li>Gipfelsturm (a trip + Lachgas): a few seconds of breakthrough, like a small DMT.</li>
 *   <li>Ausgetrocknet (MDMA + alcohol): the body dries out and overheats far faster.</li>
 * </ul>
 */
public final class Mixes {
    private Mixes() {}

    /** Every second, for every player. */
    public static void tick(Player player) {
        boolean trip = Psychedelics.tripping(player) > 0.1f;
        if (trip && player.hasEffect(ModEffects.ROLLING)) mark(player, ModEffects.CANDYFLIP);
        if (speedball(player)) mark(player, ModEffects.SPEEDBALL);
        if (trip && player.hasEffect(ModEffects.WEED_HIGH)) mark(player, ModEffects.STONED_TRIP);
        if (trip && player.hasEffect(ModEffects.WAH)) {
            mark(player, ModEffects.NITROUS_PEAK);
            // Joins in at the fading end of a breakthrough: a short spike, not the whole journey.
            if (!player.hasEffect(ModEffects.BREAKTHROUGH)) {
                player.addEffect(new MobEffectInstance(ModEffects.BREAKTHROUGH, 200, 0, false, false, true));
            }
        }
        if (dehydrating(player)) mark(player, ModEffects.DEHYDRATED);
    }

    public static boolean speedball(LivingEntity entity) {
        return entity.hasEffect(ModEffects.NOD) && (entity.hasEffect(ModEffects.COKE_HIGH) || entity.hasEffect(ModEffects.TWEAK));
    }

    public static boolean dehydrating(Player player) {
        return player.hasEffect(ModEffects.ROLLING) && DrunkServer.state(player).blood >= Intoxication.TIPSY;
    }

    /** The stimulant of a speedball runs out first: the heroin was only being held back. */
    public static void expired(LivingEntity entity, MobEffectInstance instance) {
        if (!(instance.is(ModEffects.COKE_HIGH) || instance.is(ModEffects.TWEAK))) return;
        MobEffectInstance nod = entity.getEffect(ModEffects.NOD);
        if (nod != null && entity.getRandom().nextFloat() < 0.5f + 0.25f * nod.getAmplifier()) {
            entity.addEffect(new MobEffectInstance(ModEffects.RESPIRATORY_DEPRESSION, 200, 0, false, false, true));
        }
    }

    private static void mark(Player player, Holder<MobEffect> mix) {
        player.addEffect(new MobEffectInstance(mix, 60, 0, false, false, true));
    }
}
