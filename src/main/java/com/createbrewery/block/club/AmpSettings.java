package com.createbrewery.block.club;

import com.createbrewery.drunk.DeckFx;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything an amp rack is set to: power, master, preset, the crossover, five zones with their
 * DSP, and which zone each linked speaker plays. Clamps whatever it is given, so a bad packet or
 * a bad save never silences the club. No Minecraft classes here; the rack does the NBT.
 */
public final class AmpSettings {
    public static final int FLOOR = 0, SUBS = 1, DELAY = 2, ROOM = 3, MONITOR = 4, ZONES = 5;
    public static final int CLUB = 0, LIVE = 1, BACKGROUND = 2, NIGHT = 3, CUSTOM = 4;
    public static final int LR24 = 0, LR48 = 1;
    public static final float MIN_MASTER = -40, MAX_MASTER = 6, MIN_GAIN = -30, MAX_GAIN = 6, MAX_EQ = 12,
        MIN_HPF = 20, MAX_HPF = 200, MAX_DELAY = 100, MIN_LIMIT = -20, MIN_CROSSOVER = 60, MAX_CROSSOVER = 200;

    /** What a move does; the same bytes go over the wire (see AmpControl). */
    public static final byte POWER = 0, MASTER = 1, PRESET = 2, AUTO_SETUP = 3, CROSSOVER = 4, SLOPE = 5, ALIGN = 6,
        ZONE_GAIN = 7, ZONE_MUTE = 8, ZONE_LOW = 9, ZONE_MID = 10, ZONE_HIGH = 11, ZONE_HPF = 12, ZONE_DELAY = 13,
        ZONE_INVERT = 14, ZONE_LIMIT = 15, ASSIGN = 16, RESET = 17, ZONE_SOLO = 18;

    /** One zone's channel: gain and EQ in dB, high-pass in Hz (0 off), delay in ms, limit in dBFS. */
    public static final class Zone {
        public float gain, low, mid, high, hpf, delayMs, limit;
        public boolean mute, invert;
        /** Only the soloed zones play (and the DJ monitor): to hear one zone on its own. */
        public boolean solo;
    }

    /** A top's zone, and whether a person put it there (the background sort leaves those alone). */
    public record Assignment(int zone, boolean manual) {}

    /** The flat tags racks saved before v2 (the original rack and bd56b48). */
    public record Legacy(float crossover, float subKnob, float topKnob, boolean propagation, boolean muteTops,
                         boolean muteSubs, int subCut, int contour, float delayMs) {}

    // master, FLOOR, SUBS, DELAY, ROOM, MONITOR
    private static final float[][] PRESET_GAINS = {
        {0, 0, 3, 0, -6, -10},
        {0, 0, 0, 0, -6, -6},
        {-12, 0, -6, -3, -3, -10},
        {-20, 0, -12, -6, -6, -12}};
    private static final float[] PRESET_SUB_HPF = {30, 35, 40, 40};

    public boolean power = true;
    public float master;
    public int preset = CLUB;
    public float crossover = 100;
    public int slope = LR24;
    /** Speed of sound and delay towers. */
    public boolean align;
    public final Zone[] zones = new Zone[ZONES];
    /** Tops by {@code BlockPos.asLong()}. Subwoofers are always SUBS and the booth always MONITOR. */
    public final Map<Long, Assignment> assign = new HashMap<>();

