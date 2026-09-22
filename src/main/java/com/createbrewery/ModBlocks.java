package com.createbrewery;

import com.createbrewery.block.BarleyCropBlock;
import com.createbrewery.block.FermenterBlock;
import com.createbrewery.block.HopsCropBlock;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {
    private static final CreateRegistrate REGISTRATE = CreateBrewery.REGISTRATE;

    public static final BlockEntry<BarleyCropBlock> BARLEY_CROP = REGISTRATE
        .block("barley_crop", BarleyCropBlock::new)
        .initialProperties(() -> Blocks.WHEAT)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .register();

    public static final BlockEntry<HopsCropBlock> HOPS_CROP = REGISTRATE
        .block("hops_crop", HopsCropBlock::new)
        .initialProperties(() -> Blocks.WHEAT)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .register();

    // simpleItem() is not decoration: without a BlockItem the fermenter cannot be placed
    // and Registrate's default loot table has nothing to drop.
    public static final BlockEntry<FermenterBlock> FERMENTER = REGISTRATE
        .block("fermenter", FermenterBlock::new)
        .initialProperties(() -> Blocks.BARREL)
        .properties(BlockBehaviour.Properties::noOcclusion)
        .simpleItem()
        .register();

    public static void register() {}
}
