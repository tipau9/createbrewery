package com.createbrewery.block.club;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Interim until the full screen: tells what the rack's traffic light says. Client only. */
public final class AmpRackScreen {
    private AmpRackScreen() {}

    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.level.getBlockEntity(pos) instanceof AmpRackBlockEntity rack)) return;
        mc.player.displayClientMessage(Component.literal(String.valueOf(rack.settings().power ? "on" : "off")), true);
    }
}
