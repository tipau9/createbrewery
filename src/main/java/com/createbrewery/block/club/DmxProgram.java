package com.createbrewery.block.club;

import java.util.ArrayList;
import java.util.List;

/**
 * What a DMX console makes its fixtures do: per group a level and a colour, a moving-head position
 * and an LED bar pixel pattern, from the console's settings and the music heard at it. Kept free of
 * Minecraft classes so it can be unit-tested; the console runs one on each client (see
 * {@link DmxConsoleBlockEntity}), and a fixture linked to no console runs its own.
 */
final class DmxProgram {
    static final int GROUPS = 8, SCENES = 16, SCENE_PAGES = 4, SCENES_PER_PAGE = 4;
    /** With flashing lights hidden, the most a level may change in one tick. */
    static final float GLIDE = 0.1f;
    static final int MANUAL = 0, AUTO = 1, CHASE = 2, PROGRAMS = 3;
    static final int CIRCLE = 0, FIGURE8 = 1, SWEEP = 2, BALLYHOO = 3, CROWD = 4, STRAIGHT = 5, FAN = 6, MIRROR = 7, MOVES = 8;
    static final int STATIC = 0, FADE = 1, RAINBOW = 2, COMPLEMENT = 3, COLOR_FX = 4;
    /** LED bar pixel effects: AUTO is the run on each beat the auto program always had. */
    static final int PX_AUTO = 0, PX_SOLID = 1, PX_CHASE = 2, PX_WAVE = 3, PX_SPARKLE = 4, PX_FILL = 5, PIXEL_FX = 6;
    /** Where the moving heads point: their movement program, or fixed on the DJ or the dance floor. */
    static final int POS_PROGRAM = 0, POS_DJ = 1, POS_FLOOR = 2, POSITIONS = 3;
    static final int GOBOS = 4, ZOOMS = 3;
    /** Beats per chase step. */
    static final int[] RATES = {1, 2, 4};
    /** White, red, amber, green, cyan, blue, magenta, UV. */
    static final int[] PALETTE = {0xFFFFFF, 0xFF1020, 0xFFA020, 0x20FF40, 0x20E0FF, 0x2040FF, 0xFF20C0, 0x7020FF};

    /** Industry standard Lee / Rosco gel filters */
    static final int[] GEL_FILTERS = {
        0xFFFFFF, // 0: Open White
        0x00E5FF, // 1: Lee 116 Tokyo Disco / Electric Cyan
        0xFF1020, // 2: Lee 106 Primary Red
        0xFF5500, // 3: Lee 105 Sunset Orange / Flame
        0xFFA020, // 4: Lee 152 Golden Amber / Warm Tungsten
        0x20FF40, // 5: Lee 139 Toxic Green / Acid
        0x2040FF, // 6: Lee 174 Steel Blue
        0xFF007F, // 7: Lee 128 Laser Magenta / Neon Pink
        0x7020FF, // 8: Lee 181 Congo Blue / Deep UV
        0xFFEE00  // 9: Lee 101 Sun Yellow
    };

    static int resolveColor(int val) {
        if (val >= 0 && val < PALETTE.length) return PALETTE[val];
        return val;
    }

    /** What the console is set to; saved by the console, the same on server and clients. */
    static final class Settings {
        final float[] faders = new float[GROUPS];
        final int[] colors = new int[GROUPS];
        float master = 1f;
        int program = AUTO, move = CIRCLE, rate;
        /** Colour program (see COLOR_FX), the moving heads' gobo and zoom, and whether their prism is in. */
        int colorFx, gobo, zoom = 1;
        /** LED bar pixel effect (PX_*) and where the heads point (POS_*). */
        int pixelFx, position;
        boolean prism;
        boolean blackout;
        /** Bump overrides: blind all (tungsten wash) and strobe all (20 Hz burst) */
        boolean blindAll;
        boolean strobeAll;
        /** Crossfade time in seconds for scene recalls (0.0s = snap) */
        float fadeTime = 0.0f;
        /** Beat timing: lock to DJ booth beat or follow manual tap tempo BPM */
        boolean djSync = true;
        float manualBpm = 120.0f;
        int scenePage = 0;
        /** Groups whose flash button is held, one bit each. */
        int flash;
        /** Groups that flash to full on every drop (blinders), one bit each. */
        int dropFlash;
        /** Stored looks, null where none was stored. */
        final Scene[] scenes = new Scene[SCENES];

