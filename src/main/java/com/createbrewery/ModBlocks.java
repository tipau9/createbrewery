package com.createbrewery;

import com.createbrewery.block.BarleyCropBlock;
import com.createbrewery.block.FermenterBlock;
import com.createbrewery.block.HopsCropBlock;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;

public class ModBlocks {
    private static final CreateRegistrate REGISTRATE = CreateBrewery.REGISTRATE;

    public static final BlockEntry<BarleyCropBlock> BARLEY_CROP = REGISTRATE
        .block("barley_crop", BarleyCropBlock::new)
        .initialProperties(() -> Blocks.WHEAT)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.CROPS)
        .blockstate(ModBlocks::cropBlockstate)
        .loot((p, b) -> {
            var condition = net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(b)
                .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties().hasProperty(CropBlock.AGE, 7));
            p.add(b, p.createCropDrops(b, ModItems.BARLEY.get(), ModItems.BARLEY_SEEDS.get(), condition));
        })
        .register();

    public static final BlockEntry<HopsCropBlock> HOPS_CROP = REGISTRATE
        .block("hops_crop", HopsCropBlock::new)
        .initialProperties(() -> Blocks.WHEAT)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .tag(BlockTags.CROPS)
        .blockstate(ModBlocks::cropBlockstate)
        .loot((p, b) -> {
            var condition = net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(b)
                .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties().hasProperty(CropBlock.AGE, 7));
            p.add(b, p.createCropDrops(b, ModItems.HOP_CONES.get(), ModItems.HOP_CONES.get(), condition));
        })
        .register();

    // Its item is the seedling (ModItems.COCA_SEEDLING): one drops at any age, so a harvester that
    // replants keeps its seedling and hands on only the leaves.
    public static final BlockEntry<com.createbrewery.block.CocaBushBlock> COCA_BUSH = REGISTRATE
        .block("coca_bush", com.createbrewery.block.CocaBushBlock::new)
        .initialProperties(() -> Blocks.SWEET_BERRY_BUSH)
        .blockstate((ctx, prov) -> prov.getVariantBuilder(ctx.getEntry()).forAllStates(state -> {
            int age = state.getValue(com.createbrewery.block.CocaBushBlock.AGE);
            return new ConfiguredModel[] {
                new ConfiguredModel(prov.models()
                    .withExistingParent(ctx.getName() + "_stage" + age, prov.mcLoc("block/cross"))
                    .texture("cross", prov.modLoc("block/" + ctx.getName() + "_stage" + age))
                    .renderType("minecraft:cutout"))
            };
        }))
        .loot((p, b) -> p.add(b, net.minecraft.world.level.storage.loot.LootTable.lootTable()
            .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(ModItems.COCA_SEEDLING.get())))
            .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                .when(net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(b)
                    .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties()
                        .hasProperty(com.createbrewery.block.CocaBushBlock.AGE, com.createbrewery.block.CocaBushBlock.MAX_AGE)))
                .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(ModItems.COCA_LEAF.get())
                    .apply(net.minecraft.world.level.storage.loot.functions.SetItemCountFunction.setCount(
                        net.minecraft.world.level.storage.loot.providers.number.UniformGenerator.between(
                            com.createbrewery.block.CocaBushBlock.MIN_LEAVES, com.createbrewery.block.CocaBushBlock.MAX_LEAVES)))))))
        .register();

    // Its item is the seed (ModItems.HEMP_SEEDS). Young plants give their seed back; grown, a female
    // gives buds (seeded ones if a male got to her), a male only fibre.
    public static final BlockEntry<com.createbrewery.block.CannabisPlantBlock> CANNABIS_PLANT = REGISTRATE
        .block("cannabis_plant", com.createbrewery.block.CannabisPlantBlock::new)
        .initialProperties(() -> Blocks.WHEAT)
        .blockstate((ctx, prov) -> prov.getVariantBuilder(ctx.getEntry()).forAllStates(state -> {
            int age = state.getValue(com.createbrewery.block.CannabisPlantBlock.AGE);
            String tex = age < com.createbrewery.block.CannabisPlantBlock.SEXED ? "cannabis_stage" + age
                : (state.getValue(com.createbrewery.block.CannabisPlantBlock.FEMALE) ? "cannabis_female_stage" : "cannabis_male_stage") + age;
            return new ConfiguredModel[] {
                new ConfiguredModel(prov.models()
                    .withExistingParent(tex, prov.mcLoc("block/cross"))
                    .texture("cross", prov.modLoc("block/" + tex))
                    .renderType("minecraft:cutout"))
            };
        }))
        .loot((p, b) -> {
            java.util.function.IntFunction<net.minecraft.world.level.storage.loot.predicates.LootItemCondition.Builder> age = a ->
                net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(b)
                    .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties().hasProperty(com.createbrewery.block.CannabisPlantBlock.AGE, a));
            java.util.function.BiFunction<Boolean, Boolean, net.minecraft.world.level.storage.loot.predicates.LootItemCondition.Builder> kind = (female, seeded) ->
                net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(b)
                    .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties()
                        .hasProperty(com.createbrewery.block.CannabisPlantBlock.FEMALE, female).hasProperty(com.createbrewery.block.CannabisPlantBlock.SEEDED, seeded));
            java.util.function.Function<net.minecraft.world.level.ItemLike, net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer.Builder<?>> some = item ->
                net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(item).apply(net.minecraft.world.level.storage.loot.functions.SetItemCountFunction.setCount(
                    net.minecraft.world.level.storage.loot.providers.number.UniformGenerator.between(3, 5)));
            p.add(b, net.minecraft.world.level.storage.loot.LootTable.lootTable()
                // Too young to tell: the seed back.
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                    .when(net.minecraft.world.level.storage.loot.predicates.AnyOfCondition.anyOf(age.apply(0), age.apply(1), age.apply(2)))
                    .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(ModItems.HEMP_SEEDS.get())))
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool().when(age.apply(com.createbrewery.block.CannabisPlantBlock.MAX_AGE)).when(kind.apply(true, false))
                    .add(some.apply(ModItems.WET_BUDS.get())))
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool().when(age.apply(com.createbrewery.block.CannabisPlantBlock.MAX_AGE)).when(kind.apply(true, true))
                    .add(some.apply(ModItems.SEEDED_BUDS.get())))
                .withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                    .when(net.minecraft.world.level.storage.loot.predicates.AnyOfCondition.anyOf(age.apply(3), age.apply(4), age.apply(5)))
                    .when(net.minecraft.world.level.storage.loot.predicates.AnyOfCondition.anyOf(kind.apply(false, false), kind.apply(false, true)))
                    .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(ModItems.HEMP_FIBER.get())
                        .apply(net.minecraft.world.level.storage.loot.functions.SetItemCountFunction.setCount(net.minecraft.world.level.storage.loot.providers.number.UniformGenerator.between(1, 3))))));
        })
        .register();

    // simpleItem() is not decoration: without a BlockItem the fermenter cannot be placed
    // and Registrate's default loot table has nothing to drop.
    public static final BlockEntry<FermenterBlock> FERMENTER = REGISTRATE
        .block("fermenter", FermenterBlock::new)
        .initialProperties(() -> Blocks.BARREL)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.StrobeLightBlock> STROBE_LIGHT = REGISTRATE
        .block("strobe_light", com.createbrewery.block.club.StrobeLightBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(p -> p.noOcclusion().lightLevel(s -> s.getValue(com.createbrewery.block.club.StrobeLightBlock.LIT) ? 15 : 0))
        .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.LaserProjectorBlock> LASER_PROJECTOR = REGISTRATE
        .block("laser_projector", com.createbrewery.block.club.LaserProjectorBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.FogMachineBlock> FOG_MACHINE = REGISTRATE
        .block("fog_machine", com.createbrewery.block.club.FogMachineBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.SubwooferBlock> SUBWOOFER = REGISTRATE
        .block("subwoofer", com.createbrewery.block.club.SubwooferBlock::new)
        .initialProperties(() -> Blocks.NOTE_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.DjBoothBlock> DJ_BOOTH = REGISTRATE
        .block("dj_booth", com.createbrewery.block.club.DjBoothBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.SpeakerBlock> SPEAKER = REGISTRATE
        .block("speaker", com.createbrewery.block.club.SpeakerBlock::new)
        .initialProperties(() -> Blocks.NOTE_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.AmpRackBlock> AMP_RACK = REGISTRATE
        .block("amp_rack", com.createbrewery.block.club.AmpRackBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .lang("Amp Rack")
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.MicrophoneBlock> MICROPHONE = REGISTRATE
        .block("microphone", com.createbrewery.block.club.MicrophoneBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .lang("Microphone")
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.DmxConsoleBlock> DMX_CONSOLE = REGISTRATE
        .block("dmx_console", com.createbrewery.block.club.DmxConsoleBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .lang("DMX Console")
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.FixtureBlock> LED_BAR = REGISTRATE
        .block("led_bar", p -> new com.createbrewery.block.club.FixtureBlock(p, com.createbrewery.block.club.FixtureBlock.Kind.LED_BAR))
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .lang("LED Bar")
        .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.FixtureBlock> MOVING_HEAD = REGISTRATE
        .block("moving_head", p -> new com.createbrewery.block.club.FixtureBlock(p, com.createbrewery.block.club.FixtureBlock.Kind.MOVING_HEAD))
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .lang("Moving Head")
        .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.FixtureBlock> BLINDER = REGISTRATE
        .block("blinder", p -> new com.createbrewery.block.club.FixtureBlock(p, com.createbrewery.block.club.FixtureBlock.Kind.BLINDER))
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .lang("Blinder")
        .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.FixtureBlock> PAR_CAN = REGISTRATE
        .block("par_can", p -> new com.createbrewery.block.club.FixtureBlock(p, com.createbrewery.block.club.FixtureBlock.Kind.PAR))
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .lang("Par Can")
        .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.Co2JetBlock> CO2_JET = REGISTRATE
        .block("co2_jet", com.createbrewery.block.club.Co2JetBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.ColdSparkBlock> COLD_SPARK = REGISTRATE
        .block("cold_spark", com.createbrewery.block.club.ColdSparkBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .lang("Cold Spark Machine")
        .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.HazerBlock> HAZER = REGISTRATE
        .block("hazer", com.createbrewery.block.club.HazerBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .lang("Hazer")
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.BeerTapBlock> BEER_TAP = REGISTRATE
        .block("beer_tap", com.createbrewery.block.BeerTapBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    /**
     * Real 8-stage (age 0-7) crop blockstate/models, replacing Registrate's placeholder
     * single-variant cube default (Registrate's {@code BlockBuilder.defaultBlockstate()} has no
     * CropBlock special case — see task-D-report.md). Keyed on {@code CropBlock.AGE} exactly the
     * way vanilla wheat does it: parent model {@code minecraft:block/crop} (a cross-shaped model,
     * confirmed by extracting assets/minecraft/models/block/crop.json from
     * minecraft_1.21.1_client.jar) with one {@code "crop"}-textured child model per age. Calling
     * {@code .blockstate(...)} here *replaces* the queued default (Registrate's
     * {@code AbstractRegistrate.setDataGenerator} de-dupes per (entry, ProviderType) and removes
     * the previous consumer before adding the new one — confirmed by decompiling
     * AbstractRegistrate.class), so there is no double-registration/conflicting-variant hazard.
     *
     * <p>Stage textures are placeholders on purpose (brief: "do not spend effort on art") — all
     * 8 stage models reuse the single existing {@code block/<name>.png} placeholder texture; only
     * the model/blockstate *structure* is real.
     */
    private static <T extends CropBlock> void cropBlockstate(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov) {
        prov.getVariantBuilder(ctx.getEntry()).forAllStates(state -> {
            int age = state.getValue(CropBlock.AGE);
            return new ConfiguredModel[] {
                new ConfiguredModel(
                    prov.models()
                        .withExistingParent(ctx.getName() + "_stage" + age, prov.mcLoc("block/crop"))
                        .texture("crop", prov.modLoc("block/" + ctx.getName())))
            };
        });
    }

    public static void register() {}
}
