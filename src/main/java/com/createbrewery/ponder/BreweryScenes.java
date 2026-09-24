package com.createbrewery.ponder;

import com.createbrewery.ModBlocks;
import com.createbrewery.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class BreweryScenes {

    public static void fermenter(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("fermenter", "Fermenting Beer in the Fermenter");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();

        BlockPos fermenter = util.grid().at(2, 1, 2);
        BlockPos pipeIn = util.grid().at(1, 1, 2);
        BlockPos pipeOut = util.grid().at(3, 1, 2);

        scene.idle(5);
        scene.world().setBlock(fermenter, ModBlocks.FERMENTER.getDefaultState(), false);
        scene.world().showSection(util.select().position(fermenter), Direction.DOWN);
        scene.idle(15);

        // Intro
        scene.overlay().showText(70)
            .text("The Fermenter turns Hopped Wort and Yeast into Beer over time.")
            .pointAt(util.vector().blockSurface(fermenter, Direction.WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(80);

        // Emphasize NO SHAFT / PASSIVE
        scene.overlay().showOutlineWithText(util.select().position(fermenter), 90)
            .colored(PonderPalette.GREEN)
            .text("Unlike most Create machines, the Fermenter requires NO shaft or rotational force. It is completely passive.")
            .pointAt(util.vector().topOf(fermenter))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(100);

        // Hopped wort piped in
        scene.world().setBlock(pipeIn, AllBlocks.FLUID_PIPE.getDefaultState(), false);
        scene.world().showSection(util.select().position(pipeIn), Direction.EAST);
        scene.idle(15);
        scene.overlay().showText(60)
            .text("Hopped Wort can be piped into the Fermenter from any side.")
            .pointAt(util.vector().topOf(pipeIn))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        // Yeast inserted (funnel or hand)
        scene.overlay().showControls(util.vector().topOf(fermenter), Pointing.DOWN, 60)
            .withItem(ModItems.YEAST.asStack())
            .rightClick();
        scene.idle(10);
        scene.overlay().showText(60)
            .text("Yeast can be inserted with a Funnel or by hand.")
            .pointAt(util.vector().topOf(fermenter))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        // Goggles & progress counting up
        scene.overlay().showControls(util.vector().topOf(fermenter), Pointing.DOWN, 80)
            .withItem(AllItems.GOGGLES.asStack());
        scene.overlay().showText(40)
            .text("Engineer's Goggles display fermentation progress.")
            .pointAt(util.vector().blockSurface(fermenter, Direction.WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(45);

        scene.overlay().showText(30)
            .colored(PonderPalette.MEDIUM)
            .text("Fermenting: 20% (0.8 days remaining)")
            .pointAt(util.vector().blockSurface(fermenter, Direction.WEST))
            .placeNearTarget();
        scene.idle(35);

        scene.overlay().showText(30)
            .colored(PonderPalette.MEDIUM)
            .text("Fermenting: 60% (0.4 days remaining)")
            .pointAt(util.vector().blockSurface(fermenter, Direction.WEST))
            .placeNearTarget();
        scene.idle(35);

        scene.overlay().showText(30)
            .colored(PonderPalette.GREEN)
            .text("Fermenting: 100% (Ready)")
            .pointAt(util.vector().blockSurface(fermenter, Direction.WEST))
            .placeNearTarget();
        scene.idle(40);

        // Beer piped out
        scene.world().setBlock(pipeOut, AllBlocks.FLUID_PIPE.getDefaultState(), false);
        scene.world().showSection(util.select().position(pipeOut), Direction.WEST);
        scene.idle(15);
        scene.overlay().showText(60)
            .text("Finished Beer can then be piped out and bottled.")
            .pointAt(util.vector().topOf(pipeOut))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(70);

        scene.markAsFinished();
    }

    public static void brewingHeat(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("brewing_heat", "Brewing Heat Requirements");
        scene.configureBasePlate(0, 0, 5);

        // Structure from mechanical_mixer/mixing schematic
        // Base plate: layer 0
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        // Kinetic drive and shaft:
        scene.world().showSection(util.select().fromTo(1, 4, 3, 1, 1, 5), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(3, 1, 1, 1, 1, 1), Direction.SOUTH);
        scene.world().showSection(util.select().fromTo(3, 1, 5, 3, 1, 2), Direction.SOUTH);
        // Mixer
        scene.world().showSection(util.select().position(1, 4, 2), Direction.DOWN);
        // Basin
        scene.world().showSection(util.select().position(1, 2, 2), Direction.DOWN);

        BlockPos burnerPos = util.grid().at(1, 1, 2);
        BlockPos basinPos = util.grid().at(1, 2, 2);

        // Replace the casing beneath the basin with a KINDLED Blaze Burner
        scene.world().setBlock(burnerPos, AllBlocks.BLAZE_BURNER.getDefaultState()
            .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED), false);
        scene.world().showSection(util.select().position(burnerPos), Direction.DOWN);
        scene.idle(20);

        // Keyframe 1: Overview
        scene.overlay().showText(70)
            .text("Brewing requires different heat tiers for different stages of the process.")
            .pointAt(util.vector().blockSurface(basinPos, Direction.WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(80);

        // Keyframe 2: Mashing (KINDLED / Heated)
        scene.overlay().showOutlineWithText(util.select().position(burnerPos), 80)
            .colored(PonderPalette.MEDIUM)
            .text("Mashing (Grist + Water -> Wort) requires standard HEAT (Kindled Blaze Burner).")
            .pointAt(util.vector().blockSurface(burnerPos, Direction.WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // Keyframe 3: Feeding Blaze Cake to superheat
        scene.overlay().showControls(util.vector().topOf(burnerPos), Pointing.DOWN, 60)
            .withItem(AllItems.BLAZE_CAKE.asStack())
            .rightClick();
        scene.idle(15);
        scene.world().modifyBlock(burnerPos, s -> s.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING), false);
        scene.idle(20);

        // Keyframe 4: Boiling (SEETHING / Superheated)
        scene.overlay().showOutlineWithText(util.select().position(burnerPos), 80)
            .colored(PonderPalette.BLUE)
            .text("Boiling (Wort + Hops -> Hopped Wort) requires SUPERHEATED heat (fed with Blaze Cake).")
            .pointAt(util.vector().blockSurface(burnerPos, Direction.WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(90);

        // Keyframe 5: Summary
        scene.overlay().showText(70)
            .text("Remember: Mashing needs standard heat, while Boiling hops must be Superheated!")
            .pointAt(util.vector().blockSurface(basinPos, Direction.WEST))
            .placeNearTarget()
            .attachKeyFrame();
        scene.idle(80);

        scene.markAsFinished();
    }
}