        Settings() {
            java.util.Arrays.fill(faders, 0.8f);
        }
    }

    /**
     * A stored look: the faders and colours, and (scenes stored since 1.0.1) the rest of the desk -
     * program, movement, rate, colour program, gobo, zoom and prism. Master and blackout stay the operator's.
     */
    record Scene(float[] levels, int[] colors, @org.jetbrains.annotations.Nullable DmxTimecode.Look look) {
        Scene(float[] levels, int[] colors) {
            this(levels, colors, null);
        }
    }

    final float[] level = new float[GROUPS];
    final int[] color = new int[GROUPS];
    /** Chase steps so far: moves on every {@code rate} beats, or every second with no music. */
    int step;
    /** 0..1 through the current beat, and the moving heads' clock (radians). */
    float beatPhase, movePhase;
    private boolean playing;
    private double sinceStep, time, clock;
    private int beats;
    private boolean noFlashing;
    private final List<Scene> stored = new ArrayList<>();
    // What the last update saw, for levelFor / colorFor of fanned fixtures.
    private float snapEnv, snapTension, snapDrop, snapBass;
    private boolean snapBreakdown, snapShutter;
    private final ClubState own = new ClubState();

    /**
     * Raw-number entry (a fixture linked to no booth, and the tests): the numbers go through a
     * private {@link ClubState}. The rig itself uses {@link #update(Settings, long, float, ClubState)}.
     */
    void update(Settings s, long tick, float dt, float kick, float drop, float tension, boolean playing, double period, boolean noFlashing) {
        own.update(dt, kick, drop, tension, playing, period, ClubState.Mixer.NONE, noFlashing);
        update(s, tick, dt, own);
    }

    /**
     * Once a tick (client): what the club is doing, as {@link ClubState} works it out. {@code tick}
     * is the game time, so every rig strobes in phase. With {@code c.noFlashing}: no strobing, and
     * every level glides instead of jumping.
     */
    void update(Settings s, long tick, float dt, ClubState c) {
        time += dt;
        sinceStep += dt;
        playing = c.playing;
        noFlashing = c.noFlashing;
        float tension = c.buildUp;

        double period;
        if (s.djSync && c.playing && c.period > 0) {
            period = c.period;
            if (c.beat && ++beats % RATES[Math.floorMod(s.rate, RATES.length)] == 0) nextStep();
            beatPhase = c.beatPhase;
        } else {
            period = 60.0 / Math.max(30.0, s.manualBpm);
            if (sinceStep >= period * RATES[Math.floorMod(s.rate, RATES.length)]) nextStep();
            beatPhase = (float) ((time / period) % 1.0);
        }
        clock += dt / Math.max(0.1, period);
        // One turn of a pattern every four beats; slow and steady with no music.
        movePhase += (float) (dt * Math.PI / (2 * Math.max(0.1, period)));

        snapEnv = c.env;
        snapTension = tension;
        snapDrop = c.dropLevel;
        snapBass = Math.max(c.bassCut, c.filterClosed);
        snapBreakdown = c.breakdown;
        // Build-ups strobe faster and faster: a flash every 5 ticks (4 a second) up to every other tick (10).
        // Counted in ticks, like the strobe: a sine at those rates, sampled once a tick, aliases to nothing.
        snapShutter = noFlashing || tension < 0.15f || tick % Math.round(5 - 3 * tension) == 0;

        stored.clear();
        for (Scene sc : s.scenes) if (sc != null) stored.add(sc);

        for (int g = 0; g < GROUPS; g++) {
            float target = target(s, g, 0);
            // Photosensitivity: at most a tenth of full range a tick, half a second from dark to full.
            level[g] = noFlashing ? level[g] + Math.max(-GLIDE, Math.min(GLIDE, target - level[g])) : target;
            color[g] = colorFor(s, g, 0);
        }
    }

    boolean noFlashing() {
        return noFlashing;
    }

