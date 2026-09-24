package com.createbrewery.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Adds a fixed count of a single item to a loot table's results, gated by this modifier's own
 * {@code conditions} array (typically a {@code neoforge:loot_table_id} condition to target the
 * host table plus a {@code minecraft:random_chance} condition for drop rate). Modelled directly
 * on NeoForge's own {@code AddTableLootModifier} (decompiled from neoforge-21.1.228-universal.jar,
 * package net.neoforged.neoforge.common.loot) — that class adds an entire other loot table's
 * rolls; this one adds a single plain item stack, which is all Step 1/2 of the task-8 brief need
 * (no built-in NeoForge modifier does this).
 */
public class AddItemLootModifier extends LootModifier {
    public static final MapCodec<AddItemLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    IGlobalLootModifier.LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(m -> m.conditions),
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(m -> m.item),
                    Codec.INT.fieldOf("count").forGetter(m -> m.count))
            .apply(instance, AddItemLootModifier::new));

    private final Item item;
    private final int count;

    public AddItemLootModifier(LootItemCondition[] conditionsIn, Item item, int count) {
        super(conditionsIn);
        this.item = item;
        this.count = count;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        generatedLoot.add(new ItemStack(item, count));
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.ADD_ITEM.get();
    }
}
