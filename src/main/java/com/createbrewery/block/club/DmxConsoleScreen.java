package com.createbrewery.block.club;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The DMX desk: per group a colour, a fader and a flash button; on the right the program, the
 * moving-head pattern, the chase rate, blackout, four scenes (click to recall, shift-click to
 * store) and the master. Every move goes to the server (see {@link DmxControl}); what is shown is
 * read back from the console. Client only.
 */
public class DmxConsoleScreen extends Screen {
    private static final int BASE_W = 380, EXTRA = 100, W = BASE_W + EXTRA, H = 200, COL = 30, PANEL = 256, PW = BASE_W - PANEL - 8;
    private static final String[] PROGRAMS = {"manual", "auto", "chase"};
    private static final String[] MOVES = {"circle", "figure8", "sweep", "ballyhoo", "crowd", "straight", "fan", "mirror"};
    private static final String[] COLOR_FX = {"static", "fade", "rainbow", "complement"};
    private static final String[] GOBOS = {"circle", "star", "dots", "bar"};
    private static final String[] ZOOMS = {"narrow", "normal", "wide"};

    private final BlockPos pos;
    private int left, top;
    private final VFader[] faders = new VFader[DmxProgram.GROUPS];
    private final FlashPad[] pads = new FlashPad[DmxProgram.GROUPS];
    private VFader master;
    private Button program, move, rate, blackout, record, colorFx, gobo, prism, zoom;
    private final Button[] scenes = new Button[DmxProgram.SCENES];

    private DmxConsoleScreen(BlockPos pos) {
        super(Component.translatable("createbrewery.dmx.console"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DmxConsoleScreen(pos));
    }

    private DmxConsoleBlockEntity console() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof DmxConsoleBlockEntity c ? c : null;
    }

