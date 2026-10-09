package com.createbrewery.block.club;

import com.createbrewery.drunk.MusicPulse;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Client: tells the booth a record's tempo once it has played alone long enough to be sure of it.
 * The beat detector hears everything at once, so only a booth's lone playing deck is measured.
 */
public final class TrackInfoReporter {
    private TrackInfoReporter() {}

    /** Seconds a record plays before its tempo counts; beats the tempo must hold within {@link #STEADY}. */
    private static final double MIN_SECONDS = 30.0;
    private static final int STEADY_BEATS = 8;
    private static final double STEADY = 0.01;

    private static BlockPos boothAt;
    private static int deckAt = -1;
    private static double heldPeriod;
    private static int heldSince = -1;

    /** Once a frame per deck track (MusicPulse). */
    public static void check(DjBoothBlockEntity dj, int deck, MusicPulse.Track t) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack disc = dj.getDisc(deck);
        if (mc.player == null || disc.isEmpty() || disc.has(TrackInfo.TRACK_INFO.get()) || !dj.isPlaying(deck)
            || !mc.player.canInteractWithBlock(dj.getBlockPos(), 1.0)) return;
        for (int i = 0; i < DjBoothBlockEntity.DECKS; i++) if (i != deck && dj.isPlaying(i)) return;
        if (!dj.getBlockPos().equals(boothAt) || deck != deckAt) {
            boothAt = dj.getBlockPos();
            deckAt = deck;
            heldSince = -1;
        }
        if (t.rate <= 0 || t.deckFrame / (double) t.rate < MIN_SECONDS) return;
        double period = t.getBeatPeriod();
        int beats = t.getBeats();
        if (heldSince < 0 || Math.abs(period - heldPeriod) > heldPeriod * STEADY) {
            heldPeriod = period;
            heldSince = beats;
            return;
        }
        if (beats - heldSince < STEADY_BEATS) return;
        heldSince = -1;
        DjControl.send(dj.getBlockPos(), DjControl.REPORT_BPM, deck, TrackInfo.baseBpm(period, dj.getPitch(deck)));
    }
}
