package com.createbrewery.block.club;

import com.createbrewery.drunk.DeckFx;
import com.createbrewery.drunk.MusicPulse;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The amp rack, for people who never touched one: a power button, a traffic light that says what
 * to do, Auto Setup, presets and a fader per zone on the first page; which zone every speaker
 * plays on the second; the full DSP on the third. Every move goes to the server (see
 * {@link AmpControl}) and is heard here at once. Client only.
 */
public class AmpRackScreen extends Screen {
    private static final int W = 340, H = 244;
    private static final int SIMPLE = 0, ZONES = 1, EXPERT = 2;
    private static final String[] TABS = {"createbrewery.amp.tab.simple", "createbrewery.amp.tab.zones", "createbrewery.amp.tab.expert"};
    private static final int ROW = 14;

    private final BlockPos pos;
    private int left, top, tab = SIMPLE, expertZone = AmpSettings.FLOOR, scroll;
    private final List<Slider> sliders = new ArrayList<>();
    private final List<Runnable> refresh = new ArrayList<>();
    private final List<AbstractWidget> tabButtons = new ArrayList<>();
    private List<Row> rows = List.of();
    private int subCount;
    /** The speaker picked on the Zones page to be shown in the world, and for how many more ticks. */
    private BlockPos identify;
    private int identifyTicks;

    /** A top on the Zones page. */
    private record Row(BlockPos pos, int distance, Direction dir, int zone, boolean manual) {}

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

    // ---------------------------------------------------------------- the traffic light, shared with the DJ mixer

    static AmpStatus.Status status(Level level, AmpRackBlockEntity rack) {
        BlockPos booth = rack.getBooth();
        AmpRackBlockEntity driver = booth == null ? null : AmpRackBlockEntity.driving(level, booth);
        AmpMeters m = booth == null ? null : MusicPulse.meters(booth);
        AmpSettings s = rack.settings();
        boolean[] muted = new boolean[AmpSettings.ZONES], solo = new boolean[AmpSettings.ZONES];
        float[] limits = new float[AmpSettings.ZONES];
        for (int z = 0; z < AmpSettings.ZONES; z++) {
            muted[z] = s.zones[z].mute;
            solo[z] = s.zones[z].solo;
            limits[z] = m == null ? 0f : m.limitDb(z);
        }
        return AmpStatus.of(booth != null, driver != null && driver != rack, s.power, limits, rack.zoneCounts(), solo, muted);
    }

    static int color(AmpStatus.Light light) {
        return switch (light) {
            case GREEN -> 0x40D060;
            case YELLOW -> 0xF0C030;
            case RED -> 0xFF4040;
        };
    }

    static Component message(AmpStatus.Status st) {
        return st.zone() >= 0 ? Component.translatable(st.langKey(), zoneName(st.zone())) : Component.translatable(st.langKey());
    }

    private static Component zoneName(int zone) {
        return Component.translatable("createbrewery.amp.zone." + zone);
    }

