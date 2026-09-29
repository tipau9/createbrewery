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
    /** The other grows start from a rare find too. */
    private static final float SPORES_CHANCE = 0.04F, PEYOTE_PUP_CHANCE = 0.03F, OPIUM_POPPY_SEEDS_CHANCE = 0.05F;
    /** Ergot: now and then a ripe ear carries the fungus. */
    private static final float ERGOT_CHANCE = 0.01F;
    /** Wine, tequila and gin start from a find too. */
    private static final float GRAPE_CUTTING_CHANCE = 0.04F, AGAVE_PUP_CHANCE = 0.03F, JUNIPER_CHANCE = 0.03F;

    private static LootItemCondition ripe(net.minecraft.world.level.block.Block crop) {
        return net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(crop)
            .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties()
                .hasProperty(net.minecraft.world.level.block.CropBlock.AGE, net.minecraft.world.level.block.CropBlock.MAX_AGE)).build();
    }

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

                add("mushroom_spores_from_brown_mushroom", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/brown_mushroom")).build(),
                        LootItemRandomChanceCondition.randomChance(SPORES_CHANCE).build()
                    },
                    ModItems.MUSHROOM_SPORES.get(), 1));

                add("peyote_pup_from_dead_bush", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/dead_bush")).build(),
                        LootItemRandomChanceCondition.randomChance(PEYOTE_PUP_CHANCE).build()
                    },
                    ModItems.PEYOTE_PUP.get(), 1));

                add("opium_poppy_seeds_from_poppy", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/poppy")).build(),
                        LootItemRandomChanceCondition.randomChance(OPIUM_POPPY_SEEDS_CHANCE).build()
                    },
                    ModItems.OPIUM_POPPY_SEEDS.get(), 1));

                add("ergot_from_wheat", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/wheat")).build(),
                        ripe(net.minecraft.world.level.block.Blocks.WHEAT),
                        LootItemRandomChanceCondition.randomChance(ERGOT_CHANCE).build()
                    },
                    ModItems.ERGOT.get(), 1));

                add("ergot_from_barley", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "blocks/barley_crop")).build(),
                        ripe(com.createbrewery.ModBlocks.BARLEY_CROP.get()),
                        LootItemRandomChanceCondition.randomChance(ERGOT_CHANCE).build()
                    },
                    ModItems.ERGOT.get(), 1));

                add("grape_cutting_from_vine", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/vine")).build(),
                        LootItemRandomChanceCondition.randomChance(GRAPE_CUTTING_CHANCE).build()
                    },
                    ModItems.GRAPE_CUTTING.get(), 1));

                add("agave_pup_from_cactus", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/cactus")).build(),
                        LootItemRandomChanceCondition.randomChance(AGAVE_PUP_CHANCE).build()
                    },
                    ModItems.AGAVE_PUP.get(), 1));

                add("juniper_berries_from_spruce_leaves", new AddItemLootModifier(
                    new LootItemCondition[] {
                        LootTableIdCondition.builder(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/spruce_leaves")).build(),
                        LootItemRandomChanceCondition.randomChance(JUNIPER_CHANCE).build()
                    },
                    ModItems.JUNIPER_BERRIES.get(), 1));

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
