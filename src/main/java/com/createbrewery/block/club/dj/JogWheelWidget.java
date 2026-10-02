package com.createbrewery.block.club.dj;

import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.block.club.DjControl;
import com.createbrewery.drunk.MusicPulse;
import com.createbrewery.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

/** CDJ Jogwheel widget with animated on-jog LCD center display & spinning cue needle. */
public class JogWheelWidget extends AbstractWidget {
    private final boolean isLeft;
    private final DjBoothContext ctx;
    private float visualRotation = 0f;
    private double grabAngle = 0;
    private boolean innerTouch = false;
    private float accumulatedDelta = 0;
    private long lastScratchSoundTime = 0;
    private int lastDirection = 0;

    public JogWheelWidget(int x, int y, int w, int h, boolean isLeft, DjBoothContext ctx) {
        super(x, y, w, h, Component.literal("Jog"));
        this.isLeft = isLeft;
        this.ctx = ctx;
    }

    public boolean isHoldingPlatter() {
        return innerTouch;
    }

    public void releasePlatter() {
        if (innerTouch) {
            innerTouch = false;
            DjBoothBlockEntity dj = ctx.booth();
            int deck = ctx.currentDeck(isLeft);
            if (dj != null && dj.isVinylMode(deck)) {
                dj.setScratchHeld(deck, false);
                DjControl.send(ctx.pos(), DjControl.JOG_TOUCH, deck, 0.0f);
                MusicPulse.Track track = MusicPulse.trackFor(ctx.pos(), deck);
                if (track != null) {
                    if (dj.isSlipMode(deck)) {
                        track.deckFrame = track.slipFrame;
                    } else {
                        track.slipFrame = track.deckFrame;
                    }
                    DjControl.send(ctx.pos(), DjControl.JUMP_PLAYHEAD, deck, (float) track.deckFrame);
                }
            }
        }
        accumulatedDelta = 0;
        lastDirection = 0;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = getX() + width / 2, cy = getY() + height / 2;
        int r = width / 2;
        int deck = ctx.currentDeck(isLeft);
        DjBoothBlockEntity dj = ctx.booth();

        // Outer textured rim
        g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF282B34);
        g.fill(cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2, 0xFF181A20);

        // Vinyl platter face
        int pr = r - 5;
        g.fill(cx - pr, cy - pr, cx + pr, cy + pr, 0xFF0E1014);
        g.renderOutline(cx - pr, cy - pr, pr * 2, pr * 2, 0xFF1E2129);
        g.renderOutline(cx - pr + 4, cy - pr + 4, (pr - 4) * 2, (pr - 4) * 2, 0xFF14161C);

        // Spin platter rotation when playing and not physically held by DJ hand
        boolean held = innerTouch || (dj != null && dj.isScratchHeld(deck) && dj.isVinylMode(deck));
        if (dj != null && dj.isPlaying(deck) && !held) {
            visualRotation += 14.0f * dj.getPitch(deck) * (dj.isReverse(deck) ? -1 : 1);
        }

