package com.createbrewery.block.club.dj;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;

/** Vertical Channel Volume Fader. */
public class VFader extends AbstractWidget {
    private final int deck;
    private final Consumer<Float> onFlush;
    private double value = 1.0;
    private boolean held, dirty;

    public VFader(int x, int y, int w, int h, int deck, Consumer<Float> onFlush) {
        super(x, y, w, h, Component.empty());
        this.deck = deck;
        this.onFlush = onFlush;
    }

    public void show(double v) {
        if (!held && !dirty) value = Mth.clamp(v, 0.0, 1.0);
    }

    public void flush() {
        if (!dirty) return;
        dirty = false;
        onFlush.accept((float) value);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        held = true;
        updateFromMouse(mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (held) updateFromMouse(mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) { held = false; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isHovered()) return false;
        value = Mth.clamp(value + scrollY * 0.04, 0.0, 1.0);
        dirty = true;
        return true;
    }

    private void updateFromMouse(double mouseY) {
        double relY = mouseY - (getY() + 3);
        double trackLen = height - 6;
        value = 1.0 - Mth.clamp(relY / trackLen, 0.0, 1.0);
        dirty = true;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int trackX = getX() + width / 2;
        g.fill(trackX - 1, getY() + 2, trackX + 1, getY() + height - 2, 0xFF08090C);
        g.renderOutline(trackX - 2, getY() + 1, 4, height - 2, 0xFF242730);

        int handleY = getY() + 2 + (int) Math.round((1.0 - value) * (height - 8));
        int hx = getX() + 1, hw = width - 2;
        g.fill(hx, handleY, hx + hw, handleY + 5, isHoveredOrFocused() ? 0xFF888E9A : 0xFF505460);
        g.fill(hx + 1, handleY + 2, hx + hw - 1, handleY + 3, 0xFFFFFFFF);
        g.renderOutline(hx, handleY, hw, 5, 0xFF14161C);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
