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
        // Note the asymmetry in both: the fluid wrapper is built from the tanks' capability
        // handlers (input insert-only, output extract-only), and the yeast slot is exposed
        // through an insert-only view so a hopper cannot cancel a batch by robbing it.
        .registerCapability(event -> {
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.FERMENTER.get(),
                (be, side) -> new CombinedTankWrapper(
                    be.getInputTank().getCapability(), be.getOutputTank().getCapability()));
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.FERMENTER.get(),
                (be, side) -> be.getYeastInsertionHandler());
        })
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.StrobeLightBlockEntity> STROBE_LIGHT = REGISTRATE
        .blockEntity("strobe_light", com.createbrewery.block.club.StrobeLightBlockEntity::new)
        .validBlocks(ModBlocks.STROBE_LIGHT)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.LaserProjectorBlockEntity> LASER_PROJECTOR = REGISTRATE
        .blockEntity("laser_projector", com.createbrewery.block.club.LaserProjectorBlockEntity::new)
        .validBlocks(ModBlocks.LASER_PROJECTOR)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.FogMachineBlockEntity> FOG_MACHINE = REGISTRATE
        .blockEntity("fog_machine", com.createbrewery.block.club.FogMachineBlockEntity::new)
        .validBlocks(ModBlocks.FOG_MACHINE)
        .registerCapability(event -> {
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.FOG_MACHINE.get(),
                (be, side) -> be.getTank().getCapability());
        })
        .register();

    public static void register() {}
}
