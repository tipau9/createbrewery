package com.createbrewery.event;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.drunk.Intoxication;
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
     * Chat Distortion: Slur messages when drunk, scaling with inebriation stage!
     */
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        float bac = DrunkServer.state(player).blood;

        if (bac >= Intoxication.TIPSY) {
            int stage = Intoxication.slurStage(bac); // 0 = occasional slip ... 3 = barely legible
            String original = event.getRawText();
            String slurred = slurText(original, player.getRandom(), stage);
            event.setMessage(Component.literal(slurred));
        } else if (player.hasEffect(ModEffects.HANGOVER)) {
            // Hungover player also mumbles occasionally
            String original = event.getRawText();
            String slurred = slurText(original, player.getRandom(), 1);
            event.setMessage(Component.literal(slurred));
        }
    }

    private static String slurText(String text, RandomSource random, int stage) {
        if (stage == 0 && random.nextFloat() > 0.35f) {
            return text; // Level I: only 35% chance to slur slightly
        }

        StringBuilder sb = new StringBuilder();
        float stretchChance = 0.15f + (stage * 0.15f);

        for (char c : text.toCharArray()) {
            char lower = Character.toLowerCase(c);
            if (lower == 's' && stage >= 1) {
                sb.append(random.nextBoolean() ? "shh" : "sch");
            } else if (lower == 'z' && stage >= 2) {
                sb.append("tss");
            } else if ((lower == 'a' || lower == 'e' || lower == 'o' || lower == 'u') && random.nextFloat() < stretchChance) {
                sb.append(c).append(c);
                if (stage >= 2 && random.nextFloat() < 0.4f) {
                    sb.append(c); // triple vowel: "haallloo"
                }
            } else {
                sb.append(c);
            }
        }

        // Random hiccup / burp at end of message (more frequent at higher stages)
        float hiccupChance = 0.25f + (stage * 0.22f);
        if (random.nextFloat() < hiccupChance) {
            String[] hiccups = { " *hick*", " *r\u00fclps*", "... *hicks*", " *hik!*", " ...waasss?" };
            sb.append(hiccups[random.nextInt(hiccups.length)]);
        }

        return sb.toString();
    }

    /**
     * Sobering Up: Drinking water steps down the inebriation tier and shortens effect durations.
     */
    @SubscribeEvent
    public static void onFinishDrinking(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            ItemStack stack = event.getItem();
            if (stack.is(Items.POTION)) {
                PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
                if (contents != null && contents.is(Potions.WATER)) {
                    DrunkState state = DrunkServer.state(player);
                    boolean drunk = state.total() > 0f;
                    boolean hungover = player.hasEffect(ModEffects.HANGOVER);

                    if (drunk || hungover) {
                        // Water dilutes the stomach and takes 0.15 per mille off the blood;
                        // it shortens a hangover and calms hiccups, but it is no instant cure.
                        if (drunk) DrunkServer.water(player);
                        reduceDuration(player, ModEffects.HANGOVER, 260);
                        reduceDuration(player, ModEffects.HICCUPS, 260);

                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 1.1f);
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
