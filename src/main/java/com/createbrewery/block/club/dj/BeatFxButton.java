package com.createbrewery.block.club.dj;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Pulsing round illuminated Beat FX ON/OFF button. */
public class BeatFxButton extends AbstractWidget {
    private final Consumer<BeatFxButton> onPress;
    private boolean active;

    public BeatFxButton(int x, int y, int w, int h, Consumer<BeatFxButton> onPress) {
        super(x, y, w, h, Component.literal("BFX"));
        this.onPress = onPress;
    }

    public void setActive(boolean on) { this.active = on; }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = getX() + width / 2, cy = getY() + height / 2;
        int r = width / 2;

        var mc = Minecraft.getInstance();
        int ring = active ? 0xFFFFCC00 : (isHoveredOrFocused() ? 0xFF886600 : 0xFF352A05);
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF151206);
        g.renderOutline(cx - r, cy - r, width, height, ring);

        int textCol = active ? 0xFFFFFFFF : 0xFF888888;
        g.drawCenteredString(mc.font, "ON", cx, cy - 4, textCol);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
