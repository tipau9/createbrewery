package com.createbrewery.block.club.dj;

import com.createbrewery.block.club.DjBoothBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;

/**
 * Decouples DJ widgets and view components from the main DjMixerScreen.
 */
public interface DjBoothContext {
    DjBoothBlockEntity booth();
    BlockPos pos();
    int currentDeck(boolean isLeft);
    boolean isQuantize();

    default Font font() {
        return Minecraft.getInstance().font;
    }

    default Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
