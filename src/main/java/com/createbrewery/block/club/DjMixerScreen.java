package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Flagship AlphaTheta XDJ-AZ DJ System Screen:
 * Dual CDJ jogwheels with animated on-jog LCD displays and spinning cue needles,
 * iconic circular illuminated CUE and PLAY/PAUSE transport buttons,
 * 4-channel DJM club mixer with 3-band EQ kills, Sound Color FX filters,
 * live stereo LED VU-meters (Green/Yellow/Red ladders),
 * 10.1" tilted central high-res screen with parallel stacked frequency waveforms and beat grid,
 * and automated Drop-to-Redstone pyrotechnic triggers. Client only.
 */
public class DjMixerScreen extends Screen {
    private static final int W = 380, H = 240;
    private static final String[] EFFECTS = {"none", "echo", "reverb"};

    private final BlockPos pos;
    private int left, top;

    // Transport & Decks
    private TransportButton[] playButtons;
    private TransportButton[] cueButtons;
    private JogWheelWidget[] jogWheels;
    private VPitchFader[] pitchFaders;
    private Button[] syncButtons;
    private Button[] cueHeadphones;
    private PadButton[][] loopPads;

    // Center Mixer
    private Knob[][] knobs;
    private Button[] fxButtons;
    private Fader crossfader;
    private Button autoDropButton;
    private Button manualDropButton;
    private Button automixButton;
    private Button ejectButton;

    // Live VU-Meter peak holders
    private float vuA = 0f, vuB = 0f, vuMaster = 0f;

