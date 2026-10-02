package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A DMX lighting console: eight fixture groups with a fader, a colour and a flash button each, a
 * master, blackout, four scenes and three programs (manual, beat-synced auto, scene chase). The
 * server owns the settings; every client runs the program itself from the music it hears (see
 * {@link DmxProgram}), and linked fixtures read their group's look from it.
 */
public class DmxConsoleBlockEntity extends BlockEntity {
    /** A flash button held on the screen is refreshed every few ticks; let go (or the screen closed) it drops. */
    static final int FLASH_HOLD = 8;

    final DmxProgram.Settings settings = new DmxProgram.Settings();
    /** Client: this tick's output. */
    final DmxProgram program = new DmxProgram();
    private final long[] flashUntil = new long[DmxProgram.GROUPS];

    /** The show recorded to songs, and the booth whose records it follows (server). */
    final DmxTimecode timecode = new DmxTimecode();
    @org.jetbrains.annotations.Nullable
    private BlockPos booth;
    /** Recording: moves go into the show instead of the show playing. Synced, with the song's cue count. */
    boolean recording;
    int cues;
    /** Song ticks into each deck's record (at its pitch), whether it played last tick, and what is heard now. */
    private final float[] songTime = new float[2];
    private final boolean[] wasPlaying = new boolean[2];
    private int song, songTick, lastCue = -1, lastSong;
    private boolean hearing;

    public DmxConsoleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---------------------------------------------------------------- server: the controls

    void setFader(int g, float v) {
        settings.faders[g] = DmxProgram.clamp(v);
        changed();
    }

    void setMaster(float v) {
        settings.master = DmxProgram.clamp(v);
        changed();
    }

    void setColor(int g, int c) {
        settings.colors[g] = Math.floorMod(c, DmxProgram.PALETTE.length);
        changed();
    }

    void setProgram(int p) {
        settings.program = Math.floorMod(p, DmxProgram.PROGRAMS);
        changed();
    }

    void setMove(int m) {
        settings.move = Math.floorMod(m, DmxProgram.MOVES);
        changed();
    }

    void setRate(int r) {
        settings.rate = Math.floorMod(r, DmxProgram.RATES.length);
        changed();
    }

    void setColorFx(int v) {
        settings.colorFx = Math.floorMod(v, DmxProgram.COLOR_FX);
        changed();
    }

    void setGobo(int v) {
        settings.gobo = Math.floorMod(v, DmxProgram.GOBOS);
        changed();
    }

    void setZoom(int v) {
        settings.zoom = Math.floorMod(v, DmxProgram.ZOOMS);
        changed();
    }

    void setPrism(boolean on) {
        settings.prism = on;
        changed();
    }

    void setBlackout(boolean b) {
        settings.blackout = b;
        changed();
    }

    void setBlindAll(boolean on) {
        settings.blindAll = on;
        changed();
    }

    void setStrobeAll(boolean on) {
        settings.strobeAll = on;
        changed();
    }

    void setFadeTime(float sec) {
        settings.fadeTime = Math.max(0f, sec);
        changed();
    }

    void setManualBpm(float bpm) {
        settings.manualBpm = Math.max(20f, Math.min(300f, bpm));
        settings.djSync = false;
        changed();
    }

    void setDjSync(boolean on) {
        settings.djSync = on;
        changed();
    }

    void setScenePage(int page) {
        settings.scenePage = Math.floorMod(page, DmxProgram.SCENE_PAGES);
        changed();
    }

