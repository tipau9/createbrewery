package com.createbrewery.block.club;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The console's patch list: every linked light with its group and arrows to move it to another;
 * for fixtures also their brightness and whether pan and tilt run the other way. Click a light's
 * name to find it: it blinks white, rings and sends up a column of light.
 */
class DmxPatchScreen extends Screen {
    private static final int W = 400, ROWS = 10, ROW_H = 16, H = 46 + ROWS * ROW_H;
    /** Where the name ends: clicking left of it shows the light. */
    private static final int NAME_END = 214;

    private final BlockPos console;
    private List<DmxPatch.Light> lights = new ArrayList<>();
    private int page, left, top;
    private Button prev, next;
    private final Button[] down = new Button[ROWS], up = new Button[ROWS], dimDown = new Button[ROWS], dimUp = new Button[ROWS],
        pan = new Button[ROWS], tilt = new Button[ROWS];
    /** The light picked to be shown, and for how many more ticks. */
    private BlockPos shown;
    private int shownTicks;

    DmxPatchScreen(BlockPos console) {
        super(Component.translatable("createbrewery.dmx.patch.title"));
        this.console = console;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        Tooltip dimTip = Tooltip.create(Component.translatable("createbrewery.dmx.patch.dim.tip"));
        for (int i = 0; i < ROWS; i++) {
            int row = i, y = top + 24 + i * ROW_H;
            dimDown[i] = addRenderableWidget(Button.builder(Component.literal("-"), b -> dim(row, -1)).bounds(left + 220, y, 12, 14).tooltip(dimTip).build());
            dimUp[i] = addRenderableWidget(Button.builder(Component.literal("+"), b -> dim(row, 1)).bounds(left + 264, y, 12, 14).tooltip(dimTip).build());
            pan[i] = addRenderableWidget(Button.builder(Component.literal("P"), b -> invert(row, 1)).bounds(left + 282, y, 16, 14)
                .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.patch.invert_pan.tip"))).build());
            tilt[i] = addRenderableWidget(Button.builder(Component.literal("T"), b -> invert(row, 2)).bounds(left + 300, y, 16, 14)
                .tooltip(Tooltip.create(Component.translatable("createbrewery.dmx.patch.invert_tilt.tip"))).build());
            down[i] = addRenderableWidget(Button.builder(Component.literal("◀"), b -> move(row, -1)).bounds(left + W - 62, y, 16, 14).build());
            up[i] = addRenderableWidget(Button.builder(Component.literal("▶"), b -> move(row, 1)).bounds(left + W - 22, y, 16, 14).build());
        }
        prev = addRenderableWidget(Button.builder(Component.literal("◀"), b -> { page--; labels(); }).bounds(left + 6, top + H - 20, 20, 14).build());
        next = addRenderableWidget(Button.builder(Component.literal("▶"), b -> { page++; labels(); }).bounds(left + 30, top + H - 20, 20, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(left + W - 66, top + H - 20, 60, 14).build());
        update();
    }

    /** Reads the linked lights again. */
    private void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.canInteractWithBlock(console, 1.0)
            || !(mc.level.getBlockEntity(console) instanceof DmxConsoleBlockEntity)) {
            mc.setScreen(null);
            return;
        }
        lights = new ArrayList<>(DmxPatch.linkedTo(mc.level, console));
        labels();
    }

