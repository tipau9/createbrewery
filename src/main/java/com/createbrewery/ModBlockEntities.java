package com.createbrewery;

import com.createbrewery.block.entity.FermenterBlockEntity;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.neoforged.neoforge.capabilities.Capabilities;

public class ModBlockEntities {
    private static final CreateRegistrate REGISTRATE = CreateBrewery.REGISTRATE;

    public static final BlockEntityEntry<FermenterBlockEntity> FERMENTER = REGISTRATE
        .blockEntity("fermenter", FermenterBlockEntity::new)
        .validBlocks(ModBlocks.FERMENTER)
        // Without these the tanks and the yeast slot are invisible to pipes, spouts and
        // funnels, so the block could never be fed or drained by a Create contraption.
        .registerCapability(event -> {
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.FERMENTER.get(),
                (be, side) -> new CombinedTankWrapper(
                    be.getInputTank().getCapability(), be.getOutputTank().getCapability()));
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FERMENTER.get(),
                (be, side) -> be.getYeastSlot());
        })
        .register();

    public static void register() {}
}
