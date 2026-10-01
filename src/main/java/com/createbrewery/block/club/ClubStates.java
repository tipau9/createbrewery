package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Client: which {@link ClubState} a light or effect listens to, advanced once per game tick no
 * matter how many blocks ask. A block follows its console's booth; with none, the nearest booth
 * that plays within the club's reach; with no booth at all it gets a state of its own fed from the
 * music heard at its position, which is what it did before there was a shared state.
 */
final class ClubStates {
    private static final double REACH = 32.0;
    private static final int KEEP = 200;

    private static final class Entry {
        final ClubState state = new ClubState();
        long tick = Long.MIN_VALUE;
    }

    private static final Map<BlockPos, Entry> BOOTH = new HashMap<>(), LOOSE = new HashMap<>();
    private static WeakReference<Level> seen = new WeakReference<>(null);
    private static long lastPrune;

    private ClubStates() {}

    static ClubState at(Level level, BlockPos pos, @Nullable BlockPos linkedBooth) {
        if (seen.get() != level) {
            BOOTH.clear();
            LOOSE.clear();
            seen = new WeakReference<>(level);
        }
        // The server resets the client's game time every second or so, so it can repeat; the client's own tick count only rises.
        long now = Minecraft.getInstance().gui.getGuiTicks();
        DjBoothBlockEntity dj = null;
        if (linkedBooth != null && level.isLoaded(linkedBooth) && level.getBlockEntity(linkedBooth) instanceof DjBoothBlockEntity linked) dj = linked;
        if (dj == null) dj = DjBoothBlockEntity.nearestPlaying(level, pos, REACH);

        BlockPos at = dj != null ? dj.getBlockPos() : pos;
        Map<BlockPos, Entry> map = dj != null ? BOOTH : LOOSE;
        Entry e = map.computeIfAbsent(at.immutable(), k -> new Entry());
        if (e.tick != now) {
            e.tick = now;
            e.state.update(0.05f, MusicPulse.kickNear(at), MusicPulse.dropNear(at), MusicPulse.tensionNear(at),
                MusicPulse.playingNear(at), MusicPulse.periodNear(at),
                dj != null ? dj.mixer(level.getGameTime()) : ClubState.Mixer.NONE,
                Minecraft.getInstance().options.hideLightningFlash().get());
            if (map.size() > 64 && now != lastPrune) {
                lastPrune = now;
                map.values().removeIf(old -> old.tick < now - KEEP);
            }
        }
        return e.state;
    }
}
