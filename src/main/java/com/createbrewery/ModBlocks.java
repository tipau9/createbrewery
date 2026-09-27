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
            prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.LaserProjectorBlock> LASER_PROJECTOR = REGISTRATE
        .block("laser_projector", com.createbrewery.block.club.LaserProjectorBlock::new)
        .lang("Club Laser")
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((ctx, prov) -> prov.directionalBlock(ctx.getEntry(),
            prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
        .simpleItem()
        .register();

    public static final BlockEntry<com.createbrewery.block.club.FogMachineBlock> FOG_MACHINE = REGISTRATE
        .block("fog_machine", com.createbrewery.block.club.FogMachineBlock::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .blockstate((ctx, prov) -> prov.horizontalBlock(ctx.getEntry(),
            prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
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
