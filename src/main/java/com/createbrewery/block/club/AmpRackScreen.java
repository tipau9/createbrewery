package com.createbrewery.block.club;

import com.createbrewery.drunk.DeckFx;
import com.createbrewery.drunk.MusicPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 19" Touring Amplifier Chassis & DSP Matrix Screen.
 * Features 4-channel DSP: crossover, sub/top gains, channel mutes, bass contour,
 * subsonic high-pass filter, and delay line alignment, with live 4-channel LED VU meters
 * and dynamic matrix LCD telemetry.
 */
public class AmpRackScreen extends Screen {
    private static final int W = 320, H = 216;

    private final BlockPos pos;
    private int left, top;

    private Slider crossoverSlider, subSlider, topsSlider, delaySlider;
    private Button muteTopsBtn, muteSubsBtn, contourBtn, subCutBtn, speedBtn;

    // 4-Channel live VU meter telemetry with peak hold
    private final float[] meterLevel = new float[4];
    private final float[] peakLevel = new float[4];
    private final int[] peakHoldTicks = new int[4];

    private AmpRackScreen(BlockPos pos) {
        super(Component.translatable("block.createbrewery.amp_rack"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new AmpRackScreen(pos));
    }

    private AmpRackBlockEntity rack() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof AmpRackBlockEntity r ? r : null;
    }

    /** 60..200 Hz on a log scale, as a slider 0..1. */
    private static double hz(double slider) {
        return AmpRackBlockEntity.MIN_CROSSOVER * Math.pow(AmpRackBlockEntity.MAX_CROSSOVER / AmpRackBlockEntity.MIN_CROSSOVER, slider);
    }

