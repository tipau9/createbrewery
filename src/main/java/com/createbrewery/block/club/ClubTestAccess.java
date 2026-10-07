package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The package-private club controls, for ClubGameTests. */
public final class ClubTestAccess {
    private ClubTestAccess() {}

    public static boolean regroup(Level level, BlockPos console, BlockPos light, int group) {
        return DmxPatch.regroup(level, console, light, group);
    }

    public static int linkedLights(Level level, BlockPos console) {
        return DmxPatch.linkedTo(level, console).size();
    }

    public static java.util.List<BlockPos> blastHazers(DmxConsoleBlockEntity console, net.minecraft.server.level.ServerLevel level) {
        return console.blastHazers(level);
    }

    public static void linkFixture(ItemStack stack, BlockPos console) {
        FixtureBlock.link(stack, console);
    }

    public static boolean applyFixtureLink(Level level, BlockPos pos, ItemStack stack) {
        return FixtureBlock.applyLink(level, pos, stack);
    }

    public static BlockPos consoleBooth(DmxConsoleBlockEntity dmx) {
        return dmx.boothPos();
    }

    public static void tickConsole(DmxConsoleBlockEntity dmx) {
        dmx.serverTick();
    }

    public static void setBlackout(DmxConsoleBlockEntity dmx, boolean on) {
        dmx.setBlackout(on);
    }

    public static boolean applyEffectLink(Level level, BlockPos pos, ItemStack stack) {
        return ConsoleLink.applyLink(level, pos, stack);
    }

    public static int effectGroup(Level level, BlockPos pos) {
        return ((ConsoleLinked) level.getBlockEntity(pos)).consoleLink().group;
    }

    public static float effectGate(Level level, BlockPos pos) {
        return ((ConsoleLinked) level.getBlockEntity(pos)).consoleLink().gate(level);
    }

    public static void loadEffect(Level level, BlockPos pos, net.minecraft.nbt.CompoundTag tag) {
        level.getBlockEntity(pos).loadCustomOnly(tag, level.registryAccess());
    }

    /** Sets the light settings (out of range on purpose), writes and reads them back: true if they wrapped and survived. */
    public static boolean lightSettingsRoundTrip(DmxConsoleBlockEntity dmx) {
        dmx.setColorFx(99);
        dmx.setGobo(-1);
        dmx.setZoom(7);
        dmx.setPrism(true);
        dmx.setMove(13);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        DmxConsoleBlockEntity.write(dmx.settings, tag);
        DmxProgram.Settings back = new DmxProgram.Settings();
        DmxConsoleBlockEntity.read(back, tag);
        return back.colorFx == Math.floorMod(99, DmxProgram.COLOR_FX) && back.gobo == Math.floorMod(-1, DmxProgram.GOBOS)
            && back.zoom == Math.floorMod(7, DmxProgram.ZOOMS) && back.prism && back.move == Math.floorMod(13, DmxProgram.MOVES);
    }

    /** A tag from before the light settings: only the old keys. */
    public static boolean oldTagDefaults() {
        DmxProgram.Settings old = new DmxProgram.Settings();
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        DmxConsoleBlockEntity.write(old, tag);
        tag.remove("ColorFx");
        tag.remove("Gobo");
        tag.remove("Prism");
        tag.remove("Zoom");
        DmxProgram.Settings back = new DmxProgram.Settings();
        back.zoom = 2;
        back.colorFx = 3;
        DmxConsoleBlockEntity.read(back, tag);
        return back.colorFx == 0 && back.gobo == 0 && back.zoom == 1 && !back.prism;
    }

    public static int fixtureFan(Level level, BlockPos pos) {
        return ((FixtureBlockEntity) level.getBlockEntity(pos)).getFan();
    }

    public static void setFixtureFan(Level level, BlockPos pos, int fan) {
        ((FixtureBlockEntity) level.getBlockEntity(pos)).setFan(fan);
    }

    /** Swaps deck {@code deck}'s record for the next one from the crate beside the booth. */
    public static boolean restock(DjBoothBlockEntity dj, int deck) {
        return dj.restock(deck);
    }

    /** Where a microphone's voice would come out. */
    public static java.util.List<net.minecraft.world.phys.Vec3> micSpeakers(MicrophoneBlockEntity mic) {
        return mic.speakers();
    }

    /** Stores the faders in scene 1, moves them, recalls it: true if they came back. */
    public static boolean sceneRoundTrip(DmxConsoleBlockEntity dmx) {
        dmx.setFader(0, 0.3f);
        dmx.setColor(0, 5);
        dmx.storeScene(0);
        dmx.setFader(0, 1f);
        dmx.setColor(0, 1);
        return dmx.recallScene(0) && dmx.settings.faders[0] == 0.3f && dmx.settings.colors[0] == 5 && !dmx.recallScene(3);
    }
}
