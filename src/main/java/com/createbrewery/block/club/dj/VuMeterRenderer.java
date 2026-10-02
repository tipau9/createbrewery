package com.createbrewery.block.club.dj;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Utility for rendering DJ channel and master stereo LED VU-meters.
 */
public final class VuMeterRenderer {
    private VuMeterRenderer() {}

    public static void renderVuMeter(GuiGraphics g, int x, int y, int w, int h, float level) {
        g.fill(x, y, x + w, y + h, 0xFF0B0D12);
        g.renderOutline(x, y, w, h, 0xFF20242E);

        int leds = 10;
        int ledHeight = (h - 4) / leds;
        int activeCount = (int) Math.round(level * leds);

        for (int i = 0; i < leds; i++) {
            int ly = y + h - 2 - (i + 1) * ledHeight;
            boolean lit = i < activeCount;
            int ledColor = (i >= 8) ? (lit ? 0xFFFF2222 : 0xFF2A0808)
                                    : (i >= 5 ? (lit ? 0xFFFFBB00 : 0xFF2A2006)
                                              : (lit ? 0xFF00FF55 : 0xFF06240C));
            g.fill(x + 1, ly, x + w - 1, ly + ledHeight - 1, ledColor);
        }
    }

    public static void renderMasterVu(GuiGraphics g, int x, int y, int w, int h, float level) {
        g.fill(x, y, x + w, y + h, 0xFF0B0D12);
        g.renderOutline(x, y, w, h, 0xFF20242E);

        int barW = 5;
        renderVuMeter(g, x + 2, y + 2, barW, h - 4, level);
        renderVuMeter(g, x + w - barW - 2, y + 2, barW, h - 4, level * 0.96f);

        // Center dB scale ticks
        g.fill(x + 8, y + 6, x + 10, y + 7, 0xFFFF3333);
        g.fill(x + 8, y + 16, x + 10, y + 17, 0xFFFFCC00);
        g.fill(x + 8, y + 28, x + 10, y + 29, 0xFF44FF88);
    }
}
