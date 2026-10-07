package com.createbrewery.block.club;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The DMX desk:
 * - 8 group channels with Lee/Rosco gel swatches, vertical faders and flash pads.
 * - GrandMA3 bump / flasher / blinder / hazer section.
 * - Master fader and 16 scenes across 4 pages with smooth crossfade time and active cue highlighting.
 * - BPM sync locking to DJ booth or manual tap tempo with rolling average.
 * - 2D live stage visualizer rendering truss, fixtures, beams, gobos, prism, strobe, hazer smoke and blinder wash.
 * - Fixture attribute encoders and HSV / gel palette modal picker with Escape handling.
 */
public class DmxConsoleScreen extends Screen {
    private static final int W = 500, H = 216, COL = 27;
    private static final String[] PROGRAMS = {"manual", "auto", "chase"};
    private static final String[] MOVES = {"circle", "figure8", "sweep", "ballyhoo", "crowd", "straight", "fan", "mirror"};
    private static final String[] COLOR_FX = {"static", "fade", "rainbow", "complement"};
    private static final String[] GOBOS = {"circle", "star", "dots", "bar"};
    private static final String[] ZOOMS = {"narrow", "normal", "wide"};
    private static final float[] FADE_TIMES = {0.0f, 0.5f, 1.0f, 2.0f, 3.0f, 5.0f};

    private final BlockPos pos;
    private final DropToggle[] dropToggles = new DropToggle[DmxProgram.GROUPS];
    /** Per group, the linked lights in it by name; read again every second. */
    private final List<java.util.Map<String, Integer>> patch = new ArrayList<>();
    private int left, top;
    private final Swatch[] swatches = new Swatch[DmxProgram.GROUPS];
    private final VFader[] faders = new VFader[DmxProgram.GROUPS];
    private final FlashPad[] pads = new FlashPad[DmxProgram.GROUPS];
    private VFader master;

    // Bump & Master
    private Button blindAll, strobeAll, hazer, blackout;

    // Cue & Scene Playback
    private final Button[] pageButtons = new Button[DmxProgram.SCENE_PAGES];
    private final Button[] sceneButtons = new Button[DmxProgram.SCENES_PER_PAGE];
    private Button fadeTime, record, djSync, tapTempo;
    private int activeScene = -1;

    // Attributes & Color Picker
    private Button program, move, rate, colorFx, gobo, prism, zoom, gels;

    // Modal Gel & HSV color picker state
    private boolean gelPickerOpen;
    private int gelTargetGroup = 0; // -1 for ALL
    private boolean draggingHue;

    // Tap tempo history (rolling timestamps)
    private final List<Long> tapTimes = new ArrayList<>();

    // Hazer smoke animation in 2D visualizer
    private long hazerTriggerTime = 0;

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
        top = Math.max(0, height - H - 4);

        // 1. Group Faders (Left side)
        for (int g = 0; g < DmxProgram.GROUPS; g++) {
            int group = g, x = left + 8 + g * COL;
            swatches[g] = addRenderableWidget(new Swatch(x, top + 30, group));
            faders[g] = addRenderableWidget(new VFader(x, top + 42, 22, 104, DmxControl.FADER, g));
            pads[g] = addRenderableWidget(new FlashPad(x, top + 150, group));
            dropToggles[g] = addRenderableWidget(new DropToggle(x, top + 169, group));
        }
        addRenderableWidget(Button.builder(Component.translatable("createbrewery.dmx.patch"), b -> minecraft.setScreen(new DmxPatchScreen(pos)))
            .bounds(left + 8, top + 198, 49, 13)
            .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.patch.tip")))
            .build());
        readPatch();

        // 2. Master Fader
        master = addRenderableWidget(new VFader(left + 230, top + 42, 22, 104, DmxControl.MASTER, 0));

