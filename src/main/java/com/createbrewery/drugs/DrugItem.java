package com.createbrewery.drugs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A bag of Koks or Keta held up to the nose, or a joint to the mouth (see DrugServer). */
public class DrugItem extends Item {
    private static final int COOLDOWN = 100;
    private final DrugServer.Kind kind;

    private final UseAnim anim;
    private final int duration;

    public DrugItem(Properties properties, DrugServer.Kind kind) {
        super(properties);
        this.kind = kind;
        this.anim = switch (kind) {
            case SHROOMS, MESCALINE, MDMA, XANAX -> UseAnim.EAT; // chewed, and they taste awful
            case WEED, DMT, LACHGAS -> UseAnim.TOOT_HORN;
            case HEROIN -> UseAnim.BOW;
            default -> UseAnim.NONE;
        };
        this.duration = switch (kind) {
            case WEED -> 30;      // a long drag on the joint
            case LSD -> 16;       // a tab on the tongue
            case SHROOMS -> 32;
            case MESCALINE -> 48; // tough, bitter cactus
            case DMT -> 40;       // one deep hit, held in
            case MDMA, XANAX -> 12; // a pill, swallowed
            case HEROIN -> 36;    // finding the vein
            case LACHGAS -> 20;   // one deep breath from the balloon
            default -> 0;
        };
    }

    public DrugServer.Kind kind() {
        return kind;
    }

    public static boolean isLit(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("Lit");
    }

    public static void setLit(ItemStack stack, boolean lit) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data != null ? data.copyTag() : new CompoundTag();
        tag.putBoolean("Lit", lit);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isLighter(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath().toLowerCase();
        boolean lighter = stack.is(Items.FLINT_AND_STEEL)
            || stack.is(Items.FIRE_CHARGE)
            || stack.getItem() instanceof net.minecraft.world.item.FlintAndSteelItem
            || id.getNamespace().equals("tobacconery") && path.contains("lighter")
            || path.contains("lighter") && !path.contains("fluid")
            || path.contains("feuerzeug");
        if (!lighter) return false;
        if (stack.isDamageableItem()) {
            // Tobacconery's empty lighter has damage >= maxDamage - 1
            int margin = id.getNamespace().equals("tobacconery") ? 1 : 0;
            return stack.getDamageValue() < stack.getMaxDamage() - margin;
        }
        return true;
    }