    private boolean readOnly(AmpRackBlockEntity r) {
        return r.getBooth() != null && !r.drives();
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        sliders.clear();
        refresh.clear();
        tabButtons.clear();
        AmpRackBlockEntity r = rack();
        if (r == null) return;
        for (int i = 0; i < 3; i++) {
            int t = i;
            Button b = addRenderableWidget(Button.builder(Component.translatable(TABS[i]), btn -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(left + W - 190 + i * 62, top + 4, 60, 14).build());
            b.active = tab != i;
            tabButtons.add(b);
        }
        if (tab == SIMPLE) initSimple();
        else if (tab == EXPERT) initExpert();
        if (readOnly(r)) {
            for (var w : children()) if (w instanceof AbstractWidget aw && !tabButtons.contains(aw)) aw.active = false;
        }
        refresh.forEach(Runnable::run);
    }

    private void initSimple() {
        toggle(left + 8, top + 24, 60, 32, AmpSettings.POWER, 0,
            on -> Component.translatable("createbrewery.amp.power").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY),
            "createbrewery.amp.power.tip");
        addRenderableWidget(Button.builder(Component.translatable("createbrewery.amp.auto"), b -> send(AmpSettings.AUTO_SETUP, 0, 1f))
            .bounds(left + 76, top + 42, W - 84, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.amp.auto.tip"))).build());
        new Slider(left + 8, top + 62, W - 16, AmpSettings.MASTER, 0, AmpSettings.MIN_MASTER, AmpSettings.MAX_MASTER, false, false, "createbrewery.amp.master.tip");
        for (int p = 0; p < AmpSettings.CUSTOM; p++) {
            int preset = p;
            Button b = addRenderableWidget(Button.builder(Component.empty(), btn -> send(AmpSettings.PRESET, 0, preset))
                .bounds(left + 8 + p * 82, top + 82, 78, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.amp.preset.tip"))).build());
            refresh.add(() -> {
                AmpRackBlockEntity now = rack();
                boolean lit = now != null && now.settings().preset == preset;
                b.setMessage(Component.translatable("createbrewery.amp.preset." + preset).withStyle(lit ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            });
        }
        for (int slot = 0; slot < AmpSettings.USER_SLOTS; slot++) new UserPreset(left + 8 + slot * 82, top + 100, slot);
        for (int z = 0; z < AmpSettings.ZONES; z++) {
            int y = zoneRow(z);
            new Slider(left + 8, y, 160, AmpSettings.ZONE_GAIN, z, AmpSettings.MIN_GAIN, AmpSettings.MAX_GAIN, false, false, "createbrewery.amp.zone.tip." + z);
            toggle(left + 172, y, 18, 16, AmpSettings.ZONE_MUTE, z,
                on -> Component.translatable("createbrewery.amp.mute").withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY),
                "createbrewery.amp.mute.tip");
            toggle(left + 192, y, 18, 16, AmpSettings.ZONE_SOLO, z,
                on -> Component.translatable("createbrewery.amp.solo").withStyle(on ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                "createbrewery.amp.solo.tip");
        }
    }

    private int zoneRow(int zone) {
        return top + 136 + zone * 19;
    }

    private void initExpert() {
        for (int z = 0; z < AmpSettings.ZONES; z++) {
            int zone = z;
            Button b = addRenderableWidget(Button.builder(zoneName(z), btn -> {
                expertZone = zone;
                rebuildWidgets();
            }).bounds(left + 8 + z * 65, top + 24, 63, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.amp.zone.tip." + z))).build());
            b.active = expertZone != z;
        }
        int z = expertZone;
        new Slider(left + 8, top + 44, 160, AmpSettings.ZONE_LOW, z, -AmpSettings.MAX_EQ, AmpSettings.MAX_EQ, false, false, "createbrewery.amp.low.tip");
        new Slider(left + 172, top + 44, 160, AmpSettings.ZONE_MID, z, -AmpSettings.MAX_EQ, AmpSettings.MAX_EQ, false, false, "createbrewery.amp.mid.tip");
        new Slider(left + 8, top + 64, 160, AmpSettings.ZONE_HIGH, z, -AmpSettings.MAX_EQ, AmpSettings.MAX_EQ, false, false, "createbrewery.amp.high.tip");
        new Slider(left + 172, top + 64, 160, AmpSettings.ZONE_HPF, z, AmpSettings.MIN_HPF, AmpSettings.MAX_HPF, true, true, "createbrewery.amp.hpf.tip");
        new Slider(left + 8, top + 84, 160, AmpSettings.ZONE_DELAY, z, 0, AmpSettings.MAX_DELAY, false, false, "createbrewery.amp.delay.tip");
        new Slider(left + 172, top + 84, 160, AmpSettings.ZONE_LIMIT, z, AmpSettings.MIN_LIMIT, 0, false, false, "createbrewery.amp.limit.tip");
        toggle(left + 8, top + 104, 160, 16, AmpSettings.ZONE_INVERT, z,
            on -> Component.translatable("createbrewery.amp.invert", Component.translatable(on ? "createbrewery.amp.invert.on" : "createbrewery.amp.invert.off")),
            "createbrewery.amp.invert.tip");
        new Slider(left + 8, top + 140, W - 16, AmpSettings.CROSSOVER, 0, AmpSettings.MIN_CROSSOVER, AmpSettings.MAX_CROSSOVER, true, false, "createbrewery.amp.crossover.tip");
        toggle(left + 8, top + 160, 160, 16, AmpSettings.SLOPE, 0,
            on -> Component.translatable("createbrewery.amp.slope", Component.translatable("createbrewery.amp.slope." + (on ? 1 : 0))),
            "createbrewery.amp.slope.tip");
        toggle(left + 172, top + 160, 160, 16, AmpSettings.ALIGN, 0,
            on -> Component.translatable("createbrewery.amp.align", net.minecraft.network.chat.CommonComponents.optionStatus(on)),
            "createbrewery.amp.align.tip");
    }

    /** A button that flips {@code action}; right-click puts it back. */
    private void toggle(int x, int y, int w, int h, byte action, int zone, Function<Boolean, Component> label, String tip) {
        Toggle b = addRenderableWidget(new Toggle(x, y, w, h, action, zone));
        b.setTooltip(Tooltip.create(Component.translatable(tip)));
        refresh.add(() -> {
            AmpRackBlockEntity now = rack();
            if (now != null) b.setMessage(label.apply(now.settings().get(action, zone) > 0.5f));
        });
    }

    private void send(byte action, int zone, float value) {
        send(action, zone, value, pos);
    }

    private void send(byte action, int zone, float value, BlockPos target) {
        AmpControl.send(pos, action, zone, value, target);
        AmpRackBlockEntity r = rack();
        if (r != null) r.apply(action, zone, value, target, null);
    }

    // ---------------------------------------------------------------- every tick

    @Override
    public void tick() {
        AmpRackBlockEntity r = rack();
        if (r == null || minecraft.player == null || !minecraft.player.canInteractWithBlock(pos, 1.0)) {
            onClose();
            return;
        }
        for (Slider s : sliders) {
            s.flush();
            s.sync();
        }
        refresh.forEach(Runnable::run);
        if (tab == ZONES) rows = rows(r);
        tickIdentify();
    }

    /** Shows a speaker in the world: a bell from it and a column of light rising out of it, for five seconds. */
    private void identify(BlockPos speaker) {
        identify = speaker;
        identifyTicks = 100;
    }

    private void tickIdentify() {
        if (identify == null) return;
        if (--identifyTicks <= 0) {
            identify = null;
            return;
        }
        Level level = minecraft.level;
        Vec3 c = Vec3.atCenterOf(identify);
        if (identifyTicks % 20 == 19) {
            level.playLocalSound(c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, 1.5f, false);
            level.addParticle(ParticleTypes.NOTE, c.x, c.y + 0.9, c.z, (identifyTicks % 24) / 24.0, 0, 0);
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.END_ROD, c.x + (level.random.nextDouble() - 0.5) * 0.7, c.y + 0.6,
                c.z + (level.random.nextDouble() - 0.5) * 0.7, 0, 0.12, 0);
        }
    }

    private List<Row> rows(AmpRackBlockEntity r) {
        BlockPos booth = r.getBooth();
        if (booth == null) return List.of();
        List<Row> out = new ArrayList<>();
        int subs = 0;
        Vec3 b = Vec3.atCenterOf(booth);
        for (SpeakerBlockEntity s : SpeakerBlockEntity.linked(minecraft.level, booth)) {
            if (s instanceof SubwooferBlockEntity) subs++;
            if (!s.isSpeaker()) continue;
            Vec3 d = s.mouth().subtract(b);
            AmpSettings.Assignment a = r.settings().assign.get(s.getBlockPos().asLong());
            out.add(new Row(s.getBlockPos(), (int) Math.round(d.length()), Direction.getNearest(d.x, 0, d.z),
                a == null ? AmpSettings.FLOOR : a.zone(), a != null && a.manual()));
        }
        subCount = subs;
        out.sort(Comparator.comparingInt(Row::distance));
        return out;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No blur: the club stays in sight while you tune it (and a speaker you pick lights up in it).
        g.fill(left, top, left + W, top + H, 0xE80E0F13);
        g.fill(left, top, left + W, top + 21, 0xFF16171D);
        g.renderOutline(left, top, W, H, 0xFF3A3A48);
    }

    /** A small grey heading with a rule running to the right edge. */
    private void section(GuiGraphics g, Component text, int y) {
        g.drawString(font, text, left + 8, y, 0x777777, false);
        g.fill(left + 12 + font.width(text), y + 4, left + W - 8, y + 5, 0xFF2A2B36);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        AmpRackBlockEntity r = rack();
        if (r == null) return;
        g.drawString(font, title, left + 8, top + 7, 0xFFFFFF);
        AmpRackBlockEntity driver = r.getBooth() == null ? null : AmpRackBlockEntity.driving(minecraft.level, r.getBooth());
        if (driver != null && driver != r) {
            BlockPos d = driver.getBlockPos();
            g.drawCenteredString(font, Component.translatable("createbrewery.amp.readonly", d.getX(), d.getY(), d.getZ()), left + W / 2, top + H - 11, 0xFF6060);
        }
        if (tab == SIMPLE) renderSimple(g, r, mouseX, mouseY);
        else if (tab == ZONES) renderZones(g, mouseX, mouseY);
        else renderExpert(g, r, mouseX, mouseY);
    }

    private void renderExpert(GuiGraphics g, AmpRackBlockEntity r, int mouseX, int mouseY) {
        section(g, Component.translatable("createbrewery.amp.system"), top + 128);
        g.drawCenteredString(font, Component.translatable("createbrewery.amp.reset_hint"), left + W / 2, top + 184, 0x666666);
        // The zone being set, as it plays right now.
        if (r.zoneCounts()[expertZone] > 0) {
            AmpMeters m = r.getBooth() == null ? null : MusicPulse.meters(r.getBooth());
            meterWithReadout(g, left + 172, top + 107, 124, m, expertZone, mouseX, mouseY);
        }
    }

    private void renderSimple(GuiGraphics g, AmpRackBlockEntity r, int mouseX, int mouseY) {
        AmpStatus.Status st = status(minecraft.level, r);
        int col = color(st.light());
        g.fill(left + 75, top + 25, left + 87, top + 37, 0xFF000000);
        g.fill(left + 76, top + 26, left + 86, top + 36, 0xFF000000 | col);
        // Two lines fit above Auto Setup; the longer hints need them.
        var lines = font.split(message(st), W - 100);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.drawString(font, lines.get(i), left + 92, top + (lines.size() > 1 ? 23 : 27) + i * 9, col, false);
        }
        section(g, Component.translatable("createbrewery.amp.section.zones"), top + 122);
        if (r.settings().preset == AmpSettings.CUSTOM && r.settings().userSlot < 0) {
            Component custom = Component.translatable("createbrewery.amp.preset.custom");
            int x = left + W - 8 - font.width(custom);
            g.fill(x - 4, top + 120, left + W - 6, top + 131, 0xFF0E0F13);
            g.drawString(font, custom, x, top + 122, 0x888888, false);
        }
        int[] counts = r.zoneCounts();
        AmpMeters m = r.getBooth() == null ? null : MusicPulse.meters(r.getBooth());
        for (int z = 0; z < AmpSettings.ZONES; z++) {
            int y = zoneRow(z);
            if (counts[z] == 0) g.drawString(font, Component.translatable("createbrewery.amp.none"), left + 218, y + 4, 0x666666);
            else meterWithReadout(g, left + 216, y + 3, 80, m, z, mouseX, mouseY);
        }
    }

    /**
     * A zone's meter and, right of it, what the limiter is doing: nothing while it rests, how many
     * dB it takes off while it works, a flashing CLIP when it is slammed. Hover for both numbers.
     */
    private void meterWithReadout(GuiGraphics g, int x, int y, int w, AmpMeters m, int zone, int mouseX, int mouseY) {
        meter(g, x, y, w, 10, m, zone);
        float lim = m == null ? 0f : m.limitDb(zone);
        int tx = x + w + 4;
        if (lim > 6f) {
            if (minecraft.player.tickCount / 4 % 2 == 0) g.drawString(font, Component.translatable("createbrewery.amp.clip"), tx, y + 1, 0xFF4040, false);
        } else if (lim >= 0.5f) {
            g.drawString(font, String.format("-%.1f", lim), tx, y + 1, lim >= 1f ? 0xF0C030 : 0x999999, false);
        } else if (m != null && m.peakDb(zone) > AmpMeters.FLOOR_DB) {
            g.drawString(font, String.format("%.0f", m.peakDb(zone)), tx, y + 1, 0x5A5A66, false);
        }
        if (m != null && mouseX >= x && mouseX < tx + 30 && mouseY >= y - 2 && mouseY < y + 12) {
            g.renderTooltip(font, Component.translatable("createbrewery.amp.meter.tip", String.format("%.1f", m.peakDb(zone)), String.format("%.1f", lim)), mouseX, mouseY);
        }
    }

    /** Green to -6 dB, yellow to -1, red above, like a rack's LEDs; the limiter's work along the bottom from the right. */
    private static void meter(GuiGraphics g, int x, int y, int w, int h, AmpMeters m, int zone) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2A2B36);
        g.fill(x, y, x + w, y + h, 0xFF0A0A0C);
        if (m != null) {
            int fill = px(m.peakDb(zone), w), yellow = px(-6f, w), red = px(-1f, w);
            g.fill(x, y, x + Math.min(fill, yellow), y + h, 0xFF40D060);
            if (fill > yellow) g.fill(x + yellow, y, x + Math.min(fill, red), y + h, 0xFFF0C030);
            if (fill > red) g.fill(x + red, y, x + fill, y + h, 0xFFFF4040);
            float lim = m.limitDb(zone);
            if (lim > 0.1f) {
                int gr = (int) Math.min(w, w * lim / 12f);
                g.fill(x + w - gr, y + h - 2, x + w, y + h, 0xFFFF6040);
            }
        }
        for (float db : new float[] {-24, -12, -6, -3}) {
            int t = x + px(db, w);
            g.fill(t, y, t + 1, y + h, 0x70000000);
        }
    }

    private static int px(float db, int w) {
        return (int) (w * Math.max(0f, Math.min(1f, (db - AmpMeters.FLOOR_DB) / -AmpMeters.FLOOR_DB)));
    }

    private static int zoneColor(int zone) {
        return switch (zone) {
            case AmpSettings.DELAY -> 0xFF2A3F66;
            case AmpSettings.ROOM -> 0xFF4A2F60;
            default -> 0xFF2A5236;
        };
    }

    private void renderZones(GuiGraphics g, int mouseX, int mouseY) {
        int y0 = top + 26, visible = (H - 56) / ROW;
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("createbrewery.amp.zones.empty"), left + W / 2, y0 + 20, 0xAAAAAA);
        }
        BlockPos looked = minecraft.hitResult instanceof BlockHitResult bh ? bh.getBlockPos() : null;
        for (int i = 0; i < visible && i + scroll < rows.size(); i++) {
            Row row = rows.get(i + scroll);
            int y = y0 + i * ROW;
            boolean shown = row.pos().equals(identify);
            boolean name = mouseX >= left + 6 && mouseX < left + 214 && mouseY >= y - 1 && mouseY < y + ROW - 2;
            if (shown) g.fill(left + 4, y - 1, left + W - 4, y + ROW - 2, 0x5040A0FF);
            else if (row.pos().equals(looked)) g.fill(left + 4, y - 1, left + W - 4, y + ROW - 2, 0x40FFFFFF);
            else if (name) g.fill(left + 4, y - 1, left + 214, y + ROW - 2, 0x20FFFFFF);
            g.drawString(font, Component.translatable("createbrewery.amp.row", row.distance(),
                Component.translatable("createbrewery.amp.dir." + row.dir().getName())), left + 10, y + 2, shown ? 0x9FD0FF : 0xDDDDDD);
            boolean hover = mouseX >= left + 218 && mouseX < left + 308 && mouseY >= y && mouseY < y + ROW - 2;
            g.fill(left + 218, y, left + 308, y + ROW - 2, hover ? 0xFF3A3F50 : zoneColor(row.zone()));
            g.drawCenteredString(font, zoneName(row.zone()), left + 263, y + 2, 0xFFFFFF);
            if (!row.manual()) g.drawString(font, Component.translatable("createbrewery.amp.zones.auto"), left + 312, y + 2, 0x888888);
        }
        g.drawString(font, Component.translatable("createbrewery.amp.zones.subs", subCount), left + 10, top + H - 26, 0x888888);
        if (mouseY >= y0 && mouseY < y0 + Math.min(visible, rows.size() - scroll) * ROW) {
            if (mouseX >= left + 218 && mouseX < left + 308) g.renderTooltip(font, Component.translatable("createbrewery.amp.zones.tip"), mouseX, mouseY);
            else if (mouseX >= left + 6 && mouseX < left + 214) g.renderTooltip(font, Component.translatable("createbrewery.amp.zones.identify"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        AmpRackBlockEntity r = rack();
        int i = (int) Math.floor((my - (top + 26)) / ROW);
        boolean onRow = tab == ZONES && button == 0 && r != null && i >= 0 && i < (H - 56) / ROW && i + scroll < rows.size();
        // Anyone may look where a speaker is, even at a rack that only watches.
        if (onRow && mx >= left + 6 && mx < left + 214) {
            identify(rows.get(i + scroll).pos());
            return true;
        }
        if (onRow && !readOnly(r) && mx >= left + 218 && mx < left + 308) {
            Row row = rows.get(i + scroll);
            int next = row.zone() == AmpSettings.FLOOR ? AmpSettings.DELAY : row.zone() == AmpSettings.DELAY ? AmpSettings.ROOM : AmpSettings.FLOOR;
            send(AmpSettings.ASSIGN, next, 0f, row.pos());
            rows = rows(r);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab == ZONES) {
            scroll = Math.max(0, Math.min(Math.max(0, rows.size() - (H - 56) / ROW), scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------- controls

    /** One of the rack's own presets: click loads it, right-click stores the sound as it is now. */
    private class UserPreset extends Button {
        private final int slot;

        UserPreset(int x, int y, int slot) {
            super(x, y, 78, 14, Component.empty(), b -> ((UserPreset) b).load(), DEFAULT_NARRATION);
            this.slot = slot;
            setTooltip(Tooltip.create(Component.translatable("createbrewery.amp.user.tip")));
            addRenderableWidget(this);
            refresh.add(() -> {
                AmpRackBlockEntity now = rack();
                if (now == null) return;
                AmpSettings s = now.settings();
                ChatFormatting style = s.userSlot == slot ? ChatFormatting.GREEN : s.user[slot] != null ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY;
                setMessage(Component.translatable("createbrewery.amp.user", slot + 1).withStyle(style));
            });
        }

        private void load() {
            AmpRackBlockEntity now = rack();
            if (now != null && now.settings().user[slot] != null) send(AmpSettings.USER_LOAD, 0, slot);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 1 && active && visible && isMouseOver(mx, my)) {
                send(AmpSettings.USER_SAVE, 0, slot);
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.translatable("createbrewery.amp.user.saved", slot + 1), true);
                    minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.4f);
                }
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
    }

    /** A toggle; right-click resets it. */
    private class Toggle extends Button {
        private final byte action;
        private final int zone;

        Toggle(int x, int y, int w, int h, byte action, int zone) {
            super(x, y, w, h, Component.empty(), b -> ((Toggle) b).flip(), DEFAULT_NARRATION);
            this.action = action;
            this.zone = zone;
        }

        private void flip() {
            AmpRackBlockEntity now = rack();
            if (now != null) send(action, zone, now.settings().get(action, zone) > 0.5f ? 0f : 1f);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 1 && active && visible && isMouseOver(mx, my) && action != AmpSettings.POWER) {
                send(AmpSettings.RESET, zone, action);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
    }

    /** A fader that sends at most once a tick, follows the rack when let go, and resets on right-click. */
    private class Slider extends AbstractSliderButton {
        private final byte action;
        private final int zone;
        private final float min, max;
        private final boolean log, offAtStart;
        private boolean dirty, held;

        Slider(int x, int y, int w, byte action, int zone, float min, float max, boolean log, boolean offAtStart, String tip) {
            super(x, y, w, 16, Component.empty(), 0);
            this.action = action;
            this.zone = zone;
            this.min = min;
            this.max = max;
            this.log = log;
            this.offAtStart = offAtStart;
            setTooltip(Tooltip.create(Component.translatable(tip)));
            value = toSlider(current());
            updateMessage();
            sliders.add(this);
            addRenderableWidget(this);
        }

        private float current() {
            AmpRackBlockEntity r = rack();
            return r == null ? 0f : r.settings().get(action, zone);
        }

        /** The slider as the value it sends; with offAtStart the first 3 % is "off" (0). */
        private float wire() {
            double v = value;
            if (offAtStart) {
                if (v < 0.03) return 0f;
                v = (v - 0.03) / 0.97;
            }
            return (float) (log ? min * Math.pow(max / min, v) : min + (max - min) * v);
        }

        private double toSlider(float x) {
            if (offAtStart && x <= 0) return 0;
            double v = log ? Math.log(x / min) / Math.log(max / min) : (x - min) / (max - min);
            v = Math.max(0, Math.min(1, v));
            return offAtStart ? 0.03 + v * 0.97 : v;
        }

        @Override
        protected void updateMessage() {
            setMessage(label(action, zone, wire()));
        }

        @Override
        protected void applyValue() {
            dirty = true;
        }

        @Override
        public void onClick(double mx, double my) {
            held = true;
            super.onClick(mx, my);
        }

        @Override
        public void onRelease(double mx, double my) {
            held = false;
            super.onRelease(mx, my);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 1 && active && visible && isMouseOver(mx, my)) {
                dirty = false;
                send(AmpSettings.RESET, zone, action);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        void flush() {
            if (!dirty) return;
            dirty = false;
            send(action, zone, wire());
        }

        /** Shows what the rack has (another player's move, a preset, a reset) unless it is in hand. */
        void sync() {
            if (dirty || held) return;
            value = toSlider(current());
            updateMessage();
        }
    }

    private static Component label(byte action, int zone, float v) {
        return switch (action) {
            case AmpSettings.MASTER -> Component.translatable("createbrewery.amp.master", db(v));
            case AmpSettings.ZONE_GAIN -> Component.translatable("createbrewery.amp.zone_gain", zoneName(zone),
                v <= AmpSettings.MIN_GAIN ? Component.translatable("createbrewery.amp.off") : Component.literal(db(v) + " dB"));
            case AmpSettings.ZONE_LOW -> Component.translatable("createbrewery.amp.low", db(v));
            case AmpSettings.ZONE_MID -> Component.translatable("createbrewery.amp.mid", db(v));
            case AmpSettings.ZONE_HIGH -> Component.translatable("createbrewery.amp.high", db(v));
            case AmpSettings.ZONE_HPF -> Component.translatable("createbrewery.amp.hpf",
                v <= 0 ? Component.translatable("createbrewery.amp.off") : Component.translatable("createbrewery.amp.hz", Math.round(v)));
            case AmpSettings.ZONE_DELAY -> Component.translatable("createbrewery.amp.delay", Math.round(v), String.format("%.1f", v / 1000 * DeckFx.SPEED_OF_SOUND));
            case AmpSettings.ZONE_LIMIT -> Component.translatable("createbrewery.amp.limit_at", db(v));
            case AmpSettings.CROSSOVER -> Component.translatable("createbrewery.amp.crossover", Math.round(v));
            default -> Component.empty();
        };
    }

    private static String db(float v) {
        return String.format("%+.1f", v);
    }
}
