package com.createbrewery.block.club;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The booth's BROWSE screen, rekordbox style: playlists (the containers around the booth and the
 * shulker boxes in them) on the left, their records in a searchable, sortable table. The records
 * stay physical: loading one moves it from its container onto the deck.
 */
public class DjLibraryScreen extends Screen {
    private static final int W = 540, H = 276;
    private static final int SIDEBAR_W = 110, PLAYLIST_H = 12, ROW_H = 14, HEADER_Y = 24, FIRST_ROW_Y = 38;
    private static final int ROWS = (H - FIRST_ROW_Y - 6) / ROW_H;
    /** Column x from the table's left edge: #, title, artist, BPM, key, time; then the four load buttons. */
    private static final int[] COL_X = {4, 26, 180, 284, 320, 346};
    private static final String[] HEADERS = {"#", "TITLE", "ARTIST", "BPM", "KEY", "TIME"};
    private static final DjLibrary.Sort[] HEADER_SORT = {DjLibrary.Sort.INDEX, DjLibrary.Sort.TITLE, DjLibrary.Sort.ARTIST, DjLibrary.Sort.BPM, null, DjLibrary.Sort.LENGTH};
    private static final int COL_LOAD = 370, LOAD_W = 10, LOAD_STEP = 11;

    private final BlockPos pos;
    private int left, top, tableX;
    private EditBox search;
    /** -1: ALL TRACKS. */
    private int playlist = -1;
    private DjLibrary.Sort sort = DjLibrary.Sort.INDEX;
    private boolean descending;
    private int scroll, selected, ticks;
    /** What the table shows, as last drawn; clicks and keys act on it. */
    private List<DjLibrary.Entry> rows = List.of();

