package com.createbrewery.block.club.dj;

import com.createbrewery.block.club.DjBoothBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Backlit Rubber Performance Pad supporting HOT CUE, BEAT LOOP, SLIP LOOP, and BEAT JUMP modes. */
public class PadButton extends AbstractWidget {
    private final int index;
    private final int baseColor;
    private final Consumer<PadButton> onPress;
    private final Consumer<PadButton> onRelease;
    private boolean active;
    private int mode = DjBoothBlockEntity.PAD_BEAT_LOOP;
    private boolean isHeld = false;

    public PadButton(int x, int y, int w, int h, int index, int baseColor,
                     Consumer<PadButton> onPress,
                     Consumer<PadButton> onRelease) {
        super(x, y, w, h, Component.empty());
        this.index = index;
        this.baseColor = baseColor;
        this.onPress = onPress;
        this.onRelease = onRelease;
    }

    public void setActive(boolean on) { this.active = on; }
    public void setMode(int mode) { this.mode = mode; }

    public void releaseButton() {
        if (isHeld) {
            isHeld = false;
            if (onRelease != null) onRelease.accept(this);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        isHeld = true;
        if (onPress != null) onPress.accept(this);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        releaseButton();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bg = active ? (baseColor & 0x00FFFFFF) | 0x88000000 : (isHoveredOrFocused() ? 0xFF242630 : 0xFF161820);
        int border = active ? baseColor : (isHoveredOrFocused() ? baseColor : 0xFF353948);

        g.fill(getX(), getY(), getX() + width, getY() + height, bg);
        g.renderOutline(getX(), getY(), width, height, border);

        // Pad label depends on mode
        String label = switch (mode) {
            case DjBoothBlockEntity.PAD_HOT_CUE -> String.valueOf((char) ('A' + index));
            case DjBoothBlockEntity.PAD_BEAT_JUMP -> (index < 4 ? "-" : "+") + (index < 4 ? (8 >> index) : (1 << (index - 4)));
            default -> String.valueOf(DjBoothBlockEntity.LOOPS[index % DjBoothBlockEntity.LOOPS.length]);
        };

        int textCol = active ? 0xFFFFFFFF : (isHoveredOrFocused() ? 0xFFE0E0E0 : 0xFFAAAAAA);
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, label, getX() + width / 2, getY() + (height - 8) / 2, textCol);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
