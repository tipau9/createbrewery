package com.createbrewery.block.club.dj;

import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.block.club.DjControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Rotary Knob with glowing 13-dot LED arc. */
public class Knob extends AbstractWidget {
    private final byte action;
    private final int deck;
    private final boolean bipolar;
    private final DjBoothContext ctx;
    private double value = 0.5, grabY, grabValue;
    private boolean held, dirty;
    private long lastClick;

    public Knob(int x, int y, byte action, int deck, String label, boolean bipolar, DjBoothContext ctx) {
        super(x, y, 18, 18, Component.literal(label));
        this.action = action;
        this.deck = deck;
        this.bipolar = bipolar;
        this.ctx = ctx;
        if (action == DjControl.FX_AMOUNT || action == DjControl.BEAT_FX_DEPTH) value = 0;
    }

    public void show(double v) {
        if (!held && !dirty) value = v;
    }

    public void flush() {
        if (!dirty) return;
        dirty = false;
        float v = (float) (bipolar ? value * 2 - 1 : value);
        DjControl.send(ctx.pos(), action, deck, v);

        DjBoothBlockEntity dj = ctx.booth();
        if (dj == null) return;
        switch (action) {
            case DjControl.TRIM -> dj.setTrim(deck, v);
            case DjControl.EQ_HIGH -> dj.setEq(deck, DjBoothBlockEntity.HIGH, v);
            case DjControl.EQ_MID -> dj.setEq(deck, DjBoothBlockEntity.MID, v);
            case DjControl.EQ_LOW -> dj.setEq(deck, DjBoothBlockEntity.LOW, v);
            case DjControl.FILTER -> dj.setFilter(deck, v);
            case DjControl.FX_AMOUNT -> dj.setFxAmount(deck, v);
            case DjControl.COLOR_FX_PARAM -> dj.setColorFxParam(v);
            case DjControl.BEAT_FX_DEPTH -> dj.setBeatFxDepth(v);
            default -> {}
        }
    }

    public void set(double v) {
        value = Math.max(0, Math.min(1, v));
        dirty = true;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        long now = net.minecraft.Util.getMillis();
        if (now - lastClick < 300) set(action == DjControl.FX_AMOUNT || action == DjControl.BEAT_FX_DEPTH ? 0 : 0.5);
        lastClick = now;
        held = true;
        grabY = mouseY;
        grabValue = value;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        set(grabValue + (grabY - mouseY) / 100.0);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) { held = false; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isHovered()) return false;
        set(value + scrollY * 0.04);
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = getX() + 9, cy = getY() + 8;
        int dots = 11;
        for (int i = 0; i < dots; i++) {
            double f = i / (double) (dots - 1);
            double a = Math.toRadians(135 + 270 * f);
            int px = cx + (int) Math.round(Math.cos(a) * 7), py = cy + (int) Math.round(Math.sin(a) * 7);
            boolean lit = bipolar
                ? (f >= Math.min(0.5, value) - 1e-6 && f <= Math.max(0.5, value) + 1e-6)
                : f <= value + 1e-6;
            g.fill(px - 1, py - 1, px + 1, py + 1, lit ? 0xFF00E5FF : 0xFF222630);
        }
        g.fill(cx - 3, cy - 3, cx + 3, cy + 3, isHoveredOrFocused() ? 0xFF656B7A : 0xFF424652);
        double a = Math.toRadians(135 + 270 * value);
        for (int r = 1; r <= 3; r++) {
            int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
        }
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, getMessage(), cx, getY() + 15, value < 0.02 && !bipolar ? 0xFFFF4444 : 0xFFAAAAAA);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.translatable("createbrewery.dj.knob_narration", getMessage(), Math.round(value * 100)));
    }
}