    private DjLibraryScreen(BlockPos pos) {
        super(Component.literal("BROWSE"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DjLibraryScreen(pos));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        tableX = left + 6 + SIDEBAR_W + 4;
        String query = search != null ? search.getValue() : "";
        search = addRenderableWidget(new EditBox(font, left + 120, top + 5, 200, 14, Component.literal("Search")));
        search.setHint(Component.literal("Search title / artist").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(q -> {
            scroll = 0;
            selected = 0;
        });
        addRenderableWidget(Button.builder(Component.literal("BACK"), b -> onClose()).bounds(left + W - 50, top + 5, 44, 14).build());
        DjLibrarySync.request(pos);
    }

    @Override
    public void tick() {
        // Gone or out of reach: the server would ignore every click, so close like a chest does.
        if (minecraft == null || minecraft.level == null || minecraft.player == null
            || !(minecraft.level.getBlockEntity(pos) instanceof DjBoothBlockEntity)
            || !minecraft.player.canInteractWithBlock(pos, 1.0)) {
            if (minecraft != null) minecraft.setScreen(null);
            return;
        }
        if (++ticks % 20 == 0) DjLibrarySync.request(pos);
    }

    @Override
    public void onClose() {
        DjMixerScreen.open(pos);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + H, 0xFF111317);
        g.renderOutline(left, top, W, H, 0xFF353944);
        g.fill(left + 6, top + HEADER_Y, left + 6 + SIDEBAR_W, top + H - 6, 0xFF0A0E16);
        g.fill(tableX, top + HEADER_Y, left + W - 6, top + H - 6, 0xFF080C14);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, "BROWSE", left + 8, top + 8, 0xFFFFCC00);
        int cx = tableX + (left + W - 6 - tableX) / 2;

        DjLibrary.Snapshot lib = DjLibrarySync.received(pos);
        if (lib == null) {
            g.drawCenteredString(font, "LOADING...", cx, top + 80, 0xFF888888);
            return;
        }
        if (lib.playlists().isEmpty()) {
            g.drawCenteredString(font, "NO RECORD CRATE AT BOOTH", cx, top + 80, 0xFFFF4444);
            g.drawCenteredString(font, "Put a chest or barrel next to the booth", cx, top + 94, 0xFF888888);
            return;
        }
        if (playlist >= lib.playlists().size()) playlist = -1;
        rows = DjLibrary.view(lib, playlist, search.getValue(), sort, descending);
        selected = Mth.clamp(selected, 0, Math.max(0, rows.size() - 1));
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - ROWS));

        renderSidebar(g, lib);
        for (int h = 0; h < HEADERS.length; h++) {
            boolean active = HEADER_SORT[h] == sort && sort != null;
            String label = HEADERS[h] + (active && h > 0 ? (descending ? " v" : " ^") : "");
            g.drawString(font, label, tableX + COL_X[h], top + HEADER_Y + 2, active ? 0xFFFFCC00 : 0xFF88AAFF);
        }
        g.drawString(font, "LOAD", tableX + COL_LOAD + 8, top + HEADER_Y + 2, 0xFF88AAFF);

        if (rows.isEmpty()) {
            g.drawCenteredString(font, search.getValue().isBlank() ? "PLAYLIST EMPTY" : "NO MATCHES", cx, top + 80, 0xFF888888);
            return;
        }
        for (int r = 0; r < ROWS; r++) {
            int idx = scroll + r;
            if (idx >= rows.size()) break;
            DjLibrary.Entry e = rows.get(idx);
            int ry = top + FIRST_ROW_Y + r * ROW_H;
            g.fill(tableX + 1, ry, left + W - 7, ry + ROW_H - 1, idx == selected ? 0xFF2A3A5A : (r % 2 == 0 ? 0xFF0E1420 : 0xFF141C2C));
            int text = 0xFFE0E0E0;
            g.drawString(font, String.valueOf(idx + 1), tableX + COL_X[0], ry + 3, 0xFF888888);
            g.drawString(font, font.plainSubstrByWidth(e.title().getString(), COL_X[2] - COL_X[1] - 4), tableX + COL_X[1], ry + 3, text);
            g.drawString(font, font.plainSubstrByWidth(e.artist(), COL_X[3] - COL_X[2] - 4), tableX + COL_X[2], ry + 3, 0xFFAAAAAA);
            g.drawString(font, e.bpm() > 0 ? String.format("%.1f", e.bpm()) : "-", tableX + COL_X[3], ry + 3, text);
            g.drawString(font, "-", tableX + COL_X[4], ry + 3, 0xFF666666);
            g.drawString(font, e.lengthTicks() > 0 ? time(e.lengthTicks()) : "--:--", tableX + COL_X[5], ry + 3, text);
            for (int d = 0; d < DjBoothBlockEntity.DECKS; d++) {
                int bx = tableX + COL_LOAD + d * LOAD_STEP;
                boolean hover = mouseX >= bx && mouseX < bx + LOAD_W && mouseY >= ry + 1 && mouseY < ry + 12;
                g.fill(bx, ry + 1, bx + LOAD_W, ry + 12, hover ? 0xFF354560 : 0xFF1E283A);
                g.renderOutline(bx, ry + 1, LOAD_W, 11, DjMixerScreen.DECK_COLORS[d]);
                g.drawCenteredString(font, String.valueOf(d + 1), bx + 5, ry + 3, DjMixerScreen.DECK_COLORS[d]);
            }
        }
    }

    private void renderSidebar(GuiGraphics g, DjLibrary.Snapshot lib) {
        int x = left + 6;
        // ponytail: playlists past the sidebar's height are not drawn; add sidebar scrolling if a booth ever has that many
        for (int i = -1; i < lib.playlists().size(); i++) {
            int y = top + HEADER_Y + (i + 1) * PLAYLIST_H;
            if (y + PLAYLIST_H > top + H - 6) break;
            boolean nested = i >= 0 && lib.playlists().get(i).nested();
            String name = i < 0 ? "ALL TRACKS" : lib.playlists().get(i).name().getString();
            if (i == playlist) g.fill(x, y, x + SIDEBAR_W, y + PLAYLIST_H, 0xFF2A3A5A);
            int indent = nested ? 12 : 4;
            g.drawString(font, font.plainSubstrByWidth(name, SIDEBAR_W - indent - 2), x + indent, y + 2, i == playlist ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
    }

    private static String time(int ticks) {
        int seconds = ticks / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        DjLibrary.Snapshot lib = DjLibrarySync.received(pos);
        if (lib == null) return false;
        setFocused(null);
        search.setFocused(false);

        if (mouseX >= left + 6 && mouseX < left + 6 + SIDEBAR_W && mouseY >= top + HEADER_Y) {
            int i = (int) ((mouseY - top - HEADER_Y) / PLAYLIST_H) - 1;
            if (i >= -1 && i < lib.playlists().size()) {
                playlist = i;
                scroll = 0;
                selected = 0;
                return true;
            }
        }
        if (mouseX >= tableX && mouseY >= top + HEADER_Y && mouseY < top + FIRST_ROW_Y - 2) {
            for (int h = HEADERS.length - 1; h >= 0; h--) {
                if (mouseX >= tableX + COL_X[h]) {
                    if (HEADER_SORT[h] == null) return true;
                    descending = HEADER_SORT[h] == sort && !descending;
                    sort = HEADER_SORT[h];
                    return true;
                }
            }
        }
        if (mouseX >= tableX && mouseY >= top + FIRST_ROW_Y) {
            int r = (int) ((mouseY - top - FIRST_ROW_Y) / ROW_H);
            int idx = scroll + r;
            if (r < ROWS && idx < rows.size()) {
                int ry = top + FIRST_ROW_Y + r * ROW_H;
                for (int d = 0; d < DjBoothBlockEntity.DECKS; d++) {
                    int bx = tableX + COL_LOAD + d * LOAD_STEP;
                    if (mouseX >= bx && mouseX < bx + LOAD_W && mouseY >= ry + 1 && mouseY < ry + 12) {
                        load(lib, rows.get(idx), d);
                        return true;
                    }
                }
                selected = idx;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 3, 0, Math.max(0, rows.size() - ROWS));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_TAB) {
                search.setFocused(false);
                setFocused(null);
                return true;
            }
            // Typing goes into the search, digits included: 1-4 load only outside it.
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            selected = Mth.clamp(selected + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), 0, Math.max(0, rows.size() - 1));
            if (selected < scroll) scroll = selected;
            if (selected >= scroll + ROWS) scroll = selected - ROWS + 1;
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_4 && selected < rows.size()) {
            DjLibrary.Snapshot lib = DjLibrarySync.received(pos);
            if (lib != null) load(lib, rows.get(selected), keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void load(DjLibrary.Snapshot lib, DjLibrary.Entry entry, int deck) {
        if (entry.playlist() < 0 || entry.playlist() >= lib.playlists().size()) return;
        DjLibrary.Playlist p = lib.playlists().get(entry.playlist());
        DjLibrarySync.load(pos, deck, p.source(), p.shulker(), entry.slot());
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 1.2f);
        }
    }
}
