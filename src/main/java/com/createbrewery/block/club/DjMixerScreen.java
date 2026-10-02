package com.createbrewery.block.club;

import com.createbrewery.block.club.dj.*;
import static com.createbrewery.block.club.dj.DjConstants.*;
import com.createbrewery.drunk.MusicPulse;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;

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
public class DjMixerScreen extends Screen implements DjBoothContext {
    private final BlockPos pos;
    private int left, top;

    // Physical player active layers
    private int leftDeck = DjBoothBlockEntity.A;   // 0 (Deck 1) or 2 (Deck 3)
    private int rightDeck = DjBoothBlockEntity.B;  // 1 (Deck 2) or 3 (Deck 4)

    // Touchscreen component
    private final TouchscreenView touchscreen = new TouchscreenView();

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

    @Override
    public DjBoothBlockEntity booth() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof DjBoothBlockEntity dj ? dj : null;
    }

    @Override
    public BlockPos pos() {
        return pos;
    }

    @Override
    public int currentDeck(boolean isLeft) {
        return isLeft ? leftDeck : rightDeck;
    }

    @Override
    public boolean isQuantize() {
        return quantizeEnabled;
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
        tabWaveButton = addRenderableWidget(Button.builder(Component.literal("WAVE"), b -> touchscreen.setScreenTab(0))
            .bounds(sx + 84, sy + 2, 24, 11).build());
        tabBrowseButton = addRenderableWidget(Button.builder(Component.literal("BROWSE"), b -> touchscreen.setScreenTab(1))
            .bounds(sx + 110, sy + 2, 30, 11).build());
        tabInfoButton = addRenderableWidget(Button.builder(Component.literal("INFO"), b -> touchscreen.setScreenTab(2))
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
        colorParamKnob = addRenderableWidget(new Knob(mx + 8, top + 134, DjControl.COLOR_FX_PARAM, 0, "PARAM", false, this));

        // ---------------------------------------------------------------------------------
        // 5. CENTER MIXER - 4 CHANNEL STRIPS (Channels 1, 2, 3, 4)
        // ---------------------------------------------------------------------------------
        for (int ch = 0; ch < DjBoothBlockEntity.DECKS; ch++) {
            int d = ch;
            int cx = mx + 36 + ch * 23;

            // TRIM, HI, MID, LOW, COLOR knobs
            chKnobs[ch][0] = addRenderableWidget(new Knob(cx, top + 86, DjControl.TRIM, d, "TRIM", false, this));
            chKnobs[ch][1] = addRenderableWidget(new Knob(cx, top + 100, DjControl.EQ_HIGH, d, "HI", false, this));
            chKnobs[ch][2] = addRenderableWidget(new Knob(cx, top + 114, DjControl.EQ_MID, d, "MID", false, this));
            chKnobs[ch][3] = addRenderableWidget(new Knob(cx, top + 128, DjControl.EQ_LOW, d, "LOW", false, this));
            chKnobs[ch][4] = addRenderableWidget(new Knob(cx, top + 142, DjControl.FILTER, d, "COLOR", true, this));

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

        bfxDepthKnob = addRenderableWidget(new Knob(bfxX + 7, top + 152, DjControl.BEAT_FX_DEPTH, 0, "DEPTH", false, this));

        // ---------------------------------------------------------------------------------
        // 7. BOTTOM CONTROLS: Magvel Crossfader & Redstone Automation
        // ---------------------------------------------------------------------------------
        crossfader = addRenderableWidget(new Fader(mx + 30, top + 226, 78, DjControl.CROSSFADER, false, this));

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
        }).bounds(px + 3, top + 8, 16, 12).build());

        deckLayerButtons[d2] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(d2 + 1)), b -> {
            if (isLeft) leftDeck = d2; else rightDeck = d2;
            refresh();
        }).bounds(px + 21, top + 8, 16, 12).build());

        // Manual Loop Buttons: IN / 4BEAT, OUT, RELOOP/EXIT, and MASTER TEMPO (MT)
        loopInButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("IN"), b -> handleLoopIn(isLeft))
            .bounds(px + 39, top + 8, 18, 12).tooltip(Tooltip.create(Component.literal("Loop In / Hold Shift for 4-Beat Auto Loop"))).build());

        loopOutButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("OUT"), b -> handleLoopOut(isLeft))
            .bounds(px + 59, top + 8, 19, 12).tooltip(Tooltip.create(Component.literal("Loop Out"))).build());

        reloopButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("EXIT"), b -> handleReloop(isLeft))
            .bounds(px + 80, top + 8, 22, 12).tooltip(Tooltip.create(Component.literal("Reloop / Exit"))).build());

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
        }).bounds(px + 104, top + 8, 17, 12).tooltip(Tooltip.create(Component.literal("Master Tempo (Key Lock): Preserve musical pitch when changing tempo"))).build());

        // CDJ Jogwheel (Center at px + 40, top + 56, radius 32)
        jogWheels[playerIdx] = addRenderableWidget(new JogWheelWidget(px + 8, top + 24, 64, 64, isLeft, this));

        // Pitch / Tempo Vertical Fader
        pitchFaders[playerIdx] = addRenderableWidget(new VPitchFader(px + 84, top + 24, 18, 64, isLeft, this));

        // Deck Controls row: VINYL, SLIP, REV, QTZ, SYNC
        vinylButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("VIN"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) DjControl.send(pos, DjControl.VINYL_MODE, cd, dj.isVinylMode(cd) ? 0f : 1f);
        }).bounds(px + 4, top + 92, 19, 12).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.vinyl_hint"))).build());

        slipButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("SLP"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) DjControl.send(pos, DjControl.SLIP_MODE, cd, dj.isSlipMode(cd) ? 0f : 1f);
        }).bounds(px + 25, top + 92, 19, 12).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.slip_hint"))).build());

        revButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("REV"), b -> {
            DjBoothBlockEntity dj = booth();
            int cd = currentDeck(isLeft);
            if (dj != null) DjControl.send(pos, DjControl.REVERSE, cd, dj.isReverse(cd) ? 0f : 1f);
        }).bounds(px + 46, top + 92, 19, 12).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.reverse_hint"))).build());

        quantizeButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("QTZ"), b -> {
            quantizeEnabled = !quantizeEnabled;
            refresh();
        }).bounds(px + 67, top + 92, 22, 12).tooltip(Tooltip.create(Component.literal("Quantize Beat Snapping"))).build());

        syncButtons[playerIdx] = addRenderableWidget(Button.builder(Component.literal("SYNC"), b -> syncTempo(currentDeck(isLeft)))
            .bounds(px + 91, top + 92, 26, 12).tooltip(Tooltip.create(Component.translatable("createbrewery.dj.sync"))).build());

        // Transport Buttons: CUE and PLAY/PAUSE
        cueButtons[playerIdx] = addRenderableWidget(new TransportButton(px + 8, top + 108, 26, 26, false, isLeft, this, b -> {
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

        playButtons[playerIdx] = addRenderableWidget(new TransportButton(px + 38, top + 108, 26, 26, true, isLeft, this, b -> {
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
                    handlePadPress(isLeft, padIndex);
                }, b -> {
                    handlePadRelease(isLeft, padIndex);
                }));
            }
        }
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
                if (track != null && track.rate > 0) {
                    double period = track.getBeatPeriod();
                    if (period > 0) {
                        long deltaFrames = (long) (jumpBeats * period * track.rate);
                        track.scrub(deltaFrames);
                        DjControl.send(pos, DjControl.JUMP_PLAYHEAD, cd, (float) track.deckFrame);
                    }
                }
                DjControl.send(pos, DjControl.PAD_TRIGGER, cd, padIndex);
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
        return String.format(java.util.Locale.ROOT, "%.1f", 60.0 / period);
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
            reloopButtons[p].setMessage(Component.literal(inLoop ? "EXIT" : "RELOOP").withStyle(inLoop ? ChatFormatting.GOLD : ChatFormatting.GRAY));
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
        tabWaveButton.setMessage(Component.literal("WAVE").withStyle(touchscreen.getScreenTab() == 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        tabBrowseButton.setMessage(Component.literal("BROWSE").withStyle(touchscreen.getScreenTab() == 1 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        tabInfoButton.setMessage(Component.literal("INFO").withStyle(touchscreen.getScreenTab() == 2 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
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
        touchscreen.render(g, font, left + 128, top + 6, 166, 76,
            dj, pos, leftDeck, rightDeck, findMasterDeck(dj, -1), time, partialTick, mouseX, mouseY);

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
            VuMeterRenderer.renderVuMeter(g, vx, top + 86, 3, 68, chVU[ch]);
        }

        // Master Stereo VU-Meters
        VuMeterRenderer.renderMasterVu(g, left + 130, top + 172, 24, 44, vuMaster);
    }

    private void renderDeckHeader(GuiGraphics g, int px, int deck, DjBoothBlockEntity dj) {
        ItemStack disc = dj.getDisc(deck);
        boolean playing = dj.isPlaying(deck);
        int col = DECK_COLORS[deck % DECK_COLORS.length];

        g.drawString(font, "DECK " + (deck + 1), px + 52, top + 10, col);
        int master = findMasterDeck(dj, -1);
        if (deck == master && playing) {
            g.fill(px + 86, top + 8, px + 116, top + 18, 0xFFCC2222);
            g.drawCenteredString(font, "MASTER", px + 101, top + 9, 0xFFFFFFFF);
        }
        String title = disc.isEmpty() ? Component.translatable("createbrewery.dj.empty").getString() : dj.title(disc).getString();
        g.drawString(font, font.plainSubstrByWidth(title, 110), px + 6, top + 218, playing ? 0xFFFFFFFF : 0xFFAAAAAA);

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
                g.drawString(font, timeStr, px + 6, top + 230, timeCol);
            } else {
                g.drawString(font, "PLAYING", px + 6, top + 230, 0xFF00FF66);
            }
        } else if (playing) {
            g.drawString(font, "--:--", px + 6, top + 230, 0xFF667788);
        } else {
            g.drawString(font, "STOPPED", px + 6, top + 230, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (touchscreen.mouseClicked(mouseX, mouseY, button, booth(), pos, left + 130, top + 6)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (touchscreen.mouseScrolled(mouseX, mouseY, scrollX, scrollY, booth())) {
            return true;
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