        // Radial platter markings
        double radAngle = Math.toRadians(visualRotation);
        for (int i = 0; i < 4; i++) {
            double a = radAngle + i * (Math.PI / 2.0);
            int x1 = cx + (int) Math.round(Math.cos(a) * 16);
            int y1 = cy + (int) Math.round(Math.sin(a) * 16);
            int x2 = cx + (int) Math.round(Math.cos(a) * 25);
            int y2 = cy + (int) Math.round(Math.sin(a) * 25);
            g.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, 0xFF242730);
        }

        // On-Jog LCD center screen
        int cr = 14;
        g.fill(cx - cr, cy - cr, cx + cr, cy + cr, 0xFF04070D);
        int ringCol = DjConstants.DECK_COLORS[deck % DjConstants.DECK_COLORS.length];
        g.renderOutline(cx - cr, cy - cr, cr * 2, cr * 2, ringCol);

        // Center Deck Number
        g.drawCenteredString(ctx.font(), String.valueOf(deck + 1), cx, cy - 4, 0xFFFFFFFF);

        // Spinning Cue Needle marker
        int nx = cx + (int) Math.round(Math.cos(radAngle) * (cr - 2));
        int ny = cy + (int) Math.round(Math.sin(radAngle) * (cr - 2));
        g.fill(nx - 1, ny - 1, nx + 2, ny + 2, 0xFFFFFFFF);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int cx = getX() + width / 2, cy = getY() + height / 2;
        double dx = mouseX - cx, dy = mouseY - cy;
        innerTouch = (dx * dx + dy * dy <= 27 * 27);
        grabAngle = Math.atan2(dy, dx);
        accumulatedDelta = 0;
        lastDirection = 0;

        DjBoothBlockEntity dj = ctx.booth();
        int deck = ctx.currentDeck(isLeft);
        if (dj != null && innerTouch && dj.isVinylMode(deck)) {
            dj.setScratchHeld(deck, true);
            DjControl.send(ctx.pos(), DjControl.JOG_TOUCH, deck, 1.0f);
            var mc = ctx.minecraft();
            if (mc != null && mc.player != null) {
                mc.player.playSound(ModSounds.DJ_SCRATCH_STOP.get(), 0.65f, 1.0f);
            }
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        int cx = getX() + width / 2, cy = getY() + height / 2;
        double curAngle = Math.atan2(mouseY - cy, mouseX - cx);
        double delta = curAngle - grabAngle;
        while (delta < -Math.PI) delta += 2 * Math.PI;
        while (delta > Math.PI) delta -= 2 * Math.PI;
        if (Math.abs(delta) < 1e-4) return;
        grabAngle = curAngle;
        visualRotation += (float) Math.toDegrees(delta);

        DjBoothBlockEntity dj = ctx.booth();
        int deck = ctx.currentDeck(isLeft);
        if (dj == null) return;

        if (innerTouch && dj.isVinylMode(deck)) {
            accumulatedDelta += (float) delta;
            int dir = delta > 0 ? 1 : -1;
            long now = net.minecraft.Util.getMillis();
            boolean directionFlipped = (lastDirection != 0 && dir != lastDirection);
            boolean timeElapsed = (now - lastScratchSoundTime > 75);

            // Real-time audio spooling/scrubbing
            MusicPulse.Track track = MusicPulse.trackFor(ctx.pos(), deck);
            if (track != null) {
                long deltaFrames = (long) ((delta / (2.0 * Math.PI)) * 1.8 * track.rate);
                track.scrub(deltaFrames);
            }

            if (directionFlipped || (timeElapsed && Math.abs(accumulatedDelta) > 0.04f)) {
                lastDirection = dir;
                lastScratchSoundTime = now;
                float scrubVal = accumulatedDelta;
                accumulatedDelta = 0;

                DjControl.send(ctx.pos(), DjControl.JOG_SCRUB, deck, scrubVal);
                var mc = ctx.minecraft();
                if (mc != null && mc.player != null) {
                    SoundEvent sound = dir > 0 ? ModSounds.DJ_SCRATCH_FWD.get() : ModSounds.DJ_SCRATCH_BACK.get();
                    float pitch = Mth.clamp(0.75f + Math.abs(scrubVal) * 3.5f, 0.6f, 1.8f);
                    float vol = Mth.clamp(0.6f + Math.abs(scrubVal) * 2.5f, 0.4f, 1.0f);
                    mc.player.playSound(sound, vol, pitch);
                }
            }
        } else {
            float cur = dj.getPitch(deck);
            float nudge = Mth.clamp((float) (cur + delta * 0.03), 1f - DjBoothBlockEntity.PITCH_RANGE, 1f + DjBoothBlockEntity.PITCH_RANGE);
            DjControl.send(ctx.pos(), DjControl.PITCH, deck, nudge);
            dj.setPitch(deck, nudge);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        releasePlatter();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isHovered()) return false;
        visualRotation += (float) (scrollY * 18.0);
        DjBoothBlockEntity dj = ctx.booth();
        int deck = ctx.currentDeck(isLeft);
        if (dj != null) {
            if (dj.isVinylMode(deck)) {
                float scrubTicks = (float) (scrollY * 0.15f);
                DjControl.send(ctx.pos(), DjControl.JOG_SCRUB, deck, scrubTicks);
                MusicPulse.Track track = MusicPulse.trackFor(ctx.pos(), deck);
                if (track != null) {
                    long deltaFrames = (long) (scrollY * 0.25 * track.rate);
                    track.scrub(deltaFrames);
                    DjControl.send(ctx.pos(), DjControl.JUMP_PLAYHEAD, deck, (float) track.deckFrame);
                }
                var mc = ctx.minecraft();
                if (mc != null && mc.player != null) {
                    SoundEvent sound = scrollY > 0 ? ModSounds.DJ_SCRATCH_FWD.get() : ModSounds.DJ_SCRATCH_BACK.get();
                    float pitch = Mth.clamp(0.85f + (float) Math.abs(scrollY) * 0.2f, 0.7f, 1.6f);
                    mc.player.playSound(sound, 0.75f, pitch);
                }
            } else {
                float cur = dj.getPitch(deck);
                float nudge = Mth.clamp((float) (cur + scrollY * 0.005), 1f - DjBoothBlockEntity.PITCH_RANGE, 1f + DjBoothBlockEntity.PITCH_RANGE);
                DjControl.send(ctx.pos(), DjControl.PITCH, deck, nudge);
                dj.setPitch(deck, nudge);
            }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {}
}