    public void triggerHazer(@org.jetbrains.annotations.Nullable Player player) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.9, worldPosition.getZ() + 0.5,
                16, 1.2, 0.4, 1.2, 0.015);
            for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-16, -6, -16), worldPosition.offset(16, 10, 16))) {
                if (server.isLoaded(p) && server.getBlockEntity(p) instanceof FixtureBlockEntity fix && worldPosition.equals(fix.getConsole())) {
                    server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.5, 0.3, 0.5, 0.01);
                }
            }
            server.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.5f);
            if (player != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("createbrewery.dmx.hazer_active"), true);
            }
        }
    }

    /** A flash button pressed (or still held); it lets go on its own {@link #FLASH_HOLD} ticks after the last press. */
    void flash(int g) {
        if (level == null) return;
        flashUntil[g] = level.getGameTime() + FLASH_HOLD;
        updateFlash();
    }

    void release(int g) {
        flashUntil[g] = 0;
        updateFlash();
    }

    void storeScene(int i) {
        if (i < 0 || i >= DmxProgram.SCENES) return;
        settings.scenes[i] = new DmxProgram.Scene(settings.faders.clone(), settings.colors.clone());
        sync();
    }

    private float fadeDuration, fadeElapsed;
    private final float[] fadeStartFaders = new float[DmxProgram.GROUPS];
    private final float[] fadeTargetFaders = new float[DmxProgram.GROUPS];
    private final int[] fadeStartColors = new int[DmxProgram.GROUPS];
    private final int[] fadeTargetColors = new int[DmxProgram.GROUPS];
    private boolean isFading;

    /** Puts a stored scene back on the faders; smoothly crossfades if fadeTime > 0. */
    boolean recallScene(int i) {
        if (i < 0 || i >= DmxProgram.SCENES) return false;
        DmxProgram.Scene sc = settings.scenes[i];
        if (sc == null) return false;
        if (settings.fadeTime <= 0.05f) {
            System.arraycopy(sc.levels(), 0, settings.faders, 0, DmxProgram.GROUPS);
            System.arraycopy(sc.colors(), 0, settings.colors, 0, DmxProgram.GROUPS);
            isFading = false;
            changed();
        } else {
            System.arraycopy(settings.faders, 0, fadeStartFaders, 0, DmxProgram.GROUPS);
            System.arraycopy(sc.levels(), 0, fadeTargetFaders, 0, DmxProgram.GROUPS);
            for (int g = 0; g < DmxProgram.GROUPS; g++) {
                fadeStartColors[g] = DmxProgram.resolveColor(settings.colors[g]);
                fadeTargetColors[g] = DmxProgram.resolveColor(sc.colors()[g]);
            }
            fadeDuration = settings.fadeTime;
            fadeElapsed = 0f;
            isFading = true;
            changed();
        }
        return true;
    }

    /** A move on the desk: while recording, it goes into the show at this point of the song. */
    private void changed() {
        if (recording && hearing) {
            timecode.record(song, songTick, DmxTimecode.Look.of(settings));
            cues = timecode.count(song);
            lastCue = timecode.at(song, songTick);
        }
        sync();
    }

    /** REC on or off. Turned on, it follows the nearest DJ booth if it follows none yet. False with no booth about. */
    boolean setRecording(boolean on) {
        if (on && booth() == null && level != null && level.getGameTime() - scannedAt >= SCAN_COOLDOWN) {
            scannedAt = level.getGameTime();
            booth = nearestBooth();
        }
        if (on && booth == null) return false;
        recording = on;
        sync();
        return true;
    }

    /** Forgets the show for the record playing now. */
    void clearShow() {
        if (!hearing) return;
        timecode.clear(song);
        cues = 0;
        lastCue = -1;
        sync();
    }

    /** The booth this console follows, whether or not it is loaded; null if none was found yet. */
    @org.jetbrains.annotations.Nullable
    BlockPos boothPos() {
        return booth;
    }

    @org.jetbrains.annotations.Nullable
    private DjBoothBlockEntity booth() {
        return booth != null && level != null && level.isLoaded(booth) && level.getBlockEntity(booth) instanceof DjBoothBlockEntity dj ? dj : null;
    }

    /** The scan below visits about 18k positions; a spammed REC button must not repeat it every packet. */
    private static final int SCAN_COOLDOWN = 20;
    private long scannedAt = Long.MIN_VALUE / 2;

    @org.jetbrains.annotations.Nullable
    private BlockPos nearestBooth() {
        BlockPos best = null;
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-16, -8, -16), worldPosition.offset(16, 8, 16))) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof DjBoothBlockEntity && (best == null || p.distSqr(worldPosition) < best.distSqr(worldPosition))) {
                best = p.immutable();
            }
        }
        return best;
    }

    /** Follows the booth's records through their songs, and plays the show recorded to them. */
    private void followSong() {
        DjBoothBlockEntity dj = booth();
        hearing = false;
        if (dj == null) return;
        long now = level.getGameTime();
        for (int d = DjBoothBlockEntity.A; d <= DjBoothBlockEntity.B; d++) {
            boolean playing = dj.isPlaying(d);
            if (playing && !wasPlaying[d]) songTime[d] = 0;
            if (playing) songTime[d] += dj.getPitch(d);
            wasPlaying[d] = playing;
        }
        // The deck the crowd hears most.
        int deck = dj.deckGain(DjBoothBlockEntity.A, now) >= dj.deckGain(DjBoothBlockEntity.B, now) ? DjBoothBlockEntity.A : DjBoothBlockEntity.B;
        if (!dj.isPlaying(deck)) deck = 1 - deck;
        if (!dj.isPlaying(deck)) return;
        hearing = true;
        // From the record as saved (its id and components, an Etched disc's track included): the
        // item's own hash differs from one game start to the next, and the show must find it again.
        net.minecraft.world.item.ItemStack disc = dj.getDisc(deck);
        if (disc.isEmpty()) return;
        song = disc.save(level.registryAccess()).toString().hashCode();
        songTick = (int) songTime[deck];
        if (song != lastSong) {
            lastSong = song;
            lastCue = -1;
            cues = timecode.count(song);
            sync();
        }
        if (recording) return;
        int cue = timecode.at(song, songTick);
        if (cue >= 0 && cue != lastCue) {
            timecode.look(song, cue).applyTo(settings);
            sync();
        }
        lastCue = cue;
    }

    /** Without REC pressed: a console with no live booth looks for the nearest one now and then. */
    private void adoptBooth() {
        if (booth() != null || level.getGameTime() - scannedAt < SCAN_COOLDOWN * 5) return;
        scannedAt = level.getGameTime();
        BlockPos found = nearestBooth();
        if (found != null && !found.equals(booth)) {
            booth = found;
            sync();
        }
    }

    void serverTick() {
        if (level == null) return;
        adoptBooth();
        followSong();
        updateFlash();
        if (isFading) {
            fadeElapsed += 0.05f;
            float progress = Math.min(1.0f, fadeElapsed / Math.max(0.05f, fadeDuration));
            for (int g = 0; g < DmxProgram.GROUPS; g++) {
                settings.faders[g] = Mth.lerp(progress, fadeStartFaders[g], fadeTargetFaders[g]);
                settings.colors[g] = DmxProgram.mix(fadeStartColors[g], fadeTargetColors[g], progress);
            }
            if (progress >= 1.0f) {
                isFading = false;
            }
            changed();
        }
    }

    /** The flash buttons alone: a press must not run the show clock ({@link #followSong}) a second time in a tick. */
    private void updateFlash() {
        if (level == null) return;
        long now = level.getGameTime();
        int mask = 0;
        for (int g = 0; g < DmxProgram.GROUPS; g++) if (flashUntil[g] > now) mask |= 1 << g;
        if (mask != settings.flash) {
            settings.flash = mask;
            sync();
        }
    }

    // ---------------------------------------------------------------- client

    private long ranAt = Long.MIN_VALUE;

    void clientTick() {
        output();
    }

    /**
     * This tick's look. Whoever asks first in a tick - the console or one of its fixtures, block
     * entities tick in the order they were loaded - works it out, so the whole rig is in step.
     */
    DmxProgram output() {
        // Counted in client ticks: the game time can repeat when the server corrects it.
        long now = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        if (level == null || now == ranAt) return program;
        ranAt = now;
        program.update(settings, level.getGameTime(), 0.05f, ClubStates.at(level, worldPosition, booth));
        return program;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeShown(tag);
        ListTag show = new ListTag();
        for (var e : timecode.songs.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putInt("Song", e.getKey());
            ListTag list = new ListTag();
            for (DmxTimecode.Cue c : e.getValue()) {
                CompoundTag cue = new CompoundTag();
                cue.putInt("Tick", c.tick());
                DmxProgram.Settings look = new DmxProgram.Settings();
                c.look().applyTo(look);
                write(look, cue);
                cue.remove("Scenes");
                list.add(cue);
            }
            s.put("Cues", list);
            show.add(s);
        }
        tag.put("Show", show);
    }

    /** What the screens and fixtures need: the settings and the recorder's state, not the whole show. */
    private void writeShown(CompoundTag tag) {
        write(settings, tag);
        tag.putBoolean("Recording", recording);
        tag.putInt("Cues", cues);
        if (booth != null) tag.put("Booth", net.minecraft.nbt.NbtUtils.writeBlockPos(booth));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        read(settings, tag);
        recording = tag.getBoolean("Recording");
        cues = tag.getInt("Cues");
        if (tag.contains("Booth")) booth = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "Booth").orElse(null);
        if (!tag.contains("Show")) return;
        timecode.songs.clear();
        for (Tag t : tag.getList("Show", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) t;
            int songKey = s.getInt("Song");
            for (Tag c : s.getList("Cues", Tag.TAG_COMPOUND)) {
                CompoundTag cue = (CompoundTag) c;
                DmxProgram.Settings look = new DmxProgram.Settings();
                read(look, cue);
                timecode.record(songKey, cue.getInt("Tick"), DmxTimecode.Look.of(look));
            }
        }
    }

    static void write(DmxProgram.Settings s, CompoundTag tag) {
        tag.put("Faders", floats(s.faders));
        tag.putIntArray("Colors", s.colors);
        tag.putFloat("Master", s.master);
        tag.putInt("Program", s.program);
        tag.putInt("Move", s.move);
        tag.putInt("Rate", s.rate);
        tag.putBoolean("Blackout", s.blackout);
        tag.putInt("ColorFx", s.colorFx);
        tag.putInt("Gobo", s.gobo);
        tag.putInt("Zoom", s.zoom);
        tag.putBoolean("Prism", s.prism);
        tag.putInt("Flash", s.flash);
        tag.putBoolean("BlindAll", s.blindAll);
        tag.putBoolean("StrobeAll", s.strobeAll);
        tag.putFloat("FadeTime", s.fadeTime);
        tag.putBoolean("DjSync", s.djSync);
        tag.putFloat("ManualBpm", s.manualBpm);
        tag.putInt("ScenePage", s.scenePage);
        ListTag scenes = new ListTag();
        for (int i = 0; i < DmxProgram.SCENES; i++) {
            CompoundTag sc = new CompoundTag();
            if (s.scenes[i] != null) {
                sc.put("Levels", floats(s.scenes[i].levels()));
                sc.putIntArray("Colors", s.scenes[i].colors());
            }
            scenes.add(sc);
        }
        tag.put("Scenes", scenes);
    }

    static void read(DmxProgram.Settings s, CompoundTag tag) {
        if (!tag.contains("Faders")) return;
        readFloats(tag.getList("Faders", Tag.TAG_FLOAT), s.faders);
        int[] colors = tag.getIntArray("Colors");
        System.arraycopy(colors, 0, s.colors, 0, Math.min(colors.length, DmxProgram.GROUPS));
        s.master = tag.getFloat("Master");
        s.program = Math.floorMod(tag.getInt("Program"), DmxProgram.PROGRAMS);
        s.move = Math.floorMod(tag.getInt("Move"), DmxProgram.MOVES);
        s.rate = Math.floorMod(tag.getInt("Rate"), DmxProgram.RATES.length);
        s.blackout = tag.getBoolean("Blackout");
        s.colorFx = Math.floorMod(tag.getInt("ColorFx"), DmxProgram.COLOR_FX);
        s.gobo = Math.floorMod(tag.getInt("Gobo"), DmxProgram.GOBOS);
        s.zoom = tag.contains("Zoom") ? Math.floorMod(tag.getInt("Zoom"), DmxProgram.ZOOMS) : 1;
        s.prism = tag.getBoolean("Prism");
        s.flash = tag.getInt("Flash");
        s.blindAll = tag.getBoolean("BlindAll");
        s.strobeAll = tag.getBoolean("StrobeAll");
        s.fadeTime = tag.getFloat("FadeTime");
        s.djSync = !tag.contains("DjSync") || tag.getBoolean("DjSync");
        s.manualBpm = tag.contains("ManualBpm") ? tag.getFloat("ManualBpm") : 120.0f;
        s.scenePage = tag.contains("ScenePage") ? tag.getInt("ScenePage") : 0;
        ListTag scenes = tag.getList("Scenes", Tag.TAG_COMPOUND);
        for (int i = 0; i < DmxProgram.SCENES; i++) {
            CompoundTag sc = i < scenes.size() ? scenes.getCompound(i) : new CompoundTag();
            if (!sc.contains("Levels")) {
                s.scenes[i] = null;
                continue;
            }
            float[] levels = new float[DmxProgram.GROUPS];
            int[] cols = new int[DmxProgram.GROUPS];
            readFloats(sc.getList("Levels", Tag.TAG_FLOAT), levels);
            int[] c = sc.getIntArray("Colors");
            System.arraycopy(c, 0, cols, 0, Math.min(c.length, DmxProgram.GROUPS));
            s.scenes[i] = new DmxProgram.Scene(levels, cols);
        }
    }

    private static ListTag floats(float[] values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(net.minecraft.nbt.FloatTag.valueOf(v));
        return list;
    }

    private static void readFloats(ListTag list, float[] into) {
        for (int i = 0; i < Math.min(list.size(), into.length); i++) into[i] = DmxProgram.clamp(list.getFloat(i));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeShown(tag);
        return tag;
    }
}
