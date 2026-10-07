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
import net.minecraft.world.item.JukeboxSong;

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
    private static final int W = 540, H = 276;

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

    /** Beat FX names that fit the 34 px button; the INFO tab shows the full name. */
    private static final String[] BFX_SHORT = {"DELAY", "ECHO", "REVRB", "FLANG", "PHASE", "ROLL", "TRANS", "HELIX", "PNG"};

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
    private final Button[] quantizeButtons = new Button[2];
    private final Button[] loopInButtons = new Button[2];
    private final Button[] loopOutButtons = new Button[2];
    private final Button[] reloopButtons = new Button[2];
    private final Button[] mtButtons = new Button[2];
    private boolean quantizeEnabled = true;
    private final long[] manualLoopIn = {-1, -1, -1, -1};
    private final long[] manualLoopOut = {-1, -1, -1, -1};
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
        initDeckPlayer(1, left + 384, false);

        // ---------------------------------------------------------------------------------
        // 3. TOP TOUCHSCREEN (10.1" Capacitive Display)
        // ---------------------------------------------------------------------------------
        int sx = left + 166, sy = top + 6;
        tabWaveButton = addRenderableWidget(Button.builder(Component.literal("WAVE"), b -> screenTab = 0)
            .bounds(sx + 92, sy + 2, 34, 12).build());
        tabBrowseButton = addRenderableWidget(Button.builder(Component.literal("BROWSE"), b -> screenTab = 1)
            .bounds(sx + 128, sy + 2, 46, 12).build());
        tabInfoButton = addRenderableWidget(Button.builder(Component.literal("INFO"), b -> screenTab = 2)
            .bounds(sx + 176, sy + 2, 30, 12).build());

        // ---------------------------------------------------------------------------------
        // 4. CENTER MIXER - SOUND COLOR FX UNIT (Left side of mixer)
        // ---------------------------------------------------------------------------------
        int mx = left + 164;
        String[] colorLabels = {"SPC", "DUB", "SWP", "NOI", "CRS", "FLT"};
        for (int i = 0; i < DjBoothBlockEntity.COLOR_FX_COUNT; i++) {
            int fxId = i;
            int cx = mx + (i / 3) * 26;
            int cy = top + 94 + (i % 3) * 16;
            colorFxButtons[i] = addRenderableWidget(Button.builder(Component.literal(colorLabels[i]), b ->
                DjControl.send(pos, DjControl.COLOR_FX_SELECT, fxId))
                .bounds(cx, cy, 24, 14)
                .tooltip(Tooltip.create(Component.literal(DjBoothBlockEntity.COLOR_FX_NAMES[i])))
                .build());
        }
        colorParamKnob = addRenderableWidget(new Knob(mx + 16, top + 148, DjControl.COLOR_FX_PARAM, 0, "PARAM", false));

        // ---------------------------------------------------------------------------------
        // 5. CENTER MIXER - 4 CHANNEL STRIPS (Channels 1, 2, 3, 4)
        // ---------------------------------------------------------------------------------
        for (int ch = 0; ch < DjBoothBlockEntity.DECKS; ch++) {
            int d = ch;
            int cx = mx + 54 + ch * 28;

            // TRIM, HI, MID, LOW, COLOR knobs
            chKnobs[ch][0] = addRenderableWidget(new Knob(cx + 5, top + 94, DjControl.TRIM, d, "TRIM", false));
            chKnobs[ch][1] = addRenderableWidget(new Knob(cx + 5, top + 114, DjControl.EQ_HIGH, d, "HI", false));
            chKnobs[ch][2] = addRenderableWidget(new Knob(cx + 5, top + 134, DjControl.EQ_MID, d, "MID", false));
            chKnobs[ch][3] = addRenderableWidget(new Knob(cx + 5, top + 154, DjControl.EQ_LOW, d, "LOW", false));
            chKnobs[ch][4] = addRenderableWidget(new Knob(cx + 5, top + 174, DjControl.FILTER, d, "CLR", true));

            // Channel Volume Fader (Vertical)
            chFaders[ch] = addRenderableWidget(new VFader(cx + 7, top + 196, 14, 30, d, f -> {
                DjControl.send(pos, DjControl.CHANNEL_FADER, d, f);
                DjBoothBlockEntity dj = booth();
                if (dj != null) dj.setChannelFader(d, f);
            }));

            // Channel Headphone CUE button
            chCueButtons[ch] = addRenderableWidget(Button.builder(Component.literal("CUE"), b -> MusicPulse.toggleCue(pos, d))
                .bounds(cx + 3, top + 228, 22, 10).build());

            // Crossfader Assign switch [A | · | B]
            chXfAssignButtons[ch] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                DjBoothBlockEntity dj = booth();
                if (dj != null) {
                    int next = (dj.getCrossfaderAssign(d) + 1) % 3;
                    DjControl.send(pos, DjControl.CROSSFADER_ASSIGN, d, next);
                }
            }).bounds(cx + 3, top + 240, 22, 10).build());
        }

        // ---------------------------------------------------------------------------------
        // 6. CENTER MIXER - BEAT FX UNIT (Right side of mixer)
        // ---------------------------------------------------------------------------------
        int bfxX = mx + 170;
        bfxTypeButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) {
                int next = (dj.getBeatFxType() + 1) % DjBoothBlockEntity.BFX_COUNT;
                DjControl.send(pos, DjControl.BEAT_FX_TYPE, next);
            }
        }).bounds(bfxX, top + 94, 38, 12).build());

        // Beat Fraction ◀ ▶ buttons
        bfxBeatsDown = addRenderableWidget(Button.builder(Component.literal("◀"), b -> adjustBeatFxBeats(false))
            .bounds(bfxX, top + 108, 18, 12).build());
        bfxBeatsUp = addRenderableWidget(Button.builder(Component.literal("▶"), b -> adjustBeatFxBeats(true))
            .bounds(bfxX + 20, top + 108, 18, 12).build());

        // Channel selector
        bfxChButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) {
                int ch = dj.getBeatFxChannel();
                int next = ch >= 3 ? -1 : ch + 1; // -1 (Master) -> 0 -> 1 -> 2 -> 3 -> -1
                DjControl.send(pos, DjControl.BEAT_FX_CHANNEL, next);
            }
        }).bounds(bfxX, top + 122, 38, 12).build());

        // Pulsing Beat FX ON/OFF button
        bfxOnButton = addRenderableWidget(new BeatFxButton(bfxX + 9, top + 136, 20, 20, b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) DjControl.send(pos, DjControl.BEAT_FX_ON, dj.isBeatFxOn() ? 0f : 1f);
        }));

        bfxDepthKnob = addRenderableWidget(new Knob(bfxX + 10, top + 160, DjControl.BEAT_FX_DEPTH, 0, "DEPTH", false));

        // ---------------------------------------------------------------------------------
        // 7. BOTTOM CONTROLS: Magvel Crossfader & Redstone Automation
        // ---------------------------------------------------------------------------------
        crossfader = addRenderableWidget(new Fader(left + 220, top + 252, 112, DjControl.CROSSFADER, false));

        autoDropButton = addRenderableWidget(Button.builder(Component.literal("A-DROP"), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) DjControl.send(pos, DjControl.AUTO_DROP, dj.isAutoDrop() ? 0f : 1f);
        }).bounds(bfxX, top + 190, 38, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.autodrop_hint"))).build());

        manualDropButton = addRenderableWidget(Button.builder(Component.literal("DROP"), b ->
            DjControl.send(pos, DjControl.DROP, 0f))
            .bounds(bfxX, top + 206, 38, 14).build());

        automixButton = addRenderableWidget(Button.builder(Component.literal("AUTO"), b -> {
            DjBoothBlockEntity dj = booth();
            if (dj != null) DjControl.send(pos, DjControl.AUTOMIX, dj.isAutomix() ? 0f : 1f);
        }).bounds(bfxX, top + 222, 38, 14).build());

        ejectButton = addRenderableWidget(Button.builder(Component.literal("⏏"), b ->
            DjControl.send(pos, DjControl.EJECT, 0f))
            .bounds(bfxX, top + 238, 38, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.eject"))).build());

        refresh();
    }

    private void initDeckPlayer(int playerIdx, int px, boolean isLeft) {
        int d1 = isLeft ? DjBoothBlockEntity.A : DjBoothBlockEntity.B;
        int d2 = isLeft ? DjBoothBlockEntity.C : DjBoothBlockEntity.D;

        // Deck Layer Switch Buttons: [1]/[3] or [2]/[4]
        deckLayerButtons[d1] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(d1 + 1)), b -> {
            if (isLeft) leftDeck = d1; else rightDeck = d1;
            refresh();
        }).bounds(px + 2, top + 8, 18, 14).build());

        deckLayerButtons[d2] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(d2 + 1)), b -> {
            if (isLeft) leftDeck = d2; else rightDeck = d2;
            refresh();
        }).bounds(px + 22, top + 8, 18, 14).build());

        // Manual Loop Buttons: IN / 4BEAT, OUT, RELOOP/EXIT, and MASTER TEMPO (MT)
        loopInButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("IN"), b -> handleLoopIn(isLeft))
            .bounds(px + 43, top + 8, 22, 14).tooltip(Tooltip.create(Component.literal("Loop In / Hold Shift for 4-Beat Auto Loop"))).build());

        loopOutButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("OUT"), b -> handleLoopOut(isLeft))
            .bounds(px + 68, top + 8, 26, 14).tooltip(Tooltip.create(Component.literal("Loop Out"))).build());

        reloopButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("EXIT"), b -> handleReloop(isLeft))
            .bounds(px + 97, top + 8, 30, 14).tooltip(Tooltip.create(Component.literal("Reloop / Exit"))).build());

        mtButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("MT"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) {
                boolean next = !dj.isMasterTempo(cd);
                dj.setMasterTempo(cd, next);
                DjControl.send(pos, DjControl.MASTER_TEMPO, cd, next ? 1f : 0f);
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, next ? 1.4f : 0.9f);
                }
                refresh();
            }
        }).bounds(px + 130, top + 8, 20, 14).tooltip(Tooltip.create(Component.literal("Master Tempo (Key Lock): Preserve musical pitch when changing tempo"))).build());

        // CDJ Jogwheel (Center at px + 40, top + 56, radius 32)
        jogWheels[playerIdx] = addRenderableWidget(new JogWheelWidget(px + 14, top + 28, 64, 64, isLeft));

        // Pitch / Tempo Vertical Fader
        pitchFaders[playerIdx] = addRenderableWidget(new VPitchFader(px + 118, top + 36, 18, 56, isLeft));

        // Deck Controls row: VINYL, SLIP, REV, QTZ, SYNC
        vinylButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("VIN"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) DjControl.send(pos, DjControl.VINYL_MODE, cd, dj.isVinylMode(cd) ? 0f : 1f);
        }).bounds(px + 4, top + 96, 26, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.vinyl_hint"))).build());

        slipButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("SLP"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) DjControl.send(pos, DjControl.SLIP_MODE, cd, dj.isSlipMode(cd) ? 0f : 1f);
        }).bounds(px + 33, top + 96, 26, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.slip_hint"))).build());

        revButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("REV"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) DjControl.send(pos, DjControl.REVERSE, cd, dj.isReverse(cd) ? 0f : 1f);
        }).bounds(px + 62, top + 96, 26, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.reverse_hint"))).build());

        quantizeButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("QTZ"), b -> {
            quantizeEnabled = !quantizeEnabled;
            refresh();
        }).bounds(px + 91, top + 96, 26, 14).tooltip(Tooltip.create(Component.literal("Quantize Beat Snapping"))).build());

        syncButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("SYNC"), b -> syncTempo(currentDeck(isLeft)))
            .bounds(px + 120, top + 96, 30, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.sync"))).build());

        // Transport Buttons: CUE and PLAY/PAUSE
        cueButtons[playerIdx] = addRenderableWidget(new TransportButton(px + 14, top + 118, 28, 28, false, isLeft, b -> {
            int cd = currentDeck(isLeft);
            DjBoothBlockEntity dj = booth();
            MusicPulse.Track track = MusicPulse.trackFor(pos, cd);
            if (dj == null) return;

            if (dj.isPlaying(cd)) {
                // When playing: pause and jump to cue
                DjControl.send(pos, DjControl.TOGGLE_PLAY, cd, 0f);
                long cue = dj.getMainCue(cd);
                if (track != null) {
                    track.deckFrame = cue;
                    track.slipFrame = cue;
                }
                DjControl.send(pos, DjControl.JUMP_PLAYHEAD, cd, (float) cue);
            } else {
                // When paused:
                long cue = dj.getMainCue(cd);
                long curFrame = track != null ? track.getEffectiveFrame() : 0L;
                boolean atCue = track != null && Math.abs(curFrame - cue) < 200;
                if (!atCue && track != null) {
                    long newCue = curFrame;
                    if (quantizeEnabled && track.rate > 0) {
                        double period = track.getBeatPeriod();
                        if (period > 0) {
                            long bf = (long) (period * track.rate);
                            if (bf > 0) newCue = Math.round((double) newCue / bf) * bf;
                        }
                    }
                    dj.setMainCue(cd, newCue);
                    track.cueFrame = newCue;
                    DjControl.send(pos, DjControl.SET_MAIN_CUE, cd, (float) newCue);
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.4f);
                    }
                } else if (track != null) {
                    track.auditioning = true;
                }
            }
        }, b -> {
            int cd = currentDeck(isLeft);
            MusicPulse.Track track = MusicPulse.trackFor(pos, cd);
            DjBoothBlockEntity dj = booth();
            if (track != null && track.auditioning) {
                track.auditioning = false;
                long cue = dj != null ? dj.getMainCue(cd) : track.cueFrame;
                track.deckFrame = cue;
                track.slipFrame = cue;
                DjControl.send(pos, DjControl.JUMP_PLAYHEAD, cd, (float) cue);
            }
        }));

        playButtons[playerIdx] = addRenderableWidget(new TransportButton(px + 48, top + 118, 28, 28, true, isLeft, b -> {
            int cd = currentDeck(isLeft);
            MusicPulse.Track track = MusicPulse.trackFor(pos, cd);
            DjBoothBlockEntity dj = booth();
            if (track != null && track.auditioning) {
                // Cue-Play latch
                track.auditioning = false;
                if (dj != null && !dj.isPlaying(cd)) {
                    DjControl.send(pos, DjControl.TOGGLE_PLAY, cd, 0f);
                }
            } else {
                DjControl.send(pos, DjControl.TOGGLE_PLAY, cd, 0f);
            }
        }, null));

        // Headphone CUE monitor button
        cueHeadphones[playerIdx] = addRenderableWidget(Button.builder(Component.literal("CUE"), b -> MusicPulse.toggleCue(pos, currentDeck(isLeft)))
            .bounds(px + 86, top + 124, 30, 16).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.cue_hint"))).build());

        // Pad Mode Tabs: HOT CUE, LOOP, SLIP, JUMP
        String[] padLabels = {"CUE", "LOOP", "SLIP", "JUMP"};
        for (int m = 0; m < 4; m++) {
            int modeId = m;
            padModeTabs[playerIdx][m] = addRenderableWidget(Button.builder(Component.literal(padLabels[m]), b -> {
                int cd = currentDeck(isLeft);
                DjControl.send(pos, DjControl.PAD_MODE, cd, modeId);
            }).bounds(px + 4 + m * 37, top + 152, 35, 13).build());
        }

        // 8 Backlit Rubber Performance Pads (2 rows of 4 pads)
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                int padIndex = row * 4 + col;
                int padX = px + 4 + col * 37;
                int padY = top + 170 + row * 22;
                int color = PAD_COLORS[padIndex];
                performancePads[playerIdx][padIndex] = addRenderableWidget(new PadButton(padX, padY, 35, 20, padIndex, color, b -> {
                    handlePadPress(isLeft, padIndex);
                }, b -> {
                    handlePadRelease(isLeft, padIndex);
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

    private void handleLoopIn(boolean isLeft) {
        int cd = currentDeck(isLeft);
        DjBoothBlockEntity dj = booth();
        if (dj == null) return;
        MusicPulse.Track track = MusicPulse.trackFor(pos, cd);
        if (track == null || track.rate <= 0) return;

        if (Screen.hasShiftDown()) {
            // Shift + IN: Instant 4-Beat Auto Loop
            long cur = track.getEffectiveFrame();
            double period = track.getBeatPeriod();
            if (period <= 0) period = 0.5;
            long beatFrames = Math.max(1, (long) (period * track.rate));
            if (quantizeEnabled && beatFrames > 0) cur = Math.round((double) cur / beatFrames) * beatFrames;
            manualLoopIn[cd] = cur;
            manualLoopOut[cd] = cur + 4 * beatFrames;
            track.manualLoopIn = manualLoopIn[cd];
            track.manualLoopOut = manualLoopOut[cd];
            DjControl.send(pos, DjControl.LOOP, cd, 4);
            dj.setLoop(cd, 4);
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.4f);
            }
            return;
        }

        long frame = track.getEffectiveFrame();
        if (quantizeEnabled) {
            double period = track.getBeatPeriod();
            if (period > 0) {
                long bf = (long) (period * track.rate);
                if (bf > 0) frame = Math.round((double) frame / bf) * bf;
            }
        }
        manualLoopIn[cd] = frame;
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 1.2f);
        }
    }

    private void handleLoopOut(boolean isLeft) {
        int cd = currentDeck(isLeft);
        DjBoothBlockEntity dj = booth();
        if (dj == null) return;
        MusicPulse.Track track = MusicPulse.trackFor(pos, cd);
        if (track == null || track.rate <= 0) return;

        long frame = track.getEffectiveFrame();
        if (quantizeEnabled) {
            double period = track.getBeatPeriod();
            if (period > 0) {
                long bf = (long) (period * track.rate);
                if (bf > 0) frame = Math.round((double) frame / bf) * bf;
            }
        }
        long in = manualLoopIn[cd] >= 0 ? manualLoopIn[cd] : 0;
        if (frame > in) {
            manualLoopOut[cd] = frame;
            track.manualLoopIn = in;
            track.manualLoopOut = frame;
            double period = track.getBeatPeriod();
            int beats = (period > 0) ? (int) Math.round((frame - in) / (period * track.rate)) : 4;
            beats = Math.max(1, beats);
            DjControl.send(pos, DjControl.LOOP, cd, beats);
            dj.setLoop(cd, beats);
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.4f);
            }
        }
    }

    private void handleReloop(boolean isLeft) {
        int cd = currentDeck(isLeft);
        DjBoothBlockEntity dj = booth();
        if (dj == null) return;
        MusicPulse.Track track = MusicPulse.trackFor(pos, cd);
        if (track == null) return;

        if (dj.getLoopBeats(cd) > 0 || (track.manualLoopIn >= 0 && track.manualLoopOut > track.manualLoopIn)) {
            // Exit loop: seamlessly preserve current playhead position!
            track.deckFrame = track.getEffectiveFrame();
            track.slipFrame = track.deckFrame;
            track.loopLen = 0;
            DjControl.send(pos, DjControl.LOOP, cd, 0);
            dj.setLoop(cd, 0);
            DjControl.send(pos, DjControl.JUMP_PLAYHEAD, cd, (float) track.deckFrame);
            track.manualLoopIn = -1;
            track.manualLoopOut = -1;
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 0.9f);
            }
        } else if (manualLoopIn[cd] >= 0 && manualLoopOut[cd] > manualLoopIn[cd]) {
            // Reloop
            track.manualLoopIn = manualLoopIn[cd];
            track.manualLoopOut = manualLoopOut[cd];
            track.deckFrame = manualLoopIn[cd];
            track.slipFrame = manualLoopIn[cd];
            DjControl.send(pos, DjControl.JUMP_PLAYHEAD, cd, (float) manualLoopIn[cd]);
            double period = track.getBeatPeriod();
            int beats = (period > 0) ? (int) Math.round((manualLoopOut[cd] - manualLoopIn[cd]) / (period * track.rate)) : 4;
            DjControl.send(pos, DjControl.LOOP, cd, Math.max(1, beats));
            dj.setLoop(cd, Math.max(1, beats));
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.3f);
            }
        }
    }

    private void handlePadPress(boolean isLeft, int padIndex) {
        int cd = currentDeck(isLeft);
        DjBoothBlockEntity dj = booth();
        if (dj == null) return;
        int mode = dj.getPadMode(cd);
        MusicPulse.Track track = MusicPulse.trackFor(pos, cd);

        switch (mode) {
            case DjBoothBlockEntity.PAD_HOT_CUE -> {
                if (Screen.hasShiftDown()) {
                    if (dj.getHotCue(cd, padIndex) >= 0) {
                        dj.setHotCue(cd, padIndex, -1L);
                        if (track != null) track.hotCues[padIndex] = -1L;
                        DjControl.send(pos, DjControl.SET_HOT_CUE, (cd & 3) | (padIndex << 2), -1f);
                        if (minecraft != null && minecraft.player != null) {
                            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 0.7f);
                        }
                    }
                } else {
                    long cue = dj.getHotCue(cd, padIndex);
                    if (cue < 0) {
                        long frame = track != null ? track.getEffectiveFrame() : 0L;
                        if (quantizeEnabled && track != null && track.rate > 0) {
                            double period = track.getBeatPeriod();
                            if (period > 0) {
                                long bf = (long) (period * track.rate);
                                if (bf > 0) frame = Math.round((double) frame / bf) * bf;
                            }
                        }
                        dj.setHotCue(cd, padIndex, frame);
                        if (track != null) track.hotCues[padIndex] = frame;
                        DjControl.send(pos, DjControl.SET_HOT_CUE, (cd & 3) | (padIndex << 2), (float) frame);
                        if (minecraft != null && minecraft.player != null) {
                            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.3f);
                        }
                    } else {
                        if (track != null) {
                            track.deckFrame = cue;
                            track.slipFrame = cue;
                        }
                        DjControl.send(pos, DjControl.JUMP_PLAYHEAD, cd, (float) cue);
                        if (!dj.isPlaying(cd)) {
                            DjControl.send(pos, DjControl.TOGGLE_PLAY, cd, 0f);
                        }
                        if (minecraft != null && minecraft.player != null) {
                            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.5f);
                        }
                    }
                }
            }
            case DjBoothBlockEntity.PAD_BEAT_LOOP -> {
                int beats = DjBoothBlockEntity.LOOPS[padIndex % DjBoothBlockEntity.LOOPS.length];
                int curLoop = dj.getLoopBeats(cd);
                int nextLoop = (curLoop == beats) ? 0 : beats;
                if (nextLoop == 0 && track != null) {
                    track.deckFrame = track.getEffectiveFrame();
                    track.slipFrame = track.deckFrame;
                    track.loopLen = 0;
                    DjControl.send(pos, DjControl.JUMP_PLAYHEAD, cd, (float) track.deckFrame);
                }
                DjControl.send(pos, DjControl.LOOP, cd, nextLoop);
                dj.setLoop(cd, nextLoop);
            }
            case DjBoothBlockEntity.PAD_SLIP_LOOP -> {
                int beats = DjBoothBlockEntity.LOOPS[padIndex % DjBoothBlockEntity.LOOPS.length];
                DjControl.send(pos, DjControl.LOOP, cd, beats);
                dj.setLoop(cd, beats);
            }
            case DjBoothBlockEntity.PAD_BEAT_JUMP -> {
                int[] jumps = {-8, -4, -2, -1, 1, 2, 4, 8};
                int jumpBeats = jumps[padIndex];
                double period = track != null && track.rate > 0 ? track.getBeatPeriod() : 0;
                if (period > 0) {
                    long deltaFrames = (long) (jumpBeats * period * track.rate);
                    track.scrub(deltaFrames);
                    DjControl.send(pos, DjControl.JUMP_PLAYHEAD, cd, (float) track.deckFrame);
                    DjControl.send(pos, DjControl.BEAT_JUMP, cd, (float) (jumpBeats * period * 20));
                } else {
                    DjControl.send(pos, DjControl.PAD_TRIGGER, cd, padIndex);
                }
                if (minecraft != null && minecraft.player != null) {
                    minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
                }
            }
        }
    }

    private void handlePadRelease(boolean isLeft, int padIndex) {
        int cd = currentDeck(isLeft);
        DjBoothBlockEntity dj = booth();
        if (dj == null) return;
        int mode = dj.getPadMode(cd);

        if (mode == DjBoothBlockEntity.PAD_SLIP_LOOP) {
            DjControl.send(pos, DjControl.LOOP, cd, 0);
            dj.setLoop(cd, 0);
            MusicPulse.Track track = MusicPulse.trackFor(pos, cd);
            if (track != null) {
                track.deckFrame = track.slipFrame;
            }
        }
    }

    private int findMasterDeck(DjBoothBlockEntity dj, int myDeck) {
        int best = (myDeck + 1) % DjBoothBlockEntity.DECKS;
        float maxFader = -1f;
        for (int d = 0; d < DjBoothBlockEntity.DECKS; d++) {
            if (d == myDeck) continue;
            if (dj.isPlaying(d) && !dj.getDisc(d).isEmpty()) {
                float f = dj.getChannelFader(d);
                if (f > maxFader) {
                    maxFader = f;
                    best = d;
                }
            }
        }
        return best;
    }

    private void syncTempo(int deck) {
        DjBoothBlockEntity dj = booth();
        if (dj == null || minecraft == null || minecraft.player == null) return;
        int otherDeck = findMasterDeck(dj, deck);
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

        // Phase Lock / zero-phase kick sync
        MusicPulse.Track trackMine = MusicPulse.trackFor(pos, deck);
        MusicPulse.Track trackOther = MusicPulse.trackFor(pos, otherDeck);
        if (trackMine != null && trackOther != null && trackMine.rate > 0 && trackOther.rate > 0) {
            long bfMine = (long) (mine * trackMine.rate);
            long bfOther = (long) (theirs * trackOther.rate);
            if (bfMine > 0 && bfOther > 0) {
                long phaseMine = Math.floorMod(trackMine.deckFrame, bfMine);
                long phaseOther = Math.floorMod(trackOther.deckFrame, bfOther);
                long diff = phaseOther - phaseMine;
                if (diff > bfMine / 2) diff -= bfMine;
                else if (diff < -bfMine / 2) diff += bfMine;
                trackMine.scrub(diff);
            }
        }

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

            quantizeButtons[p].setMessage(Component.literal("QTZ").withStyle(quantizeEnabled ? ChatFormatting.AQUA : ChatFormatting.GRAY));

            int master = findMasterDeck(dj, deck);
            double minePeriod = MusicPulse.beatPeriodAt(dj.deckPos(deck));
            double masterPeriod = MusicPulse.beatPeriodAt(dj.deckPos(master));
            double mineBpm = minePeriod > 0 ? 60.0 / (minePeriod / dj.getPitch(deck)) : 0;
            double masterBpm = masterPeriod > 0 ? 60.0 / (masterPeriod / dj.getPitch(master)) : 0;
            boolean isSynced = dj.isPlaying(deck) && mineBpm > 0 && Math.abs(mineBpm - masterBpm) < 0.2;
            syncButtons[p].setMessage(Component.literal("SYNC").withStyle(isSynced ? ChatFormatting.AQUA : ChatFormatting.GRAY));

            boolean hasIn = manualLoopIn[deck] >= 0;
            loopInButtons[p].setMessage(Component.literal("IN").withStyle(hasIn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            boolean hasOut = manualLoopOut[deck] >= 0;
            loopOutButtons[p].setMessage(Component.literal("OUT").withStyle(hasOut ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            boolean inLoop = dj.getLoopBeats(deck) > 0;
            reloopButtons[p].setMessage(Component.literal(inLoop ? "EXIT" : "RLP").withStyle(inLoop ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            mtButtons[p].setMessage(Component.literal("MT").withStyle(dj.isMasterTempo(deck) ? ChatFormatting.GREEN : ChatFormatting.GRAY));

            cueHeadphones[p].setMessage(Component.literal("CUE")
                .withStyle(MusicPulse.isCued(pos, deck) ? ChatFormatting.GOLD : ChatFormatting.GRAY));

            int curMode = dj.getPadMode(deck);
            for (int m = 0; m < 4; m++) {
                padModeTabs[p][m].setMessage(Component.literal(padModeTabs[p][m].getMessage().getString())
                    .withStyle(m == curMode ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            }

            for (int i = 0; i < 8; i++) {
                boolean active = false;
                if (curMode == DjBoothBlockEntity.PAD_HOT_CUE) {
                    active = dj.getHotCue(deck, i) >= 0;
                } else if (curMode == DjBoothBlockEntity.PAD_BEAT_LOOP || curMode == DjBoothBlockEntity.PAD_SLIP_LOOP) {
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
        bfxTypeButton.setMessage(Component.literal(BFX_SHORT[dj.getBeatFxType()]).withStyle(ChatFormatting.YELLOW));
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
        g.fill(left + 4, top + 6, left + 160, top + H - 6, 0xFF171920);
        g.renderOutline(left + 4, top + 6, 156, H - 12, 0xFF242730);

        // Right Deck Recessed Plate
        g.fill(left + 380, top + 6, left + W - 4, top + H - 6, 0xFF171920);
        g.renderOutline(left + 380, top + 6, 156, H - 12, 0xFF242730);

        // Center Mixer Recessed Plate
        g.fill(left + 162, top + 90, left + 378, top + H - 6, 0xFF14161C);
        g.renderOutline(left + 162, top + 90, 216, H - 96, 0xFF282B35);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        DjBoothBlockEntity dj = booth();
        if (dj == null || minecraft == null || minecraft.level == null) return;
        long time = minecraft.level.getGameTime();
        // The amp rack's traffic light above the mixer: the DJ sees at once when the rig is too loud or off.
        AmpRackBlockEntity rack = AmpRackBlockEntity.driving(minecraft.level, pos);
        if (rack != null) {
            AmpStatus.Status st = AmpRackScreen.status(minecraft.level, rack);
            int col = AmpRackScreen.color(st.light());
            g.fill(left + 6, top - 11, left + 14, top - 3, 0xFF000000 | col);
            g.drawString(font, AmpRackScreen.message(st), left + 18, top - 11, col);
            AmpMeters meters = MusicPulse.meters(pos);
            if (meters != null && meters.worstLimit() >= 0.1f) {
                Component lim = Component.translatable("createbrewery.amp.limit", String.format("%.1f", meters.worstLimit()));
                g.drawString(font, lim, left + W - 6 - font.width(lim), top - 11, 0xFF4040);
            }
        }

        // ---------------------------------------------------------------------------------
        // 10.1" CENTRAL TOUCHSCREEN DISPLAY
        // ---------------------------------------------------------------------------------
        renderTouchScreen(g, left + 164, top + 6, 212, 82, dj, time, partialTick);

        // ---------------------------------------------------------------------------------
        // TRACK LABELS & TIME REMAINING
        // ---------------------------------------------------------------------------------
        renderDeckHeader(g, left + 6, leftDeck, dj);
        renderDeckHeader(g, left + 384, rightDeck, dj);

        // ---------------------------------------------------------------------------------
        // 4 CHANNELS STEREO LED VU-METERS
        // ---------------------------------------------------------------------------------
        for (int ch = 0; ch < DjBoothBlockEntity.DECKS; ch++) {
            int vx = left + 164 + 54 + ch * 28 + 24;
            renderVuMeter(g, vx, top + 94, 3, 100, chVU[ch]);
        }

        // Master Stereo VU-Meters
        renderMasterVu(g, left + 174, top + 190, 30, 56, vuMaster);
    }

    private void renderDeckHeader(GuiGraphics g, int px, int deck, DjBoothBlockEntity dj) {
        ItemStack disc = dj.getDisc(deck);
        boolean playing = dj.isPlaying(deck);
        int col = DECK_COLORS[deck % DECK_COLORS.length];

        int master = findMasterDeck(dj, -1);
        if (deck == master && playing) {
            g.fill(px + 122, top + 248, px + 150, top + 260, 0xFFCC2222);
            g.drawCenteredString(font, "MST", px + 136, top + 250, 0xFFFFFFFF);
        }
        String title = disc.isEmpty() ? Component.translatable("createbrewery.dj.empty").getString() : dj.title(disc).getString();
        g.drawString(font, font.plainSubstrByWidth(title, 140), px + 6, top + 236, playing ? 0xFFFFFFFF : 0xFFAAAAAA);

        // Time remaining / elapsed
        if (playing && !disc.isEmpty()) {
            MusicPulse.Track track = MusicPulse.trackFor(pos, deck);
            if (track != null && track.rate > 0) {
                long curF = track.getEffectiveFrame();
                long el = (long) (curF / track.rate);
                float tot = 0f;
                if (minecraft != null && minecraft.level != null) {
                    tot = JukeboxSong.fromStack(minecraft.level.registryAccess(), disc)
                        .map(s -> s.value().lengthInSeconds())
                        .orElse(0f);
                }
                if (tot <= 0f) tot = (float) track.getFramesRead() / track.rate;
                float rem = Math.max(0f, tot - el);
                boolean blink = rem <= 30f && ((System.currentTimeMillis() / 400) % 2 == 0);
                int timeCol = blink ? 0xFFFF3333 : 0xFF00FF66;
                String timeStr = String.format(java.util.Locale.ROOT, "%02d:%02d [-%02d:%02d]", el / 60, el % 60, (long) rem / 60, (long) rem % 60);
                g.drawString(font, timeStr, px + 6, top + 250, timeCol);
            } else {
                g.drawString(font, "PLAYING", px + 6, top + 250, 0xFF00FF66);
            }
        } else if (playing) {
            g.drawString(font, "--:--", px + 6, top + 250, 0xFF667788);
        } else {
            g.drawString(font, "STOPPED", px + 6, top + 250, 0xFF888888);
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
        g.drawString(font, "XDJ-AZ", sx + 3, sy + 3, 0xFF88AAFF);

        if (screenTab == 0) {
            // ================= WAVEFORM TAB =================
            // Left Deck Waveform
            renderWaveform(g, sx + 2, sy + 14, sw - 4, 22, leftDeck, dj, DECK_COLORS[leftDeck], 0xFF0055AA, time, pt);
            // Right Deck Waveform
            renderWaveform(g, sx + 2, sy + 38, sw - 4, 22, rightDeck, dj, DECK_COLORS[rightDeck], 0xFFAA5500, time, pt);

            // Beat Phase Grid (4-beat indicator dots & bar progress)
            int phaseX = sx + (sw - 40) / 2, phaseY = sy + 64;
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
            g.drawCenteredString(font, "Chest next to booth", x + w / 2, y + 28, 0xFF888888);
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
        g.drawString(font, "ENGINE: DSP 44.1 kHz", x + 4, y + 4, 0xFF88AAFF);
        g.drawString(font, "COLOR FX: " + DjBoothBlockEntity.COLOR_FX_NAMES[dj.getActiveColorFx()], x + 4, y + 16, 0xFF00FF66);
        g.drawString(font, "BEAT FX: " + DjBoothBlockEntity.BFX_NAMES[dj.getBeatFxType()] + " " + dj.getBeatFxBeats() + "b", x + 4, y + 28, 0xFFFFCC00);
        g.drawString(font, "AUTO-DROP: " + (dj.isAutoDrop() ? "ACTIVE" : "OFF"), x + 4, y + 40, dj.isAutoDrop() ? 0xFFFF3344 : 0xFF888888);
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
                int sx = left + 168, sy = top + 22, sw = 204;
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
        for (TransportButton cb : cueButtons) {
            if (cb != null) cb.releaseButton();
        }
        for (TransportButton pb : playButtons) {
            if (pb != null) pb.releaseButton();
        }
        for (PadButton[] row : performancePads) {
            for (PadButton pad : row) {
                if (pad != null) pad.releaseButton();
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        for (JogWheelWidget jw : jogWheels) {
            if (jw != null) jw.releasePlatter();
        }
        for (TransportButton cb : cueButtons) {
            if (cb != null) cb.releaseButton();
        }
        for (TransportButton pb : playButtons) {
            if (pb != null) pb.releaseButton();
        }
        for (PadButton[] row : performancePads) {
            for (PadButton pad : row) {
                if (pad != null) pad.releaseButton();
            }
        }
        super.onClose();
    }

    private boolean isMouseOver(int x, int y, int w, int h) {
        if (minecraft == null) return false;
        double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** 3-band Pioneer full-color scrolling audio waveform with live slice decoding & full-track overview. */
    private void renderWaveform(GuiGraphics g, int x, int y, int w, int h, int deck, DjBoothBlockEntity dj, int colorHigh, int colorLow, long time, float pt) {
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

        int master = findMasterDeck(dj, -1);
        if (deck == master && playing) {
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
        if (minecraft != null && minecraft.level != null) {
            totalSec = JukeboxSong.fromStack(minecraft.level.registryAccess(), disc)
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
                    int hColor = PAD_COLORS[hIdx];
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
        int tx = x + w / 2 - 1;
        g.fill(tx, y + 8, tx + 2, y + 9, 0xFFFF3333);
        g.fill(tx, y + 22, tx + 2, y + 23, 0xFFFFCC00);
        g.fill(tx, y + 38, tx + 2, y + 39, 0xFF44FF88);
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
                    MusicPulse.Track track = MusicPulse.trackFor(pos, deck);
                    if (track != null) {
                        if (dj.isSlipMode(deck)) {
                            track.deckFrame = track.slipFrame;
                        } else {
                            track.slipFrame = track.deckFrame;
                        }
                        DjControl.send(pos, DjControl.JUMP_PLAYHEAD, deck, (float) track.deckFrame);
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

                // Real-time audio spooling/scrubbing
                MusicPulse.Track track = MusicPulse.trackFor(pos, deck);
                if (track != null) {
                    long deltaFrames = (long) ((delta / (2.0 * Math.PI)) * 1.8 * track.rate);
                    track.scrub(deltaFrames);
                }

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
                    MusicPulse.Track track = MusicPulse.trackFor(pos, deck);
                    if (track != null) {
                        long deltaFrames = (long) (scrollY * 0.25 * track.rate);
                        track.scrub(deltaFrames);
                        DjControl.send(pos, DjControl.JUMP_PLAYHEAD, deck, (float) track.deckFrame);
                    }
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

    /** Iconic circular illuminated CUE and PLAY/PAUSE transport buttons with authentic Pioneer CDJ behavior. */
    private class TransportButton extends AbstractWidget {
        private final boolean isPlay;
        private final boolean isLeft;
        private final java.util.function.Consumer<TransportButton> onPress;
        private final java.util.function.Consumer<TransportButton> onRelease;
        private boolean isHeld = false;

        TransportButton(int x, int y, int w, int h, boolean isPlay, boolean isLeft,
                        java.util.function.Consumer<TransportButton> onPress,
                        java.util.function.Consumer<TransportButton> onRelease) {
            super(x, y, w, h, Component.literal(isPlay ? "PLAY" : "CUE"));
            this.isPlay = isPlay;
            this.isLeft = isLeft;
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
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, isPlay ? 1.2f : 1.0f);
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
            int deck = currentDeck(isLeft);

            DjBoothBlockEntity dj = booth();
            boolean hasDisc = dj != null && !dj.getDisc(deck).isEmpty();
            boolean isPlaying = dj != null && dj.isPlaying(deck);
            MusicPulse.Track track = MusicPulse.trackFor(pos, deck);
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
                g.drawString(font, "▶||", cx - 7, cy - 4, textColor);
            } else {
                g.drawCenteredString(font, "CUE", cx, cy - 4, textColor);
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
        private final java.util.function.Consumer<PadButton> onRelease;
        private boolean active;
        private int mode = DjBoothBlockEntity.PAD_BEAT_LOOP;
        private boolean isHeld = false;

        PadButton(int x, int y, int w, int h, int index, int baseColor,
                  java.util.function.Consumer<PadButton> onPress,
                  java.util.function.Consumer<PadButton> onRelease) {
            super(x, y, w, h, Component.empty());
            this.index = index;
            this.baseColor = baseColor;
            this.onPress = onPress;
            this.onRelease = onRelease;
        }

        void setActive(boolean on) { this.active = on; }
        void setMode(int mode) { this.mode = mode; }

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
            super(x, y, 18, 19, Component.literal(label));
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
            int cx = getX() + 9, cy = getY() + 6;
            int dots = 11;
            for (int i = 0; i < dots; i++) {
                double f = i / (double) (dots - 1);
                double a = Math.toRadians(135 + 270 * f);
                int px = cx + (int) Math.round(Math.cos(a) * 5), py = cy + (int) Math.round(Math.sin(a) * 5);
                boolean lit = bipolar
                    ? (f >= Math.min(0.5, value) - 1e-6 && f <= Math.max(0.5, value) + 1e-6)
                    : f <= value + 1e-6;
                g.fill(px - 1, py - 1, px + 1, py + 1, lit ? 0xFF00E5FF : 0xFF222630);
            }
            g.fill(cx - 2, cy - 2, cx + 2, cy + 2, isHoveredOrFocused() ? 0xFF656B7A : 0xFF424652);
            double a = Math.toRadians(135 + 270 * value);
            for (int r = 1; r <= 2; r++) {
                int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
            }
            var font = Minecraft.getInstance().font;
            g.pose().pushPose();
            g.pose().translate(cx, getY() + 11, 0);
            g.pose().scale(0.8f, 0.8f, 1f);
            g.drawCenteredString(font, getMessage(), 0, 0, value < 0.02 && !bipolar ? 0xFFFF4444 : 0xFFAAAAAA);
            g.pose().popPose();
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
