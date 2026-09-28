package com.createbrewery.data;

import com.createbrewery.CreateBrewery;
import com.createbrewery.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootTableIdCondition;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registers the {@code createbrewery:add_item} global loot modifier serializer (see
 * {@link AddItemLootModifier}) and, at datagen time, the actual modifier instances for task-8
 * Steps 1 and 2: barley seeds out of short/tall grass, and hop cones into the plains village
 * house chest. Global loot modifiers in NeoForge 1.21.1 are entirely data-driven — the codec is
 * registered to {@code NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS} (confirmed via
 * javap on neoforge-21.1.228-universal.jar) and the actual per-modifier JSON is written by a
 * {@link GlobalLootModifierProvider} subclass hooked into {@link GatherDataEvent}, mirroring how
 * NeoForge's own {@code GlobalLootModifierProvider} is documented/used upstream.
 */
public class ModLootModifiers {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> GLM_SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, CreateBrewery.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddItemLootModifier>> ADD_ITEM =
        GLM_SERIALIZERS.register("add_item", () -> AddItemLootModifier.CODEC);

    // Roughly half of vanilla's 0.125 random_chance for wheat_seeds out of short_grass/tall_grass
    // (confirmed by extracting assets/minecraft/loot_table/blocks/{short_grass,tall_grass}.json
    // from minecraft_1.21.1_client.jar: both use a 0.125 random_chance condition, not a weighted
    // pool entry).
    private static final float GRASS_SEED_CHANCE = 0.0625F;
    private static final float CHEST_HOP_CONE_CHANCE = 0.3F;
    /** Hemp seeds are rarer than barley in the grass: a few to start a grow, then it seeds itself. */
    private static final float HEMP_SEED_CHANCE = 0.0125F;
    /** A seedling now and then out of jungle leaves: the start of a plantation, not a harvest. */
    private static final float COCA_SEEDLING_CHANCE = 0.025F;

    public static void register(IEventBus modEventBus) {
        GLM_SERIALIZERS.register(modEventBus);
        modEventBus.addListener(ModLootModifiers::gatherData);
    }

    private static void gatherData(GatherDataEvent event) {
        event.createProvider((output, lookup) -> new GlobalLootModifierProvider(output, lookup, CreateBrewery.MOD_ID) {
            @Override
            protected void start() {
                add("barley_seeds_from_short_grass", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/short_grass")).build(),
                        LootItemRandomChanceCondition.randomChance(GRASS_SEED_CHANCE).build()
                    },
                    ModItems.BARLEY_SEEDS.get(), 1));

                add("barley_seeds_from_tall_grass", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/tall_grass")).build(),
                        LootItemRandomChanceCondition.randomChance(GRASS_SEED_CHANCE).build()
                    },
                    ModItems.BARLEY_SEEDS.get(), 1));

                add("hemp_seeds_from_short_grass", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/short_grass")).build(),
                        LootItemRandomChanceCondition.randomChance(HEMP_SEED_CHANCE).build()
                    },
                    ModItems.HEMP_SEEDS.get(), 1));

                add("coca_seedling_from_jungle_leaves", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/jungle_leaves")).build(),
                        LootItemRandomChanceCondition.randomChance(COCA_SEEDLING_CHANCE).build()
                    },
                    ModItems.COCA_SEEDLING.get(), 1));

                add("hop_cones_in_village_plains_house", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "chests/village/village_plains_house")).build(),
                        LootItemRandomChanceCondition.randomChance(CHEST_HOP_CONE_CHANCE).build()
                    },
                    ModItems.HOP_CONES.get(), 2));
            }
        });
    }
}
