package com.createbrewery.block.club;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A light show recorded to songs: per record, the console's look at points in the song (song
 * ticks, at the record's own speed). Played back, the look of the latest point passed is put on
 * the console, so the show hits the same moments every time the record plays. Pure, no Minecraft.
 */
final class DmxTimecode {
    /** A look is kept at most this often; moves closer together replace each other (a fader drag). */
    static final int MERGE = 5;
    static final int MAX_CUES = 256, MAX_SONGS = 16;

    /** The console's look without its stored scenes and held flashes. */
    record Look(float[] faders, int[] colors, float master, int program, int move, int rate, boolean blackout,
                int colorFx, int gobo, int zoom, boolean prism) {
        static Look of(DmxProgram.Settings s) {
            return new Look(s.faders.clone(), s.colors.clone(), s.master, s.program, s.move, s.rate, s.blackout,
                s.colorFx, s.gobo, s.zoom, s.prism);
        }

        void applyTo(DmxProgram.Settings s) {
            System.arraycopy(faders, 0, s.faders, 0, DmxProgram.GROUPS);
            System.arraycopy(colors, 0, s.colors, 0, DmxProgram.GROUPS);
            s.master = master;
            s.program = program;
            s.move = move;
            s.rate = rate;
            s.blackout = blackout;
            s.colorFx = colorFx;
            s.gobo = gobo;
            s.zoom = zoom;
            s.prism = prism;
        }

        /** The look without its faders, colours, master and blackout: what a scene puts back beside its faders. */
        void applyStyleTo(DmxProgram.Settings s) {
            s.program = program;
            s.move = move;
            s.rate = rate;
            s.colorFx = colorFx;
            s.gobo = gobo;
            s.zoom = zoom;
            s.prism = prism;
        }
    }

    record Cue(int tick, Look look) {}

    /** Per record (its item and components hashed), the cues in song order; the oldest record is forgotten first. */
    final Map<Integer, List<Cue>> songs = new LinkedHashMap<>();

    /** Records the look at song tick {@code tick}. False once the song's list is full. */
    boolean record(int song, int tick, Look look) {
        List<Cue> cues = songs.get(song);
        if (cues == null) {
            if (songs.size() >= MAX_SONGS) songs.remove(songs.keySet().iterator().next());
            songs.put(song, cues = new ArrayList<>());
        }
        int i = 0;
        while (i < cues.size() && cues.get(i).tick() < tick - MERGE) i++;
        // One within MERGE ticks is the same move still going: replace it.
        if (i < cues.size() && cues.get(i).tick() <= tick + MERGE) {
            cues.set(i, new Cue(tick, look));
            return true;
        }
        if (cues.size() >= MAX_CUES) return false;
        cues.add(i, new Cue(tick, look));
        return true;
    }

    /** The cue in effect at song tick {@code tick}: the latest at or before it, -1 before the first. */
    int at(int song, int tick) {
        List<Cue> cues = songs.get(song);
        if (cues == null) return -1;
        int found = -1;
        for (int i = 0; i < cues.size() && cues.get(i).tick() <= tick; i++) found = i;
        return found;
    }

    Look look(int song, int index) {
        return songs.get(song).get(index).look();
    }

    int count(int song) {
        List<Cue> cues = songs.get(song);
        return cues == null ? 0 : cues.size();
    }

    void clear(int song) {
        songs.remove(song);
    }
}
