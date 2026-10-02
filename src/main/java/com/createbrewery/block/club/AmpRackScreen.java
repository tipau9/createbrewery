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
    private static final int W = 300, H = 214;
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
        boolean[] muted = new boolean[AmpSettings.ZONES];
        for (int z = 0; z < AmpSettings.ZONES; z++) muted[z] = s.zones[z].mute;
        return AmpStatus.of(booth != null, driver != null && driver != rack, s.power, m == null ? 0f : m.worstLimit(), rack.zoneCounts(), muted);
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
            .bounds(left + 76, top + 42, 216, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.amp.auto.tip"))).build());
        new Slider(left + 8, top + 62, 284, AmpSettings.MASTER, 0, AmpSettings.MIN_MASTER, AmpSettings.MAX_MASTER, false, false, "createbrewery.amp.master.tip");
        for (int p = 0; p < AmpSettings.CUSTOM; p++) {
            int preset = p;
            Button b = addRenderableWidget(Button.builder(Component.empty(), btn -> send(AmpSettings.PRESET, 0, preset))
                .bounds(left + 8 + p * 72, top + 82, 68, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.amp.preset.tip"))).build());
            refresh.add(() -> {
                AmpRackBlockEntity now = rack();
                boolean lit = now != null && now.settings().preset == preset;
                b.setMessage(Component.translatable("createbrewery.amp.preset." + preset).withStyle(lit ? ChatFormatting.GREEN : ChatFormatting.WHITE));
            });
        }
        for (int z = 0; z < AmpSettings.ZONES; z++) {
            int y = top + 114 + z * 19;
            new Slider(left + 8, y, 170, AmpSettings.ZONE_GAIN, z, AmpSettings.MIN_GAIN, AmpSettings.MAX_GAIN, false, false, "createbrewery.amp.zone.tip." + z);
            toggle(left + 182, y, 20, 16, AmpSettings.ZONE_MUTE, z,
                on -> Component.translatable("createbrewery.amp.mute").withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY),
                "createbrewery.amp.mute.tip");
        }
    }

    private void initExpert() {
        for (int z = 0; z < AmpSettings.ZONES; z++) {
            int zone = z;
            Button b = addRenderableWidget(Button.builder(zoneName(z), btn -> {
                expertZone = zone;
                rebuildWidgets();
            }).bounds(left + 8 + z * 57, top + 24, 55, 14).tooltip(Tooltip.create(Component.translatable("createbrewery.amp.zone.tip." + z))).build());
            b.active = expertZone != z;
        }
        int z = expertZone;
        new Slider(left + 8, top + 44, 140, AmpSettings.ZONE_LOW, z, -AmpSettings.MAX_EQ, AmpSettings.MAX_EQ, false, false, "createbrewery.amp.low.tip");
        new Slider(left + 152, top + 44, 140, AmpSettings.ZONE_MID, z, -AmpSettings.MAX_EQ, AmpSettings.MAX_EQ, false, false, "createbrewery.amp.mid.tip");
        new Slider(left + 8, top + 64, 140, AmpSettings.ZONE_HIGH, z, -AmpSettings.MAX_EQ, AmpSettings.MAX_EQ, false, false, "createbrewery.amp.high.tip");
        new Slider(left + 152, top + 64, 140, AmpSettings.ZONE_HPF, z, AmpSettings.MIN_HPF, AmpSettings.MAX_HPF, true, true, "createbrewery.amp.hpf.tip");
        new Slider(left + 8, top + 84, 140, AmpSettings.ZONE_DELAY, z, 0, AmpSettings.MAX_DELAY, false, false, "createbrewery.amp.delay.tip");
        new Slider(left + 152, top + 84, 140, AmpSettings.ZONE_LIMIT, z, AmpSettings.MIN_LIMIT, 0, false, false, "createbrewery.amp.limit.tip");
        toggle(left + 8, top + 104, 140, 16, AmpSettings.ZONE_INVERT, z,
            on -> Component.translatable("createbrewery.amp.invert", Component.translatable(on ? "createbrewery.amp.invert.on" : "createbrewery.amp.invert.off")),
            "createbrewery.amp.invert.tip");
        new Slider(left + 8, top + 140, 284, AmpSettings.CROSSOVER, 0, AmpSettings.MIN_CROSSOVER, AmpSettings.MAX_CROSSOVER, true, false, "createbrewery.amp.crossover.tip");
        toggle(left + 8, top + 160, 140, 16, AmpSettings.SLOPE, 0,
            on -> Component.translatable("createbrewery.amp.slope", Component.translatable("createbrewery.amp.slope." + (on ? 1 : 0))),
            "createbrewery.amp.slope.tip");
        toggle(left + 152, top + 160, 140, 16, AmpSettings.ALIGN, 0,
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
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + W, top + H, 0xE80E0F13);
        g.renderOutline(left, top, W, H, 0xFF3A3A48);
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
            g.drawCenteredString(font, Component.translatable("createbrewery.amp.readonly", d.getX(), d.getY(), d.getZ()), left + W / 2, top + H - 12, 0xFF6060);
        }
        if (tab == SIMPLE) renderSimple(g, r);
        else if (tab == ZONES) renderZones(g, mouseX, mouseY);
        else g.drawString(font, Component.translatable("createbrewery.amp.system"), left + 8, top + 128, 0xAAAAAA);
        if (tab == EXPERT) g.drawCenteredString(font, Component.translatable("createbrewery.amp.reset_hint"), left + W / 2, top + 184, 0x666666);
    }

    private void renderSimple(GuiGraphics g, AmpRackBlockEntity r) {
        AmpStatus.Status st = status(minecraft.level, r);
        int col = color(st.light());
        g.fill(left + 76, top + 26, left + 86, top + 36, 0xFF000000 | col);
        g.drawString(font, message(st), left + 90, top + 27, col);
        if (r.settings().preset == AmpSettings.CUSTOM) g.drawString(font, Component.translatable("createbrewery.amp.preset.custom"), left + 8, top + 100, 0x888888);
        int[] counts = r.zoneCounts();
        AmpMeters m = r.getBooth() == null ? null : MusicPulse.meters(r.getBooth());
        for (int z = 0; z < AmpSettings.ZONES; z++) {
            int y = top + 114 + z * 19;
            if (counts[z] == 0) g.drawString(font, Component.translatable("createbrewery.amp.none"), left + 208, y + 4, 0x666666);
            else meter(g, left + 206, y + 3, 86, 10, m, z);
        }
    }

    private static void meter(GuiGraphics g, int x, int y, int w, int h, AmpMeters m, int zone) {
        g.fill(x, y, x + w, y + h, 0xFF101010);
        if (m == null) return;
        float p = m.peakDb(zone);
        int fill = (int) (w * Math.max(0f, (p - AmpMeters.FLOOR_DB) / -AmpMeters.FLOOR_DB));
        int col = m.limitDb(zone) >= 1f ? 0xFFFF4040 : p > -6f ? 0xFFF0C030 : 0xFF40D060;
        g.fill(x, y, x + fill, y + h, col);
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
            if (row.pos().equals(looked)) g.fill(left + 4, y - 1, left + W - 4, y + ROW - 2, 0x40FFFFFF);
            g.drawString(font, Component.translatable("createbrewery.amp.row", row.distance(),
                Component.translatable("createbrewery.amp.dir." + row.dir().getName())), left + 10, y + 2, 0xDDDDDD);
            boolean hover = mouseX >= left + 190 && mouseX < left + 280 && mouseY >= y && mouseY < y + ROW - 2;
            g.fill(left + 190, y, left + 280, y + ROW - 2, hover ? 0xFF3A3F50 : 0xFF262A35);
            g.drawCenteredString(font, zoneName(row.zone()), left + 235, y + 2, 0xFFFFFF);
            if (!row.manual()) g.drawString(font, Component.translatable("createbrewery.amp.zones.auto"), left + 284, y + 2, 0x888888);
        }
        g.drawString(font, Component.translatable("createbrewery.amp.zones.subs", subCount), left + 10, top + H - 26, 0x888888);
        if (mouseX >= left + 190 && mouseX < left + 280 && mouseY >= y0 && mouseY < y0 + visible * ROW) {
            g.renderTooltip(font, Component.translatable("createbrewery.amp.zones.tip"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        AmpRackBlockEntity r = rack();
        if (tab == ZONES && button == 0 && r != null && !readOnly(r) && mx >= left + 190 && mx < left + 280) {
            int i = (int) ((my - (top + 26)) / ROW);
            if (my >= top + 26 && i >= 0 && i < (H - 56) / ROW && i + scroll < rows.size()) {
                Row row = rows.get(i + scroll);
                int next = row.zone() == AmpSettings.FLOOR ? AmpSettings.DELAY : row.zone() == AmpSettings.DELAY ? AmpSettings.ROOM : AmpSettings.FLOOR;
                send(AmpSettings.ASSIGN, next, 0f, row.pos());
                rows = rows(r);
                return true;
            }
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