    /** The level group {@code group} would show for a fixture {@code fan} steps along (no glide, see {@link #update}). */
    float levelFor(Settings s, int group, int fan) {
        return target(s, Math.floorMod(group, GROUPS), fan);
    }

    private float target(Settings s, int g, int fan) {
        float fader = s.faders[g], lv;
        int stp = step + fan;
        switch (s.program) {
            case CHASE -> {
                if (stored.isEmpty()) {
                    // No scenes: a running light through the groups.
                    lv = Math.floorMod(stp, GROUPS) == g ? fader : 0f;
                } else {
                    lv = stored.get(Math.floorMod(stp, stored.size())).levels()[g];
                }
            }
            case AUTO -> {
                if (!playing) {
                    lv = fader * (0.15f + 0.1f * (float) Math.sin(time * 1.2 + g * 0.8 + fan * 0.4));
                } else if (snapBreakdown) {
                    lv = fader * 0.08f;
                } else {
                    // Odd and even groups trade the beat: a punch that dies before the next kick.
                    boolean on = Math.floorMod(g + stp, 2) == 0;
                    lv = fader * (on ? 0.15f + 0.85f * snapEnv : 0.1f);
                    if (!snapShutter) lv = 0f;
                    else if (snapTension >= 0.15f) lv = Math.max(lv, fader * snapTension);
                }
                // The DJ takes the lows or closes the filter: the room darkens with it (a drop below still hits full).
                lv *= 1f - 0.6f * snapBass;
                if (snapDrop > 0.02f) lv = Math.max(lv, fader * snapDrop);
            }
            default -> lv = fader;
        }
        if ((s.flash >> g & 1) != 0) lv = 1f;
        if ((s.dropFlash >> g & 1) != 0 && snapDrop > 0.02f) lv = Math.max(lv, snapDrop);
        if (s.blackout) return 0f;
        if (s.blindAll) return 1f;
        if (s.strobeAll) return snapShutter ? 1f : 0f;
        return clamp(lv * s.master);
    }

    /** The colour of group {@code group} for a fixture {@code fan} steps along: palette, colour program, scene or drop white. */
    int colorFor(Settings s, int group, int fan) {
        return colorFor(s, Math.floorMod(group, GROUPS), fan, 0.0);
    }

    /** The colour of pixel {@code i} of {@code pixels} on an LED bar: a colour program spreads along the bar. */
    int pixelColor(Settings s, int i, int pixels, int group, int fan) {
        return colorFor(s, Math.floorMod(group, GROUPS), fan, i / (double) Math.max(1, pixels));
    }

    private int colorFor(Settings s, int g, int fan, double spread) {
        if (s.blindAll) return 0xFFE0A0; // Warm tungsten wash
        if (s.strobeAll) return 0xFFFFFF; // Blinding white
        int stp = step + fan;
        if (s.program == CHASE && !stored.isEmpty()) {
            return resolveColor(stored.get(Math.floorMod(stp, stored.size())).colors()[g]);
        }
        int base = resolveColor(s.colors[g]);
        int idx = s.colors[g] >= 0 && s.colors[g] < PALETTE.length ? s.colors[g] : 0;
        int col = switch (s.colorFx) {
            case FADE -> {
                // One palette colour every 8 beats, each fixture a step along the palette per fan.
                double p = clock / 8.0 + idx + fan / 8.0 + spread * 2;
                int k = (int) Math.floor(p);
                yield mix(PALETTE[Math.floorMod(k, PALETTE.length)], PALETTE[Math.floorMod(k + 1, PALETTE.length)], (float) (p - k));
            }
            case RAINBOW -> hsv((float) (((clock / 16.0 + g / 8.0 + fan / 8.0 + spread * 0.5) % 1.0 + 1.0) % 1.0));
            case COMPLEMENT -> Math.floorMod(stp + (spread >= 0.5 ? 1 : 0), 2) == 0 ? base : base ^ 0xFFFFFF;
            default -> base;
        };
        if (s.program == AUTO && snapDrop > 0.02f) col = mix(col, 0xFFFFFF, snapDrop);
        return col;
    }

