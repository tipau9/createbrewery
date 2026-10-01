package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import com.createbrewery.sound.ModSounds;
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
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Flagship AlphaTheta XDJ-AZ All-In-One DJ System Screen:
 * - 4-Deck Standalone DJ System (Deck 1 / 3 on left physical player, Deck 2 / 4 on right physical player)
 * - 10.1" Capacitive Touchscreen with WAVEFORM, BROWSE, and INFO diagnostic tabs
 * - Interactive Crate / Disc Browser with virtual rotary load buttons
 * - Dual CDJ Jogwheels with on-jog LCD center displays, deck rings, and spinning cue needles
 * - 8 Backlit Rubber Performance Pads per deck with 4 Pad Modes (HOT CUE, BEAT LOOP, SLIP LOOP, BEAT JUMP)
 * - 6 Dedicated Sound Color FX buttons (SPACE, DUB ECHO, SWEEP, NOISE, CRUSH, FILTER) + PARAMETER knob
 * - Complete 4-Channel DJM Club Mixer (Channels 1, 2, 3, 4 with Trim, 3-Band Kill EQ, Color, Cue, Level Meters, Volume Faders, Crossfader Assign)
 * - Dedicated Beat FX Unit with Delay, Echo, Reverb, Flanger, Phaser, Roll, Trans, Beat fraction selector, and pulsing illuminated ON/OFF button
 * - Automated Beat-Drop to Redstone Pyrotechnic pulse triggers & Auto-Mix engine.
 */
public class DjMixerScreen extends Screen {
    private static final int W = 424, H = 248;

    private static final int[] DECK_COLORS = {
        0xFF00E5FF, // Deck 1: Cyan / Electric Blue
        0xFFFF9900, // Deck 2: Orange / Golden Amber
        0xFFA833FF, // Deck 3: Purple / Neon Violet
        0xFF00FF66  // Deck 4: Lime / Neon Green
    };

    private static final int[] PAD_COLORS = {
        0xFFFF3344, // Pad 1: Red
        0xFFFF7722, // Pad 2: Orange
        0xFFFFCC00, // Pad 3: Yellow
        0xFF00FF66, // Pad 4: Green
        0xFF00E5FF, // Pad 5: Cyan
        0xFF2288FF, // Pad 6: Blue
        0xFFA833FF, // Pad 7: Purple
        0xFFFF33AA  // Pad 8: Magenta
    };

    private static final String[] PAD_MODE_NAMES = {"HOT CUE", "BEAT LOOP", "SLIP LOOP", "BEAT JUMP"};

    private final BlockPos pos;
    private int left, top;

    // Physical player active layers
    private int leftDeck = DjBoothBlockEntity.A;   // 0 (Deck 1) or 2 (Deck 3)
    private int rightDeck = DjBoothBlockEntity.B;  // 1 (Deck 2) or 3 (Deck 4)
    private int screenTab = 0;                     // 0 = WAVE, 1 = BROWSE, 2 = INFO
    private int browseOffset = 0;

    // Left & Right Decks UI controls (index 0 = left physical player, 1 = right physical player)
    private final Button[] deckLayerButtons = new Button[4];
    private final TransportButton[] playButtons = new TransportButton[2];
    private final TransportButton[] cueButtons = new TransportButton[2];
    private final JogWheelWidget[] jogWheels = new JogWheelWidget[2];
    private final VPitchFader[] pitchFaders = new VPitchFader[2];
    private final Button[] syncButtons = new Button[2];
    private final Button[] cueHeadphones = new Button[2];
    private final Button[] vinylButtons = new Button[2];
    private final Button[] slipButtons = new Button[2];
    private final Button[] revButtons = new Button[2];
    private final Button[][] padModeTabs = new Button[2][4];
    private final PadButton[][] performancePads = new PadButton[2][8];

    // Center Mixer (4 Channels)
    private final Knob[][] chKnobs = new Knob[DjBoothBlockEntity.DECKS][5]; // TRIM, HIGH, MID, LOW, COLOR
    private final VFader[] chFaders = new VFader[DjBoothBlockEntity.DECKS];
    private final Button[] chCueButtons = new Button[DjBoothBlockEntity.DECKS];
    private final Button[] chXfAssignButtons = new Button[DjBoothBlockEntity.DECKS];

    // Sound Color FX Unit (6 buttons + Parameter knob)
    private final Button[] colorFxButtons = new Button[DjBoothBlockEntity.COLOR_FX_COUNT];
    private Knob colorParamKnob;

    // Beat FX Unit
    private Button bfxTypeButton;
    private Button bfxBeatsDown, bfxBeatsUp;
    private Button bfxChButton;
    private BeatFxButton bfxOnButton;
    private Knob bfxDepthKnob;

    // Bottom Controls & Crossfader
    private Fader crossfader;
    private Button autoDropButton;
    private Button manualDropButton;
    private Button automixButton;
    private Button ejectButton;

    // Touchscreen Tab Buttons
    private Button tabWaveButton;
    private Button tabBrowseButton;
    private Button tabInfoButton;

    // Live VU-Meter peak followers
    private final float[] chVU = new float[DjBoothBlockEntity.DECKS];
    private float vuMaster = 0f;

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

        // ---------------------------------------------------------------------------------
        // 1. LEFT PHYSICAL PLAYER (Decks 1 & 3)
        // ---------------------------------------------------------------------------------
        initDeckPlayer(0, left + 6, true);

        // ---------------------------------------------------------------------------------
        // 2. RIGHT PHYSICAL PLAYER (Decks 2 & 4)
        // ---------------------------------------------------------------------------------
        initDeckPlayer(1, left + 300, false);

