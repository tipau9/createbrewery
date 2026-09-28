package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * The booth's mixer: per deck play/stop, pitch and tempo sync, a three-band kill EQ and filter, an
 * echo or reverb send, beat loops and the headphone cue; then the crossfader, drop, auto-mix and
 * eject. Every move goes to the server (see {@link DjControl}); what is shown is read back from the
 * booth, so an auto-mix fade moves the crossfader here too. Client only.
 */
public class DjMixerScreen extends Screen {
    private static final int W = 300, H = 262, COL = 138;
    private static final String[] EFFECTS = {"none", "echo", "reverb"};

    private final BlockPos pos;
    private int left, top;
    private Button[] play;
    private Fader[] pitch;
    private Fader crossfader;
    private Button automix;
    private Knob[][] knobs;
    private Button[] fx, cue;
    private Button[][] loops;

    private DjMixerScreen(BlockPos pos) {
        super(Component.translatable("createbrewery.dj.mixer"));
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
        play = new Button[2];
        pitch = new Fader[2];
        knobs = new Knob[2][];
        fx = new Button[2];
        cue = new Button[2];
        loops = new Button[2][DjBoothBlockEntity.LOOPS.length];
        for (int deck = DjBoothBlockEntity.A; deck <= DjBoothBlockEntity.B; deck++) {
            int d = deck, x = left + 8 + deck * (COL + 8);
            play[deck] = addRenderableWidget(Button.builder(Component.empty(),
                    b -> DjControl.send(pos, d == DjBoothBlockEntity.A ? DjControl.TOGGLE_A : DjControl.TOGGLE_B, 0f))
                .bounds(x, top + 54, COL, 20).build());
            pitch[deck] = addRenderableWidget(new Fader(x, top + 78, COL, d == DjBoothBlockEntity.A ? DjControl.PITCH_A : DjControl.PITCH_B, true));
            addRenderableWidget(Button.builder(Component.translatable("createbrewery.dj.sync"), b -> syncTempo(d))
                .bounds(x, top + 102, COL, 20).build());
            // Hi, mid, low, filter, and the effect send.
            int k = (COL - 5 * 26) / 4 + 26;
            knobs[deck] = new Knob[] {
                addRenderableWidget(new Knob(x, top + 136, DjControl.EQ_HIGH, d, "createbrewery.dj.eq_high", false)),
                addRenderableWidget(new Knob(x + k, top + 136, DjControl.EQ_MID, d, "createbrewery.dj.eq_mid", false)),
                addRenderableWidget(new Knob(x + 2 * k, top + 136, DjControl.EQ_LOW, d, "createbrewery.dj.eq_low", false)),
                addRenderableWidget(new Knob(x + 3 * k, top + 136, DjControl.FILTER, d, "createbrewery.dj.filter", true)),
                addRenderableWidget(new Knob(x + 4 * k, top + 136, DjControl.FX_AMOUNT, d, "createbrewery.dj.fx_amount", false)),
            };
            fx[deck] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    DjBoothBlockEntity dj = booth();
                    if (dj != null) DjControl.send(pos, DjControl.FX, d, dj.getFx(d) + 1);
                }).bounds(x, top + 168, COL - 44, 16).build());
            cue[deck] = addRenderableWidget(Button.builder(Component.translatable("createbrewery.dj.cue"), b -> MusicPulse.toggleCue(pos, d))
                .bounds(x + COL - 40, top + 168, 40, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("createbrewery.dj.cue_hint"))).build());
            int lw = (COL - 6) / 4;
            for (int i = 0; i < DjBoothBlockEntity.LOOPS.length; i++) {
                int beats = DjBoothBlockEntity.LOOPS[i];
                loops[deck][i] = addRenderableWidget(Button.builder(Component.empty(), b -> DjControl.send(pos, DjControl.LOOP, d, beats))
                    .bounds(x + i * (lw + 2), top + 188, lw, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("createbrewery.dj.loop_hint"))).build());
            }
        }
        crossfader = addRenderableWidget(new Fader(left + 8, top + 210, W - 16, DjControl.CROSSFADER, false));
        int third = (W - 16 - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("createbrewery.dj.drop"), b -> DjControl.send(pos, DjControl.DROP, 0f))
            .bounds(left + 8, top + 236, third, 20).build());
        automix = addRenderableWidget(Button.builder(Component.empty(), b -> {
                DjBoothBlockEntity dj = booth();
                if (dj != null) DjControl.send(pos, DjControl.AUTOMIX, dj.isAutomix() ? 0f : 1f);
            }).bounds(left + 12 + third, top + 236, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("createbrewery.dj.eject"), b -> DjControl.send(pos, DjControl.EJECT, 0f))
            .bounds(left + 16 + 2 * third, top + 236, third, 20).build());
        refresh();
    }

    /** Matches this deck's tempo to the other's, trying half and double time for one within the pitch fader's reach. */
    private void syncTempo(int deck) {
        DjBoothBlockEntity dj = booth();
        if (dj == null || minecraft == null || minecraft.player == null) return;
        // Heard periods: a deck's period shrinks as its pitch goes up.
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

    /** Shows what the booth says, and closes once it is gone or out of reach. */
    private void refresh() {
        DjBoothBlockEntity dj = booth();
        if (dj == null || minecraft.player == null || !minecraft.player.canInteractWithBlock(pos, 1.0)) {
            onClose();
            return;
        }
        long now = minecraft.level.getGameTime();
        for (int deck = DjBoothBlockEntity.A; deck <= DjBoothBlockEntity.B; deck++) {
            play[deck].setMessage(Component.translatable(dj.isPlaying(deck) ? "createbrewery.dj.stop" : "createbrewery.dj.play"));
            play[deck].active = !dj.getDisc(deck).isEmpty();
            pitch[deck].show((dj.getPitch(deck) - (1 - DjBoothBlockEntity.PITCH_RANGE)) / (2 * DjBoothBlockEntity.PITCH_RANGE));
            knobs[deck][0].show(dj.getEq(deck, DjBoothBlockEntity.HIGH));
            knobs[deck][1].show(dj.getEq(deck, DjBoothBlockEntity.MID));
            knobs[deck][2].show(dj.getEq(deck, DjBoothBlockEntity.LOW));
            knobs[deck][3].show((dj.getFilter(deck) + 1) / 2);
            knobs[deck][4].show(dj.getFxAmount(deck));
            fx[deck].setMessage(Component.translatable("createbrewery.dj.fx." + EFFECTS[dj.getFx(deck)]));
            cue[deck].setMessage(Component.translatable("createbrewery.dj.cue").withStyle(MusicPulse.isCued(pos, deck) ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.RESET));
            for (int i = 0; i < DjBoothBlockEntity.LOOPS.length; i++) {
                boolean on = dj.getLoopBeats(deck) == DjBoothBlockEntity.LOOPS[i];
                loops[deck][i].setMessage(Component.literal(String.valueOf(DjBoothBlockEntity.LOOPS[i])).withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RESET));
            }
        }
        crossfader.show(dj.crossfader(now));
        automix.setMessage(Component.translatable("createbrewery.dj.automix", CommonComponents.optionStatus(dj.isAutomix())));
    }

    @Override
    public void tick() {
        refresh();
        if (play == null) return;
        for (Fader f : pitch) f.flush();
        crossfader.flush();
        for (Knob[] row : knobs) for (Knob k : row) k.flush();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // Dark, like the rest of the pack's UI.
        g.fill(left, top, left + W, top + H, 0xE0101014);
        g.renderOutline(left, top, W, H, 0xFF3A3A48);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + W / 2, top + 6, 0xFFFFFF);
        DjBoothBlockEntity dj = booth();
        if (dj == null) return;
        for (int deck = DjBoothBlockEntity.A; deck <= DjBoothBlockEntity.B; deck++) {
            int x = left + 8 + deck * (COL + 8);
            ItemStack disc = dj.getDisc(deck);
            g.drawString(font, DjBoothBlockEntity.name(deck), x, top + 20, dj.isPlaying(deck) ? 0x55FF88 : 0xAAAAAA);
            String track = disc.isEmpty() ? Component.translatable("createbrewery.dj.empty").getString() : dj.title(disc).getString();
            g.drawString(font, font.plainSubstrByWidth(track, COL), x, top + 31, 0xFFFFFF);
            double period = MusicPulse.beatPeriodAt(dj.deckPos(deck));
            Component tempo = dj.isPlaying(deck) && period > 0
                ? Component.translatable("createbrewery.dj.bpm", bpm(period))
                : Component.translatable("createbrewery.dj.bpm_unknown");
            g.drawString(font, tempo, x, top + 42, 0x88AAFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * A rotary knob: drag up and down (or scroll), double-click to put it back in the middle. Sends
     * at most once a tick and follows the booth while not held. {@code bipolar}: the filter, whose
     * value on the wire is -1..1.
     */
    private class Knob extends net.minecraft.client.gui.components.AbstractWidget {
        private final byte action;
        private final int deck;
        private final boolean bipolar;
        private double value = 0.5, grabY, grabValue;
        private boolean held, dirty;
        private long lastClick;

        Knob(int x, int y, byte action, int deck, String label, boolean bipolar) {
            super(x, y, 26, 28, Component.translatable(label));
            this.action = action;
            this.deck = deck;
            this.bipolar = bipolar;
            if (action == DjControl.FX_AMOUNT) value = 0;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(label + ".hint")));
        }

        void show(double v) {
            if (!held && !dirty) value = v;
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            float v = (float) (bipolar ? value * 2 - 1 : value);
            DjControl.send(pos, action, deck, v);
            // Heard at once here, not after the server's answer: a kill or a sweep has to be on the beat.
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
            // A full turn over 120 pixels of mouse.
            set(grabValue + (grabY - mouseY) / 120.0);
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
            int cx = getX() + 13, cy = getY() + 10;
            // A ring of dots from 7 to 5 o'clock, lit up to the value (from the middle for bipolar knobs and kills-to-flat).
            int dots = 15;
            for (int i = 0; i < dots; i++) {
                double f = i / (double) (dots - 1);
                double a = Math.toRadians(135 + 270 * f);
                int px = cx + (int) Math.round(Math.cos(a) * 9), py = cy + (int) Math.round(Math.sin(a) * 9);
                boolean lit = bipolar || action != DjControl.FX_AMOUNT
                    ? (f >= Math.min(0.5, value) - 1e-6 && f <= Math.max(0.5, value) + 1e-6)
                    : f <= value + 1e-6;
                g.fill(px - 1, py - 1, px + 1, py + 1, lit ? 0xFF4A90FF : 0xFF2A2A34);
            }
            g.fill(cx - 5, cy - 5, cx + 5, cy + 5, isHoveredOrFocused() ? 0xFF606070 : 0xFF404050);
            double a = Math.toRadians(135 + 270 * value);
            for (int r = 1; r <= 5; r++) {
                int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
                g.fill(px, py, px + 1, py + 1, 0xFFFFFFFF);
            }
            var font = Minecraft.getInstance().font;
            g.drawCenteredString(font, getMessage(), cx, getY() + 20, value < 0.02 && action != DjControl.FX_AMOUNT && !bipolar ? 0xFF5050 : 0xAAAAAA);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {
            out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                Component.translatable("createbrewery.dj.knob_narration", getMessage(), Math.round(value * 100)));
        }
    }

    /** A fader that sends its moves at most once a tick and follows the booth while it is not held. */
    private class Fader extends AbstractSliderButton {
        private final byte action;
        private final boolean isPitch;
        private boolean held, dirty;

        Fader(int x, int y, int w, byte action, boolean isPitch) {
            super(x, y, w, 20, Component.empty(), 0.5);
            this.action = action;
            this.isPitch = isPitch;
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
            float v = (float) value;
            DjControl.send(pos, action, isPitch ? 1 - DjBoothBlockEntity.PITCH_RANGE + v * 2 * DjBoothBlockEntity.PITCH_RANGE : v);
        }

        @Override
        protected void updateMessage() {
            if (isPitch) {
                double percent = (value * 2 - 1) * DjBoothBlockEntity.PITCH_RANGE * 100;
                setMessage(Component.translatable("createbrewery.dj.pitch", String.format("%+.1f%%", percent)));
            } else {
                setMessage(Component.translatable("createbrewery.dj.crossfader"));
            }
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