    /** A fully saturated colour of the given hue, 0..1. */
    static int hsv(float h) {
        float x = h * 6f;
        int i = (int) Math.floor(x);
        float f = x - i, q = 1f - f;
        float r, g, b;
        switch (Math.floorMod(i, 6)) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return Math.round(r * 255f) << 16 | Math.round(g * 255f) << 8 | Math.round(b * 255f);
    }

    /** How much of its brightness an effect in {@code group} may show: the console's blackout, master and the group's fader (or its held flash). */
    static float gate(Settings s, int group) {
        if (s.blackout) return 0f;
        int g = Math.floorMod(group, GROUPS);
        return clamp(s.master * ((s.flash >> g & 1) != 0 ? 1f : s.faders[g]));
    }

    private void nextStep() {
        step++;
        sinceStep = 0;
    }

    /** Where a moving head of group {@code g} points: {pan, tilt} in degrees off its facing. */
    float[] aim(Settings s, int g) {
        return aim(s, g, 0);
    }

    float[] aim(Settings s, int g, int fan) {
        double t = movePhase + g * Math.PI / 4 + fan * Math.PI / 8;
        return switch (s.move) {
            case FIGURE8 -> new float[] {(float) (35 * Math.sin(t)), (float) (20 * Math.sin(2 * t))};
            // All heads side by side, fanning out and back.
            case SWEEP -> new float[] {(float) ((g - 3.5) * 8 * Math.sin(movePhase + fan * Math.PI / 8)), 15f};
            // Snapping to a new spot on every chase step.
            case BALLYHOO -> {
                int k = (step + fan) * 31 + g * 17;
                yield new float[] {(float) (Math.floorMod(k * 7919, 100) - 50), (float) (Math.floorMod(k * 104729, 60) - 20)};
            }
            // Heads converge on the floor in front of them and sway slowly.
            case CROWD -> new float[] {(float) ((g - 3.5) * 3 + 6 * Math.sin(movePhase * 0.5 + fan * Math.PI / 8)), (float) (20 + 8 * Math.sin(movePhase * 0.25))};
            // Straight along the facing: a floor-mounted head points at the ceiling, a hung one at the floor.
            case STRAIGHT -> new float[] {0f, 0f};
            // A fixed spread across the room, a little wider for each fan step.
            case FAN -> new float[] {(float) ((g - 3.5) * 10 + fan * 2), 15f};
            // The left half of the groups mirrors the right.
            case MIRROR -> new float[] {(float) ((g < GROUPS / 2 ? -1 : 1) * 30 * Math.cos(t)), (float) (30 * Math.sin(t))};
            default -> new float[] {(float) (30 * Math.cos(t)), (float) (30 * Math.sin(t))};
        };
    }

    /** How lit pixel {@code i} of an LED bar is, times its group level, for the console's pixel effect. */
    float pixel(Settings s, int i, int pixels) {
        switch (s.pixelFx) {
            case PX_SOLID:
                return 1f;
            case PX_CHASE: {
                // Two pixels a beat round the bar, with a short tail.
                double at = (clock * 2) % pixels, d = Math.abs(i - at);
                d = Math.min(d, pixels - d);
                return Math.max(0.1f, 1f - (float) d / 1.5f);
            }
            case PX_WAVE:
                return 0.55f + 0.45f * (float) Math.sin(2 * Math.PI * (i / (double) pixels - clock / 2));
            case PX_SPARKLE: {
                // A new random quarter of the pixels four times a beat.
                int h = (i * 73856093 ^ (int) Math.floor(clock * 4) * 19349663) * 0x9E3779B1;
                return (h >>> 24) < 70 ? 1f : 0.1f;
            }
            case PX_FILL:
                return i < Math.ceil(beatPhase * pixels) ? 1f : 0.1f;
            default:
                break;
        }
        // AUTO: a knight-rider run across the bar on each beat, while the auto program plays.
        if (s.program != AUTO || !playing) return 1f;
        float at = beatPhase * (pixels - 1);
        if (step % 2 == 1) at = pixels - 1 - at;
        return Math.max(0.15f, 1f - Math.abs(i - at) / 2.5f);
    }

    static int mix(int a, int b, float t) {
        t = clamp(t);
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
        int g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