        // ---------------------------------------------------------------------------------
        // 3. TOP TOUCHSCREEN (10.1" Capacitive Display)
        // ---------------------------------------------------------------------------------
        int sx = left + 130, sy = top + 6;
        tabWaveButton = addRenderableWidget(Button.builder(Component.literal("WAVE"), b -> screenTab = 0)
            .bounds(sx + 84, sy + 2, 24, 11).build());
        tabBrowseButton = addRenderableWidget(Button.builder(Component.literal("BROWSE"), b -> screenTab = 1)
            .bounds(sx + 110, sy + 2, 30, 11).build());
        tabInfoButton = addRenderableWidget(Button.builder(Component.literal("INFO"), b -> screenTab = 2)
            .bounds(sx + 142, sy + 2, 20, 11).build());

        // ---------------------------------------------------------------------------------
        // 4. CENTER MIXER - SOUND COLOR FX UNIT (Left side of mixer)
        // ---------------------------------------------------------------------------------
        int mx = left + 128;
        String[] colorLabels = {"SPC", "DUB", "SWP", "NOI", "CRU", "FLT"};
        for (int i = 0; i < DjBoothBlockEntity.COLOR_FX_COUNT; i++) {
            int fxId = i;
            int cx = mx + 2 + (i / 3) * 16;
            int cy = top + 88 + (i % 3) * 14;
            colorFxButtons[i] = addRenderableWidget(Button.builder(Component.literal(colorLabels[i]), b ->
                DjControl.send(pos, DjControl.COLOR_FX_SELECT, fxId))
                .bounds(cx, cy, 15, 12)
                .tooltip(Tooltip.create(Component.literal(DjBoothBlockEntity.COLOR_FX_NAMES[i])))
                .build());
        }
        colorParamKnob = addRenderableWidget(new Knob(mx + 8, top + 134, DjControl.COLOR_FX_PARAM, 0, "PARAM", false));

