package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** The console an effect is linked to and its DMX group; what every linkable effect block entity holds. */
final class ConsoleLinkData {
    @Nullable
    BlockPos console;
    int group;

    void save(CompoundTag tag) {
        if (console == null) return;
        tag.put("Console", NbtUtils.writeBlockPos(console));
        tag.putInt("Group", group);
    }

    void load(CompoundTag tag) {
        console = NbtUtils.readBlockPos(tag, "Console").orElse(null);
        group = Math.floorMod(tag.getInt("Group"), DmxProgram.GROUPS);
    }

    @Nullable
    DmxConsoleBlockEntity console(Level level) {
        return console != null && level.isLoaded(console) && level.getBlockEntity(console) instanceof DmxConsoleBlockEntity dmx ? dmx : null;
    }

    /** 1 when unlinked or the console is not there (never stuck dark), else the console's gate for this group. */
    float gate(Level level) {
        DmxConsoleBlockEntity dmx = console(level);
        return dmx == null ? 1f : DmxProgram.gate(dmx.settings, group);
    }

    /** The booth of the linked console, null if unlinked. */
    @Nullable
    BlockPos booth(Level level) {
        DmxConsoleBlockEntity dmx = console(level);
        return dmx == null ? null : dmx.boothPos();
    }
}