    public static ItemStack findLighter(Player player) {
        if (isLighter(player.getOffhandItem())) return player.getOffhandItem();
        if (isLighter(player.getMainHandItem())) return player.getMainHandItem();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isLighter(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltip, TooltipFlag flag) {
        if (kind == DrugServer.Kind.LACHGAS) return;
        if (kind == DrugServer.Kind.WEED) {
            boolean lit = isLit(stack);
            int hitsLeft = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            if (lit) {
                tooltip.add(Component.literal("§6Angezündet"));
            } else {
                tooltip.add(Component.literal("§7Nicht angezündet §8(Feuerzeug im Inventar benötigt)"));
            }
            tooltip.add(Component.literal("§aZüge: §f" + hitsLeft + " / " + stack.getMaxDamage()));
            return;
        }
        Purity purity = stack.get(Purity.PURITY.get());
        tooltip.add(purity != null && purity.tested() ? TestKitItem.result(purity)
            : Component.literal("Ungetestet - was drin ist, weiß keiner").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return anim;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return duration;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!DrugServer.enabled()) return InteractionResultHolder.fail(player.getItemInHand(hand));
        ItemStack stack = player.getItemInHand(hand);

        if (kind == DrugServer.Kind.WEED) {
            if (!isLit(stack)) {
                ItemStack lighter = findLighter(player);
                if (lighter.isEmpty()) {
                    if (level.isClientSide) {
                        player.displayClientMessage(Component.literal("§cDu brauchst ein Feuerzeug im Inventar, um den Joint anzuzünden."), true);
                    }
                    return InteractionResultHolder.fail(stack);
                }
                // Light with lighter from inventory or hands
                level.playSound(player, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS,
                    1.0f, level.random.nextFloat() * 0.4f + 0.8f);
                if (!level.isClientSide && !player.hasInfiniteMaterials()) {
                    if (lighter.is(Items.FLINT_AND_STEEL)) {
                        lighter.hurtAndBreak(1, (net.minecraft.server.level.ServerLevel) level,
                            player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null, item -> {});
                    } else if (lighter.isDamageableItem()) {
                        lighter.setDamageValue(Math.min(lighter.getMaxDamage(), lighter.getDamageValue() + 1));
                    } else if (lighter.is(Items.FIRE_CHARGE)) {
                        lighter.shrink(1);
                    }
                }
                setLit(stack, true);
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        boolean line = kind == DrugServer.Kind.COKE || kind == DrugServer.Kind.KETA || kind == DrugServer.Kind.METH;
        if (line) {
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.minecraft.advancements.CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
            }
            if (!level.isClientSide) {
                Purity purity = Purity.of(stack, kind, player.getRandom());
                int doses = Pharmacology.doses(purity.strength(), player.getRandom().nextFloat());
                java.util.function.Consumer<Player> dose = p -> {
                    if (!(purity.fentanyl() && kind == DrugServer.Kind.XANAX)) {
                        for (int i = 0; i < doses; i++) DrugServer.take(p, kind);
                        if (doses == 0 && kind != DrugServer.Kind.WEED) DrugServer.think(p, "Gestreckt… das merk ich kaum.", 0xA0A0A0);
                    }
                    if (purity.fentanyl()) Opioids.fentanyl(p);
                };
                // The dose kicks in only after the full routine (line chopped, sniffed, phone tucked away).
                DrugServer.later(player, Math.round(DrugPose.SNIFF_TICKS * DrugPose.DOSE_AT), dose);
                DrugPose.act(player, DrugPose.SNIFF, DrugPose.SNIFF_TICKS);
                player.getCooldowns().addCooldown(this, DrugPose.SNIFF_TICKS + 10);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (kind != DrugServer.Kind.WEED) return;
        if (!isLit(stack)) return;

        // Water or rain extinguishes the joint
        if (entity.isInWaterOrRain()) {
            setLit(stack, false);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.PLAYERS,
                0.7f, 1.2f + level.random.nextFloat() * 0.2f);
            if (level.isClientSide) {
                level.addParticle(ParticleTypes.SMOKE, entity.getX(), entity.getEyeY() - 0.2, entity.getZ(), 0, 0.05, 0);
            }
            return;
        }

        // Holding a lit joint: gentle smoke wisps rising
        if (level.isClientSide && entity instanceof LivingEntity le) {
            boolean inHand = le.getMainHandItem() == stack || le.getOffhandItem() == stack;
            if (inHand && level.random.nextFloat() < 0.25f) {
                double x = entity.getX() + (level.random.nextFloat() - 0.5) * 0.3;
                double y = entity.getEyeY() - 0.25;
                double z = entity.getZ() + (level.random.nextFloat() - 0.5) * 0.3;
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.015, 0);
            }
        }

        // Slow idle burn down (every 15 seconds)
        if (!level.isClientSide && entity.tickCount % 300 == 0) {
            if (stack.getDamageValue() + 1 >= stack.getMaxDamage()) {
                stack.shrink(1);
            } else {
                stack.setDamageValue(stack.getDamageValue() + 1);
            }
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        // Rain or a dunk put it out mid-drag: nothing to smoke.
        if (kind == DrugServer.Kind.WEED && !isLit(stack)) return stack;
        // Advancements: before the stack shrinks, or the last dose reads as air.
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.advancements.CriteriaTriggers.CONSUME_ITEM.trigger(sp, stack);
        }
        if (!level.isClientSide) {
            // A street batch: cut, normal or strong (any number of doses), maybe laced with fentanyl.
            Purity purity = Purity.of(stack, kind, player.getRandom());
            int doses = Pharmacology.doses(purity.strength(), player.getRandom().nextFloat());
            java.util.function.Consumer<Player> dose = p -> {
                // A fake Xanax bar is only the fentanyl; laced heroin is both.
                if (!(purity.fentanyl() && kind == DrugServer.Kind.XANAX)) {
                    for (int i = 0; i < doses; i++) DrugServer.take(p, kind);
                    if (doses == 0 && kind != DrugServer.Kind.WEED) DrugServer.think(p, "Gestreckt… das merk ich kaum.", 0xA0A0A0);
                }
                if (purity.fentanyl()) Opioids.fentanyl(p);
            };
            dose.accept(player);
            switch (kind) {
                case WEED -> DrugPose.act(player, DrugPose.SMOKE, 30);
                case DMT, LACHGAS -> DrugPose.act(player, DrugPose.INHALE, 30);
                case HEROIN -> DrugPose.act(player, DrugPose.INJECT, 40);
                default -> {}
            }
            // A joint is smoked hit by hit: one hit off its durability, a short breath between hits.
            player.getCooldowns().addCooldown(this, kind == DrugServer.Kind.WEED ? 30 : COOLDOWN);
        }
        if (kind == DrugServer.Kind.WEED) {
            if (player.hasInfiniteMaterials()) {
                // hurtAndBreak spares creative players; a joint still burns down.
                if (!level.isClientSide) {
                    if (stack.getDamageValue() + 1 >= stack.getMaxDamage()) stack.shrink(1);
                    else stack.setDamageValue(stack.getDamageValue() + 1);
                }
            } else {
                stack.hurtAndBreak(1, entity, LivingEntity.getSlotForHand(entity.getUsedItemHand()));
            }
        } else if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }
}
