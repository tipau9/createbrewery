package com.createbrewery.block.club.dj;

import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.block.club.DjControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Vertical Pitch / Tempo Fader with center zero detent. */
public class VPitchFader extends AbstractWidget {
    private final boolean isLeft;
    private final DjBoothContext ctx;
    private double value = 0.5;
    private boolean held, dirty;
    private long lastClick;

    public VPitchFader(int x, int y, int w, int h, boolean isLeft, DjBoothContext ctx) {
        super(x, y, w, h, Component.empty());
        this.isLeft = isLeft;
        this.ctx = ctx;
    }

    public void show(double v) {
        if (!held && !dirty) value = Mth.clamp(v, 0.0, 1.0);
    }

    public void flush() {
        if (!dirty) return;
        dirty = false;
        float p = (float) (1.0 - DjBoothBlockEntity.PITCH_RANGE + value * 2.0 * DjBoothBlockEntity.PITCH_RANGE);
        int deck = ctx.currentDeck(isLeft);
        DjControl.send(ctx.pos(), DjControl.PITCH, deck, p);
        DjBoothBlockEntity dj = ctx.booth();
        if (dj != null) dj.setPitch(deck, p);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        long now = net.minecraft.Util.getMillis();
        if (now - lastClick < 300) {
            value = 0.5;
            dirty = true;
            held = false;
            lastClick = 0;
            flush();
            return;
        }
        lastClick = now;
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
        value = Mth.clamp(value + scrollY * 0.02, 0.0, 1.0);
        dirty = true;
        return true;
    }

    private void updateFromMouse(double mouseY) {
        double relY = mouseY - (getY() + 4);
        double trackLen = height - 8;
        value = 1.0 - Mth.clamp(relY / trackLen, 0.0, 1.0);
        dirty = true;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int trackX = getX() + width / 2;
        g.fill(trackX - 1, getY() + 4, trackX + 1, getY() + height - 4, 0xFF08090C);
        g.renderOutline(trackX - 2, getY() + 3, 4, height - 6, 0xFF282B34);

        int midY = getY() + height / 2;
        g.fill(trackX - 4, midY, trackX + 4, midY + 1, 0xFF88AAFF);

        int handleY = getY() + 4 + (int) Math.round((1.0 - value) * (height - 12));
        int hx = getX() + 2, hw = width - 4;
        g.fill(hx, handleY, hx + hw, handleY + 6, isHoveredOrFocused() ? 0xFF888E9A : 0xFF585D68);
        g.fill(hx + 1, handleY + 2, hx + hw - 1, handleY + 4, 0xFFFFFFFF);
        g.renderOutline(hx, handleY, hw, 6, 0xFF14161C);

        double percent = (value * 2.0 - 1.0) * DjBoothBlockEntity.PITCH_RANGE * 100.0;
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, String.format(java.util.Locale.ROOT, "%+.1f", percent), trackX, getY() - 9, Math.abs(percent) < 0.1 ? 0xFF55FF88 : 0xFFAAAAAA);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
