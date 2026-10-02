package com.createbrewery.block.club.dj;

import com.createbrewery.block.club.DjBoothBlockEntity;
import com.createbrewery.drunk.MusicPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

/**
 * 3-band Pioneer full-color scrolling audio waveform renderer with live slice decoding & full-track overview.
 */
public final class WaveformView {
    private WaveformView() {}

    public static void renderWaveform(GuiGraphics g, Font font, int x, int y, int w, int h, int deck,
                                      DjBoothBlockEntity dj, BlockPos pos, int colorHigh, int colorLow,
                                      long time, float pt, boolean isMaster) {
        g.fill(x, y, x + w, y + h, 0xFF080C14);
        g.renderOutline(x, y, w, h, 0xFF182234);

        boolean playing = dj.isPlaying(deck);
        MusicPulse.Track track = MusicPulse.trackFor(pos, deck);

        ItemStack disc = dj.getDisc(deck);
        if (disc.isEmpty()) {
            g.drawString(font, String.valueOf(deck + 1), x + 3, y + 2, colorHigh);
            int cx = x + w / 2;
            g.drawString(font, "NO TRACK", cx - 22, y + 5, 0xFF35445A);
            return;
        }

        int cx = x + w / 2;
        int waveH = h - 4; // Reserve bottom 3px for track overview mini-bar
        int cy = y + waveH / 2;

        if (track == null || track.rate <= 0) {
            g.drawString(font, String.valueOf(deck + 1), x + 3, y + 2, colorHigh);
            g.fill(x + 2, cy, x + w - 2, cy + 1, 0xFF1E2A3A);
            g.drawCenteredString(font, "LOADED", cx, y + 5, 0xFF455870);
            return;
        }

        long currentFrame = track.getEffectiveFrame();
        float rate = track.rate;

        // Pioneer XDJ-AZ display header:
        // Left: [Deck] [M] [Live BPM] [% Pitch] [MT]
        float pitch = dj.getPitch(deck);
        double period = MusicPulse.beatPeriodAt(dj.deckPos(deck));
        double baseBpm = period > 0 ? (60.0 / period) : 120.0;
        double liveBpm = baseBpm * pitch;
        float pitchPct = (pitch - 1.0f) * 100.0f;

        int curX = x + 3;
        g.drawString(font, String.valueOf(deck + 1), curX, y + 2, colorHigh);
        curX += font.width(String.valueOf(deck + 1)) + 3;

        if (isMaster && playing) {
            g.fill(curX - 1, y + 2, curX + 7, y + 9, 0xFFCC2222);
            g.drawString(font, "M", curX, y + 2, 0xFFFFFFFF);
            curX += 9;
        }

        String bpmStr = playing && period > 0 ? String.format(java.util.Locale.ROOT, "%.1f", liveBpm) : "--";
        g.drawString(font, bpmStr, curX, y + 2, 0xFFFFFFFF);
        curX += font.width(bpmStr) + 3;

        if (playing) {
            String pctStr = String.format(java.util.Locale.ROOT, "%+.1f%%", pitchPct);
            g.drawString(font, pctStr, curX, y + 2, 0xFF88AA99);
            curX += font.width(pctStr) + 3;
        }

        if (dj.isMasterTempo(deck)) {
            g.drawString(font, "MT", curX, y + 2, 0xFF00E5FF);
        }

        // Right side: Countdown remaining time with blinking warning under 30s
        long elapsedSec = (long) (Math.max(0, currentFrame) / rate);
        float totalSec = 0f;
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            totalSec = JukeboxSong.fromStack(mc.level.registryAccess(), disc)
                .map(s -> s.value().lengthInSeconds())
                .orElse(0f);
        }
        if (totalSec <= 0f) {
            totalSec = (float) track.getFramesRead() / rate;
        }
        float remSec = Math.max(0f, totalSec - elapsedSec);
        boolean blink = remSec <= 30f && playing && ((System.currentTimeMillis() / 400) % 2 == 0);
        int timeCol = blink ? 0xFFFF3333 : 0xFFE0E0E0;
        String remStr = String.format(java.util.Locale.ROOT, "-%02d:%02d", (long) remSec / 60, (long) remSec % 60);
        g.drawString(font, remStr, x + w - font.width(remStr) - 3, y + 2, timeCol);

        // Zoom scale: 3.5 seconds across visible window of width w
        float visibleSeconds = 3.5f;
        float framesPerPixel = (visibleSeconds * rate) / (float) w;

        // 1. Beat Grid Markers (subtle vertical lines, bolder every 4 beats)
        double beatPeriod = track.getBeatPeriod();
        if (beatPeriod > 0 && track.getBeats() >= 4) {
            long beatFrames = (long) (beatPeriod * rate);
            if (beatFrames > 0) {
                long startBeat = (currentFrame - (long) (visibleSeconds * 0.5f * rate)) / beatFrames;
                long endBeat = (currentFrame + (long) (visibleSeconds * 0.5f * rate)) / beatFrames + 1;
                for (long b = startBeat; b <= endBeat; b++) {
                    long bFrame = b * beatFrames;
                    int bx = cx + (int) ((bFrame - currentFrame) / framesPerPixel);
                    if (bx >= x + 2 && bx < x + w - 2) {
                        boolean isBar = (b % 4 == 0);
                        int lineCol = isBar ? 0x88FFFFFF : 0x445588AA;
                        int lineTop = isBar ? y + 2 : y + 4;
                        int lineBot = isBar ? y + waveH : y + waveH - 2;
                        g.fill(bx, lineTop, bx + 1, lineBot, lineCol);
                    }
                }
            }
        }

