package com.createbrewery.block.club;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/** The console's patch list: every linked light with its group, and arrows to move it to another group. */
class DmxPatchScreen extends Screen {
    private static final int W = 300, ROWS = 10, ROW_H = 16, H = 46 + ROWS * ROW_H;

    private final BlockPos console;
    private List<DmxPatch.Light> lights = List.of();
    private int page, left, top;
    private Button prev, next;
    private final Button[] down = new Button[ROWS], up = new Button[ROWS];

    DmxPatchScreen(BlockPos console) {
        super(Component.translatable("createbrewery.dmx.patch.title"));
        this.console = console;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        for (int i = 0; i < ROWS; i++) {
            int row = i, y = top + 24 + i * ROW_H;
            down[i] = addRenderableWidget(Button.builder(Component.literal("◀"), b -> move(row, -1)).bounds(left + W - 62, y, 16, 14).build());
            up[i] = addRenderableWidget(Button.builder(Component.literal("▶"), b -> move(row, 1)).bounds(left + W - 22, y, 16, 14).build());
        }
        prev = addRenderableWidget(Button.builder(Component.literal("◀"), b -> { page--; update(); }).bounds(left + 6, top + H - 20, 20, 14).build());
        next = addRenderableWidget(Button.builder(Component.literal("▶"), b -> { page++; update(); }).bounds(left + 30, top + H - 20, 20, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(left + W - 66, top + H - 20, 60, 14).build());
        update();
    }

    private void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.canInteractWithBlock(console, 1.0)
            || !(mc.level.getBlockEntity(console) instanceof DmxConsoleBlockEntity)) {
            mc.setScreen(null);
            return;
        }
        lights = DmxPatch.linkedTo(mc.level, console);
        int pages = Math.max(1, (lights.size() + ROWS - 1) / ROWS);
        page = Math.floorMod(page, pages);
        prev.visible = next.visible = pages > 1;
        for (int i = 0; i < ROWS; i++) down[i].visible = up[i].visible = page * ROWS + i < lights.size();
    }

    private void move(int row, int step) {
        int i = page * ROWS + row;
        if (i >= lights.size()) return;
        DmxPatch.Light l = lights.get(i);
        int group = Math.floorMod(l.group() + step, DmxProgram.GROUPS);
        DmxPatch.send(console, l.pos(), group);
        // Shown at once; the light's own update confirms it a tick later.
        lights.set(i, new DmxPatch.Light(l.pos(), l.block(), group));
    }

    @Override
    public void tick() {
        // Each light's group comes with its block update; read them again now and then.
        if (minecraft != null && minecraft.player != null && minecraft.player.tickCount % 10 == 0) update();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
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
        for (int row = 0; row < ROWS; row++) {
            int i = page * ROWS + row;
            if (i >= lights.size()) break;
            DmxPatch.Light l = lights.get(i);
            int y = top + 24 + row * ROW_H + 3;
            int dist = (int) Math.round(Math.sqrt(l.pos().distSqr(console)));
            g.drawString(font, l.block().getName(), left + 8, y, 0xE0E0E0);
            g.drawString(font, Component.translatable("createbrewery.dmx.patch.where", l.pos().getX(), l.pos().getY(), l.pos().getZ(), dist),
                left + 110, y, 0x888888);
            int colour = minecraft.level.getBlockEntity(console) instanceof DmxConsoleBlockEntity c ? 0xFF000000 | c.program.color[l.group()] : 0xFFFFFFFF;
            g.fill(left + W - 44, y - 3, left + W - 24, y + 11, 0xFF202028);
            g.drawCenteredString(font, String.valueOf(l.group() + 1), left + W - 34, y, colour);
        }
        int pages = Math.max(1, (lights.size() + ROWS - 1) / ROWS);
        if (pages > 1) g.drawString(font, (page + 1) + "/" + pages, left + 56, top + H - 17, 0x888888);
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