        // 3. Bump & Flasher section (GrandMA3 style)
        int px = left + 258;
        blindAll = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c != null) {
                boolean next = !c.settings.blindAll;
                c.settings.blindAll = next;
                DmxControl.send(pos, DmxControl.BLIND_ALL, 0, next ? 1f : 0f);
            }
        }).bounds(px, top + 24, 84, 15)
        .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.blind_all.tip")))
        .build());

        strobeAll = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c != null) {
                boolean next = !c.settings.strobeAll;
                c.settings.strobeAll = next;
                DmxControl.send(pos, DmxControl.STROBE_ALL, 0, next ? 1f : 0f);
            }
        }).bounds(px, top + 42, 84, 15)
        .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.strobe_all.tip")))
        .build());

        hazer = addRenderableWidget(Button.builder(Component.translatable("createbrewery.dmx.hazer"), b -> {
            hazerTriggerTime = System.currentTimeMillis();
            DmxControl.send(pos, DmxControl.HAZER, 0, 1f);
        }).bounds(px, top + 60, 84, 15)
        .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.hazer.tip")))
        .build());

        blackout = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c != null) {
                boolean next = !c.settings.blackout;
                c.settings.blackout = next;
                DmxControl.send(pos, DmxControl.BLACKOUT, 0, next ? 1f : 0f);
            }
        }).bounds(px, top + 78, 84, 15)
        .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.blackout.tip")))
        .build());

        // 4. Cue & Scene Playback (4 Pages x 4 Scenes)
        for (int p = 0; p < DmxProgram.SCENE_PAGES; p++) {
            int page = p;
            pageButtons[p] = addRenderableWidget(Button.builder(
                Component.translatable("createbrewery.dmx.page", page + 1),
                b -> {
                    DmxConsoleBlockEntity c = console();
                    if (c != null) c.settings.scenePage = page;
                    DmxControl.send(pos, DmxControl.SCENE_PAGE, 0, page);
                }
            ).bounds(px + p * 21, top + 98, 20, 13)
            .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.scene_page.tip", page + 1)))
            .build());
        }

        for (int i = 0; i < DmxProgram.SCENES_PER_PAGE; i++) {
            int slot = i;
            int bx = px + (slot % 2) * 44;
            int by = top + 114 + (slot / 2) * 16;
            sceneButtons[i] = addRenderableWidget(Button.builder(Component.empty(), b -> {
                DmxConsoleBlockEntity c = console();
                if (c == null) return;
                int scene = c.settings.scenePage * DmxProgram.SCENES_PER_PAGE + slot;
                if (!hasShiftDown()) activeScene = scene;
                DmxControl.send(pos, hasShiftDown() ? DmxControl.STORE : DmxControl.RECALL, scene, 0f);
            }).bounds(bx, by, 40, 14)
            .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.scene_hint")))
            .build());
        }

        fadeTime = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c == null) return;
            float cur = c.settings.fadeTime;
            int nextIdx = 0;
            for (int i = 0; i < FADE_TIMES.length; i++) {
                if (Math.abs(FADE_TIMES[i] - cur) < 0.1f) {
                    nextIdx = (i + 1) % FADE_TIMES.length;
                    break;
                }
            }
            float val = FADE_TIMES[nextIdx];
            c.settings.fadeTime = val;
            DmxControl.send(pos, DmxControl.FADE_TIME, 0, val);
        }).bounds(px, top + 148, 52, 14)
        .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.fade_time.tip")))
        .build());

        record = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c == null) return;
            if (hasShiftDown()) DmxControl.send(pos, DmxControl.CLEAR_SHOW, 0, 0f);
            else DmxControl.send(pos, DmxControl.RECORD, 0, c.recording ? 0f : 1f);
        }).bounds(px + 54, top + 148, 30, 14)
        .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.record_hint")))
        .build());

        djSync = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c != null) {
                boolean next = !c.settings.djSync;
                c.settings.djSync = next;
                DmxControl.send(pos, DmxControl.DJ_SYNC, 0, next ? 1f : 0f);
            }
        }).bounds(px, top + 165, 42, 14)
        .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.dj_sync.tip")))
        .build());

        tapTempo = addRenderableWidget(Button.builder(Component.empty(), b -> onTapTempo())
            .bounds(px + 44, top + 165, 40, 14)
            .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.tap.tip")))
            .build());

        // 5. Fixture Attributes (Right side)
        int px2 = left + 350;
        program = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.PROGRAM, s -> s.program)).bounds(px2, top + 110, 69, 15).build());
        move = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.MOVE, s -> s.move)).bounds(px2 + 73, top + 110, 69, 15).build());
        rate = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.RATE, s -> s.rate)).bounds(px2, top + 128, 69, 15).build());
        colorFx = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.COLORFX, s -> s.colorFx)).bounds(px2 + 73, top + 128, 69, 15).build());
        gobo = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.GOBO, s -> s.gobo)).bounds(px2, top + 146, 69, 15).build());
        prism = addRenderableWidget(Button.builder(Component.empty(), b -> {
            DmxConsoleBlockEntity c = console();
            if (c != null) {
                boolean next = !c.settings.prism;
                c.settings.prism = next;
                DmxControl.send(pos, DmxControl.PRISM, 0, next ? 1f : 0f);
            }
        }).bounds(px2 + 73, top + 146, 69, 15).build());
        zoom = addRenderableWidget(Button.builder(Component.empty(), b -> cycle(DmxControl.ZOOM, s -> s.zoom)).bounds(px2, top + 164, 69, 15).build());
        gels = addRenderableWidget(Button.builder(Component.translatable("createbrewery.dmx.gels"), b -> openGelPicker(0))
            .bounds(px2 + 73, top + 164, 69, 15)
            .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.gels.tip")))
            .build());

        refresh();
    }

    private void onTapTempo() {
        long now = System.currentTimeMillis();
        if (!tapTimes.isEmpty() && (now - tapTimes.get(tapTimes.size() - 1)) > 2500) {
            tapTimes.clear();
        }
        tapTimes.add(now);
        if (tapTimes.size() > 4) tapTimes.remove(0);

        if (tapTimes.size() >= 2) {
            long totalDelta = tapTimes.get(tapTimes.size() - 1) - tapTimes.get(0);
            double avgDeltaMs = (double) totalDelta / (tapTimes.size() - 1);
            float bpm = (float) Math.round(60000.0 / Math.max(10.0, avgDeltaMs));
            bpm = Math.max(30f, Math.min(240f, bpm));
            DmxConsoleBlockEntity c = console();
            if (c != null) {
                c.settings.manualBpm = bpm;
                c.settings.djSync = false;
                refresh();
            }
            DmxControl.send(pos, DmxControl.TAP_TEMPO, 0, bpm);
        } else {
            tapTempo.setMessage(Component.literal("TAP...").withStyle(ChatFormatting.YELLOW));
        }
    }

    public void openGelPicker(int group) {
        this.gelTargetGroup = group;
        this.gelPickerOpen = true;
    }

    private void applyColor(int rgb) {
        DmxConsoleBlockEntity c = console();
        if (gelTargetGroup == -1) {
            for (int g = 0; g < DmxProgram.GROUPS; g++) {
                if (c != null) c.settings.colors[g] = rgb;
                DmxControl.send(pos, DmxControl.COLOR_RGB, g, rgb);
            }
        } else {
            if (c != null) c.settings.colors[gelTargetGroup] = rgb;
            DmxControl.send(pos, DmxControl.COLOR_RGB, gelTargetGroup, rgb);
        }
    }

    private void cycle(byte action, java.util.function.ToIntFunction<DmxProgram.Settings> current) {
        DmxConsoleBlockEntity c = console();
        if (c != null) DmxControl.send(pos, action, 0, current.applyAsInt(c.settings) + 1);
    }

    private void refresh() {
        DmxConsoleBlockEntity c = console();
        if (c == null || minecraft.player == null || !minecraft.player.canInteractWithBlock(pos, 1.0)) {
            onClose();
            return;
        }
        DmxProgram.Settings s = c.settings;
        for (int g = 0; g < DmxProgram.GROUPS; g++) faders[g].show(s.faders[g]);
        master.show(s.master);

        blindAll.setMessage(Component.literal(s.blindAll ? "[BLIND ALL]" : "BLIND ALL")
            .withStyle(s.blindAll ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        strobeAll.setMessage(Component.literal(s.strobeAll ? "[STROBE ALL]" : "STROBE ALL")
            .withStyle(s.strobeAll ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        blackout.setMessage(Component.literal(s.blackout ? "[B.O. ON]" : "BLACKOUT")
            .withStyle(s.blackout ? ChatFormatting.RED : ChatFormatting.GRAY));

        for (int p = 0; p < DmxProgram.SCENE_PAGES; p++) {
            boolean active = s.scenePage == p;
            pageButtons[p].setMessage(Component.literal(active ? ">P" + (p + 1) + "<" : "P" + (p + 1))
                .withStyle(active ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        }

        for (int i = 0; i < DmxProgram.SCENES_PER_PAGE; i++) {
            int scene = s.scenePage * DmxProgram.SCENES_PER_PAGE + i;
            boolean stored = s.scenes[scene] != null;
            boolean active = activeScene == scene;
            String prefix = stored ? (active ? "▶ " : "● ") : "";
            sceneButtons[i].setMessage(Component.literal(prefix + (scene + 1))
                .withStyle(active ? ChatFormatting.AQUA : stored ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }

        fadeTime.setMessage(s.fadeTime <= 0.05f
            ? Component.translatable("createbrewery.dmx.fade_time", Component.translatable("createbrewery.dmx.fade_snap"))
            : Component.translatable("createbrewery.dmx.fade_time", String.format(java.util.Locale.ROOT, "%.1fs", s.fadeTime)));

        record.setMessage(Component.translatable("createbrewery.dmx.record")
            .withStyle(c.recording ? ChatFormatting.RED : ChatFormatting.RESET));

        djSync.setMessage(Component.literal(s.djSync ? "SYNC ON" : "MANUAL")
            .withStyle(s.djSync ? ChatFormatting.GREEN : ChatFormatting.GRAY));

        if (tapTimes.size() < 2) {
            tapTempo.setMessage(Component.literal(String.format(java.util.Locale.ROOT, "TAP %d", Math.round(s.manualBpm))));
        }

        program.setMessage(Component.translatable("createbrewery.dmx.program." + PROGRAMS[s.program]));
        move.setMessage(Component.translatable("createbrewery.dmx.move." + MOVES[s.move]));
        rate.setMessage(Component.translatable("createbrewery.dmx.rate", DmxProgram.RATES[s.rate]));
        colorFx.setMessage(Component.translatable("createbrewery.dmx.colorfx." + COLOR_FX[s.colorFx]));
        gobo.setMessage(Component.translatable("createbrewery.dmx.gobo." + GOBOS[s.gobo]));
        prism.setMessage(Component.translatable("createbrewery.dmx.prism", CommonComponents.optionStatus(s.prism)));
        zoom.setMessage(Component.translatable("createbrewery.dmx.zoom." + ZOOMS[s.zoom]));
    }

    @Override
    public void tick() {
        DmxConsoleBlockEntity c = console();
        if (c != null) c.output();
        refresh();
        if (c == null) return;
        for (VFader f : faders) f.flush();
        master.flush();
        for (FlashPad p : pads) p.tick();
        if (minecraft.player.tickCount % 20 == 0) readPatch();
    }

    private void readPatch() {
        patch.clear();
        for (int g = 0; g < DmxProgram.GROUPS; g++) patch.add(new java.util.TreeMap<>());
        if (minecraft == null || minecraft.level == null) return;
        for (DmxPatch.Light l : DmxPatch.linkedTo(minecraft.level, pos)) {
            patch.get(l.group()).merge(l.block().getName().getString(), 1, Integer::sum);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingHue = false;
        for (FlashPad p : pads) p.letGo();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        for (FlashPad p : pads) if (p != null) p.letGo();
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (gelPickerOpen && keyCode == 256) { // GLFW_KEY_ESCAPE closes modal
            gelPickerOpen = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(left, top, left + W, top + H, 0xE6101016);
        g.renderOutline(left, top, W, H, 0xFF353545);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + W / 2, top + 6, 0xFFFFFF);

        DmxConsoleBlockEntity c = console();
        if (c == null) return;

        // Fader group headers & live meters
        for (int i = 0; i < DmxProgram.GROUPS; i++) {
            int x = left + 8 + i * COL;
            int out = (int) (c.program.level[i] * 22);
            g.drawCenteredString(font, String.valueOf(i + 1), x + 11, top + 18, 0xAAAAAA);
            g.fill(x, top + 27, x + 22, top + 28, 0xFF202028);
            if (out > 0) {
                g.fill(x, top + 27, x + Math.min(22, out), top + 28, 0xFF000000 | c.program.color[i]);
            }
        }

        // Lights per group, under its drop toggle; hovering lists them.
        for (int i = 0; i < DmxProgram.GROUPS && i < patch.size(); i++) {
            int x = left + 8 + i * COL, n = 0;
            for (int count : patch.get(i).values()) n += count;
            g.drawCenteredString(font, String.valueOf(n), x + 11, top + 185, n > 0 ? 0xCCCCCC : 0x555555);
            if (mouseX >= x && mouseX < x + 22 && mouseY >= top + 183 && mouseY < top + 195) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("createbrewery.dmx.group_lights", i + 1));
                patch.get(i).forEach((name, count) -> lines.add(Component.literal(count + "× " + name).withStyle(ChatFormatting.GRAY)));
                if (n == 0) lines.add(Component.translatable("createbrewery.dmx.group_empty").withStyle(ChatFormatting.DARK_GRAY));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }

        // Master label
        g.drawCenteredString(font, Component.translatable("createbrewery.dmx.master"), left + 241, top + 18, 0xAAAAAA);
        g.drawCenteredString(font, Math.round(c.settings.master * 100) + "%", left + 241, top + 152, 0x888888);

        // Cue count string
        g.drawString(font, Component.translatable("createbrewery.dmx.cues", c.cues), left + 258, top + 184, c.recording ? 0xFF6060 : 0x888888);

        // 2D Stage Visualizer window (upper right)
        // Thousands of one-pixel fills: in performance mode one draw call instead of one each.
        if (com.createbrewery.Config.performance()) g.drawManaged(() -> renderStageVisualizer(g, left + 350, top + 20, 142, 84, c));
        else renderStageVisualizer(g, left + 350, top + 20, 142, 84, c);

        // Render modal Gel / HSV dialog if open
        if (gelPickerOpen) {
            renderGelPicker(g, mouseX, mouseY);
        }
    }

    private void renderStageVisualizer(GuiGraphics g, int vx, int vy, int vw, int vh, DmxConsoleBlockEntity c) {
        c.output();

        // 1. Dark stage background
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF0B0B12);
        g.renderOutline(vx, vy, vw, vh, 0xFF353545);

        // 2. Stage floor perspective lines
        int floorY = vy + 66;
        g.fill(vx + 1, floorY, vx + vw - 1, vy + vh - 1, 0xFF141420);
        int vpX = vx + vw / 2;
        for (int i = 0; i <= 6; i++) {
            float fx = vx + 10 + i * ((vw - 20) / 6.0f);
            drawLine(g, (int) fx, vy + vh - 2, (int) (vpX + (fx - vpX) * 0.4f), floorY, 0xFF222230);
        }
        g.fill(vx + 2, floorY, vx + vw - 2, floorY + 1, 0xFF2A2A3A);
        g.fill(vx + 2, floorY + 8, vx + vw - 2, floorY + 9, 0xFF252535);

        // 3. Stage Truss at top
        int trussY = vy + 10;
        g.fill(vx + 6, trussY - 2, vx + vw - 6, trussY - 1, 0xFF5A5A6E);
        g.fill(vx + 6, trussY + 4, vx + vw - 6, trussY + 5, 0xFF4A4A5C);
        for (int tx = vx + 8; tx < vx + vw - 16; tx += 12) {
            drawLine(g, tx, trussY - 2, tx + 12, trussY + 4, 0xFF3A3A4C);
            drawLine(g, tx + 12, trussY - 2, tx, trussY + 4, 0xFF3A3A4C);
        }

        // 4. Fixtures and Beams
        boolean blind = c.settings.blindAll;
        boolean strobe = c.settings.strobeAll;
        boolean bo = c.settings.blackout;
        int zoom = c.settings.zoom;
        float baseSpread = zoom == 0 ? 5.0f : zoom == 2 ? 18.0f : 11.0f;
        boolean prism = c.settings.prism;
        int gobo = c.settings.gobo;

        for (int i = 0; i < DmxProgram.GROUPS; i++) {
            float fx = vx + 12 + i * ((vw - 24) / 7.0f);
            float fy = trussY + 5;

            g.fill((int) fx - 3, (int) fy - 2, (int) fx + 3, (int) fy + 2, 0xFF2E2E3C);
            g.fill((int) fx - 2, (int) fy, (int) fx + 2, (int) fy + 3, 0xFF1A1A24);

            float lv = c.program.level[i];
            if (bo) lv = 0f;
            if (blind) lv = 1.0f;

            int rgb = c.program.color[i];
            if (blind) rgb = 0xFFE0A0;
            else if (strobe) rgb = 0xFFFFFF;

            int emitCol = lv > 0.05f ? (0xFF000000 | rgb) : 0xFF303038;
            g.fill((int) fx - 1, (int) fy + 2, (int) fx + 2, (int) fy + 4, emitCol);

            if (lv <= 0.02f) continue;

            float[] aim = c.program.aim(c.settings, i);
            float panRad = (float) Math.toRadians(aim[0]);
            float tiltRad = (float) Math.toRadians(aim[1]);

            float hitY = Math.min(vy + vh - 3, floorY + 4 + (float) Math.sin(tiltRad) * 10f);
            float distY = Math.max(10f, hitY - fy);
            float hitX = fx + (float) Math.tan(panRad) * distY * 0.85f;
            hitX = Math.max(vx + 4, Math.min(vx + vw - 4, hitX));

            if (prism) {
                renderBeamCone(g, fx, fy, hitX - 8, hitY, baseSpread * 0.6f, rgb, lv * 0.7f, gobo, vx, vy, vw, vh);
                renderBeamCone(g, fx, fy, hitX, hitY, baseSpread * 0.6f, rgb, lv * 0.7f, gobo, vx, vy, vw, vh);
                renderBeamCone(g, fx, fy, hitX + 8, hitY, baseSpread * 0.6f, rgb, lv * 0.7f, gobo, vx, vy, vw, vh);
            } else {
                renderBeamCone(g, fx, fy, hitX, hitY, baseSpread, rgb, lv, gobo, vx, vy, vw, vh);
            }
        }

        // 5. Blinder Wash overlay
        if (blind) {
            g.fill(vx + 1, vy + 1, vx + vw - 1, vy + vh - 1, 0x60FFE0A0);
        }
        // 6. Strobe flash overlay (20 Hz)
        if (strobe && (System.currentTimeMillis() / 50) % 2 == 0) {
            g.fill(vx + 1, vy + 1, vx + vw - 1, vy + vh - 1, 0x85FFFFFF);
        }

        // 7. Hazer smoke drift simulation
        long hazerAge = System.currentTimeMillis() - hazerTriggerTime;
        if (hazerAge < 3500) {
            float hazerAlpha = (1.0f - hazerAge / 3500f) * 0.35f;
            int smokeCol = ((int) (hazerAlpha * 255) << 24) | 0xD0E0E8;
            for (int s = 0; s < 4; s++) {
                int sx = vx + 15 + s * 30 + (int) ((hazerAge / 40) % 15);
                int sy = vy + vh - 10 - (int) ((hazerAge / 35) + s * 12) % (vh - 20);
                g.fill(sx, sy, sx + 22, sy + 6, smokeCol);
            }
        }

        // Overlay status indicators
        g.drawString(font, Component.translatable("createbrewery.dmx.stage_view"), vx + 4, vy + 3, 0x70A0A0B0, false);
        if (c.settings.djSync && c.program.beatPhase < 0.25f) {
            g.fill(vx + vw - 7, vy + 4, vx + vw - 3, vy + 8, 0xFF00FF88);
        }
    }

    private void renderBeamCone(GuiGraphics g, float x0, float y0, float x1, float y1, float baseSpread, int rgb, float level, int gobo, int vx, int vy, int vw, int vh) {
        int startY = (int) y0;
        int endY = (int) y1;
        if (endY <= startY) return;

        int r = (rgb >> 16) & 0xFF;
        int gr = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        float height = endY - startY;
        for (int y = startY; y <= endY; y += 2) {
            if (gobo == 2 && (y % 4 == 0)) continue;
            if (gobo == 3 && (y % 6 >= 3)) continue;

            float t = (y - startY) / height;
            float cx = Mth.lerp(t, x0, x1);
            float hw = Mth.lerp(t, 1.0f, baseSpread / 2.0f);
            if (gobo == 1 && (y % 2 != 0)) hw *= 1.3f;

            int lx = (int) Math.max(vx + 2, cx - hw);
            int rx = (int) Math.min(vx + vw - 2, cx + hw + 1);
            if (rx > lx) {
                int alpha = (int) (level * (25 + 35 * (1.0f - t * 0.4f)));
                int col = (alpha << 24) | (r << 16) | (gr << 8) | b;
                g.fill(lx, y, rx, y + 2, col);
            }
        }

        int spotAlpha = (int) (level * 160);
        int spotCol = (spotAlpha << 24) | (r << 16) | (gr << 8) | b;
        int hRadius = (int) (baseSpread * 0.65f);
        int spotX = (int) Math.max(vx + 3 + hRadius, Math.min(vx + vw - 3 - hRadius, x1));
        int spotY = (int) y1;
        g.fill(spotX - hRadius, spotY - 1, spotX + hRadius + 1, spotY + 2, spotCol);
        if (hRadius > 3) {
            int coreCol = (Math.min(255, spotAlpha + 60) << 24) | (r << 16) | (gr << 8) | b;
            g.fill(spotX - hRadius / 2, spotY, spotX + hRadius / 2 + 1, spotY + 1, coreCol);
        }
    }

    private void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int cx = x0, cy = y0;
        while (true) {
            g.fill(cx, cy, cx + 1, cy + 1, color);
            if (cx == x1 && cy == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; cx += sx; }
            if (e2 < dx) { err += dx; cy += sy; }
        }
    }

    private void renderGelPicker(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(left, top, left + W, top + H, 0xB8080810);

        int gw = 264, gh = 156;
        int gx = left + (W - gw) / 2;
        int gy = top + (H - gh) / 2;

        g.fill(gx, gy, gx + gw, gy + gh, 0xF4141420);
        g.renderOutline(gx, gy, gw, gh, 0xFF4A4A64);

        String grpLabel = gelTargetGroup == -1 ? "ALLE" : String.valueOf(gelTargetGroup + 1);
        g.drawString(font, "GEL & RGB-FARBE - GRUPPE " + grpLabel, gx + 10, gy + 8, 0xFFE0E0E0);

        // Close button [X]
        int closeX = gx + gw - 18, closeY = gy + 6;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        g.fill(closeX, closeY, closeX + 12, closeY + 12, closeHov ? 0xFF902020 : 0xFF353545);
        g.drawCenteredString(font, "✕", closeX + 6, closeY + 2, 0xFFFFFF);

        // Group selector tabs [1]..[8] and [ALL]
        int tabW = 22, tabH = 13;
        for (int gIdx = 0; gIdx < DmxProgram.GROUPS; gIdx++) {
            int tx = gx + 10 + gIdx * (tabW + 2);
            boolean sel = gelTargetGroup == gIdx;
            g.fill(tx, gy + 22, tx + tabW, gy + 22 + tabH, sel ? 0xFF355588 : 0xFF222230);
            g.renderOutline(tx, gy + 22, tabW, tabH, sel ? 0xFF6599DD : 0xFF3A3A4A);
            g.drawCenteredString(font, String.valueOf(gIdx + 1), tx + tabW / 2, gy + 25, sel ? 0xFFFFFF : 0xAAAAAA);
        }
        int allX = gx + 10 + 8 * (tabW + 2);
        boolean allSel = gelTargetGroup == -1;
        g.fill(allX, gy + 22, allX + tabW + 6, gy + 22 + tabH, allSel ? 0xFF355588 : 0xFF222230);
        g.renderOutline(allX, gy + 22, tabW + 6, tabH, allSel ? 0xFF6599DD : 0xFF3A3A4A);
        g.drawCenteredString(font, "ALL", allX + (tabW + 6) / 2, gy + 25, allSel ? 0xFFFFFF : 0xAAAAAA);

        DmxConsoleBlockEntity c = console();
        int curCol = c != null && gelTargetGroup >= 0 ? DmxProgram.resolveColor(c.settings.colors[gelTargetGroup]) : 0xFFFFFF;

        // 10 Gel chips
        String[] gelCodes = {"000", "116", "106", "105", "152", "139", "174", "128", "181", "101"};
        String[] gelLabels = {"White", "Tokyo", "Red", "Flame", "Amber", "Acid", "Blue", "Pink", "Congo", "Sun"};
        int chipW = 46, chipH = 20;
        for (int i = 0; i < DmxProgram.GEL_FILTERS.length; i++) {
            int r = i / 5, colIdx = i % 5;
            int cx = gx + 10 + colIdx * (chipW + 3);
            int cy = gy + 42 + r * (chipH + 3);
            int gelCol = DmxProgram.GEL_FILTERS[i];
            boolean activeGel = (curCol & 0xFFFFFF) == (gelCol & 0xFFFFFF);
            boolean hov = mouseX >= cx && mouseX < cx + chipW && mouseY >= cy && mouseY < cy + chipH;

            g.fill(cx, cy, cx + chipW, cy + chipH, hov ? 0xFF323244 : 0xFF1E1E2C);
            g.renderOutline(cx, cy, chipW, chipH, activeGel ? 0xFFFFD040 : hov ? 0xFFFFFFFF : 0xFF38384A);
            g.fill(cx + 2, cy + 2, cx + 14, cy + chipH - 2, 0xFF000000 | gelCol);
            g.renderOutline(cx + 2, cy + 2, 12, chipH - 4, 0xFF101018);
            g.drawString(font, gelCodes[i], cx + 16, cy + 3, activeGel ? 0xFFFFD040 : 0xFFFFFF, false);
            g.drawString(font, gelLabels[i], cx + 16, cy + 11, 0x888888, false);
        }

        // Rainbow Hue Bar
        int hx = gx + 10, hy = gy + 96, hw = 244, hh = 13;
        g.drawString(font, "HUE SLIDER (RGB FARBE)", hx, hy - 9, 0x888888, false);
        for (int x = 0; x < hw; x++) {
            float hue = (float) x / hw;
            g.fill(hx + x, hy, hx + x + 1, hy + hh, 0xFF000000 | DmxProgram.hsv(hue));
        }
        g.renderOutline(hx, hy, hw, hh, 0xFF606075);

        // Current color swatch and hex readout
        int previewX = hx + 184, previewY = hy + 18;
        g.fill(previewX, previewY, previewX + 16, previewY + 12, 0xFF000000 | curCol);
        g.renderOutline(previewX, previewY, 16, 12, 0xFFFFFFFF);
        g.drawString(font, String.format(java.util.Locale.ROOT, "HEX: #%06X", curCol & 0xFFFFFF), hx + 100, previewY + 2, 0xCCCCCC, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (gelPickerOpen) {
            int gw = 264, gh = 156;
            int gx = left + (W - gw) / 2;
            int gy = top + (H - gh) / 2;

            int closeX = gx + gw - 18, closeY = gy + 6;
            if (mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12) {
                gelPickerOpen = false;
                return true;
            }
            if (mouseX < gx || mouseX > gx + gw || mouseY < gy || mouseY > gy + gh) {
                gelPickerOpen = false;
                return true;
            }

            int tabW = 22, tabH = 13;
            for (int gIdx = 0; gIdx < DmxProgram.GROUPS; gIdx++) {
                int tx = gx + 10 + gIdx * (tabW + 2);
                if (mouseX >= tx && mouseX < tx + tabW && mouseY >= gy + 22 && mouseY < gy + 22 + tabH) {
                    gelTargetGroup = gIdx;
                    return true;
                }
            }
            int allX = gx + 10 + 8 * (tabW + 2);
            if (mouseX >= allX && mouseX < allX + tabW + 6 && mouseY >= gy + 22 && mouseY < gy + 22 + tabH) {
                gelTargetGroup = -1;
                return true;
            }

            int chipW = 46, chipH = 20;
            for (int i = 0; i < DmxProgram.GEL_FILTERS.length; i++) {
                int r = i / 5, colIdx = i % 5;
                int cx = gx + 10 + colIdx * (chipW + 3);
                int cy = gy + 42 + r * (chipH + 3);
                if (mouseX >= cx && mouseX < cx + chipW && mouseY >= cy && mouseY < cy + chipH) {
                    applyColor(DmxProgram.GEL_FILTERS[i]);
                    return true;
                }
            }

            int hx = gx + 10, hy = gy + 96, hw = 244, hh = 13;
            if (mouseX >= hx && mouseX < hx + hw && mouseY >= hy && mouseY < hy + hh) {
                draggingHue = true;
                float hue = (float) Math.max(0.0, Math.min(1.0, (mouseX - hx) / (double) hw));
                applyColor(DmxProgram.hsv(hue));
                return true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (gelPickerOpen && draggingHue) {
            int gw = 264;
            int gx = left + (W - gw) / 2;
            int hx = gx + 10, hw = 244;
            float hue = (float) Math.max(0.0, Math.min(1.0, (mouseX - hx) / (double) hw));
            applyColor(DmxProgram.hsv(hue));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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

    /** A group's colour swatch: left-click cycles palette, right-click opens Gel/RGB picker. */
    private class Swatch extends AbstractWidget {
        private final int group;

        Swatch(int x, int y, int group) {
            super(x, y, 22, 10, Component.translatable("createbrewery.dmx.color"));
            this.group = group;
            setTooltip(Tooltip.create(Component.translatable("createbrewery.dmx.group_color.tip", group + 1)));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isHovered()) return false;
            if (button == 1) { // Right click opens Gel picker
                openGelPicker(group);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            DmxConsoleBlockEntity c = console();
            if (c != null) {
                int cur = c.settings.colors[group];
                int next = (cur >= 0 && cur < DmxProgram.PALETTE.length) ? (cur + 1) % DmxProgram.PALETTE.length : 0;
                c.settings.colors[group] = next;
                DmxControl.send(pos, DmxControl.COLOR, group, 0f);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            DmxConsoleBlockEntity c = console();
            int col = c == null ? 0 : DmxProgram.resolveColor(c.settings.colors[group]);
            g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000 | col);
            g.renderOutline(getX(), getY(), width, height, isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF3A3A48);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    /** Full on while held. */
    /** D: the group flashes to full on every drop. */
    private class DropToggle extends AbstractWidget {
        private final int group;

        DropToggle(int x, int y, int group) {
            super(x, y, 22, 12, Component.literal("D"));
            this.group = group;
            setTooltip(Tooltip.create(Component.translatable("createbrewery.dmx.drop_flash.tip", group + 1)));
        }

        private boolean on() {
            DmxConsoleBlockEntity c = console();
            return c != null && (c.settings.dropFlash >> group & 1) != 0;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            DmxConsoleBlockEntity c = console();
            if (c == null) return;
            boolean next = !on();
            c.settings.dropFlash = next ? c.settings.dropFlash | 1 << group : c.settings.dropFlash & ~(1 << group);
            DmxControl.send(pos, DmxControl.DROP_FLASH, group, next ? 1f : 0f);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean on = on();
            g.fill(getX(), getY(), getX() + width, getY() + height, on ? 0xFFE0A030 : isHoveredOrFocused() ? 0xFF505060 : 0xFF303040);
            g.drawCenteredString(Minecraft.getInstance().font, "D", getX() + width / 2, getY() + 2, on ? 0x101010 : 0xA0A0A0);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    private class FlashPad extends AbstractWidget {
        private final int group;
        private boolean pressed;
        private int ticks;

        FlashPad(int x, int y, int group) {
            super(x, y, 22, 16, Component.translatable("createbrewery.dmx.flash"));
            this.group = group;
            setTooltip(Tooltip.create(Component.translatable("createbrewery.dmx.group_flash.tip", group + 1)));
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            pressed = true;
            ticks = 0;
            DmxControl.send(pos, DmxControl.FLASH, group, 1f);
        }

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
