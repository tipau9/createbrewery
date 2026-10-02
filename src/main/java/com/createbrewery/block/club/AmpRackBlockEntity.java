package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A booth's amp rack: power, master, five zones with their DSP, the crossover between subs and
 * tops, and which zone each speaker plays (see {@link AmpSettings}). Linked to the booth like a
 * speaker; MusicPulse reads it each frame. The server owns the settings and sorts newly linked
 * speakers into zones on its own.
 */
public class AmpRackBlockEntity extends SpeakerBlockEntity {
    /** Ticks between looks for newly linked speakers. */
    private static final int SORT_EVERY = 40;

    private AmpSettings settings = new AmpSettings();
    private int ticks;
    private long lastSetup = Long.MIN_VALUE;

    public AmpRackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public AmpSettings settings() {
        return settings;
    }

    /** The rack that drives the booth: the first linked one, in position order. */
    @Nullable
    public static AmpRackBlockEntity driving(Level level, BlockPos booth) {
        for (SpeakerBlockEntity s : linked(level, booth)) if (s instanceof AmpRackBlockEntity r) return r;
        return null;
    }

    public boolean drives() {
        return level != null && getBooth() != null && driving(level, getBooth()) == this;
    }

    /**
     * A move from the screen (client, at once) or from AmpControl (server). Auto Setup runs only on
     * the server; its result comes back with the block update.
     */
    public void apply(byte action, int zone, float value, BlockPos target, @Nullable Player player) {
        if (action == AmpSettings.AUTO_SETUP) {
            if (level == null || level.isClientSide || level.getGameTime() - lastSetup < 20) return;
            lastSetup = level.getGameTime();
            AutoSetup.Result r = autoSetup();
            if (r != null && player != null) {
                player.displayClientMessage(Component.translatable("createbrewery.amp.setup_done", r.tops(), r.subs(), r.rooms(), r.delays()), false);
            }
            return;
        }
        if (action == AmpSettings.ASSIGN) {
            if (!assign(target, zone)) return;
        } else if (!settings.set(action, zone, value)) {
            return;
        }
        changed();
    }

    /** A person moves a top to another zone; only tops of this rack's booth, only to a tops' zone. */
    private boolean assign(BlockPos target, int zone) {
        if (zone != AmpSettings.FLOOR && zone != AmpSettings.DELAY && zone != AmpSettings.ROOM) return false;
        if (level == null || getBooth() == null || !level.isLoaded(target)) return false;
        if (!(level.getBlockEntity(target) instanceof SpeakerBlockEntity s) || !s.isSpeaker() || !getBooth().equals(s.getBooth())) return false;
        settings.assign.put(target.asLong(), new AmpSettings.Assignment(zone, true));
        return true;
    }

    /** Server: sorts every linked speaker afresh and sets the rack up for the room. Null when not linked. */
    @Nullable
    public AutoSetup.Result autoSetup() {
        BlockPos booth = getBooth();
        if (booth == null || level == null) return null;
        List<AutoSetup.Seen> seen = new ArrayList<>();
        for (SpeakerBlockEntity s : linked(level, booth)) {
            if (s instanceof SubwooferBlockEntity) seen.add(new AutoSetup.Seen(s.getBlockPos().asLong(), distance(booth, s), true, false));
            else if (s.isSpeaker()) seen.add(new AutoSetup.Seen(s.getBlockPos().asLong(), distance(booth, s), false, walled(booth, s)));
        }
        AutoSetup.Result r = AutoSetup.plan(seen);
        AutoSetup.apply(settings, r);
        changed();
        return r;
    }

    /** Server, every two seconds: newly linked tops get their auto zone, ones linked elsewhere are forgotten. */
    public void serverTick() {
        if (++ticks % SORT_EVERY != 0 || level == null || getBooth() == null) return;
        BlockPos booth = getBooth();
        Set<Long> linked = new HashSet<>();
        List<SpeakerBlockEntity> fresh = new ArrayList<>();
        double nearest = Double.POSITIVE_INFINITY;
        for (SpeakerBlockEntity s : linked(level, booth)) {
            if (!s.isSpeaker()) continue;
            long p = s.getBlockPos().asLong();
            linked.add(p);
            AmpSettings.Assignment a = settings.assign.get(p);
            if (a == null) fresh.add(s);
            else if (a.zone() != AmpSettings.ROOM) nearest = Math.min(nearest, distance(booth, s));
        }
        boolean changed = prune(linked);
        for (SpeakerBlockEntity s : fresh) {
            boolean walled = walled(booth, s);
            double d = distance(booth, s);
            if (!walled) nearest = Math.min(nearest, d);
            int zone = AutoSetup.zoneOf(new AutoSetup.Seen(s.getBlockPos().asLong(), d, false, walled), nearest);
            settings.assign.put(s.getBlockPos().asLong(), new AmpSettings.Assignment(zone, false));
            changed = true;
        }
        if (changed) changed();
    }

    /**
     * Forgets speakers that are loaded but no longer linked here. One in an unloaded chunk keeps its
     * zone (a person may have set it by hand), since linked() cannot see it.
     */
    boolean prune(Set<Long> linked) {
        return settings.assign.keySet().removeIf(p -> !linked.contains(p) && level != null && level.isLoaded(BlockPos.of(p)));
    }

