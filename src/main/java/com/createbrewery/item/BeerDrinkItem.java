package com.createbrewery.item;

import com.createbrewery.effect.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

public class BeerDrinkItem extends Item {
    private final Supplier<? extends ItemLike> returnItemSupplier;

    public BeerDrinkItem(Properties properties, Supplier<? extends ItemLike> returnItemSupplier) {
        super(properties);
        this.returnItemSupplier = returnItemSupplier;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (entity instanceof Player player && !level.isClientSide) {
            // Determine current inebriation stage (0 = Stufe I, 1 = Stufe II, 2 = Stufe III, 3 = Stufe IV)
            MobEffectInstance currentInebriation = player.getEffect(ModEffects.INEBRIATION);
            int currentStage = (currentInebriation != null) ? currentInebriation.getAmplifier() : -1;
            int nextStage = Math.min(3, currentStage + 1);

            int inebriationDuration = 1200 + nextStage * 300;
            player.addEffect(new MobEffectInstance(ModEffects.INEBRIATION, inebriationDuration, nextStage, false, true, true));

            // Progressive effect scaling with each beer
            switch (nextStage) {
                case 0 -> {
                    // Stufe I (1. Bier - Leichter Schwips)
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8f, 1.2f);
                    player.addEffect(new MobEffectInstance(ModEffects.STUMBLE, 400, 0));
                    player.displayClientMessage(
                        Component.literal("\u00a7e\u00a7l*ZISCH!* \u00a7aEin angenehmes Prickeln... Der erste Schluck tut gut! \u00a77(Trunkenheit I)"), true);
                }
                case 1 -> {
                    // Stufe II (2. Bier - Angeheitert)
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0f, 0.9f);
                    double angle = player.getRandom().nextDouble() * Math.PI * 2;
                    player.setDeltaMovement(player.getDeltaMovement().add(Math.cos(angle) * 0.25, 0.08, Math.sin(angle) * 0.25));
                    player.hurtMarked = true;

                    player.addEffect(new MobEffectInstance(ModEffects.STUMBLE, 600, 0));
                    player.addEffect(new MobEffectInstance(ModEffects.HICCUPS, 300, 0));
                    player.displayClientMessage(
                        Component.literal("\u00a76\u00a7l*HICK* \u00a7eDie Welt f\u00e4ngt langsam an zu schaukeln... \u00a77(Trunkenheit II)"), true);
                }
                case 2 -> {
                    // Stufe III (3. Bier - Betrunken & Gr\u00f6\u00dfenwahn)
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.2f, 0.7f);
                    double angle = player.getRandom().nextDouble() * Math.PI * 2;
                    player.setDeltaMovement(player.getDeltaMovement().add(Math.cos(angle) * 0.38, 0.12, Math.sin(angle) * 0.38));
                    player.hurtMarked = true;

                    player.addEffect(new MobEffectInstance(ModEffects.STUMBLE, 800, 1));
                    player.addEffect(new MobEffectInstance(ModEffects.HICCUPS, 500, 0));
                    player.addEffect(new MobEffectInstance(ModEffects.DELIRIUM, 500, 0));
                    player.displayClientMessage(
                        Component.literal("\u00a7c\u00a7l*R\u00dc\u00dc\u00dcLPS!* \u00a76Das Bier steigt dir geh\u00f6rig zu Kopf! \u00a77(Trunkenheit III)"), true);
                }
                default -> {
                    // Stufe IV+ (4+ Bier - Kater des Todes & Vollabsturz)
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.4f, 0.5f);
                    double angle = player.getRandom().nextDouble() * Math.PI * 2;
                    player.setDeltaMovement(player.getDeltaMovement().add(Math.cos(angle) * 0.5, 0.15, Math.sin(angle) * 0.5));
                    player.hurtMarked = true;

                    player.addEffect(new MobEffectInstance(ModEffects.HANGOVER, 1000, 0));
                    player.addEffect(new MobEffectInstance(ModEffects.STUMBLE, 900, 2));
                    player.addEffect(new MobEffectInstance(ModEffects.HICCUPS, 600, 1));
                    player.addEffect(new MobEffectInstance(ModEffects.DELIRIUM, 600, 1));
                    player.displayClientMessage(
                        Component.literal("\u00a74\u00a7lABSTURZ! \u00a7cFilmriss droht! Dein Sch\u00e4del explodiert und die Beine versagen! \u00a77(Trunkenheit IV)"), true);
                }
            }

            // Return empty container if in survival
            if (!player.getAbilities().instabuild && returnItemSupplier != null) {
                ItemLike returnItem = returnItemSupplier.get();
                if (returnItem != null) {
                    ItemStack returnStack = new ItemStack(returnItem);
                    if (stack.isEmpty()) {
                        return returnStack;
                    }
                    if (!player.getInventory().add(returnStack)) {
                        player.drop(returnStack, false);
                    }
                }
            }
        }

        return result;
    }
}
