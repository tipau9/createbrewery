package com.createbrewery.block.club.dj;

import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.block.club.DjControl;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Magvel Crossfader slider. */
public class Fader extends AbstractSliderButton {
    private final byte action;
    private final DjBoothContext ctx;
    private boolean held, dirty;

    public Fader(int x, int y, int w, byte action, boolean isPitch, DjBoothContext ctx) {
        super(x, y, w, 16, Component.empty(), 0.5);
        this.action = action;
        this.ctx = ctx;
        updateMessage();
    }

    public void show(double v) {
        if (!held && !dirty && Math.abs(v - value) > 1e-3) {
            value = v;
            updateMessage();
        }
    }

    public void flush() {
        if (!dirty) return;
        dirty = false;
        DjControl.send(ctx.pos(), action, (float) value);
        DjBoothBlockEntity dj = ctx.booth();
        if (dj != null) dj.setCrossfader((float) value);
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("createbrewery.dj.crossfader"));
    }

    @Override
    protected void applyValue() { dirty = true; }

    @Override
    public void onClick(double mouseX, double mouseY) {
        held = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        held = false;
        super.onRelease(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isHovered()) return false;
        value = Mth.clamp(value + (scrollX != 0 ? scrollX : scrollY) * 0.05, 0.0, 1.0);
        applyValue();
        updateMessage();
        return true;
    }
}