    /** Per zone how many play it: linked tops by zone, switched-on subwoofers, and the booth as monitor. */
    public int[] zoneCounts() {
        int[] n = new int[AmpSettings.ZONES];
        if (level == null || getBooth() == null) return n;
        for (SpeakerBlockEntity s : linked(level, getBooth())) {
            if (s instanceof SubwooferBlockEntity sub) {
                if (sub.isActive()) n[AmpSettings.SUBS]++;
            } else if (s.isSpeaker()) {
                n[settings.zoneOf(s.getBlockPos().asLong())]++;
            }
        }
        n[AmpSettings.MONITOR] = 1;
        return n;
    }

    private static double distance(BlockPos booth, SpeakerBlockEntity s) {
        return s.mouth().distanceTo(Vec3.atCenterOf(booth));
    }

    /**
     * Server: a solid block between the booth and the air in front of the speaker - a side room.
     * The booth, speakers, subwoofers and racks along the way are not walls.
     */
    private boolean walled(BlockPos booth, SpeakerBlockEntity s) {
        if (level == null) return false;
        Vec3 from = Vec3.atCenterOf(booth);
        Vec3 to = s.mouth().add(Vec3.atLowerCornerOf(s.getBlockState().getValue(SpeakerBlock.FACING).getNormal()).scale(0.3));
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 1e-3) return false;
        dir = dir.scale(1 / length);
        Vec3 at = from;
        for (int i = 0; i < 16; i++) {
            BlockHitResult hit = level.clip(new ClipContext(at, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) return false;
            BlockPos b = hit.getBlockPos();
            Block block = level.getBlockState(b).getBlock();
            if (!b.equals(booth) && !(block instanceof SpeakerBlock) && !(block instanceof SubwooferBlock) && !(block instanceof DjBoothBlock)) return true;
            // On through this block to where the ray leaves it.
            at = hit.getLocation();
            for (int k = 0; k < 40 && BlockPos.containing(at).equals(b); k++) at = at.add(dir.scale(0.05));
            if (at.distanceToSqr(from) >= length * length) return false;
        }
        return false;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Amp", write(settings));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Amp")) settings = read(tag.getCompound("Amp"));
        else if (tag.contains("Crossover")) settings = AmpSettings.migrate(legacy(tag));
        else settings = new AmpSettings();
    }

    private static CompoundTag write(AmpSettings s) {
        CompoundTag t = new CompoundTag();
        t.putInt("V", 2);
        t.putBoolean("Power", s.power);
        t.putFloat("Master", s.master);
        t.putInt("Preset", s.preset);
        t.putFloat("Crossover", s.crossover);
        t.putInt("Slope", s.slope);
        t.putBoolean("Align", s.align);
        ListTag zones = new ListTag();
        for (AmpSettings.Zone z : s.zones) {
            CompoundTag zt = new CompoundTag();
            zt.putFloat("Gain", z.gain);
            zt.putBoolean("Mute", z.mute);
            zt.putFloat("Low", z.low);
            zt.putFloat("Mid", z.mid);
            zt.putFloat("High", z.high);
            zt.putFloat("Hpf", z.hpf);
            zt.putFloat("Delay", z.delayMs);
            zt.putBoolean("Invert", z.invert);
            zt.putFloat("Limit", z.limit);
            zones.add(zt);
        }
        t.put("Zones", zones);
        long[] pos = new long[s.assign.size()];
        int[] zone = new int[pos.length];
        int i = 0;
        for (var e : s.assign.entrySet()) {
            pos[i] = e.getKey();
            zone[i++] = e.getValue().zone() | (e.getValue().manual() ? 256 : 0);
        }
        t.putLongArray("AssignPos", pos);
        t.putIntArray("AssignZone", zone);
        return t;
    }

    private static AmpSettings read(CompoundTag t) {
        AmpSettings s = new AmpSettings();
        s.power = t.getBoolean("Power");
        s.master = t.getFloat("Master");
        s.preset = t.getInt("Preset");
        s.crossover = t.getFloat("Crossover");
        s.slope = t.getInt("Slope");
        s.align = t.getBoolean("Align");
        ListTag zones = t.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(zones.size(), AmpSettings.ZONES); i++) {
            CompoundTag zt = zones.getCompound(i);
            AmpSettings.Zone z = s.zones[i];
            z.gain = zt.getFloat("Gain");
            z.mute = zt.getBoolean("Mute");
            z.low = zt.getFloat("Low");
            z.mid = zt.getFloat("Mid");
            z.high = zt.getFloat("High");
            z.hpf = zt.getFloat("Hpf");
            z.delayMs = zt.getFloat("Delay");
            z.invert = zt.getBoolean("Invert");
            z.limit = zt.getFloat("Limit");
        }
        long[] pos = t.getLongArray("AssignPos");
        int[] zone = t.getIntArray("AssignZone");
        for (int i = 0; i < Math.min(pos.length, zone.length); i++) {
            int z = zone[i] & 255;
            if (z == AmpSettings.FLOOR || z == AmpSettings.DELAY || z == AmpSettings.ROOM) {
                s.assign.put(pos[i], new AmpSettings.Assignment(z, (zone[i] & 256) != 0));
            }
        }
        s.clampAll();
        return s;
    }

    /** The flat tags of the racks before v2. */
    private static AmpSettings.Legacy legacy(CompoundTag t) {
        return new AmpSettings.Legacy(t.getFloat("Crossover"),
            t.contains("SubGain") ? t.getFloat("SubGain") : 0.5f, t.contains("TopGain") ? t.getFloat("TopGain") : 0.5f,
            t.getBoolean("Propagation"), t.getBoolean("MuteTops"), t.getBoolean("MuteSubs"),
            t.getInt("SubCut"), t.getInt("BassContour"), t.getFloat("DelayMs"));
    }
}