        // 2. Render Pioneer 3-band decoded audio slices
        // Blue = Bass, Amber = Mids, White = Highs
        int halfH = Math.max(2, waveH / 2 - 1);
        for (int col = 0; col < w - 4; col++) {
            int px = x + 2 + col;
            long sampleFrame = currentFrame + (long) ((px - cx) * framesPerPixel);
            float[] slice = track.getWaveformSlice(sampleFrame);
            if (slice == null) continue;

            float bass = slice[0];
            float loud = slice[1];
            float high = slice[2];
            float kick = slice[3];

            int bH = (int) (Math.min(1.0f, bass * 1.3f) * halfH);
            int mH = (int) (Math.min(1.0f, Math.max(0f, loud - bass * 0.4f) * 1.1f) * halfH);
            int hH = (int) (Math.min(1.0f, high * 1.2f) * (halfH - 2));

            // Bass band (Pioneer Electric Blue)
            if (bH > 0) {
                int blueCol = (kick > 0.4f) ? 0xFF00AAFF : 0xFF0066EE;
                g.fill(px, cy - bH, px + 1, cy + bH + 1, blueCol);
            }

            // Mid band (Pioneer Warm Amber / Orange)
            if (mH > 0) {
                g.fill(px, cy - mH, px + 1, cy + mH + 1, 0xFFFFAA00);
            }

            // High band (Pioneer Crisp White)
            if (hH > 0) {
                g.fill(px, cy - hH, px + 1, cy + hH + 1, 0xFFFFFFFF);
            }
        }

        // Active Loop Region Highlight & Brackets
        long loopStart = track.getLoopStart();
        long loopLen = track.getLoopLen();
        if (loopLen > 0) {
            int lsX = cx + (int) ((loopStart - currentFrame) / framesPerPixel);
            int leX = cx + (int) ((loopStart + loopLen - currentFrame) / framesPerPixel);
            int drawLs = Math.max(x + 2, Math.min(x + w - 2, lsX));
            int drawLe = Math.max(x + 2, Math.min(x + w - 2, leX));
            if (drawLe > drawLs) {
                g.fill(drawLs, y + 2, drawLe, y + waveH, 0x44FFAA00);
            }
            if (lsX >= x + 2 && lsX < x + w - 2) {
                g.fill(lsX, y + 2, lsX + 1, y + waveH, 0xFFFFAA00);
            }
            if (leX >= x + 2 && leX < x + w - 2) {
                g.fill(leX, y + 2, leX + 1, y + waveH, 0xFFFFAA00);
            }
        }

        // Cue Point Marker (Pioneer Orange Flag 'C')
        long mainCue = dj.getMainCue(deck);
        if (mainCue >= 0) {
            int cueX = cx + (int) ((mainCue - currentFrame) / framesPerPixel);
            if (cueX >= x + 2 && cueX < x + w - 2) {
                g.fill(cueX, y + 2, cueX + 1, y + waveH, 0xFFFF9900);
                g.fill(cueX - 2, y + 2, cueX + 5, y + 8, 0xFFFF9900);
                g.drawString(font, "C", cueX - 1, y + 2, 0xFF000000);
            }
        }

        // Hot Cues A-H Markers (Colored flags with pad letter)
        for (int hIdx = 0; hIdx < 8; hIdx++) {
            long hc = dj.getHotCue(deck, hIdx);
            if (hc >= 0) {
                int hcX = cx + (int) ((hc - currentFrame) / framesPerPixel);
                if (hcX >= x + 2 && hcX < x + w - 2) {
                    int hColor = DjConstants.PAD_COLORS[hIdx];
                    g.fill(hcX, y + 2, hcX + 1, y + waveH, hColor);
                    g.fill(hcX - 2, y + 2, hcX + 5, y + 8, hColor);
                    String flagLetter = String.valueOf((char) ('A' + hIdx));
                    g.drawString(font, flagLetter, hcX - 1, y + 2, 0xFF000000);
                }
            }
        }

        // 3. Center Playhead Needle
        g.fill(cx, y + 1, cx + 1, y + waveH, 0xFFFF2233);
        g.fill(cx - 2, y + 1, cx + 3, y + 2, 0xFFFF2233);
        g.fill(cx - 1, y + 2, cx + 2, y + 3, 0xFFFF2233);
        g.fill(cx - 1, y + waveH - 2, cx + 2, y + waveH - 1, 0xFFFF2233);
        g.fill(cx - 2, y + waveH - 1, cx + 3, y + waveH, 0xFFFF2233);

        // 4. Full-Track Overview Mini-Bar (bottom 3 pixels)
        int overviewY = y + h - 3;
        int overviewW = w - 4;
        g.fill(x + 2, overviewY, x + 2 + overviewW, overviewY + 2, 0xFF0C1018);

        long totalTrackFrames = totalSec > 0 ? (long) (totalSec * rate) : Math.max(track.getFramesRead(), (long) (120 * rate));
        if (totalTrackFrames > 0) {
            float progress = Mth.clamp((float) currentFrame / (float) totalTrackFrames, 0f, 1f);

            for (int ox = 0; ox < overviewW; ox += 2) {
                long oFrame = (long) (((float) ox / (float) overviewW) * totalTrackFrames);
                float[] s = track.getWaveformSlice(oFrame);
                if (s != null) {
                    int c = (s[0] > 0.4f) ? 0xFF0077DD : ((s[1] > 0.3f) ? 0xFFDD8800 : 0xFF284060);
                    g.fill(x + 2 + ox, overviewY, x + 2 + ox + 2, overviewY + 2, c);
                }
            }

            int needleOx = x + 2 + (int) (progress * overviewW);
            g.fill(needleOx - 1, overviewY - 1, needleOx + 2, overviewY + 3, 0xFFFFFFFF);
        }
    }
}
