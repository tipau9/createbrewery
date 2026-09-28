package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The package-private club controls, for ClubGameTests. */
public final class ClubTestAccess {
    private ClubTestAccess() {}

    public static void linkFixture(ItemStack stack, BlockPos console) {
        FixtureBlock.link(stack, console);
    }

    public static boolean applyFixtureLink(Level level, BlockPos pos, ItemStack stack) {
        return FixtureBlock.applyLink(level, pos, stack);
    }

    /** Swaps deck {@code deck}'s record for the next one from the crate beside the booth. */
    public static boolean restock(DjBoothBlockEntity dj, int deck) {
        return dj.restock(deck);
    }

    /** Where a microphone's voice would come out. */
    public static java.util.List<net.minecraft.world.phys.Vec3> micSpeakers(MicrophoneBlockEntity mic) {
        return mic.speakers();
    }

    /** Stores the faders in scene 1, moves them, recalls it: true if they came back. */
    public static boolean sceneRoundTrip(DmxConsoleBlockEntity dmx) {
        dmx.setFader(0, 0.3f);
        dmx.setColor(0, 5);
        dmx.storeScene(0);
        dmx.setFader(0, 1f);
        dmx.setColor(0, 1);
        return dmx.recallScene(0) && dmx.settings.faders[0] == 0.3f && dmx.settings.colors[0] == 5 && !dmx.recallScene(3);
    }
}