        // ---------------------------------------------------------------------------------
        // 5. CENTER MIXER - 4 CHANNEL STRIPS (Channels 1, 2, 3, 4)
        // ---------------------------------------------------------------------------------
        for (int ch = 0; ch < DjBoothBlockEntity.DECKS; ch++) {
            int d = ch;
            int cx = mx + 36 + ch * 23;

            // TRIM, HI, MID, LOW, COLOR knobs
            chKnobs[ch][0] = addRenderableWidget(new Knob(cx, top + 86, DjControl.TRIM, d, "TRIM", false));
            chKnobs[ch][1] = addRenderableWidget(new Knob(cx, top + 100, DjControl.EQ_HIGH, d, "HI", false));
            chKnobs[ch][2] = addRenderableWidget(new Knob(cx, top + 114, DjControl.EQ_MID, d, "MID", false));
            chKnobs[ch][3] = addRenderableWidget(new Knob(cx, top + 128, DjControl.EQ_LOW, d, "LOW", false));
            chKnobs[ch][4] = addRenderableWidget(new Knob(cx, top + 142, DjControl.FILTER, d, "COLOR", true));

            // Channel Volume Fader (Vertical)
            chFaders[ch] = addRenderableWidget(new VFader(cx + 3, top + 158, 14, 38, d, f -> {
                DjControl.send(pos, DjControl.CHANNEL_FADER, d, f);
                DjBoothBlockEntity dj = booth();
                if (dj != null) dj.setChannelFader(d, f);
            }));

            // Channel Headphone CUE button
            chCueButtons[ch] = addRenderableWidget(Button.builder(Component.literal("CUE"), b -> MusicPulse.toggleCue(pos, d))
                .bounds(cx + 2, top + 198, 16, 10).build());

            // Crossfader Assign switch [A | · | B]
            chXfAssignButtons[ch] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                DjBoothBlockEntity dj = booth();
                if (dj != null) {
                    int next = (dj.getCrossfaderAssign(d) + 1) % 3;
                    DjControl.send(pos, DjControl.CROSSFADER_ASSIGN, d, next);
                }
            }).bounds(cx + 1, top + 210, 18, 10).build());
        }

        // ---------------------------------------------------------------------------------
        // 6. CENTER MIXER - BEAT FX UNIT (Right side of mixer)
        // ---------------------------------------------------------------------------------
        int bfxX = mx + 130;
        bfxTypeButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) {
                int next = (dj.getBeatFxType() + 1) % DjBoothBlockEntity.BFX_COUNT;
                DjControl.send(pos, DjControl.BEAT_FX_TYPE, next);
            }
        }).bounds(bfxX, top + 86, 34, 12).build());

        // Beat Fraction ◀ ▶ buttons
        bfxBeatsDown = addRenderableWidget(Button.builder(Component.literal("◀"), b -> adjustBeatFxBeats(false))
            .bounds(bfxX, top + 100, 16, 12).build());
        bfxBeatsUp = addRenderableWidget(Button.builder(Component.literal("▶"), b -> adjustBeatFxBeats(true))
            .bounds(bfxX + 18, top + 100, 16, 12).build());

        // Channel selector
        bfxChButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) {
                int ch = dj.getBeatFxChannel();
                int next = ch >= 3 ? -1 : ch + 1; // -1 (Master) -> 0 -> 1 -> 2 -> 3 -> -1
                DjControl.send(pos, DjControl.BEAT_FX_CHANNEL, next);
            }
        }).bounds(bfxX, top + 114, 34, 12).build());

        // Pulsing Beat FX ON/OFF button
        bfxOnButton = addRenderableWidget(new BeatFxButton(bfxX + 7, top + 128, 20, 20, b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) DjControl.send(pos, DjControl.BEAT_FX_ON, dj.isBeatFxOn() ? 0f : 1f);
        }));

        bfxDepthKnob = addRenderableWidget(new Knob(bfxX + 7, top + 152, DjControl.BEAT_FX_DEPTH, 0, "DEPTH", false));

        // ---------------------------------------------------------------------------------
        // 7. BOTTOM CONTROLS: Magvel Crossfader & Redstone Automation
        // ---------------------------------------------------------------------------------
        crossfader = addRenderableWidget(new Fader(mx + 30, top + 226, 78, DjControl.CROSSFADER, false));

        autoDropButton = addRenderableWidget(Button.builder(Component.literal("A-DROP"), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) DjControl.send(pos, DjControl.AUTO_DROP, dj.isAutoDrop() ? 0f : 1f);
        }).bounds(mx + 112, top + 224, 25, 10).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.autodrop_hint"))).build());

        manualDropButton = addRenderableWidget(Button.builder(Component.literal("DROP"), b ->
            DjControl.send(pos, DjControl.DROP, 0f))
            .bounds(mx + 112, top + 235, 25, 10).build());

        automixButton = addRenderableWidget(Button.builder(Component.literal("AUTO"), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) DjControl.send(pos, DjControl.AUTOMIX, dj.isAutomix() ? 0f : 1f);
        }).bounds(mx + 139, top + 224, 25, 10).build());

        ejectButton = addRenderableWidget(Button.builder(Component.literal("⏏"), b ->
            DjControl.send(pos, DjControl.EJECT, 0f))
            .bounds(mx + 139, top + 235, 25, 10).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.eject"))).build());

        refresh();
    }

    private void initDeckPlayer(int playerIdx, int px, boolean isLeft) {
        int d1 = isLeft ? DjBoothBlockEntity.A : DjBoothBlockEntity.B;
        int d2 = isLeft ? DjBoothBlockEntity.C : DjBoothBlockEntity.D;

        // Deck Layer Switch Buttons: [1]/[3] or [2]/[4]
        deckLayerButtons[d1] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(d1 + 1)), b -> {
            if (isLeft) leftDeck = d1; else rightDeck = d1;
            refresh();
        }).bounds(px + 4, top + 8, 20, 12).build());

        deckLayerButtons[d2] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(d2 + 1)), b -> {
            if (isLeft) leftDeck = d2; else rightDeck = d2;
            refresh();
        }).bounds(px + 26, top + 8, 20, 12).build());

        // CDJ Jogwheel (Center at px + 40, top + 56, radius 32)
        jogWheels[playerIdx] = addRenderableWidget(new JogWheelWidget(px + 8, top + 24, 64, 64, isLeft));

        // Pitch / Tempo Vertical Fader
        pitchFaders[playerIdx] = addRenderableWidget(new VPitchFader(px + 84, top + 24, 18, 64, isLeft));

        // Deck Controls row: SYNC, VINYL, SLIP, REV
        syncButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("SYNC"), b -> syncTempo(currentDeck(isLeft)))
            .bounds(px + 80, top + 92, 28, 12).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.sync"))).build());

        vinylButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("VINYL"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) DjControl.send(pos, DjControl.VINYL_MODE, cd, dj.isVinylMode(cd) ? 0f : 1f);
        }).bounds(px + 4, top + 92, 24, 12).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.vinyl_hint"))).build());

        slipButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("SLIP"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) DjControl.send(pos, DjControl.SLIP_MODE, cd, dj.isSlipMode(cd) ? 0f : 1f);
        }).bounds(px + 30, top + 92, 22, 12).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.slip_hint"))).build());

        revButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("REV"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) DjControl.send(pos, DjControl.REVERSE, cd, dj.isReverse(cd) ? 0f : 1f);
        }).bounds(px + 54, top + 92, 22, 12).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.reverse_hint"))).build());

        // Transport Buttons: CUE and PLAY/PAUSE
        cueButtons[playerIdx] = addRenderableWidget(new TransportButton(px + 8, top + 108, 26, 26, false, isLeft, b -> {
            int cd = currentDeck(isLeft);
            DjBoothBlockEntity dj = booth();
            if (dj != null && dj.isPlaying(cd)) {
                DjControl.send(pos, DjControl.TOGGLE_PLAY, cd, 0f);
            } else {
                MusicPulse.toggleCue(pos, cd);
            }
        }));

        playButtons[playerIdx] = addRenderableWidget(new TransportButton(px + 38, top + 108, 26, 26, true, isLeft, b -> {
            int cd = currentDeck(isLeft);
            DjControl.send(pos, DjControl.TOGGLE_PLAY, cd, 0f);
        }));

        // Headphone CUE monitor button
        cueHeadphones[playerIdx] = addRenderableWidget(Button.builder(Component.literal("CUE"), b -> MusicPulse.toggleCue(pos, currentDeck(isLeft)))
            .bounds(px + 68, top + 114, 24, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.cue_hint"))).build());

        // Pad Mode Tabs: HOT CUE, LOOP, SLIP, JUMP
        String[] padLabels = {"CUE", "LOOP", "SLIP", "JUMP"};
        for (int m = 0; m < 4; m++) {
            int modeId = m;
            padModeTabs[playerIdx][m] = addRenderableWidget(Button.builder(Component.literal(padLabels[m]), b -> {
                int cd = currentDeck(isLeft);
                DjControl.send(pos, DjControl.PAD_MODE, cd, modeId);
            }).bounds(px + 4 + m * 27, top + 138, 25, 11).build());
        }

        // 8 Backlit Rubber Performance Pads (2 rows of 4 pads)
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                int padIndex = row * 4 + col;
                int padX = px + 4 + col * 27;
                int padY = top + 152 + row * 16;
                int color = PAD_COLORS[padIndex];
                performancePads[playerIdx][padIndex] = addRenderableWidget(new PadButton(padX, padY, 25, 14, padIndex, color, b -> {
                    int cd = currentDeck(isLeft);
                    DjControl.send(pos, DjControl.PAD_TRIGGER, cd, padIndex);
                }));
            }
        }
    }

    private int currentDeck(boolean isLeft) {
        return isLeft ? leftDeck : rightDeck;
    }

    private void adjustBeatFxBeats(boolean up) {
        DjBoothBlockEntity dj = booth();
        if (dj == null) return;
        float cur = dj.getBeatFxBeats();
        float[] fracs = DjBoothBlockEntity.BFX_BEAT_FRACTIONS;
        int idx = 4; // default 1 beat
        for (int i = 0; i < fracs.length; i++) {
            if (Math.abs(fracs[i] - cur) < 1e-4) {
                idx = i;
                break;
            }
        }
        int next = Mth.clamp(up ? idx + 1 : idx - 1, 0, fracs.length - 1);
        DjControl.send(pos, DjControl.BEAT_FX_BEATS, fracs[next]);
    }

    private void syncTempo(int deck) {
        DjBoothBlockEntity dj = booth();
        if (dj == null || minecraft == null || minecraft.player == null) return;
        int otherDeck = (deck + 1) % DjBoothBlockEntity.DECKS;
        double mine = MusicPulse.beatPeriodAt(dj.deckPos(deck)), theirs = MusicPulse.beatPeriodAt(dj.deckPos(otherDeck));
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
        DjControl.send(pos, DjControl.PITCH, deck, (float) best);
        dj.setPitch(deck, (float) best);
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

        // Refresh Left & Right Physical Player widgets
        for (int p = 0; p < 2; p++) {
            boolean isLeft = (p == 0);
            int deck = currentDeck(isLeft);
            boolean playing = dj.isPlaying(deck);
            boolean hasDisc = !dj.getDisc(deck).isEmpty();

            playButtons[p].active = hasDisc;
            cueButtons[p].active = hasDisc;
            pitchFaders[p].show((dj.getPitch(deck) - (1 - DjBoothBlockEntity.PITCH_RANGE)) / (2 * DjBoothBlockEntity.PITCH_RANGE));

            vinylButtons[p].setMessage(Component.literal("VIN").withStyle(dj.isVinylMode(deck) ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            slipButtons[p].setMessage(Component.literal("SLP").withStyle(dj.isSlipMode(deck) ? ChatFormatting.RED : ChatFormatting.GRAY));
            revButtons[p].setMessage(Component.literal("REV").withStyle(dj.isReverse(deck) ? ChatFormatting.GREEN : ChatFormatting.GRAY));

            cueHeadphones[p].setMessage(Component.literal("CUE")
                .withStyle(MusicPulse.isCued(pos, deck) ? ChatFormatting.GOLD : ChatFormatting.GRAY));

            int curMode = dj.getPadMode(deck);
            for (int m = 0; m < 4; m++) {
                padModeTabs[p][m].setMessage(Component.literal(padModeTabs[p][m].getMessage().getString())
                    .withStyle(m == curMode ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            }

            for (int i = 0; i < 8; i++) {
                boolean active = false;
                if (curMode == DjBoothBlockEntity.PAD_BEAT_LOOP || curMode == DjBoothBlockEntity.PAD_SLIP_LOOP) {
                    active = dj.getLoopBeats(deck) == DjBoothBlockEntity.LOOPS[i % DjBoothBlockEntity.LOOPS.length];
                }
                performancePads[p][i].setActive(active);
                performancePads[p][i].setMode(curMode);
            }
        }

        // Layer selection buttons styles
        deckLayerButtons[DjBoothBlockEntity.A].setMessage(Component.literal("1").withStyle(leftDeck == DjBoothBlockEntity.A ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        deckLayerButtons[DjBoothBlockEntity.C].setMessage(Component.literal("3").withStyle(leftDeck == DjBoothBlockEntity.C ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        deckLayerButtons[DjBoothBlockEntity.B].setMessage(Component.literal("2").withStyle(rightDeck == DjBoothBlockEntity.B ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        deckLayerButtons[DjBoothBlockEntity.D].setMessage(Component.literal("4").withStyle(rightDeck == DjBoothBlockEntity.D ? ChatFormatting.GREEN : ChatFormatting.GRAY));

        // 4 Channels Mixer knobs & faders
        for (int ch = 0; ch < DjBoothBlockEntity.DECKS; ch++) {
            chKnobs[ch][0].show(dj.getTrim(ch));
            chKnobs[ch][1].show(dj.getEq(ch, DjBoothBlockEntity.HIGH));
            chKnobs[ch][2].show(dj.getEq(ch, DjBoothBlockEntity.MID));
            chKnobs[ch][3].show(dj.getEq(ch, DjBoothBlockEntity.LOW));
            chKnobs[ch][4].show((dj.getFilter(ch) + 1f) / 2f);

            chFaders[ch].show(dj.getChannelFader(ch));
            chCueButtons[ch].setMessage(Component.literal("C").withStyle(MusicPulse.isCued(pos, ch) ? ChatFormatting.GOLD : ChatFormatting.GRAY));

            int assign = dj.getCrossfaderAssign(ch);
            String assignLabel = assign == 0 ? "A" : (assign == 1 ? "B" : "·");
            ChatFormatting assignCol = assign == 0 ? ChatFormatting.AQUA : (assign == 1 ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            chXfAssignButtons[ch].setMessage(Component.literal(assignLabel).withStyle(assignCol));
        }

        // Sound Color FX Buttons
        int activeColor = dj.getActiveColorFx();
        for (int i = 0; i < DjBoothBlockEntity.COLOR_FX_COUNT; i++) {
            colorFxButtons[i].setMessage(Component.literal(colorFxButtons[i].getMessage().getString())
                .withStyle(i == activeColor ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        }
        colorParamKnob.show(dj.getColorFxParam());

        // Beat FX Unit
        bfxTypeButton.setMessage(Component.literal(DjBoothBlockEntity.BFX_NAMES[dj.getBeatFxType()]).withStyle(ChatFormatting.YELLOW));
        int bfxCh = dj.getBeatFxChannel();
        String bfxChText = bfxCh < 0 ? "MST" : "CH" + (bfxCh + 1);
        bfxChButton.setMessage(Component.literal(bfxChText));
        bfxOnButton.setActive(dj.isBeatFxOn());
        bfxDepthKnob.show(dj.getBeatFxDepth());

        // Crossfader & Redstone
        crossfader.show(dj.crossfader(now));
        automixButton.setMessage(Component.literal("AUTO").withStyle(dj.isAutomix() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        autoDropButton.setMessage(Component.literal("A-DROP").withStyle(dj.isAutoDrop() ? ChatFormatting.RED : ChatFormatting.GRAY));

        // Screen Tabs
        tabWaveButton.setMessage(Component.literal("WAVE").withStyle(screenTab == 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        tabBrowseButton.setMessage(Component.literal("BROWSE").withStyle(screenTab == 1 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        tabInfoButton.setMessage(Component.literal("INFO").withStyle(screenTab == 2 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
    }

    @Override
    public void tick() {
        refresh();
        if (pitchFaders != null) {
            for (VPitchFader f : pitchFaders) if (f != null) f.flush();
            if (crossfader != null) crossfader.flush();
            for (Knob[] row : chKnobs) for (Knob k : row) if (k != null) k.flush();
            for (VFader vf : chFaders) if (vf != null) vf.flush();
            if (colorParamKnob != null) colorParamKnob.flush();
            if (bfxDepthKnob != null) bfxDepthKnob.flush();
        }

        // Live smooth VU-Meter peak decays
        DjBoothBlockEntity dj = booth();
        if (dj != null && minecraft != null && minecraft.level != null) {
            float masterAccum = 0f;
            for (int ch = 0; ch < DjBoothBlockEntity.DECKS; ch++) {
                float target = dj.isPlaying(ch)
                    ? Mth.clamp((MusicPulse.kickNear(dj.deckPos(ch)) * 0.75f + 0.25f) * dj.getEq(ch, DjBoothBlockEntity.LOW) * 1.3f * dj.getChannelFader(ch), 0f, 1f)
                    : 0f;
                chVU[ch] = target > chVU[ch] ? target : chVU[ch] * 0.85f;
                masterAccum = Math.max(masterAccum, chVU[ch]);
            }
            vuMaster = masterAccum > vuMaster ? masterAccum : vuMaster * 0.88f;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);

        // AlphaTheta XDJ-AZ Matte Black Chassis
        g.fill(left, top, left + W, top + H, 0xFF111317);
        g.renderOutline(left, top, W, H, 0xFF353944);

        // Left Deck Recessed Plate
        g.fill(left + 4, top + 6, left + 124, top + H - 6, 0xFF171920);
        g.renderOutline(left + 4, top + 6, 120, H - 12, 0xFF242730);

        // Right Deck Recessed Plate
        g.fill(left + 298, top + 6, left + W - 4, top + H - 6, 0xFF171920);
        g.renderOutline(left + 298, top + 6, 120, H - 12, 0xFF242730);

        // Center Mixer Recessed Plate
        g.fill(left + 126, top + 84, left + 296, top + H - 6, 0xFF14161C);
        g.renderOutline(left + 126, top + 84, 170, H - 90, 0xFF282B35);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        DjBoothBlockEntity dj = booth();
        if (dj == null || minecraft == null || minecraft.level == null) return;
        long time = minecraft.level.getGameTime();

        // ---------------------------------------------------------------------------------
        // 10.1" CENTRAL TOUCHSCREEN DISPLAY
        // ---------------------------------------------------------------------------------
        renderTouchScreen(g, left + 128, top + 6, 166, 76, dj, time, partialTick);

        // ---------------------------------------------------------------------------------
        // TRACK LABELS & TIME REMAINING
        // ---------------------------------------------------------------------------------
        renderDeckHeader(g, left + 6, leftDeck, dj);
        renderDeckHeader(g, left + 300, rightDeck, dj);

        // ---------------------------------------------------------------------------------
        // 4 CHANNELS STEREO LED VU-METERS
        // ---------------------------------------------------------------------------------
        for (int ch = 0; ch < DjBoothBlockEntity.DECKS; ch++) {
            int vx = left + 128 + 36 + ch * 23 + 20;
            renderVuMeter(g, vx, top + 86, 3, 68, chVU[ch]);
        }

        // Master Stereo VU-Meters
        renderMasterVu(g, left + 130, top + 172, 24, 44, vuMaster);
    }

    private void renderDeckHeader(GuiGraphics g, int px, int deck, DjBoothBlockEntity dj) {
        ItemStack disc = dj.getDisc(deck);
        boolean playing = dj.isPlaying(deck);
        int col = DECK_COLORS[deck % DECK_COLORS.length];

        g.drawString(font, "DECK " + (deck + 1), px + 52, top + 10, col);
        String title = disc.isEmpty() ? Component.translatable("createbrewery.dj.empty").getString() : dj.title(disc).getString();
        g.drawString(font, font.plainSubstrByWidth(title, 110), px + 6, top + 218, playing ? 0xFFFFFFFF : 0xFFAAAAAA);

        // Time remaining / elapsed
        if (playing && dj.getDisc(deck).isEmpty()) {
            g.drawString(font, "--:--", px + 6, top + 230, 0xFF667788);
        } else if (playing) {
            g.drawString(font, "PLAYING", px + 6, top + 230, 0xFF00FF66);
        } else {
            g.drawString(font, "STOPPED", px + 6, top + 230, 0xFF888888);
        }
    }

    /** Renders the 10.1" tilted touchscreen of the XDJ-AZ with tabs for WAVE, BROWSE, and INFO. */
    private void renderTouchScreen(GuiGraphics g, int x, int y, int w, int h, DjBoothBlockEntity dj, long time, float pt) {
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
            renderWaveform(g, sx + 2, sy + 14, sw - 4, 18, leftDeck, dj, DECK_COLORS[leftDeck], 0xFF0055AA, time, pt);
            // Right Deck Waveform
            renderWaveform(g, sx + 2, sy + 34, sw - 4, 18, rightDeck, dj, DECK_COLORS[rightDeck], 0xFFAA5500, time, pt);

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
            renderBrowseTab(g, sx + 2, sy + 14, sw - 4, sh - 16, dj);
        } else {
            // ================= INFO / DIAGNOSTICS TAB =================
            renderInfoTab(g, sx + 2, sy + 14, sw - 4, sh - 16, dj);
        }
    }

    private void renderBrowseTab(GuiGraphics g, int x, int y, int w, int h, DjBoothBlockEntity dj) {
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
                int loadDeck = d;
                int bx = x + w - 46 + d * 11;
                boolean hovered = isMouseOver(bx, ry + 2, 10, 11);
                g.fill(bx, ry + 2, bx + 10, ry + 13, hovered ? 0xFF354560 : 0xFF1E283A);
                g.renderOutline(bx, ry + 2, 10, 11, DECK_COLORS[d]);
                g.drawCenteredString(font, String.valueOf(d + 1), bx + 5, ry + 3, DECK_COLORS[d]);
            }
        }
    }

    private void renderInfoTab(GuiGraphics g, int x, int y, int w, int h, DjBoothBlockEntity dj) {
        g.fill(x, y, x + w, y + h, 0xFF080C14);
        g.drawString(font, "ENGINE: AlphaTheta DSP 44.1kHz", x + 4, y + 4, 0xFF88AAFF);
        g.drawString(font, "COLOR FX: " + DjBoothBlockEntity.COLOR_FX_NAMES[dj.getActiveColorFx()], x + 4, y + 16, 0xFF00FF66);
        g.drawString(font, "BEAT FX: " + DjBoothBlockEntity.BFX_NAMES[dj.getBeatFxType()] + " (" + dj.getBeatFxBeats() + " Beats)", x + 4, y + 28, 0xFFFFCC00);
        g.drawString(font, "AUTO-DROP: " + (dj.isAutoDrop() ? "ACTIVE (Redstone ON)" : "OFF"), x + 4, y + 40, dj.isAutoDrop() ? 0xFFFF3344 : 0xFF888888);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (screenTab == 1) {
            // Handle clicks on browse tab load buttons
            DjBoothBlockEntity dj = booth();
            if (dj != null && dj.crate() != null) {
                var crate = dj.crate();
                List<Integer> discSlots = new ArrayList<>();
                for (int i = 0; i < crate.getSlots(); i++) {
                    if (DjBoothBlock.isMusicDisc(crate.getStackInSlot(i))) discSlots.add(i);
                }
                int sx = left + 130 + 2, sy = top + 6 + 16, sw = 166 - 4;
                for (int row = 0; row < 3; row++) {
                    int idx = browseOffset + row;
                    if (idx >= discSlots.size()) break;
                    int slot = discSlots.get(idx);
                    int ry = sy + row * 16;
                    for (int d = 0; d < 4; d++) {
                        int bx = sx + sw - 46 + d * 11;
                        if (mouseX >= bx && mouseX < bx + 10 && mouseY >= ry + 2 && mouseY < ry + 13) {
                            DjControl.send(pos, DjControl.LOAD_TRACK, d, slot);
                            if (minecraft != null && minecraft.player != null) {
                                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.2f);
                            }
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (screenTab == 1) {
            DjBoothBlockEntity dj = booth();
            if (dj != null && dj.crate() != null) {
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
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (JogWheelWidget jw : jogWheels) {
            if (jw != null) jw.releasePlatter();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        for (JogWheelWidget jw : jogWheels) {
            if (jw != null) jw.releasePlatter();
        }
        super.onClose();
    }

    private boolean isMouseOver(int x, int y, int w, int h) {
        if (minecraft == null) return false;
        double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 3-band pioneer full-color scrolling waveform rendering. */
    private void renderWaveform(GuiGraphics g, int x, int y, int w, int h, int deck, DjBoothBlockEntity dj, int colorHigh, int colorLow, long time, float pt) {
        g.fill(x, y, x + w, y + h, 0xFF080E1A);
        g.renderOutline(x, y, w, h, 0xFF142034);

        boolean playing = dj.isPlaying(deck);
        double period = MusicPulse.beatPeriodAt(dj.deckPos(deck));
        String bpmText = playing && period > 0 ? bpm(period) : "--";
        g.drawString(font, (deck + 1) + ": " + bpmText, x + 3, y + 2, colorHigh);

        int cx = x + w / 2;
        g.fill(cx, y, cx + 1, y + h, 0xFFFFFFFF);

        if (!playing && dj.getDisc(deck).isEmpty()) {
            g.drawString(font, "NO TRACK", cx - 22, y + 5, 0xFF35445A);
            return;
        }

        int bars = 36;
        float barW = (float) w / bars;
        float pulse = playing ? MusicPulse.kickNear(dj.deckPos(deck)) : 0.1f;
        float scrollOffset = playing ? (time + pt) * 0.8f * dj.getPitch(deck) : 0f;

        for (int i = 0; i < bars; i++) {
            float bx = x + i * barW;
            double wavePhase = (i * 0.45) + scrollOffset;
            float rawAmp = (float) (Math.sin(wavePhase) * 0.5 + Math.sin(wavePhase * 2.3) * 0.3 + 0.8);
            float amp = Math.min(1.0f, rawAmp * (0.35f + pulse * 0.65f));
            int barHeight = Math.max(2, (int) (amp * (h - 4)));
            int by = y + (h - barHeight) / 2;

            int col = (i % 2 == 0) ? colorHigh : colorLow;
            g.fill((int) bx, by, (int) (bx + Math.max(1, barW - 1)), by + barHeight, col);
        }
    }

    private void renderVuMeter(GuiGraphics g, int x, int y, int w, int h, float level) {
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

    private void renderMasterVu(GuiGraphics g, int x, int y, int w, int h, float level) {
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================================
    // CUSTOM HARDWARE WIDGETS
    // =====================================================================================

    /** CDJ Jogwheel widget with animated on-jog LCD center display & spinning cue needle. */
    private class JogWheelWidget extends AbstractWidget {
        private final boolean isLeft;
        private float visualRotation = 0f;
        private double grabAngle = 0;
        private boolean innerTouch = false;
        private float accumulatedDelta = 0;
        private long lastScratchSoundTime = 0;
        private int lastDirection = 0;

        JogWheelWidget(int x, int y, int w, int h, boolean isLeft) {
            super(x, y, w, h, Component.literal("Jog"));
            this.isLeft = isLeft;
        }

        public boolean isHoldingPlatter() {
            return innerTouch;
        }

        void releasePlatter() {
            if (innerTouch) {
                innerTouch = false;
                DjBoothBlockEntity dj = booth();
                int deck = currentDeck(isLeft);
                if (dj != null && dj.isVinylMode(deck)) {
                    dj.setScratchHeld(deck, false);
                    DjControl.send(pos, DjControl.JOG_TOUCH, deck, 0.0f);
                }
            }
            accumulatedDelta = 0;
            lastDirection = 0;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + width / 2, cy = getY() + height / 2;
            int r = width / 2;
            int deck = currentDeck(isLeft);
            DjBoothBlockEntity dj = booth();

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
            int ringCol = DECK_COLORS[deck % DECK_COLORS.length];
            g.renderOutline(cx - cr, cy - cr, cr * 2, cr * 2, ringCol);

            // Center Deck Number
            g.drawCenteredString(font, String.valueOf(deck + 1), cx, cy - 4, 0xFFFFFFFF);

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

            DjBoothBlockEntity dj = booth();
            int deck = currentDeck(isLeft);
            if (dj != null && innerTouch && dj.isVinylMode(deck)) {
                dj.setScratchHeld(deck, true);
                DjControl.send(pos, DjControl.JOG_TOUCH, deck, 1.0f);
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.playSound(ModSounds.DJ_SCRATCH_STOP.get(), 0.65f, 1.0f);
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

            DjBoothBlockEntity dj = booth();
            int deck = currentDeck(isLeft);
            if (dj == null) return;

            if (innerTouch && dj.isVinylMode(deck)) {
                accumulatedDelta += (float) delta;
                int dir = delta > 0 ? 1 : -1;
                long now = net.minecraft.Util.getMillis();
                boolean directionFlipped = (lastDirection != 0 && dir != lastDirection);
                boolean timeElapsed = (now - lastScratchSoundTime > 75);

                if (directionFlipped || (timeElapsed && Math.abs(accumulatedDelta) > 0.04f)) {
                    lastDirection = dir;
                    lastScratchSoundTime = now;
                    float scrubVal = accumulatedDelta;
                    accumulatedDelta = 0;

                    DjControl.send(pos, DjControl.JOG_SCRUB, deck, scrubVal);
                    if (minecraft != null && minecraft.player != null) {
                        SoundEvent sound = dir > 0 ? ModSounds.DJ_SCRATCH_FWD.get() : ModSounds.DJ_SCRATCH_BACK.get();
                        float pitch = Mth.clamp(0.75f + Math.abs(scrubVal) * 3.5f, 0.6f, 1.8f);
                        float vol = Mth.clamp(0.6f + Math.abs(scrubVal) * 2.5f, 0.4f, 1.0f);
                        minecraft.player.playSound(sound, vol, pitch);
                    }
                }
            } else {
                float cur = dj.getPitch(deck);
                float nudge = Mth.clamp((float) (cur + delta * 0.03), 1f - DjBoothBlockEntity.PITCH_RANGE, 1f + DjBoothBlockEntity.PITCH_RANGE);
                DjControl.send(pos, DjControl.PITCH, deck, nudge);
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
            DjBoothBlockEntity dj = booth();
            int deck = currentDeck(isLeft);
            if (dj != null) {
                if (dj.isVinylMode(deck)) {
                    float scrubTicks = (float) (scrollY * 0.15f);
                    DjControl.send(pos, DjControl.JOG_SCRUB, deck, scrubTicks);
                    if (minecraft != null && minecraft.player != null) {
                        SoundEvent sound = scrollY > 0 ? ModSounds.DJ_SCRATCH_FWD.get() : ModSounds.DJ_SCRATCH_BACK.get();
                        float pitch = Mth.clamp(0.85f + (float) Math.abs(scrollY) * 0.2f, 0.7f, 1.6f);
                        minecraft.player.playSound(sound, 0.75f, pitch);
                    }
                } else {
                    float cur = dj.getPitch(deck);
                    float nudge = Mth.clamp((float) (cur + scrollY * 0.005), 1f - DjBoothBlockEntity.PITCH_RANGE, 1f + DjBoothBlockEntity.PITCH_RANGE);
                    DjControl.send(pos, DjControl.PITCH, deck, nudge);
                    dj.setPitch(deck, nudge);
                }
            }
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /** Iconic circular illuminated CUE and PLAY/PAUSE transport buttons. */
    private class TransportButton extends AbstractWidget {
        private final boolean isPlay;
        private final boolean isLeft;
        private final java.util.function.Consumer<TransportButton> onPress;

        TransportButton(int x, int y, int w, int h, boolean isPlay, boolean isLeft, java.util.function.Consumer<TransportButton> onPress) {
            super(x, y, w, h, Component.literal(isPlay ? "PLAY" : "CUE"));
            this.isPlay = isPlay;
            this.isLeft = isLeft;
            this.onPress = onPress;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, isPlay ? 1.2f : 1.0f);
            }
            onPress.accept(this);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + width / 2, cy = getY() + height / 2;
            int r = width / 2;
            int deck = currentDeck(isLeft);

            DjBoothBlockEntity dj = booth();
            boolean active = dj != null && (isPlay ? dj.isPlaying(deck) : MusicPulse.isCued(pos, deck));

            int ringColor = isPlay
                ? (active ? 0xFF00FF66 : (isHoveredOrFocused() ? 0xFF008833 : 0xFF004418))
                : (active ? 0xFFFF9900 : (isHoveredOrFocused() ? 0xFF995500 : 0xFF442200));

            g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF101216);
            g.renderOutline(cx - r, cy - r, width, height, ringColor);
            g.fill(cx - r + 3, cy - r + 3, cx + r - 3, cy + r - 3, isPlay ? 0xFF06140A : 0xFF140D04);

            if (isPlay) {
                g.drawString(font, "▶||", cx - 7, cy - 4, active ? 0xFF00FF66 : 0xFF558866);
            } else {
                g.drawCenteredString(font, "CUE", cx, cy - 4, active ? 0xFFFF9900 : 0xFF886644);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /** Backlit Rubber Performance Pad supporting HOT CUE, BEAT LOOP, SLIP LOOP, and BEAT JUMP modes. */
    private static class PadButton extends AbstractWidget {
        private final int index;
        private final int baseColor;
        private final java.util.function.Consumer<PadButton> onPress;
        private boolean active;
        private int mode = DjBoothBlockEntity.PAD_BEAT_LOOP;

        PadButton(int x, int y, int w, int h, int index, int baseColor, java.util.function.Consumer<PadButton> onPress) {
            super(x, y, w, h, Component.empty());
            this.index = index;
            this.baseColor = baseColor;
            this.onPress = onPress;
        }

        void setActive(boolean on) { this.active = on; }
        void setMode(int mode) { this.mode = mode; }

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

    /** Pulsing round illuminated Beat FX ON/OFF button. */
    private class BeatFxButton extends AbstractWidget {
        private final java.util.function.Consumer<BeatFxButton> onPress;
        private boolean active;

        BeatFxButton(int x, int y, int w, int h, java.util.function.Consumer<BeatFxButton> onPress) {
            super(x, y, w, h, Component.literal("BFX"));
            this.onPress = onPress;
        }

        void setActive(boolean on) { this.active = on; }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onPress.accept(this);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + width / 2, cy = getY() + height / 2;
            int r = width / 2;

            float pulse = 0f;
            if (active && minecraft != null && minecraft.level != null) {
                pulse = (float) (Math.sin(minecraft.level.getGameTime() * 0.4) * 0.5 + 0.5);
            }
            int ring = active ? 0xFFFFCC00 : (isHoveredOrFocused() ? 0xFF886600 : 0xFF352A05);
            g.fill(cx - r, cy - r, cx + r, cy + r, 0xFF151206);
            g.renderOutline(cx - r, cy - r, width, height, ring);

            int textCol = active ? 0xFFFFFFFF : 0xFF888888;
            g.drawCenteredString(font, "ON", cx, cy - 4, textCol);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /** Vertical Pitch / Tempo Fader with center zero detent. */
    private class VPitchFader extends AbstractWidget {
        private final boolean isLeft;
        private double value = 0.5;
        private boolean held, dirty;

        VPitchFader(int x, int y, int w, int h, boolean isLeft) {
            super(x, y, w, h, Component.empty());
            this.isLeft = isLeft;
        }

        private long lastClick;

        void show(double v) {
            if (!held && !dirty) value = Mth.clamp(v, 0.0, 1.0);
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            float p = (float) (1.0 - DjBoothBlockEntity.PITCH_RANGE + value * 2.0 * DjBoothBlockEntity.PITCH_RANGE);
            int deck = currentDeck(isLeft);
            DjControl.send(pos, DjControl.PITCH, deck, p);
            DjBoothBlockEntity dj = booth();
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
            g.drawCenteredString(font, String.format("%+.1f", percent), trackX, getY() - 9, Math.abs(percent) < 0.1 ? 0xFF55FF88 : 0xFFAAAAAA);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /** Vertical Channel Volume Fader. */
    private static class VFader extends AbstractWidget {
        private final int deck;
        private final java.util.function.Consumer<Float> onFlush;
        private double value = 1.0;
        private boolean held, dirty;

        VFader(int x, int y, int w, int h, int deck, java.util.function.Consumer<Float> onFlush) {
            super(x, y, w, h, Component.empty());
            this.deck = deck;
            this.onFlush = onFlush;
        }

        void show(double v) {
            if (!held && !dirty) value = Mth.clamp(v, 0.0, 1.0);
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            onFlush.accept((float) value);
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
        public void onRelease(double mouseX, double mouseY) { held = false; }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isHovered()) return false;
            value = Mth.clamp(value + scrollY * 0.04, 0.0, 1.0);
            dirty = true;
            return true;
        }

        private void updateFromMouse(double mouseY) {
            double relY = mouseY - (getY() + 3);
            double trackLen = height - 6;
            value = 1.0 - Mth.clamp(relY / trackLen, 0.0, 1.0);
            dirty = true;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int trackX = getX() + width / 2;
            g.fill(trackX - 1, getY() + 2, trackX + 1, getY() + height - 2, 0xFF08090C);
            g.renderOutline(trackX - 2, getY() + 1, 4, height - 2, 0xFF242730);

            int handleY = getY() + 2 + (int) Math.round((1.0 - value) * (height - 8));
            int hx = getX() + 1, hw = width - 2;
            g.fill(hx, handleY, hx + hw, handleY + 5, isHoveredOrFocused() ? 0xFF888E9A : 0xFF505460);
            g.fill(hx + 1, handleY + 2, hx + hw - 1, handleY + 3, 0xFFFFFFFF);
            g.renderOutline(hx, handleY, hw, 5, 0xFF14161C);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {}
    }

    /** Rotary Knob with glowing 13-dot LED arc. */
    private class Knob extends AbstractWidget {
        private final byte action;
        private final int deck;
        private final boolean bipolar;
        private double value = 0.5, grabY, grabValue;
        private boolean held, dirty;
        private long lastClick;

        Knob(int x, int y, byte action, int deck, String label, boolean bipolar) {
            super(x, y, 18, 18, Component.literal(label));
            this.action = action;
            this.deck = deck;
            this.bipolar = bipolar;
            if (action == DjControl.FX_AMOUNT || action == DjControl.BEAT_FX_DEPTH) value = 0;
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

        private void set(double v) {
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

    /** Magvel Crossfader slider. */
    private class Fader extends AbstractSliderButton {
        private final byte action;
        private boolean held, dirty;

        Fader(int x, int y, int w, byte action, boolean isPitch) {
            super(x, y, w, 16, Component.empty(), 0.5);
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
            DjBoothBlockEntity dj = booth();
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
}