    private static double slider(double hz) {
        return Math.log(hz / AmpRackBlockEntity.MIN_CROSSOVER) / Math.log(AmpRackBlockEntity.MAX_CROSSOVER / AmpRackBlockEntity.MIN_CROSSOVER);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        AmpRackBlockEntity r = rack();
        if (r == null) return;

        int ctrlX = left + 92;
        int ctrlW = 210;

        // Sliders
        crossoverSlider = addRenderableWidget(new Slider(ctrlX, top + 67, ctrlW, 18, AmpControl.CROSSOVER, slider(r.getCrossover())));
        crossoverSlider.setTooltip(Tooltip.create(Component.literal("Frequenzweiche (60 - 200 Hz): Trennfrequenz zwischen Subwoofern und Mittel-/Hochtönern")));

        subSlider = addRenderableWidget(new Slider(ctrlX, top + 89, ctrlW, 18, AmpControl.SUB_GAIN, r.getSubGain()));
        subSlider.setTooltip(Tooltip.create(Component.literal("Subwoofer Gain (-inf bis +6 dB)")));

        topsSlider = addRenderableWidget(new Slider(ctrlX, top + 111, ctrlW, 18, AmpControl.TOP_GAIN, r.getTopGain()));
        topsSlider.setTooltip(Tooltip.create(Component.literal("Lautsprecher / Tops Gain (-inf bis +6 dB)")));

        delaySlider = addRenderableWidget(new Slider(ctrlX, top + 133, ctrlW, 18, AmpControl.DELAY_MS, r.getDelayMs() / 50.0));
        delaySlider.setTooltip(Tooltip.create(Component.literal("Delay-Alignment (0 - 50 ms): Gleicht entfernte Delay-Towers an die Hauptbühne an")));

        // Buttons Row 1: Mutes
        int halfW = 103;
        muteTopsBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            AmpRackBlockEntity now = rack();
            if (now == null) return;
            float val = now.isMuteTops() ? 0f : 1f;
            AmpControl.send(pos, AmpControl.MUTE_TOPS, val);
            AmpControl.apply(now, AmpControl.MUTE_TOPS, val);
            updateButtonLabels(now);
        }).bounds(ctrlX, top + 155, halfW, 18)
            .tooltip(Tooltip.create(Component.literal("Schaltet alle Hoch-/Mitteltöner stumm")))
            .build());

        muteSubsBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            AmpRackBlockEntity now = rack();
            if (now == null) return;
            float val = now.isMuteSubs() ? 0f : 1f;
            AmpControl.send(pos, AmpControl.MUTE_SUBS, val);
            AmpControl.apply(now, AmpControl.MUTE_SUBS, val);
            updateButtonLabels(now);
        }).bounds(ctrlX + 107, top + 155, halfW, 18)
            .tooltip(Tooltip.create(Component.literal("Schaltet alle Subwoofer stumm")))
            .build());

        // Buttons Row 2: DSP Modes
        int thirdW = 68;
        contourBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            AmpRackBlockEntity now = rack();
            if (now == null) return;
            int next = (now.getBassContour() + 1) % 3;
            AmpControl.send(pos, AmpControl.BASS_CONTOUR, (float) next);
            AmpControl.apply(now, AmpControl.BASS_CONTOUR, (float) next);
            updateButtonLabels(now);
        }).bounds(ctrlX, top + 177, thirdW, 18)
            .tooltip(Tooltip.create(Component.literal("Bass-Charakteristik: FLAT (linear), DEEP (+3dB bei 35Hz), PUNCH (+2.5dB bei 65Hz)")))
            .build());

        subCutBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            AmpRackBlockEntity now = rack();
            if (now == null) return;
            int next = (now.getSubCut() + 1) % 3;
            AmpControl.send(pos, AmpControl.SUB_CUT, (float) next);
            AmpControl.apply(now, AmpControl.SUB_CUT, (float) next);
            updateButtonLabels(now);
        }).bounds(ctrlX + 71, top + 177, thirdW, 18)
            .tooltip(Tooltip.create(Component.literal("Subsonic-Filter: Schützt Subwoofer vor unhörbarem Tieffrequenz-Rumpeln (AUS, 30Hz, 40Hz)")))
            .build());

        speedBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            AmpRackBlockEntity now = rack();
            if (now == null) return;
            float val = now.isPropagation() ? 0f : 1f;
            AmpControl.send(pos, AmpControl.PROPAGATION, val);
            AmpControl.apply(now, AmpControl.PROPAGATION, val);
            updateButtonLabels(now);
        }).bounds(ctrlX + 142, top + 177, thirdW, 18)
            .tooltip(Tooltip.create(Component.translatable("createbrewery.amp.propagation_hint")))
            .build());

        updateButtonLabels(r);
    }

    private void updateButtonLabels(AmpRackBlockEntity r) {
        if (r == null) return;
        muteTopsBtn.setMessage(Component.literal(r.isMuteTops() ? "§c✕ MUTE TOPS" : "§a✓ TOPS ON"));
        muteSubsBtn.setMessage(Component.literal(r.isMuteSubs() ? "§c✕ MUTE SUBS" : "§a✓ SUBS ON"));

        String contourName = switch (r.getBassContour()) {
            case AmpRackBlockEntity.BASS_DEEP -> "DEEP";
            case AmpRackBlockEntity.BASS_PUNCH -> "PUNCH";
            default -> "FLAT";
        };
        contourBtn.setMessage(Component.literal("BASS: " + contourName));

        String cutName = switch (r.getSubCut()) {
            case AmpRackBlockEntity.SUBCUT_30 -> "30Hz";
            case AmpRackBlockEntity.SUBCUT_40 -> "40Hz";
            default -> "AUS";
        };
        subCutBtn.setMessage(Component.literal("CUT: " + cutName));

        speedBtn.setMessage(Component.literal("SPEED: " + (r.isPropagation() ? "§aEIN" : "§7AUS")));
    }

    @Override
    public void tick() {
        AmpRackBlockEntity r = rack();
        if (r == null || crossoverSlider == null || minecraft.player == null || !minecraft.player.canInteractWithBlock(pos, 1.0)) {
            onClose();
            return;
        }
        crossoverSlider.flush();
        subSlider.flush();
        topsSlider.flush();
        delaySlider.flush();
        updateButtonLabels(r);

        // Update VU meter peak hold timers
        for (int i = 0; i < 4; i++) {
            if (peakHoldTicks[i] > 0) {
                peakHoldTicks[i]--;
            } else {
                peakLevel[i] = Math.max(0f, peakLevel[i] - 0.08f);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);

        // 19" Touring Chassis Background
        g.fill(left, top, left + W, top + H, 0xFF141619);
        g.renderOutline(left, top, W, H, 0xFF353942);
        g.renderOutline(left + 1, top + 1, W - 2, H - 2, 0xFF22242A);

        // Left rack ear (with screw slots)
        g.fill(left, top, left + 14, top + H, 0xFF1C1E23);
        g.fill(left + 14, top, left + 15, top + H, 0xFF0E1013);
        renderRackBolt(g, left + 4, top + 14);
        renderRackBolt(g, left + 4, top + H - 22);

        // Right rack ear (with screw slots)
        g.fill(left + W - 14, top, left + W, top + H, 0xFF1C1E23);
        g.fill(left + W - 15, top, left + W - 14, top + H, 0xFF0E1013);
        renderRackBolt(g, left + W - 10, top + 14);
        renderRackBolt(g, left + W - 10, top + H - 22);

        // Brand & Chassis Header
        g.drawString(font, "§l§fD40-DSP §r§8| §74-CHANNEL TOURING SYSTEM ENGINE", left + 20, top + 5, 0xE0E0E0, false);

        AmpRackBlockEntity r = rack();
        boolean active = r != null && r.getBooth() != null && MusicPulse.playingNear(r.getBooth());
        int powerLed = active ? 0xFF00FF66 : 0xFF26402E;
        g.fill(left + W - 28, top + 6, left + W - 22, top + 12, powerLed);
        g.renderOutline(left + W - 28, top + 6, 6, 6, 0xFF0F1B12);

        // Center Matrix LCD Display
        int lcdX = left + 18, lcdY = top + 17, lcdW = W - 36, lcdH = 46;
        g.fill(lcdX, lcdY, lcdX + lcdW, lcdY + lcdH, 0xFF051012);
        g.renderOutline(lcdX, lcdY, lcdW, lcdH, 0xFF0F322B);
        g.renderOutline(lcdX + 1, lcdY + 1, lcdW - 2, lcdH - 2, 0xFF071C17);

        // VU Meter Frame (Left Column)
        int vuX = left + 18, vuY = top + 67, vuW = 70, vuH = 128;
        g.fill(vuX, vuY, vuX + vuW, vuY + vuH, 0xFF0C0E11);
        g.renderOutline(vuX, vuY, vuW, vuH, 0xFF262A33);
    }

    private void renderRackBolt(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 6, y + 8, 0xFF0A0C0E);
        g.fill(x + 1, y + 1, x + 5, y + 7, 0xFF4A4E58);
        g.fill(x + 2, y + 2, x + 4, y + 6, 0xFF767C8A);
        // Screw head slot
        g.fill(x + 1, y + 4, x + 5, y + 5, 0xFF181A1E);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        AmpRackBlockEntity r = rack();
        if (r == null) return;

        BlockPos booth = r.getBooth();
        int topsCount = 0, subsCount = 0;
        if (booth != null && minecraft.level != null) {
            for (SpeakerBlockEntity s : SpeakerBlockEntity.linked(minecraft.level, booth)) {
                if (s instanceof SubwooferBlockEntity sw) {
                    if (sw.isActive()) subsCount++;
                } else if (s.isSpeaker()) {
                    topsCount++;
                }
            }
        }

        // --- 1. Dynamic LCD Matrix Telemetry ---
        int lcdX = left + 22, lcdY = top + 20;
        boolean playing = booth != null && MusicPulse.playingNear(booth);
        float level = playing ? MusicPulse.level() : 0f;
        float bass = playing ? MusicPulse.bass() : 0f;
        float kick = playing ? MusicPulse.kick() : 0f;
        float hats = playing ? MusicPulse.hats() : 0f;

        float topGain = r.isMuteTops() ? 0f : DeckFx.eqGain(r.getTopGain());
        float subGain = r.isMuteSubs() ? 0f : DeckFx.eqGain(r.getSubGain());

        int w1 = (int) (level * topGain * 850f);
        int w2 = (int) (level * topGain * 820f);
        int w3 = (int) (Math.max(kick, bass) * subGain * 2400f);
        int w4 = (int) (level * topGain * 550f);

        // Row 1: Output Power
        String pwrStr = playing
            ? String.format("P_OUT: 1:%dW  2:%dW  3:%dW  4:%dW", w1, w2, w3, w4)
            : "P_OUT: STANDBY (NO AUDIO SIGNAL)";
        g.drawString(font, pwrStr, lcdX, lcdY, 0xFF33FFCC, false);

        // Row 2: Load Impedance & Temperature
        String loadStr = booth == null
            ? "STATUS: UNLINKED // RIGHT-CLICK DJ BOOTH"
            : String.format("LOAD: 4.0Ω (%d Tops) | 2.7Ω (%d Subs) | TEMP: 44°C", topsCount, subsCount);
        g.drawString(font, loadStr, lcdX, lcdY + 10, booth == null ? 0xFFFF6666 : 0xFF66FFDD, false);

        // Row 3: DSP Configuration Summary
        String contourStr = switch (r.getBassContour()) {
            case AmpRackBlockEntity.BASS_DEEP -> "DEEP";
            case AmpRackBlockEntity.BASS_PUNCH -> "PUNCH";
            default -> "FLAT";
        };
        String cutStr = switch (r.getSubCut()) {
            case AmpRackBlockEntity.SUBCUT_30 -> "30Hz";
            case AmpRackBlockEntity.SUBCUT_40 -> "40Hz";
            default -> "OFF";
        };
        String dspStr = String.format("DSP: XO:%dHz | CUT:%s | BASS:%s | DLY:%.1fms",
            Math.round(r.getCrossover()), cutStr, contourStr, r.getDelayMs());
        g.drawString(font, dspStr, lcdX, lcdY + 20, 0xFF99FFEE, false);

        // Row 4: Limiting / Status
        float gr = booth == null ? 1f : MusicPulse.limiting(booth);
        float db = gr >= 0.999f ? 0f : (float) (-20 * Math.log10(Math.max(gr, 1e-4f)));
        if (db >= 0.1f) {
            g.drawString(font, String.format("LIMITER ENGAGED: -%.1f dB GAIN REDUCTION", db), lcdX, lcdY + 30, 0xFFFF4444, false);
        } else {
            g.drawString(font, "SYSTEM HEALTH: OPTIMAL // 96kHz 32-BIT DSP ACTIVE", lcdX, lcdY + 30, 0xFF22BBAA, false);
        }

        // --- 2. Live 4-Channel LED VU Meters ---
        renderVuMeters(g, topGain, subGain, level, kick, bass, hats);

        // Bottom hint
        g.drawCenteredString(font, Component.translatable("createbrewery.amp.hint"), left + W / 2, top + 201, 0x666666);
    }

    private void renderVuMeters(GuiGraphics g, float topGain, float subGain, float level, float kick, float bass, float hats) {
        int vuX = left + 18, vuY = top + 67;

        // Targets for 4 channels
        float t1 = Math.min(1.2f, topGain * (level * 0.95f + hats * 0.4f));
        float t2 = Math.min(1.2f, topGain * (level * 0.90f + hats * 0.45f));
        float t3 = Math.min(1.2f, subGain * (kick * 1.05f + bass * 0.9f));
        float t4 = Math.min(1.2f, topGain * (level * 0.8f + kick * 0.35f));

        float[] targets = {t1, t2, t3, t4};
        for (int ch = 0; ch < 4; ch++) {
            // Ballistic rise & decay
            if (targets[ch] > meterLevel[ch]) {
                meterLevel[ch] = targets[ch];
            } else {
                meterLevel[ch] = Math.max(0f, meterLevel[ch] * 0.82f);
            }

            if (meterLevel[ch] >= peakLevel[ch]) {
                peakLevel[ch] = meterLevel[ch];
                peakHoldTicks[ch] = 14;
            }

            int barX = vuX + 5 + ch * 16;
            int barY = vuY + 8;
            int numLeds = 11;
            int ledH = 8;
            int ledSpacing = 9;

            for (int seg = 0; seg < numLeds; seg++) {
                int segY = barY + (numLeds - 1 - seg) * ledSpacing;
                float segThreshold = (seg + 1) / (float) numLeds;
                boolean on = meterLevel[ch] >= segThreshold;
                boolean isPeak = Math.abs(peakLevel[ch] - segThreshold) < (0.8f / numLeds);

                int color;
                if (seg >= 9) { // Red CLIP/LIMIT
                    color = (on || isPeak) ? 0xFFFF2222 : 0xFF2A0808;
                } else if (seg >= 7) { // Yellow -3dB
                    color = (on || isPeak) ? 0xFFFFBB11 : 0xFF2B1F04;
                } else { // Green -6dB down
                    color = (on || isPeak) ? 0xFF00FF55 : 0xFF08260E;
                }

                g.fill(barX, segY, barX + 11, segY + ledH - 1, color);
                if (on || isPeak) {
                    // Bright center core for realistic glow
                    g.fill(barX + 2, segY + 2, barX + 9, segY + ledH - 3, 0x55FFFFFF);
                }
            }

            // Channel labels
            String label = switch (ch) {
                case 0 -> "CH1";
                case 1 -> "CH2";
                case 2 -> "SUB";
                default -> "DLY";
            };
            g.drawString(font, label, barX, vuY + 112, 0x888888, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A slider that sends at most once a tick and applies its value here straight away. */
    private class Slider extends AbstractSliderButton {
        private final byte action;
        private boolean dirty;

        Slider(int x, int y, int width, int height, byte action, double value) {
            super(x, y, width, height, Component.empty(), value);
            this.action = action;
            updateMessage();
        }

        private float wire() {
            return switch (action) {
                case AmpControl.CROSSOVER -> (float) hz(value);
                case AmpControl.DELAY_MS -> (float) (value * 50.0);
                default -> (float) value;
            };
        }

        @Override
        protected void updateMessage() {
            setMessage(switch (action) {
                case AmpControl.CROSSOVER -> Component.translatable("createbrewery.amp.crossover", Math.round(hz(value)));
                case AmpControl.SUB_GAIN -> Component.translatable("createbrewery.amp.sub_gain", db((float) value));
                case AmpControl.DELAY_MS -> {
                    float ms = (float) (value * 50.0);
                    float m = ms * 0.343f;
                    yield Component.literal(String.format("Delay: %.1f ms (%.1f m)", ms, m));
                }
                default -> Component.translatable("createbrewery.amp.top_gain", db((float) value));
            });
        }

        private static String db(float knob) {
            float g = DeckFx.eqGain(knob);
            return g <= 0.001f ? "-inf" : String.format("%+.1f", 20 * Math.log10(g));
        }

        @Override
        protected void applyValue() {
            dirty = true;
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            float v = wire();
            AmpControl.send(pos, action, v);
            AmpRackBlockEntity r = rack();
            if (r != null) AmpControl.apply(r, action, v);
        }
    }
}
