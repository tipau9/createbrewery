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

    public static final BlockEntityEntry<com.createbrewery.block.club.ColdSparkBlockEntity> COLD_SPARK = REGISTRATE
        .blockEntity("cold_spark", com.createbrewery.block.club.ColdSparkBlockEntity::new)
        .validBlocks(ModBlocks.COLD_SPARK)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.HazerBlockEntity> HAZER = REGISTRATE
        .blockEntity("hazer", com.createbrewery.block.club.HazerBlockEntity::new)
        .validBlocks(ModBlocks.HAZER)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.Co2JetBlockEntity> CO2_JET = REGISTRATE
        .blockEntity("co2_jet", com.createbrewery.block.club.Co2JetBlockEntity::new)
        .validBlocks(ModBlocks.CO2_JET)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.DmxConsoleBlockEntity> DMX_CONSOLE = REGISTRATE
        .blockEntity("dmx_console", com.createbrewery.block.club.DmxConsoleBlockEntity::new)
        .validBlocks(ModBlocks.DMX_CONSOLE)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.FixtureBlockEntity> FIXTURE = REGISTRATE
        .blockEntity("fixture", com.createbrewery.block.club.FixtureBlockEntity::new)
        .validBlocks(ModBlocks.LED_BAR, ModBlocks.MOVING_HEAD, ModBlocks.BLINDER, ModBlocks.PAR_CAN)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.SpeakerBlockEntity> SPEAKER = REGISTRATE
        .blockEntity("speaker", com.createbrewery.block.club.SpeakerBlockEntity::new)
        .validBlocks(ModBlocks.SPEAKER)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.MicrophoneBlockEntity> MICROPHONE = REGISTRATE
        .blockEntity("microphone", com.createbrewery.block.club.MicrophoneBlockEntity::new)
        .validBlocks(ModBlocks.MICROPHONE)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.AmpRackBlockEntity> AMP_RACK = REGISTRATE
        .blockEntity("amp_rack", com.createbrewery.block.club.AmpRackBlockEntity::new)
        .validBlocks(ModBlocks.AMP_RACK)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.SubwooferBlockEntity> SUBWOOFER = REGISTRATE
        .blockEntity("subwoofer", com.createbrewery.block.club.SubwooferBlockEntity::new)
        .validBlocks(ModBlocks.SUBWOOFER)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.club.DjBoothBlockEntity> DJ_BOOTH = REGISTRATE
        .blockEntity("dj_booth", com.createbrewery.block.club.DjBoothBlockEntity::new)
        .validBlocks(ModBlocks.DJ_BOOTH)
        .register();

    public static final BlockEntityEntry<com.createbrewery.block.BeerTapBlockEntity> BEER_TAP = REGISTRATE
        .blockEntity("beer_tap", com.createbrewery.block.BeerTapBlockEntity::new)
        .validBlocks(ModBlocks.BEER_TAP)
        .registerCapability(event -> {
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.BEER_TAP.get(),
                (be, side) -> be.getTank().getCapability());
        })
        .register();

    public static void register() {}
}