    private DjMixerScreen(BlockPos pos) {
        super(Component.translatable("createbrewery.dj.xdj_az"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DjMixerScreen(pos));
    }

    private DjBoothBlockEntity booth() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof DjBoothBlockEntity dj ? dj : null;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;

        playButtons = new TransportButton[2];
        cueButtons = new TransportButton[2];
        jogWheels = new JogWheelWidget[2];
        pitchFaders = new VPitchFader[2];
        syncButtons = new Button[2];
        cueHeadphones = new Button[2];
        loopPads = new PadButton[2][DjBoothBlockEntity.LOOPS.length];
        knobs = new Knob[2][];
        fxButtons = new Button[2];

        // ---------------------------------------------------- DECK 1 (Left Deck A)
        initDeck(DjBoothBlockEntity.A, left + 8, true);

        // ---------------------------------------------------- DECK 2 (Right Deck B)
        initDeck(DjBoothBlockEntity.B, left + 264, false);

        // ---------------------------------------------------- CENTER MIXER (DJM)
        int mx = left + 120;

        // Auto-Drop, Manual Drop, Auto-mix row
        autoDropButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) DjControl.send(pos, DjControl.AUTO_DROP, dj.isAutoDrop() ? 0f : 1f);
        }).bounds(mx + 4, top + 192, 44, 16)
        .tooltip(Tooltip.create(Component.translatable("createbrewery.dj.autodrop_hint")))
        .build());

        manualDropButton = addRenderableWidget(Button.builder(Component.translatable("createbrewery.dj.drop"), b -> DjControl.send(pos, DjControl.DROP, 0f))
            .bounds(mx + 50, top + 192, 40, 16).build());

        automixButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) DjControl.send(pos, DjControl.AUTOMIX, dj.isAutomix() ? 0f : 1f);
        }).bounds(mx + 92, top + 192, 44, 16).build());

        // Horizontal Magvel Crossfader
        crossfader = addRenderableWidget(new Fader(mx + 4, top + 212, 110, DjControl.CROSSFADER, false));

        // Quick Eject Button
        ejectButton = addRenderableWidget(Button.builder(Component.literal("⏏"), b -> DjControl.send(pos, DjControl.EJECT, 0f))
            .bounds(mx + 116, top + 212, 20, 18)
            .tooltip(Tooltip.create(Component.translatable("createbrewery.dj.eject")))
            .build());

        refresh();
    }

    private void initDeck(int deck, int deckX, boolean isLeft) {
        int d = deck;

        // Jog Wheel (Center at deckX + 54, top + 74, radius 32)
        jogWheels[deck] = addRenderableWidget(new JogWheelWidget(deckX + 6, top + 38, 70, 70, deck));

        // Pitch Fader (Vertical) on the edge
        int faderX = isLeft ? deckX + 84 : deckX + 80;
        int jogOffset = isLeft ? deckX + 6 : deckX + 28;
        if (!isLeft) {
            // Right deck: pitch fader on the outside right
            faderX = deckX + 86;
            jogWheels[deck].setX(deckX + 10);
        } else {
            faderX = deckX + 84;
            jogWheels[deck].setX(deckX + 6);
        }

        pitchFaders[deck] = addRenderableWidget(new VPitchFader(faderX, top + 38, 20, 72, d == DjBoothBlockEntity.A ? DjControl.PITCH_A : DjControl.PITCH_B));

        // BEAT SYNC Button below fader
        syncButtons[deck] = addRenderableWidget(Button.builder(Component.literal("SYNC"), b -> syncTempo(d))
            .bounds(faderX - 2, top + 114, 24, 14)
            .tooltip(Tooltip.create(Component.translatable("createbrewery.dj.sync")))
            .build());

        // Circular Transport Buttons: CUE and PLAY/PAUSE
        int cueX = isLeft ? deckX + 10 : deckX + 16;
        int playX = isLeft ? deckX + 44 : deckX + 50;

        cueButtons[deck] = addRenderableWidget(new TransportButton(cueX, top + 114, 26, 26, false, deck, b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null && dj.isPlaying(d)) {
                DjControl.send(pos, d == DjBoothBlockEntity.A ? DjControl.TOGGLE_A : DjControl.TOGGLE_B, 0f);
            } else {
                MusicPulse.toggleCue(pos, d);
            }
        }));

        playButtons[deck] = addRenderableWidget(new TransportButton(playX, top + 114, 26, 26, true, deck, b ->
            DjControl.send(pos, d == DjBoothBlockEntity.A ? DjControl.TOGGLE_A : DjControl.TOGGLE_B, 0f)));

        // Headphone Cue toggle button
        cueHeadphones[deck] = addRenderableWidget(Button.builder(Component.literal("CUE"), b -> MusicPulse.toggleCue(pos, d))
            .bounds(cueX, top + 144, 28, 14)
            .tooltip(Tooltip.create(Component.translatable("createbrewery.dj.cue_hint")))
            .build());

        // Performance Pads (4 Backlit Rubber Pads for Loop lengths 1, 2, 4, 8)
        int padY = top + 162;
        int padW = 23;
        int[] padColors = {0xFFFF3344, 0xFFFFB300, 0xFF00E5FF, 0xFFA833FF};
        for (int i = 0; i < DjBoothBlockEntity.LOOPS.length; i++) {
            int beats = DjBoothBlockEntity.LOOPS[i];
            int px = deckX + 4 + i * (padW + 3);
            int color = padColors[i % padColors.length];
            loopPads[deck][i] = addRenderableWidget(new PadButton(px, padY, padW, 20, beats, color, b ->
                DjControl.send(pos, DjControl.LOOP, d, beats)));
        }

        // Mixer Channel Knobs for this deck
        int kx = isLeft ? left + 124 : left + 230;
        knobs[deck] = new Knob[] {
            addRenderableWidget(new Knob(kx, top + 92, DjControl.EQ_HIGH, d, "createbrewery.dj.eq_high", false)),
            addRenderableWidget(new Knob(kx, top + 116, DjControl.EQ_MID, d, "createbrewery.dj.eq_mid", false)),
            addRenderableWidget(new Knob(kx, top + 140, DjControl.EQ_LOW, d, "createbrewery.dj.eq_low", false)),
            addRenderableWidget(new Knob(kx, top + 164, DjControl.FILTER, d, "createbrewery.dj.filter", true)),
        };

        // FX Button for Deck A or Center
        if (isLeft) {
            fxButtons[0] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                DjBoothBlockEntity dj = booth();
                if (dj != null) DjControl.send(pos, DjControl.FX, DjBoothBlockEntity.A, dj.getFx(DjBoothBlockEntity.A) + 1);
            }).bounds(left + 172, top + 158, 36, 14).build());
        }
    }

    private void syncTempo(int deck) {
        DjBoothBlockEntity dj = booth();
        if (dj == null || minecraft == null || minecraft.player == null) return;
        double mine = MusicPulse.beatPeriodAt(dj.deckPos(deck)), theirs = MusicPulse.beatPeriodAt(dj.deckPos(1 - deck));
        if (mine <= 0 || theirs <= 0) {
            minecraft.player.displayClientMessage(Component.translatable("createbrewery.dj.tempo_unknown"), true);
            return;
        }
        double best = Double.NaN;
        for (double factor : new double[] {1.0, 2.0, 0.5}) {
            double p = dj.getPitch(deck) * mine / (theirs * factor);
            if (Math.abs(p - 1) <= DjBoothBlockEntity.PITCH_RANGE && (Double.isNaN(best) || Math.abs(p - 1) < Math.abs(best - 1))) best = p;
        }
        if (Double.isNaN(best)) {
            minecraft.player.displayClientMessage(Component.translatable("createbrewery.dj.out_of_range"), true);
            return;
        }
        DjControl.send(pos, deck == DjBoothBlockEntity.A ? DjControl.PITCH_A : DjControl.PITCH_B, (float) best);
        minecraft.player.displayClientMessage(Component.translatable("createbrewery.dj.synced", DjBoothBlockEntity.name(deck), bpm(theirs)), true);
    }

    private static String bpm(double period) {
        return String.format("%.1f", 60.0 / period);
    }

    private void refresh() {
        DjBoothBlockEntity dj = booth();
        if (dj == null || minecraft == null || minecraft.player == null || !minecraft.player.canInteractWithBlock(pos, 1.0)) {
            onClose();
            return;
        }
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0;
        for (int deck = DjBoothBlockEntity.A; deck <= DjBoothBlockEntity.B; deck++) {
            boolean playing = dj.isPlaying(deck);
            boolean hasDisc = !dj.getDisc(deck).isEmpty();
            playButtons[deck].active = hasDisc;
            cueButtons[deck].active = hasDisc;
            pitchFaders[deck].show((dj.getPitch(deck) - (1 - DjBoothBlockEntity.PITCH_RANGE)) / (2 * DjBoothBlockEntity.PITCH_RANGE));

            knobs[deck][0].show(dj.getEq(deck, DjBoothBlockEntity.HIGH));
            knobs[deck][1].show(dj.getEq(deck, DjBoothBlockEntity.MID));
            knobs[deck][2].show(dj.getEq(deck, DjBoothBlockEntity.LOW));
            knobs[deck][3].show((dj.getFilter(deck) + 1) / 2);

            cueHeadphones[deck].setMessage(Component.literal("CUE")
                .withStyle(MusicPulse.isCued(pos, deck) ? ChatFormatting.GOLD : ChatFormatting.GRAY));

            for (int i = 0; i < DjBoothBlockEntity.LOOPS.length; i++) {
                boolean on = dj.getLoopBeats(deck) == DjBoothBlockEntity.LOOPS[i];
                if (loopPads[deck][i] != null) {
                    loopPads[deck][i].setActive(on);
                }
            }
        }

        if (fxButtons[0] != null) {
            fxButtons[0].setMessage(Component.literal(EFFECTS[dj.getFx(DjBoothBlockEntity.A)].toUpperCase()));
        }

        crossfader.show(dj.crossfader(now));
        automixButton.setMessage(Component.literal(dj.isAutomix() ? "AUTO: ON" : "AUTO: OFF")
            .withStyle(dj.isAutomix() ? ChatFormatting.GREEN : ChatFormatting.GRAY));

        autoDropButton.setMessage(Component.literal(dj.isAutoDrop() ? "A-DROP: ON" : "A-DROP: OFF")
            .withStyle(dj.isAutoDrop() ? ChatFormatting.RED : ChatFormatting.GRAY));
    }

    @Override
    public void tick() {
        refresh();
        if (pitchFaders != null) {
            for (VPitchFader f : pitchFaders) f.flush();
            crossfader.flush();
            for (Knob[] row : knobs) for (Knob k : row) k.flush();
        }

        // Live VU-Meter peak decay (smooth analog response)
        DjBoothBlockEntity dj = booth();
        if (dj != null && minecraft != null && minecraft.level != null) {
            float targetA = dj.isPlaying(DjBoothBlockEntity.A)
                ? Mth.clamp((MusicPulse.kickNear(dj.deckPos(DjBoothBlockEntity.A)) * 0.75f + 0.25f) * dj.getEq(DjBoothBlockEntity.A, DjBoothBlockEntity.LOW) * 1.4f, 0f, 1f)
                : 0f;
            float targetB = dj.isPlaying(DjBoothBlockEntity.B)
                ? Mth.clamp((MusicPulse.kickNear(dj.deckPos(DjBoothBlockEntity.B)) * 0.75f + 0.25f) * dj.getEq(DjBoothBlockEntity.B, DjBoothBlockEntity.LOW) * 1.4f, 0f, 1f)
                : 0f;
            float xf = dj.crossfader(minecraft.level.getGameTime());
            float targetMaster = Math.max(targetA * (1f - xf), targetB * xf);

            vuA = targetA > vuA ? targetA : vuA * 0.85f;
            vuB = targetB > vuB ? targetB : vuB * 0.85f;
            vuMaster = targetMaster > vuMaster ? targetMaster : vuMaster * 0.88f;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);

        // AlphaTheta XDJ-AZ Matte Black Metallic Chassis
        g.fill(left, top, left + W, top + H, 0xFF111317);
        g.renderOutline(left, top, W, H, 0xFF353944);

        // Left Deck Recessed Plate
        g.fill(left + 6, top + 6, left + 116, top + H - 6, 0xFF171920);
        g.renderOutline(left + 6, top + 6, 110, H - 12, 0xFF242730);

        // Right Deck Recessed Plate
        g.fill(left + 264, top + 6, left + W - 6, top + H - 6, 0xFF171920);
        g.renderOutline(left + 264, top + 6, 110, H - 12, 0xFF242730);

        // Center Mixer Recessed Plate
        g.fill(left + 118, top + 84, left + 262, top + H - 6, 0xFF14161C);
        g.renderOutline(left + 118, top + 84, 144, H - 90, 0xFF282B35);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        DjBoothBlockEntity dj = booth();
        if (dj == null || minecraft == null || minecraft.level == null) return;
        long time = minecraft.level.getGameTime();

        // ---------------------------------------------------- TOP CENTER: 10.1" XDJ-AZ SCREEN
        renderTouchScreen(g, left + 120, top + 6, 140, 76, dj, time, partialTick);

        // ---------------------------------------------------- HEADERS & TRACK LABELS
        for (int deck = DjBoothBlockEntity.A; deck <= DjBoothBlockEntity.B; deck++) {
            int dx = deck == DjBoothBlockEntity.A ? left + 10 : left + 268;
            ItemStack disc = dj.getDisc(deck);
            boolean playing = dj.isPlaying(deck);
            int color = deck == DjBoothBlockEntity.A ? 0xFF00E5FF : 0xFFFF9900;

            g.drawString(font, deck == DjBoothBlockEntity.A ? "DECK 1" : "DECK 2", dx, top + 10, color);
            String title = disc.isEmpty() ? Component.translatable("createbrewery.dj.empty").getString() : dj.title(disc).getString();
            g.drawString(font, font.plainSubstrByWidth(title, 98), dx, top + 22, playing ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        // ---------------------------------------------------- STEREO LED VU-METERS (DJM Section)
        renderVuMeter(g, left + 154, top + 90, 7, 58, vuA);
        renderVuMeter(g, left + 219, top + 90, 7, 58, vuB);
        renderMasterVu(g, left + 179, top + 90, 22, 58, vuMaster);
    }

    /** Renders the iconic 10.1" tilted touchscreen of the XDJ-AZ with parallel stacked waveforms and beat grids. */
    private void renderTouchScreen(GuiGraphics g, int x, int y, int w, int h, DjBoothBlockEntity dj, long time, float pt) {
        // Angled Bezel
        g.fill(x, y, x + w, y + h, 0xFF1B1D25);
        g.renderOutline(x, y, w, h, 0xFF383C4A);

        // High-contrast screen face
        int sx = x + 3, sy = y + 3, sw = w - 6, sh = h - 6;
        g.fill(sx, sy, sx + sw, sy + sh, 0xFF050A14);

        // Top Status Header: Model Badge & Sources
        g.drawString(font, "AlphaTheta XDJ-AZ", sx + 4, sy + 3, 0xFF88AAFF);
        g.drawString(font, "USB1", sx + sw - 26, sy + 3, 0xFF44FF88);

        // Deck 1 Waveform (Cyan / Electric Blue)
        renderWaveform(g, sx + 2, sy + 14, sw - 4, 18, DjBoothBlockEntity.A, dj, 0xFF00E5FF, 0xFF0077FF, time, pt);

        // Deck 2 Waveform (Orange / Golden Amber)
        renderWaveform(g, sx + 2, sy + 36, sw - 4, 18, DjBoothBlockEntity.B, dj, 0xFFFF8800, 0xFFFFCC00, time, pt);

        // Beat Phase Grid (4-beat indicator dots)
        int phaseX = sx + (sw - 36) / 2, phaseY = sy + 58;
        int beatIndex = (int) ((time / 10) % 4);
        for (int i = 0; i < 4; i++) {
            boolean active = i == beatIndex && (dj.isPlaying(DjBoothBlockEntity.A) || dj.isPlaying(DjBoothBlockEntity.B));
            int col = active ? 0xFFFFFFFF : 0xFF202A3C;
            g.fill(phaseX + i * 9, phaseY, phaseX + i * 9 + 7, phaseY + 5, col);
        }
    }

    /** Renders scrolling / animated frequency waveform bars for a deck. */
    private void renderWaveform(GuiGraphics g, int x, int y, int w, int h, int deck, DjBoothBlockEntity dj, int colorHigh, int colorLow, long time, float pt) {
        g.fill(x, y, x + w, y + h, 0xFF080E1A);
        g.renderOutline(x, y, w, h, 0xFF142034);

        boolean playing = dj.isPlaying(deck);
        double period = MusicPulse.beatPeriodAt(dj.deckPos(deck));
        String bpmText = playing && period > 0 ? bpm(period) : "--";
        g.drawString(font, bpmText, x + 3, y + 2, colorHigh);

        // Center playhead cursor
        int cx = x + w / 2;
        g.fill(cx, y, cx + 1, y + h, 0xFFFFFFFF);

        if (!playing && dj.getDisc(deck).isEmpty()) {
            g.drawString(font, "NO TRACK", cx - 22, y + 5, 0xFF35445A);
            return;
        }

        // 32 vertical waveform frequency bands
        int bars = 32;
        float barW = (float) w / bars;
        float pulse = playing ? MusicPulse.kickNear(dj.deckPos(deck)) : 0.1f;
        float scrollOffset = playing ? (time + pt) * 0.8f : 0f;

        for (int i = 0; i < bars; i++) {
            float bx = x + i * barW;
            // Procedural pseudo-frequency waveform heights with scrolling energy peaks
            double wavePhase = (i * 0.45) + scrollOffset;
            float rawAmp = (float) (Math.sin(wavePhase) * 0.5 + Math.sin(wavePhase * 2.3) * 0.3 + 0.8);
            float amp = Math.min(1.0f, rawAmp * (0.4f + pulse * 0.6f));
            int barHeight = Math.max(2, (int) (amp * (h - 4)));
            int by = y + (h - barHeight) / 2;

            int col = (i % 2 == 0) ? colorHigh : colorLow;
            g.fill((int) bx, by, (int) (bx + Math.max(1, barW - 1)), by + barHeight, col);
        }
    }

    /** Vertical Stereo LED VU-Meter with 10 LED segments (Green -> Amber -> Red). */
    private void renderVuMeter(GuiGraphics g, int x, int y, int w, int h, float level) {
        g.fill(x, y, x + w, y + h, 0xFF0B0D12);
        g.renderOutline(x, y, w, h, 0xFF20242E);

        int leds = 10;
        int ledHeight = (h - 4) / leds;
        int activeCount = (int) Math.round(level * leds);

        for (int i = 0; i < leds; i++) {
            // Segment 0 is bottom, 9 is top
            int segFromBottom = i;
            int ly = y + h - 2 - (i + 1) * ledHeight;
            boolean lit = segFromBottom < activeCount;

            int ledColor;
            if (segFromBottom >= 8) {
                // Top 2: Red
                ledColor = lit ? 0xFFFF2222 : 0xFF2A0808;
            } else if (segFromBottom >= 5) {
                // Middle 3: Amber/Yellow
                ledColor = lit ? 0xFFFFBB00 : 0xFF2A2006;
            } else {
                // Bottom 5: Green
                ledColor = lit ? 0xFF00FF55 : 0xFF06240C;
            }
            g.fill(x + 1, ly, x + w - 1, ly + ledHeight - 1, ledColor);
        }
    }

    /** Dual Master L/R LED VU-Meter with center scale marks. */
    private void renderMasterVu(GuiGraphics g, int x, int y, int w, int h, float level) {
        g.fill(x, y, x + w, y + h, 0xFF0B0D12);
        g.renderOutline(x, y, w, h, 0xFF20242E);

        int barW = 6;
        renderVuMeter(g, x + 2, y + 2, barW, h - 4, level);
        renderVuMeter(g, x + w - barW - 2, y + 2, barW, h - 4, level * 0.96f);

        // Center dB scale ticks
        g.fill(x + 10, y + 6, x + 12, y + 7, 0xFFFF3333);
        g.fill(x + 10, y + 20, x + 12, y + 21, 0xFFFFCC00);
        g.fill(x + 10, y + 36, x + 12, y + 37, 0xFF44FF88);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================================
    // CUSTOM XDJ-AZ WIDGETS
    // =====================================================================================

    /**
     * CDJ Jog Wheel Widget:
     * Textured rim, black vinyl platter, and an on-jog circular LCD display with a spinning white cue needle!
     */
    private class JogWheelWidget extends AbstractWidget {
        private final int deck;

        JogWheelWidget(int x, int y, int w, int h, int deck) {
            super(x, y, w, h, Component.literal("Jog"));
            this.deck = deck;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + width / 2, cy = getY() + height / 2;
            int r = width / 2;

            // Outer metallic rim with grip ticks
            g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF282B34);
            g.fill(cx - r + 2, cy - r + 2, cx + r - 2, cy + r - 2, 0xFF181A20);

            // Vinyl Platter surface
            int pr = r - 5;
            g.fill(cx - pr, cy - pr, cx + pr, cy + pr, 0xFF0E1014);
            g.renderOutline(cx - pr, cy - pr, pr * 2, pr * 2, 0xFF1E2129);

            // Subtle vinyl grooves
            g.renderOutline(cx - pr + 4, cy - pr + 4, (pr - 4) * 2, (pr - 4) * 2, 0xFF14161C);

            // On-Jog Center LCD Display
            int cr = 14;
            g.fill(cx - cr, cy - cr, cx + cr, cy + cr, 0xFF04070D);
            int ringCol = deck == DjBoothBlockEntity.A ? 0xFF00E5FF : 0xFFFF9900;
            g.renderOutline(cx - cr, cy - cr, cr * 2, cr * 2, ringCol);

            // Center Deck Number
            g.drawCenteredString(font, deck == DjBoothBlockEntity.A ? "1" : "2", cx, cy - 4, 0xFFFFFFFF);

            // Spinning Cue Needle (rotates smoothly when deck is actively playing!)
            DjBoothBlockEntity dj = booth();
            if (dj != null && dj.isPlaying(deck) && minecraft != null && minecraft.level != null) {
                float angle = (minecraft.level.getGameTime() + partialTick) * 14.0f * dj.getPitch(deck);
                double rad = Math.toRadians(angle);
                int nx = cx + (int) Math.round(Math.cos(rad) * (cr - 2));
                int ny = cy + (int) Math.round(Math.sin(rad) * (cr - 2));
                g.fill(nx - 1, ny - 1, nx + 2, ny + 2, 0xFFFFFFFF);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            // Scratch / Pitch Nudge click
            DjBoothBlockEntity dj = booth();
            if (dj != null && dj.isPlaying(deck)) {
                // Gentle pitch bend nudge on click
                float cur = dj.getPitch(deck);
                float nudge = mouseX > getX() + width / 2 ? cur + 0.01f : cur - 0.01f;
                DjControl.send(pos, deck == DjBoothBlockEntity.A ? DjControl.PITCH_A : DjControl.PITCH_B, nudge);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /**
     * Circular illuminated Transport Buttons for CUE (Amber) and PLAY/PAUSE (Green).
     */
    private class TransportButton extends AbstractWidget {
        private final boolean isPlay;
        private final int deck;
        private final java.util.function.Consumer<TransportButton> onPress;

        TransportButton(int x, int y, int w, int h, boolean isPlay, int deck, java.util.function.Consumer<TransportButton> onPress) {
            super(x, y, w, h, Component.literal(isPlay ? "PLAY" : "CUE"));
            this.isPlay = isPlay;
            this.deck = deck;
            this.onPress = onPress;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, isPlay ? 1.2f : 1.0f);
            }
            onPress.accept(this);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + width / 2, cy = getY() + height / 2;
            int r = width / 2;

            DjBoothBlockEntity dj = booth();
            boolean active = dj != null && (isPlay ? dj.isPlaying(deck) : MusicPulse.isCued(pos, deck));

            // Outer Ring Glow
            int ringColor = isPlay
                ? (active ? 0xFF00FF66 : (isHoveredOrFocused() ? 0xFF008833 : 0xFF004418))
                : (active ? 0xFFFF9900 : (isHoveredOrFocused() ? 0xFF995500 : 0xFF442200));

            g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF101216);
            g.renderOutline(cx - r, cy - r, width, height, ringColor);
            g.fill(cx - r + 3, cy - r + 3, cx + r - 3, cy + r - 3, isPlay ? 0xFF06140A : 0xFF140D04);

            // Icon / Label
            if (isPlay) {
                int iconCol = active ? 0xFF00FF66 : 0xFF558866;
                g.drawString(font, "▶||", cx - 7, cy - 4, iconCol);
            } else {
                int iconCol = active ? 0xFFFF9900 : 0xFF886644;
                g.drawCenteredString(font, "CUE", cx, cy - 4, iconCol);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /**
     * Backlit Rubber Performance Pad (1, 2, 4, 8 Beat Loops).
     */
    private static class PadButton extends AbstractWidget {
        private final int beats;
        private final int baseColor;
        private final java.util.function.Consumer<PadButton> onPress;
        private boolean active;

        PadButton(int x, int y, int w, int h, int beats, int baseColor, java.util.function.Consumer<PadButton> onPress) {
            super(x, y, w, h, Component.literal(String.valueOf(beats)));
            this.beats = beats;
            this.baseColor = baseColor;
            this.onPress = onPress;
        }

        void setActive(boolean on) {
            this.active = on;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPress.accept(this);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int bg = active ? (baseColor & 0x00FFFFFF) | 0x88000000 : (isHoveredOrFocused() ? 0xFF242630 : 0xFF161820);
            int border = active ? baseColor : (isHoveredOrFocused() ? baseColor : 0xFF353948);

            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.renderOutline(getX(), getY(), width, height, border);

            int textCol = active ? 0xFFFFFFFF : (isHoveredOrFocused() ? 0xFFE0E0E0 : 0xFFAAAAAA);
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font, String.valueOf(beats), getX() + width / 2, getY() + (height - 8) / 2, textCol);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /**
     * Vertical Pitch / Tempo Fader with center zero detent line.
     */
    private class VPitchFader extends AbstractWidget {
        private final byte action;
        private double value = 0.5; // 0..1
        private boolean held, dirty;

        VPitchFader(int x, int y, int w, int h, byte action) {
            super(x, y, w, h, Component.empty());
            this.action = action;
        }

        void show(double v) {
            if (!held && !dirty) value = Mth.clamp(v, 0.0, 1.0);
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            float p = (float) (1.0 - DjBoothBlockEntity.PITCH_RANGE + value * 2.0 * DjBoothBlockEntity.PITCH_RANGE);
            DjControl.send(pos, action, p);
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
        public void onRelease(double mouseX, double mouseY) {
            held = false;
        }

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
            // Track slot
            int trackX = getX() + width / 2;
            g.fill(trackX - 1, getY() + 4, trackX + 1, getY() + height - 4, 0xFF08090C);
            g.renderOutline(trackX - 2, getY() + 3, 4, height - 6, 0xFF282B34);

            // Center 0% detent line
            int midY = getY() + height / 2;
            g.fill(trackX - 4, midY, trackX + 4, midY + 1, 0xFF88AAFF);

            // Metal Fader Handle
            int handleY = getY() + 4 + (int) Math.round((1.0 - value) * (height - 12));
            int hx = getX() + 2, hw = width - 4;
            g.fill(hx, handleY, hx + hw, handleY + 6, isHoveredOrFocused() ? 0xFF888E9A : 0xFF585D68);
            g.fill(hx + 1, handleY + 2, hx + hw - 1, handleY + 4, 0xFFFFFFFF);
            g.renderOutline(hx, handleY, hw, 6, 0xFF14161C);

            // Percentage readout
            double percent = (value * 2.0 - 1.0) * DjBoothBlockEntity.PITCH_RANGE * 100.0;
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font, String.format("%+.1f", percent), trackX, getY() - 9, Math.abs(percent) < 0.1 ? 0xFF55FF88 : 0xFFAAAAAA);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /**
     * Rotary knob widget with glowing 15-dot LED arc.
     */
    private class Knob extends AbstractWidget {
        private final byte action;
        private final int deck;
        private final boolean bipolar;
        private double value = 0.5, grabY, grabValue;
        private boolean held, dirty;
        private long lastClick;

        Knob(int x, int y, byte action, int deck, String label, boolean bipolar) {
            super(x, y, 22, 22, Component.translatable(label));
            this.action = action;
            this.deck = deck;
            this.bipolar = bipolar;
            if (action == DjControl.FX_AMOUNT) value = 0;
            setTooltip(Tooltip.create(Component.translatable(label + ".hint")));
        }

        void show(double v) {
            if (!held && !dirty) value = v;
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            float v = (float) (bipolar ? value * 2 - 1 : value);
            DjControl.send(pos, action, deck, v);

            DjBoothBlockEntity dj = booth();
            if (dj == null) return;
            switch (action) {
                case DjControl.EQ_HIGH -> dj.setEq(deck, DjBoothBlockEntity.HIGH, v);
                case DjControl.EQ_MID -> dj.setEq(deck, DjBoothBlockEntity.MID, v);
                case DjControl.EQ_LOW -> dj.setEq(deck, DjBoothBlockEntity.LOW, v);
                case DjControl.FILTER -> dj.setFilter(deck, v);
                case DjControl.FX_AMOUNT -> dj.setFxAmount(deck, v);
                default -> {}
            }
        }

        private void set(double v) {
            value = Math.max(0, Math.min(1, v));
            dirty = true;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            long now = net.minecraft.Util.getMillis();
            if (now - lastClick < 300) set(action == DjControl.FX_AMOUNT ? 0 : 0.5);
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
        public void onRelease(double mouseX, double mouseY) {
            held = false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isHovered()) return false;
            set(value + scrollY * 0.04);
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + 11, cy = getY() + 9;
            int dots = 13;
            for (int i = 0; i < dots; i++) {
                double f = i / (double) (dots - 1);
                double a = Math.toRadians(135 + 270 * f);
                int px = cx + (int) Math.round(Math.cos(a) * 8), py = cy + (int) Math.round(Math.sin(a) * 8);
                boolean lit = bipolar || action != DjControl.FX_AMOUNT
                    ? (f >= Math.min(0.5, value) - 1e-6 && f <= Math.max(0.5, value) + 1e-6)
                    : f <= value + 1e-6;
                g.fill(px - 1, py - 1, px + 1, py + 1, lit ? 0xFF00E5FF : 0xFF222630);
            }
            g.fill(cx - 4, cy - 4, cx + 4, cy + 4, isHoveredOrFocused() ? 0xFF656B7A : 0xFF424652);
            double a = Math.toRadians(135 + 270 * value);
            for (int r = 1; r <= 4; r++) {
                int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
            }
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font, getMessage(), cx, getY() + 17, value < 0.02 && action != DjControl.FX_AMOUNT && !bipolar ? 0xFFFF4444 : 0xFFAAAAAA);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, Component.translatable("createbrewery.dj.knob_narration", getMessage(), Math.round(value * 100)));
        }
    }

    /**
     * Horizontal Magvel Crossfader.
     */
    private class Fader extends AbstractSliderButton {
        private final byte action;
        private boolean held, dirty;

        Fader(int x, int y, int w, byte action, boolean isPitch) {
            super(x, y, w, 18, Component.empty(), 0.5);
            this.action = action;
            updateMessage();
        }

        void show(double v) {
            if (!held && !dirty && Math.abs(v - value) > 1e-3) {
                value = v;
                updateMessage();
            }
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            DjControl.send(pos, action, (float) value);
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("createbrewery.dj.crossfader"));
        }

        @Override
        protected void applyValue() {
            dirty = true;
        }

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
    }
}
