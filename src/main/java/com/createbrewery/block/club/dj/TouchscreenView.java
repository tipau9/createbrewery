package com.createbrewery.block.club.dj;

import com.createbrewery.block.club.DjBoothBlock;
import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.block.club.DjControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 10.1" Capacitive Touchscreen view for the XDJ-AZ DJ system.
 * Manages WAVE, BROWSE, and INFO tabs, crate navigation, and track loading.
 */
public class TouchscreenView {
    private int screenTab = 0; // 0 = WAVE, 1 = BROWSE, 2 = INFO
    private int browseOffset = 0;

    public int getScreenTab() {
        return screenTab;
    }

    public void setScreenTab(int tab) {
        this.screenTab = tab;
    }

    public int getBrowseOffset() {
        return browseOffset;
    }

    public void setBrowseOffset(int offset) {
        this.browseOffset = offset;
    }

    public void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                       DjBoothBlockEntity dj, BlockPos pos,
                       int leftDeck, int rightDeck, int masterDeck,
                       long time, float pt,
                       double mouseX, double mouseY) {
        // Metallic outer bezel
        g.fill(x, y, x + w, y + h, 0xFF1B1D25);
        g.renderOutline(x, y, w, h, 0xFF383C4A);

        // High-contrast LCD face
        int sx = x + 2, sy = y + 2, sw = w - 4, sh = h - 4;
        g.fill(sx, sy, sx + sw, sy + sh, 0xFF040810);

        // Header branding & status bar
        g.drawString(font, "AlphaTheta XDJ-AZ", sx + 3, sy + 3, 0xFF88AAFF);

        if (screenTab == 0) {
            // ================= WAVEFORM TAB =================
            // Left Deck Waveform
            WaveformView.renderWaveform(g, font, sx + 2, sy + 14, sw - 4, 18,
                leftDeck, dj, pos, DjConstants.DECK_COLORS[leftDeck], 0xFF0055AA, time, pt, leftDeck == masterDeck);
            // Right Deck Waveform
            WaveformView.renderWaveform(g, font, sx + 2, sy + 34, sw - 4, 18,
                rightDeck, dj, pos, DjConstants.DECK_COLORS[rightDeck], 0xFFAA5500, time, pt, rightDeck == masterDeck);

            // Beat Phase Grid (4-beat indicator dots & bar progress)
            int phaseX = sx + (sw - 40) / 2, phaseY = sy + 56;
            int beatIndex = (int) ((time / 10) % 4);
            for (int i = 0; i < 4; i++) {
                boolean active = i == beatIndex && (dj.isPlaying(leftDeck) || dj.isPlaying(rightDeck));
                int col = active ? 0xFFFFFFFF : 0xFF182234;
                g.fill(phaseX + i * 10, phaseY, phaseX + i * 10 + 8, phaseY + 5, col);
            }
        } else if (screenTab == 1) {
            // ================= BROWSE TAB =================
            renderBrowseTab(g, font, sx + 2, sy + 14, sw - 4, sh - 16, dj, mouseX, mouseY);
        } else {
            // ================= INFO / DIAGNOSTICS TAB =================
            renderInfoTab(g, font, sx + 2, sy + 14, sw - 4, sh - 16, dj);
        }
    }

    private void renderBrowseTab(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 DjBoothBlockEntity dj, double mouseX, double mouseY) {
        g.fill(x, y, x + w, y + h, 0xFF080C14);
        g.renderOutline(x, y, w, h, 0xFF1A2638);

        var crate = dj.crate();
        if (crate == null) {
            g.drawCenteredString(font, "NO RECORD CRATE AT BOOTH", x + w / 2, y + 16, 0xFFFF4444);
            g.drawCenteredString(font, "(Place Chest/Barrel next to Booth)", x + w / 2, y + 28, 0xFF888888);
            return;
        }

        List<Integer> discSlots = new ArrayList<>();
        for (int i = 0; i < crate.getSlots(); i++) {
            if (DjBoothBlock.isMusicDisc(crate.getStackInSlot(i))) discSlots.add(i);
        }

        if (discSlots.isEmpty()) {
            g.drawCenteredString(font, "CRATE EMPTY", x + w / 2, y + 20, 0xFF888888);
            return;
        }

        int visible = 3;
        for (int row = 0; row < visible; row++) {
            int idx = browseOffset + row;
            if (idx >= discSlots.size()) break;
            int slot = discSlots.get(idx);
            ItemStack stack = crate.getStackInSlot(slot);
            int ry = y + 2 + row * 16;

            g.fill(x + 2, ry, x + w - 2, ry + 15, (row % 2 == 0) ? 0xFF0E1420 : 0xFF141C2C);
            String title = dj.title(stack).getString();
            g.drawString(font, String.format("%02d. %s", idx + 1, font.plainSubstrByWidth(title, 80)), x + 4, ry + 4, 0xFFE0E0E0);

            // Load buttons [1] [2] [3] [4]
            for (int d = 0; d < 4; d++) {
                int bx = x + w - 46 + d * 11;
                boolean hovered = mouseX >= bx && mouseX < bx + 10 && mouseY >= ry + 2 && mouseY < ry + 13;
                g.fill(bx, ry + 2, bx + 10, ry + 13, hovered ? 0xFF354560 : 0xFF1E283A);
                g.renderOutline(bx, ry + 2, 10, 11, DjConstants.DECK_COLORS[d]);
                g.drawCenteredString(font, String.valueOf(d + 1), bx + 5, ry + 3, DjConstants.DECK_COLORS[d]);
            }
        }
    }

    private void renderInfoTab(GuiGraphics g, Font font, int x, int y, int w, int h, DjBoothBlockEntity dj) {
        g.fill(x, y, x + w, y + h, 0xFF080C14);
        g.drawString(font, "ENGINE: AlphaTheta DSP 44.1kHz", x + 4, y + 4, 0xFF88AAFF);
        g.drawString(font, "COLOR FX: " + DjBoothBlockEntity.COLOR_FX_NAMES[dj.getActiveColorFx()], x + 4, y + 16, 0xFF00FF66);
        g.drawString(font, "BEAT FX: " + DjBoothBlockEntity.BFX_NAMES[dj.getBeatFxType()] + " (" + dj.getBeatFxBeats() + " Beats)", x + 4, y + 28, 0xFFFFCC00);
        g.drawString(font, "AUTO-DROP: " + (dj.isAutoDrop() ? "ACTIVE (Redstone ON)" : "OFF"), x + 4, y + 40, dj.isAutoDrop() ? 0xFFFF3344 : 0xFF888888);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                DjBoothBlockEntity dj, BlockPos pos, int touchX, int touchY) {
        if (screenTab == 1 && dj != null && dj.crate() != null) {
            var crate = dj.crate();
            List<Integer> discSlots = new ArrayList<>();
            for (int i = 0; i < crate.getSlots(); i++) {
                if (DjBoothBlock.isMusicDisc(crate.getStackInSlot(i))) discSlots.add(i);
            }
            int sx = touchX + 2, sy = touchY + 16, sw = 166 - 4;
            for (int row = 0; row < 3; row++) {
                int idx = browseOffset + row;
                if (idx >= discSlots.size()) break;
                int slot = discSlots.get(idx);
                int ry = sy + row * 16;
                for (int d = 0; d < 4; d++) {
                    int bx = sx + sw - 46 + d * 11;
                    if (mouseX >= bx && mouseX < bx + 10 && mouseY >= ry + 2 && mouseY < ry + 13) {
                        DjControl.send(pos, DjControl.LOAD_TRACK, d, slot);
                        var mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.2f);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                 DjBoothBlockEntity dj) {
        if (screenTab == 1 && dj != null && dj.crate() != null) {
            int totalDiscs = 0;
            var crate = dj.crate();
            for (int i = 0; i < crate.getSlots(); i++) {
                if (DjBoothBlock.isMusicDisc(crate.getStackInSlot(i))) totalDiscs++;
            }
            int maxOffset = Math.max(0, totalDiscs - 3);
            int next = Mth.clamp(browseOffset - (int) Math.signum(scrollY), 0, maxOffset);
            if (next != browseOffset) {
                browseOffset = next;
                return true;
            }
        }
        return false;
    }
}