    /** The page and the buttons, for the list as it is. */
    private void labels() {
        int pages = Math.max(1, (lights.size() + ROWS - 1) / ROWS);
        page = Math.floorMod(page, pages);
        prev.visible = next.visible = pages > 1;
        for (int i = 0; i < ROWS; i++) {
            DmxPatch.Light l = at(i);
            boolean fixture = l != null && l.fixture();
            down[i].visible = up[i].visible = l != null;
            dimDown[i].visible = dimUp[i].visible = pan[i].visible = tilt[i].visible = fixture;
            if (fixture) {
                pan[i].setMessage(Component.literal("P").withStyle((l.invert() & 1) != 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
                tilt[i].setMessage(Component.literal("T").withStyle((l.invert() & 2) != 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            }
        }
    }

    /** The row's light, or null past the end of the list. */
    private DmxPatch.Light at(int row) {
        int i = page * ROWS + row;
        return i < lights.size() ? lights.get(i) : null;
    }

    /** Shown at once; the light's own update confirms it a moment later. */
    private void replace(int row, DmxPatch.Light l) {
        lights.set(page * ROWS + row, l);
        labels();
    }

    private void move(int row, int step) {
        DmxPatch.Light l = at(row);
        if (l == null) return;
        int group = Math.floorMod(l.group() + step, DmxProgram.GROUPS);
        DmxPatch.send(console, l.pos(), DmxPatch.GROUP, group);
        replace(row, new DmxPatch.Light(l.pos(), l.block(), group, l.dim(), l.invert()));
    }

    private void dim(int row, int step) {
        DmxPatch.Light l = at(row);
        if (l == null || !l.fixture()) return;
        int dim = Math.max(1, Math.min(10, l.dim() + step));
        DmxPatch.send(console, l.pos(), DmxPatch.DIM, dim);
        replace(row, new DmxPatch.Light(l.pos(), l.block(), l.group(), dim, l.invert()));
    }

    private void invert(int row, int bit) {
        DmxPatch.Light l = at(row);
        if (l == null || !l.fixture()) return;
        int inv = l.invert() ^ bit;
        DmxPatch.send(console, l.pos(), DmxPatch.INVERT, inv);
        replace(row, new DmxPatch.Light(l.pos(), l.block(), l.group(), l.dim(), inv));
    }

    private void show(BlockPos light) {
        shown = light;
        shownTicks = 100;
        if (minecraft != null && minecraft.level != null) FixtureBlockEntity.identify(light, minecraft.level.getGameTime() + 100);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int row = (int) Math.floor((my - (top + 24)) / ROW_H);
        if (button == 0 && row >= 0 && row < ROWS && mx >= left + 4 && mx < left + NAME_END) {
            DmxPatch.Light l = at(row);
            if (l != null) {
                show(l.pos());
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void tick() {
        // Each light's settings come with its block update; read them again now and then.
        if (minecraft != null && minecraft.player != null && minecraft.player.tickCount % 10 == 0) update();
        if (shown == null || minecraft == null || minecraft.level == null) return;
        if (--shownTicks <= 0) {
            shown = null;
            return;
        }
        // Effects (strobes, lasers, fog) do not blink: the bell and the column of light find them.
        Level level = minecraft.level;
        Vec3 c = Vec3.atCenterOf(shown);
        if (shownTicks % 20 == 19) level.playLocalSound(c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, 1.8f, false);
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.END_ROD, c.x + (level.random.nextDouble() - 0.5) * 0.7, c.y + 0.6,
                c.z + (level.random.nextDouble() - 0.5) * 0.7, 0, 0.12, 0);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No blur: the picked light is seen blinking in the rig behind.
        g.fill(left, top, left + W, top + H, 0xE6101016);
        g.renderOutline(left, top, W, H, 0xFF353545);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, left + W / 2, top + 7, 0xFFFFFF);
        if (lights.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("createbrewery.dmx.patch.empty"), left + W / 2, top + 60, 0x888888);
        }
        DmxConsoleBlockEntity c = minecraft.level.getBlockEntity(console) instanceof DmxConsoleBlockEntity d ? d : null;
        Component tip = null;
        for (int row = 0; row < ROWS; row++) {
            DmxPatch.Light l = at(row);
            if (l == null) break;
            int y = top + 24 + row * ROW_H + 3;
            boolean hover = mouseX >= left + 4 && mouseX < left + NAME_END && mouseY >= y - 3 && mouseY < y + 11;
            if (l.pos().equals(shown)) g.fill(left + 4, y - 3, left + NAME_END, y + 11, 0x5040A0FF);
            else if (hover) g.fill(left + 4, y - 3, left + NAME_END, y + 11, 0x20FFFFFF);
            if (hover) tip = Component.translatable("createbrewery.dmx.patch.identify.tip");
            int dist = (int) Math.round(Math.sqrt(l.pos().distSqr(console)));
            g.drawString(font, l.block().getName(), left + 8, y, 0xE0E0E0);
            g.drawString(font, Component.translatable("createbrewery.dmx.patch.where", l.pos().getX(), l.pos().getY(), l.pos().getZ(), dist),
                left + 104, y, 0x888888);
            if (l.fixture()) g.drawCenteredString(font, l.dim() * 10 + "%", left + 248, y, l.dim() < 10 ? 0xF0C030 : 0xAAAAAA);
            int colour = c != null ? 0xFF000000 | c.program.color[l.group()] : 0xFFFFFFFF;
            g.fill(left + W - 44, y - 3, left + W - 24, y + 11, 0xFF202028);
            g.drawCenteredString(font, String.valueOf(l.group() + 1), left + W - 34, y, colour);
        }
        int pages = Math.max(1, (lights.size() + ROWS - 1) / ROWS);
        if (pages > 1) g.drawString(font, (page + 1) + "/" + pages, left + 56, top + H - 17, 0x888888);
        if (tip != null) g.renderTooltip(font, tip, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        DmxConsoleScreen.open(console);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