    public AmpSettings() {
        for (int z = 0; z < ZONES; z++) zones[z] = new Zone();
        applyPreset(CLUB);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float lin(float db) {
        return (float) Math.pow(10, db / 20);
    }

    private static boolean zoneAction(byte action) {
        return action >= ZONE_GAIN && action <= ZONE_LIMIT;
    }

    /** A move from the screen or a packet; false if it was refused or is not a settings move. */
    public boolean set(byte action, int zone, float v) {
        if (!Float.isFinite(v)) return false;
        if (action == ZONE_SOLO) {
            // A check, not a sound of its own: the preset stays lit.
            if (zone < 0 || zone >= ZONES) return false;
            zones[zone].solo = v > 0.5f;
            return true;
        }
        if ((zoneAction(action) || action == RESET) && (zone < 0 || zone >= ZONES)) return false;
        Zone z = zoneAction(action) ? zones[zone] : null;
        switch (action) {
            case POWER -> {
                power = v > 0.5f;
                return true;
            }
            case PRESET -> {
                if (v < 0 || v >= CUSTOM) return false;
                applyPreset((int) v);
                return true;
            }
            case RESET -> {
                byte target = (byte) v;
                if (target == POWER || target == PRESET || target == RESET || target == AUTO_SETUP || target == ASSIGN) return false;
                return set(target, zone, target == CROSSOVER ? 100f : 0f);
            }
            case MASTER -> master = clamp(v, MIN_MASTER, MAX_MASTER);
            case CROSSOVER -> crossover = clamp(v, MIN_CROSSOVER, MAX_CROSSOVER);
            case SLOPE -> slope = v > 0.5f ? LR48 : LR24;
            case ALIGN -> align = v > 0.5f;
            case ZONE_GAIN -> z.gain = clamp(v, MIN_GAIN, MAX_GAIN);
            case ZONE_MUTE -> z.mute = v > 0.5f;
            case ZONE_LOW -> z.low = clamp(v, -MAX_EQ, MAX_EQ);
            case ZONE_MID -> z.mid = clamp(v, -MAX_EQ, MAX_EQ);
            case ZONE_HIGH -> z.high = clamp(v, -MAX_EQ, MAX_EQ);
            case ZONE_HPF -> z.hpf = v < MIN_HPF ? 0 : Math.min(v, MAX_HPF);
            case ZONE_DELAY -> z.delayMs = clamp(v, 0, MAX_DELAY);
            case ZONE_INVERT -> z.invert = v > 0.5f;
            case ZONE_LIMIT -> z.limit = clamp(v, MIN_LIMIT, 0);
            default -> {
                return false;
            }
        }
        preset = CUSTOM;
        return true;
    }

    /** What {@link #set} would have written, for the screen's controls. */
    public float get(byte action, int zone) {
        Zone z = zones[zone >= 0 && zone < ZONES ? zone : 0];
        return switch (action) {
            case POWER -> power ? 1 : 0;
            case MASTER -> master;
            case PRESET -> preset;
            case CROSSOVER -> crossover;
            case SLOPE -> slope;
            case ALIGN -> align ? 1 : 0;
            case ZONE_GAIN -> z.gain;
            case ZONE_MUTE -> z.mute ? 1 : 0;
            case ZONE_LOW -> z.low;
            case ZONE_MID -> z.mid;
            case ZONE_HIGH -> z.high;
            case ZONE_HPF -> z.hpf;
            case ZONE_DELAY -> z.delayMs;
            case ZONE_INVERT -> z.invert ? 1 : 0;
            case ZONE_LIMIT -> z.limit;
            case ZONE_SOLO -> z.solo ? 1 : 0;
            default -> 0;
        };
    }

    /** Master, zone gains, EQs and the subs' high-pass; never zones, delays or the crossover. */
    public void applyPreset(int p) {
        if (p < 0 || p >= CUSTOM) return;
        float[] g = PRESET_GAINS[p];
        master = g[0];
        for (int z = 0; z < ZONES; z++) {
            zones[z].gain = g[z + 1];
            zones[z].low = zones[z].mid = 0;
            zones[z].high = p == BACKGROUND ? -2 : 0;
        }
        if (p == LIVE) zones[FLOOR].mid = 2;
        zones[SUBS].hpf = PRESET_SUB_HPF[p];
        preset = p;
    }

    /** After loading: every value back in range, without counting as a move. */
    public void clampAll() {
        int p = preset;
        set(MASTER, 0, Float.isFinite(master) ? master : 0);
        set(CROSSOVER, 0, Float.isFinite(crossover) ? crossover : 100);
        set(SLOPE, 0, slope);
        for (int z = 0; z < ZONES; z++) {
            for (byte a = ZONE_GAIN; a <= ZONE_LIMIT; a++) {
                float v = get(a, z);
                set(a, z, Float.isFinite(v) ? v : 0);
            }
        }
        preset = p < 0 || p > CUSTOM ? CUSTOM : p;
    }

    /** Whether any zone is soloed. */
    public boolean soloed() {
        for (Zone z : zones) if (z.solo) return true;
        return false;
    }

    /** How hard a zone is driven, as a gain: master times the zone's fader; 0 muted, at the bottom, or another zone soloed. */
    public float drive(int zone) {
        Zone z = zones[zone];
        if (z.mute || z.gain <= MIN_GAIN) return 0f;
        if (zone != MONITOR && !z.solo && soloed()) return 0f;
        return lin(z.gain + master);
    }

    public int zoneOf(long pos) {
        Assignment a = assign.get(pos);
        return a == null ? FLOOR : a.zone();
    }

    /** A knob 0..1 of the old rack as dB; the bottom of the old knob is off. */
    private static float knobDb(float knob) {
        float g = DeckFx.eqGain(knob);
        return g <= 0.001f ? MIN_GAIN : (float) (20 * Math.log10(g));
    }

    public static AmpSettings migrate(Legacy old) {
        AmpSettings s = new AmpSettings();
        s.crossover = clamp(Float.isFinite(old.crossover()) ? old.crossover() : 100, MIN_CROSSOVER, MAX_CROSSOVER);
        float contour = old.contour() == 1 ? 2.6f : old.contour() == 2 ? 1.9f : 0f;
        s.zones[SUBS].gain = clamp(knobDb(old.subKnob()) + (old.subKnob() <= 0 ? 0 : contour), MIN_GAIN, MAX_GAIN);
        s.zones[SUBS].mute = old.muteSubs();
        s.zones[SUBS].hpf = old.subCut() == 1 ? 30 : old.subCut() == 2 ? 40 : 0;
        for (int z : new int[] {FLOOR, DELAY, ROOM}) {
            s.zones[z].gain = clamp(knobDb(old.topKnob()), MIN_GAIN, MAX_GAIN);
            s.zones[z].mute = old.muteTops();
        }
        s.align = old.propagation();
        for (Zone z : s.zones) z.delayMs = clamp(Float.isFinite(old.delayMs()) ? old.delayMs() : 0, 0, MAX_DELAY);
        s.preset = CUSTOM;
        return s;
    }
}
