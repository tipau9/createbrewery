package com.createbrewery.event;

import com.createbrewery.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

public class BreweryCommonEvents {

    /**
     * Chat Distortion: Slur messages when drunk/hungover (inspired by BreweryX)
     */
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        boolean isDrunk = player.hasEffect(ModEffects.DELIRIUM) 
                       || player.hasEffect(ModEffects.STUMBLE) 
                       || player.hasEffect(ModEffects.HANGOVER)
                       || player.hasEffect(ModEffects.HICCUPS);

        if (isDrunk) {
            String original = event.getRawText();
            String slurred = slurText(original, player.getRandom());
            event.setMessage(Component.literal(slurred));
        }
    }

    private static String slurText(String text, RandomSource random) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            char lower = Character.toLowerCase(c);
            if (lower == 's') {
                sb.append(random.nextBoolean() ? "shh" : "sch");
            } else if (lower == 'z') {
                sb.append("tss");
            } else if (lower == 'a' || lower == 'e' || lower == 'o' || lower == 'u') {
                sb.append(c);
                if (random.nextFloat() < 0.45f) {
                    sb.append(c).append(c); // stretch vowel: "haallloo"
                }
            } else {
                sb.append(c);
            }
        }

        // Randomly insert drunk hiccup / burp
        if (random.nextFloat() < 0.65f) {
            String[] hiccups = { " *hick*", " *r\u00fclps*", "... *hicks*", " *hik!*", " ...waasss?" };
            sb.append(hiccups[random.nextInt(hiccups.length)]);
        }

        return sb.toString();
    }

    /**
     * Sobering Up: Drinking water only SHORTENS remaining alcohol effect durations,
     * requiring multiple drinks to sober up completely.
     */
    @SubscribeEvent
    public static void onFinishDrinking(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            ItemStack stack = event.getItem();
            if (stack.is(Items.POTION)) {
                PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
                if (contents != null && contents.is(Potions.WATER)) {
                    boolean hadEffect = player.hasEffect(ModEffects.HANGOVER)
                                     || player.hasEffect(ModEffects.STUMBLE)
                                     || player.hasEffect(ModEffects.HICCUPS)
                                     || player.hasEffect(ModEffects.DELIRIUM);

                    if (hadEffect) {
                        // Shorten duration by 240 ticks (12 seconds) per bottle
                        int reductionTicks = 240;
                        reduceDuration(player, ModEffects.HANGOVER, reductionTicks);
                        reduceDuration(player, ModEffects.STUMBLE, reductionTicks);
                        reduceDuration(player, ModEffects.HICCUPS, reductionTicks);
                        reduceDuration(player, ModEffects.DELIRIUM, reductionTicks);

                        boolean stillDrunk = player.hasEffect(ModEffects.HANGOVER)
                                          || player.hasEffect(ModEffects.STUMBLE)
                                          || player.hasEffect(ModEffects.HICCUPS)
                                          || player.hasEffect(ModEffects.DELIRIUM);

                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 1.1f);

                        if (!stillDrunk) {
                            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.8f);
                            player.displayClientMessage(
                                Component.literal("\u00a7a\u00a7lVollst\u00e4ndig ern\u00fcchtert! \u00a72Das Wasser hat den Alkohol endg\u00fcltig neutralisiert."), true);
                        } else {
                            player.displayClientMessage(
                                Component.literal("\u00a7b\u00a7lEin Schluck Wasser... \u00a77Der Rausch l\u00e4sst etwas nach (-12s), aber du bist noch benebelt!"), true);
                        }
                    }
                }
            }
        }
    }

    private static void reduceDuration(Player player, Holder<MobEffect> effectHolder, int reductionTicks) {
        MobEffectInstance current = player.getEffect(effectHolder);
        if (current != null) {
            int newDuration = current.getDuration() - reductionTicks;
            int amplifier = current.getAmplifier();
            boolean ambient = current.isAmbient();
            boolean visible = current.isVisible();
            boolean showIcon = current.showIcon();

            player.removeEffect(effectHolder);
            if (newDuration > 0) {
                player.addEffect(new MobEffectInstance(
                    effectHolder, newDuration, amplifier, ambient, visible, showIcon
                ));
            }
        }
    }
}
