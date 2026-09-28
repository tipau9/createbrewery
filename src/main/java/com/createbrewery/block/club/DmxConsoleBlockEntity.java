package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

    public DmxConsoleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---------------------------------------------------------------- server: the controls

    void setFader(int g, float v) {
        settings.faders[g] = DmxProgram.clamp(v);
        sync();
    }

    void setMaster(float v) {
        settings.master = DmxProgram.clamp(v);
        sync();
    }

    void setColor(int g, int c) {
        settings.colors[g] = Math.floorMod(c, DmxProgram.PALETTE.length);
        sync();
    }

    void setProgram(int p) {
        settings.program = Math.floorMod(p, DmxProgram.PROGRAMS);
        sync();
    }

    void setMove(int m) {
        settings.move = Math.floorMod(m, DmxProgram.MOVES);
        sync();
    }

    void setRate(int r) {
        settings.rate = Math.floorMod(r, DmxProgram.RATES.length);
        sync();
    }

    void setBlackout(boolean b) {
        settings.blackout = b;
        sync();
    }

    /** A flash button pressed (or still held); it lets go on its own {@link #FLASH_HOLD} ticks after the last press. */
    void flash(int g) {
        if (level == null) return;
        flashUntil[g] = level.getGameTime() + FLASH_HOLD;
        serverTick();
    }

    void release(int g) {
        flashUntil[g] = 0;
        serverTick();
    }

    void storeScene(int i) {
        settings.scenes[i] = new DmxProgram.Scene(settings.faders.clone(), settings.colors.clone());
        sync();
    }

    /** Puts a stored scene back on the faders; false if none was stored there. */
    boolean recallScene(int i) {
        DmxProgram.Scene sc = settings.scenes[i];
        if (sc == null) return false;
        System.arraycopy(sc.levels(), 0, settings.faders, 0, DmxProgram.GROUPS);
        System.arraycopy(sc.colors(), 0, settings.colors, 0, DmxProgram.GROUPS);
        sync();
        return true;
    }

    void serverTick() {
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
        if (level == null || level.getGameTime() == ranAt) return program;
        ranAt = level.getGameTime();
        program.update(settings, ranAt, 0.05f, MusicPulse.kickNear(worldPosition), MusicPulse.dropNear(worldPosition),
            MusicPulse.tensionNear(worldPosition), MusicPulse.playingNear(worldPosition), MusicPulse.periodNear(worldPosition),
            Minecraft.getInstance().options.hideLightningFlash().get());
        return program;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(settings, tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        read(settings, tag);
    }

    static void write(DmxProgram.Settings s, CompoundTag tag) {
        tag.put("Faders", floats(s.faders));
        tag.putIntArray("Colors", s.colors);
        tag.putFloat("Master", s.master);
        tag.putInt("Program", s.program);
        tag.putInt("Move", s.move);
        tag.putInt("Rate", s.rate);
        tag.putBoolean("Blackout", s.blackout);
        tag.putInt("Flash", s.flash);
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
        s.flash = tag.getInt("Flash");
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
        saveAdditional(tag, registries);
        return tag;
    }
}