    @Override
    protected void init() {
        left = Math.max(0, (width - W) / 2);
        // At the bottom of the screen, so the lights it runs stay in view above it.
        top = Math.max(0, height - H - 6);
        for (int g = 0; g < DmxProgram.GROUPS; g++) {
            int group = g, x = left + 8 + g * COL;
            addRenderableWidget(new Swatch(x, top + 30, group));
            faders[g] = addRenderableWidget(new VFader(x, top + 44, 24, 110, DmxControl.FADER, g));
            pads[g] = addRenderableWidget(new FlashPad(x, top + 158, group));
        }
        int px = left + PANEL, pw = PW;
        program = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.PROGRAM, s -> s.program)).bounds(px, top + 18, pw, 16).build());
        move = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.MOVE, s -> s.move)).bounds(px, top + 36, pw, 16).build());
        rate = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.RATE, s -> s.rate)).bounds(px, top + 54, pw, 16).build());
        blackout = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c != null) DmxControl.send(pos, DmxControl.BLACKOUT, 0, c.settings.blackout ? 0f : 1f);
        }).bounds(px, top + 72, pw, 16).build());
        int half = (pw - 2) / 2;
        for (int i = 0; i < DmxProgram.SCENES; i++) {
            int scene = i;
            scenes[i] = addRenderableWidget(Button.builder(Component.empty(),
                    b -> DmxControl.send(pos, hasShiftDown() ? DmxControl.STORE : DmxControl.RECALL, scene, 0f))
                .bounds(px + (i % 2) * (half + 2), top + 92 + (i / 2) * 18, half, 16)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("createbrewery.dmx.scene_hint")))
                .build());
        }
        master = addRenderableWidget(new VFader(px + (pw - 20) / 2, top + 140, 20, 50, DmxControl.MASTER, 0));
        // Timecode: records the desk's moves to the song playing; shift-click forgets that song's show.
        record = addRenderableWidget(Button.builder(Component.empty(), b -> {
                DmxConsoleBlockEntity c = console();
                if (c == null) return;
                if (hasShiftDown()) DmxControl.send(pos, DmxControl.CLEAR_SHOW, 0, 0f);
                else DmxControl.send(pos, DmxControl.RECORD, 0, c.recording ? 0f : 1f);
            }).bounds(px, top + 140, 40, 16)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("createbrewery.dmx.record_hint")))
            .build());
        int px2 = left + BASE_W + 4, pw2 = EXTRA - 8;
        colorFx = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.COLORFX, s -> s.colorFx)).bounds(px2, top + 18, pw2, 16).build());
        gobo = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.GOBO, s -> s.gobo)).bounds(px2, top + 36, pw2, 16).build());
        prism = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c != null) DmxControl.send(pos, DmxControl.PRISM, 0, c.settings.prism ? 0f : 1f);
        }).bounds(px2, top + 54, pw2, 16).build());
        zoom = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.ZOOM, s -> s.zoom)).bounds(px2, top + 72, pw2, 16).build());
        refresh();
    }

    private void cycle(byte action, java.util.function.ToIntFunction<DmxProgram.Settings> current) {
        DmxConsoleBlockEntity c = console();
        if (c != null) DmxControl.send(pos, action, 0, current.applyAsInt(c.settings) + 1);
    }

    /** Shows what the console says, and closes once it is gone or out of reach. */
    private void refresh() {
        DmxConsoleBlockEntity c = console();
        if (c == null || minecraft.player == null || !minecraft.player.canInteractWithBlock(pos, 1.0)) {
            onClose();
            return;
        }
        DmxProgram.Settings s = c.settings;
        for (int g = 0; g < DmxProgram.GROUPS; g++) faders[g].show(s.faders[g]);
        master.show(s.master);
        program.setMessage(Component.translatable("createbrewery.dmx.program." + PROGRAMS[s.program]));
        move.setMessage(Component.translatable("createbrewery.dmx.move." + MOVES[s.move]));
        rate.setMessage(Component.translatable("createbrewery.dmx.rate", DmxProgram.RATES[s.rate]));
        colorFx.setMessage(Component.translatable("createbrewery.dmx.colorfx." + COLOR_FX[s.colorFx]));
        gobo.setMessage(Component.translatable("createbrewery.dmx.gobo." + GOBOS[s.gobo]));
        prism.setMessage(Component.translatable("createbrewery.dmx.prism", CommonComponents.optionStatus(s.prism)));
        zoom.setMessage(Component.translatable("createbrewery.dmx.zoom." + ZOOMS[s.zoom]));
        blackout.setMessage(Component.translatable("createbrewery.dmx.blackout", CommonComponents.optionStatus(s.blackout)));
        record.setMessage(Component.translatable("createbrewery.dmx.record").withStyle(c.recording ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.RESET));
        for (int i = 0; i < DmxProgram.SCENES; i++) {
            scenes[i].setMessage(Component.literal((s.scenes[i] != null ? "● " : "") + (i + 1)));
        }
    }

    @Override
    public void tick() {
        refresh();
        if (console() == null) return;
        for (VFader f : faders) f.flush();
        master.flush();
        for (FlashPad p : pads) p.tick();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Let go of a flash pad even with the mouse moved off it.
        for (FlashPad p : pads) p.letGo();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        for (FlashPad p : pads) if (p != null) p.letGo();
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No blur or dimming of the world: you work the desk watching the lights.
        g.fill(left, top, left + W, top + H, 0xE0101014);
        g.renderOutline(left, top, W, H, 0xFF3A3A48);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + W / 2, top + 6, 0xFFFFFF);
        DmxConsoleBlockEntity c = console();
        if (c == null) return;
        for (int i = 0; i < DmxProgram.GROUPS; i++) {
            int x = left + 8 + i * COL;
            // The group's live output, as a little meter under its number.
            int out = (int) (c.program.level[i] * 22);
            g.drawCenteredString(font, String.valueOf(i + 1), x + 12, top + 18, 0xAAAAAA);
            g.fill(x + 1, top + 27, x + 1 + out, top + 28, 0xFF000000 | c.program.color[i]);
        }
        g.drawString(font, Component.translatable("createbrewery.dmx.cues", c.cues), left + PANEL, top + 160, c.recording ? 0xFF6060 : 0x888888);
        g.drawCenteredString(font, Component.translatable("createbrewery.dmx.master"), left + PANEL + PW / 2, top + 130, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A vertical fader that sends its moves at most once a tick and follows the console while it is not held. */
    private class VFader extends AbstractWidget {
        private final byte action;
        private final int index;
        private double value;
        private boolean held, dirty;

        VFader(int x, int y, int w, int h, byte action, int index) {
            super(x, y, w, h, Component.empty());
            this.action = action;
            this.index = index;
        }

        void show(double v) {
            if (!held && !dirty) value = v;
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            DmxControl.send(pos, action, index, (float) value);
        }

        private void set(double mouseY) {
            value = Math.max(0, Math.min(1, 1 - (mouseY - getY() - 4) / (height - 8)));
            dirty = true;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            held = true;
            set(mouseY);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            set(mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            held = false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (!isHovered()) return false;
            value = Math.max(0, Math.min(1, value + scrollY * 0.05));
            dirty = true;
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int cx = getX() + width / 2, y0 = getY() + 4, y1 = getY() + height - 4;
            g.fill(cx - 1, y0, cx + 1, y1, 0xFF2A2A34);
            int knob = (int) (y1 - value * (y1 - y0));
            g.fill(cx - 1, knob, cx + 1, y1, 0xFF4A90FF);
            g.fill(getX() + 2, knob - 3, getX() + width - 2, knob + 3, isHoveredOrFocused() ? 0xFFE0E0E0 : 0xFFB0B0B8);
            g.fill(getX() + 2, knob, getX() + width - 2, knob + 1, 0xFF202028);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            out.add(NarratedElementType.TITLE, Component.literal(Math.round(value * 100) + "%"));
        }
    }

    /** A group's colour: click for the next one on the palette. */
    private class Swatch extends AbstractWidget {
        private final int group;

        Swatch(int x, int y, int group) {
            super(x, y, 24, 10, Component.translatable("createbrewery.dmx.color"));
            this.group = group;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            DmxConsoleBlockEntity c = console();
            if (c != null) DmxControl.send(pos, DmxControl.COLOR, group, c.settings.colors[group] + 1);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            DmxConsoleBlockEntity c = console();
            int col = c == null ? 0 : DmxProgram.PALETTE[c.settings.colors[group]];
            g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000 | col);
            g.renderOutline(getX(), getY(), width, height, isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF3A3A48);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** Full on while held. */
    private class FlashPad extends AbstractWidget {
        private final int group;
        private boolean pressed;
        private int ticks;

        FlashPad(int x, int y, int group) {
            super(x, y, 24, 16, Component.translatable("createbrewery.dmx.flash"));
            this.group = group;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            pressed = true;
            ticks = 0;
            DmxControl.send(pos, DmxControl.FLASH, group, 1f);
        }

        /** The console lets go on its own unless reminded (see DmxConsoleBlockEntity.FLASH_HOLD). */
        void tick() {
            if (pressed && ++ticks % 4 == 0) DmxControl.send(pos, DmxControl.FLASH, group, 1f);
        }

        void letGo() {
            if (!pressed) return;
            pressed = false;
            DmxControl.send(pos, DmxControl.RELEASE, group, 0f);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(getX(), getY(), getX() + width, getY() + height, pressed ? 0xFFF0F0F0 : isHoveredOrFocused() ? 0xFF505060 : 0xFF303040);
            g.drawCenteredString(Minecraft.getInstance().font, "F", getX() + width / 2, getY() + 4, pressed ? 0x101010 : 0xE0E0E0);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
