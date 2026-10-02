package com.createbrewery.block.club.dj;

import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.drunk.MusicPulse;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Consumer;

/** Iconic circular illuminated CUE and PLAY/PAUSE transport buttons with authentic Pioneer CDJ behavior. */
public class TransportButton extends AbstractWidget {
    private final boolean isPlay;
    private final boolean isLeft;
    private final DjBoothContext ctx;
    private final Consumer<TransportButton> onPress;
    private final Consumer<TransportButton> onRelease;
    private boolean isHeld = false;

    public TransportButton(int x, int y, int w, int h, boolean isPlay, boolean isLeft,
                           DjBoothContext ctx,
                           Consumer<TransportButton> onPress,
                           Consumer<TransportButton> onRelease) {
        super(x, y, w, h, Component.literal(isPlay ? "PLAY" : "CUE"));
        this.isPlay = isPlay;
        this.isLeft = isLeft;
        this.ctx = ctx;
        this.onPress = onPress;
        this.onRelease = onRelease;
    }

    public void releaseButton() {
        if (isHeld) {
            isHeld = false;
            if (onRelease != null) onRelease.accept(this);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        isHeld = true;
        var mc = ctx.minecraft();
        if (mc != null && mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, isPlay ? 1.2f : 1.0f);
        }
        if (onPress != null) onPress.accept(this);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        releaseButton();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = getX() + width / 2, cy = getY() + height / 2;
        int r = width / 2;
        int deck = ctx.currentDeck(isLeft);

        DjBoothBlockEntity dj = ctx.booth();
        boolean hasDisc = dj != null && !dj.getDisc(deck).isEmpty();
        boolean isPlaying = dj != null && dj.isPlaying(deck);
        MusicPulse.Track track = MusicPulse.trackFor(ctx.pos(), deck);
        long cuePos = dj != null ? dj.getMainCue(deck) : 0;
        boolean atCue = track != null && Math.abs(track.deckFrame - cuePos) < 200;
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;

        int ringColor;
        int textColor;
        int innerBg;

        if (isPlay) {
            if (!hasDisc) {
                ringColor = 0xFF003310;
                textColor = 0xFF004418;
                innerBg = 0xFF060D08;
            } else if (isPlaying) {
                ringColor = 0xFF00FF66;
                textColor = 0xFF00FF66;
                innerBg = 0xFF061E0E;
            } else {
                // Paused with disc loaded -> blinking green
                ringColor = blink ? 0xFF00FF66 : 0xFF004418;
                textColor = blink ? 0xFF00FF66 : 0xFF005520;
                innerBg = 0xFF06140A;
            }
        } else {
            // CUE button
            if (!hasDisc) {
                ringColor = 0xFF331800;
                textColor = 0xFF442200;
                innerBg = 0xFF0D0A06;
            } else if (isPlaying) {
                // Playing -> flashing orange to signal return to cue
                ringColor = blink ? 0xFFFF9900 : 0xFF442200;
                textColor = blink ? 0xFFFF9900 : 0xFF553300;
                innerBg = 0xFF140D04;
            } else if (atCue) {
                // Paused at Cue Point -> solid orange
                ringColor = 0xFFFF9900;
                textColor = 0xFFFF9900;
                innerBg = 0xFF1E1406;
            } else {
                // Paused away from Cue Point -> flashing orange to invite setting new cue
                ringColor = blink ? 0xFFFF9900 : 0xFF442200;
                textColor = blink ? 0xFFFF9900 : 0xFF553300;
                innerBg = 0xFF140D04;
            }
        }

        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF101216);
        g.renderOutline(cx - r, cy - r, width, height, ringColor);
        g.fill(cx - r + 3, cy - r + 3, cx + r - 3, cy + r - 3, innerBg);

        if (isPlay) {
            g.drawString(ctx.font(), "▶||", cx - 7, cy - 4, textColor);
        } else {
            g.drawCenteredString(ctx.font(), "CUE", cx, cy - 4, textColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
