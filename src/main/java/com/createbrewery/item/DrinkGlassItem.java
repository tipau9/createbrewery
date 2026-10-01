package com.createbrewery.item;

import com.createbrewery.block.DrinkContent;
import com.createbrewery.block.DrinkGlassBlock;
import com.createbrewery.block.GlassType;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class DrinkGlassItem extends BlockItem {

    private final GlassType glassType;

    public DrinkGlassItem(Block block, Properties properties, GlassType glassType) {
        super(block, properties);
        this.glassType = glassType;
    }

    public GlassType getGlassType() {
        return glassType;
    }

    public static DrinkContent getContent(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return DrinkContent.EMPTY;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.contains("drink_content")) {
            try {
                return DrinkContent.valueOf(customData.copyTag().getString("drink_content"));
            } catch (Exception ignored) {}
        }
        return DrinkContent.EMPTY;
    }

    public static void setContent(ItemStack stack, DrinkContent content) {
        if (stack == null || stack.isEmpty()) return;
        if (content == DrinkContent.EMPTY) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.putString("drink_content", content.name());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public ItemStack withContent(DrinkContent content) {
        ItemStack copy = new ItemStack(this);
        setContent(copy, content);
        return copy;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        DrinkContent content = getContent(stack);
        if (content != DrinkContent.EMPTY) {
            tooltip.add(Component.translatable("createbrewery.drink_glass.filled",
                Component.translatable(content.getTranslationKey())).withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("createbrewery.drink_glass.empty_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Sneaking or empty glass: places block on the bar counter/table
        DrinkContent content = getContent(context.getItemInHand());
        if (content == DrinkContent.EMPTY || context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            return super.useOn(context);
        }
        // If not sneaking and filled, try placing. If it succeeds, placed!
        InteractionResult res = super.useOn(context);
        if (res.consumesAction()) {
            return res;
        }
        // Fallback to drinking
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        DrinkContent content = getContent(stack);

        if (content != DrinkContent.EMPTY) {
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.fail(stack);
            }
            return ItemUtils.startUsingInstantly(level, player, hand);
        }

        return super.use(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        DrinkContent content = getContent(stack);
        return content != DrinkContent.EMPTY ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        DrinkContent content = getContent(stack);
        if (content != DrinkContent.EMPTY && entity instanceof Player player) {
            if (!level.isClientSide) {
                float dose = content.getPerMille() * glassType.getAlcoholMultiplier();
                DrunkServer.drink(player, dose);
                player.getCooldowns().addCooldown(this, Intoxication.DRINK_COOLDOWN);

                if (player.hasEffect(ModEffects.PAINKILLER)) {
                    DrunkServer.irritateStomach(player);
                }

                if (content.isBeer()) {
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ModParticles.BEER_FOAM.get(), player.getX(), player.getEyeY() - 0.2, player.getZ(),
                            5, 0.08, 0.03, 0.08, 0.01);
                    }
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8f, 1.0f);
                }
            }

            if (!player.getAbilities().instabuild) {
                ItemStack empty = new ItemStack(this);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    return empty;
                }
                if (!player.addItem(empty)) {
                    player.drop(empty, false);
                }
            }
        }
        return stack;
    }
}
