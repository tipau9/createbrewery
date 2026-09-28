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
    static final int GROUPS = 8, SCENES = 4;
    /** With flashing lights hidden, the most a level may change in one tick. */
    static final float GLIDE = 0.1f;
    static final int MANUAL = 0, AUTO = 1, CHASE = 2, PROGRAMS = 3;
    static final int CIRCLE = 0, FIGURE8 = 1, SWEEP = 2, BALLYHOO = 3, MOVES = 4;
    /** Beats per chase step. */
    static final int[] RATES = {1, 2, 4};
    /** White, red, amber, green, cyan, blue, magenta, UV. */
    static final int[] PALETTE = {0xFFFFFF, 0xFF1020, 0xFFA020, 0x20FF40, 0x20E0FF, 0x2040FF, 0xFF20C0, 0x7020FF};

    /** What the console is set to; saved by the console, the same on server and clients. */
    static final class Settings {
        final float[] faders = new float[GROUPS];
        final int[] colors = new int[GROUPS];
        float master = 1f;
        int program = AUTO, move = CIRCLE, rate;
        boolean blackout;
        /** Groups whose flash button is held, one bit each. */
        int flash;
        /** Stored looks, null where none was stored. */
        final Scene[] scenes = new Scene[SCENES];

        Settings() {
            java.util.Arrays.fill(faders, 0.8f);
        }
    }

    record Scene(float[] levels, int[] colors) {}

    final float[] level = new float[GROUPS];
    final int[] color = new int[GROUPS];
    /** Chase steps so far: moves on every {@code rate} beats, or every second with no music. */
    int step;
    /** 0..1 through the current beat, and the moving heads' clock (radians). */
    float beatPhase, movePhase;
    private boolean kickLatched, playing;
    private double sinceKick = 99, sinceStep, time;
    private int beats;
    private float drop, env;

    /**
     * Once a tick (client). The music heard at the console: {@code kick} and {@code drop} 0..1 as
     * {@code MusicPulse} gives them, {@code tension} how far into a build-up, {@code period} seconds a
     * beat. {@code tick} is the game time, so every rig strobes in phase. {@code noFlashing}: the
     * player asked for no flashing lights - no strobing, and every level glides instead of jumping.
     */
    void update(Settings s, long tick, float dt, float kick, float drop, float tension, boolean playing, double period, boolean noFlashing) {
        time += dt;
        sinceStep += dt;
        this.playing = playing;
        boolean beat = kick > 0.38f && !kickLatched;
        if (beat) kickLatched = true;
        else if (kick < 0.2f) kickLatched = false;
        if (beat) {
            sinceKick = 0;
            if (++beats % RATES[Math.floorMod(s.rate, RATES.length)] == 0) nextStep();
        } else {
            sinceKick += dt;
        }
        if (!playing && sinceStep >= 1.0) nextStep();
        period = Math.max(0.2, period);
        beatPhase = (float) Math.min(1.0, sinceKick / period);
        // One turn of a pattern every four beats; slow and steady with no music.
        movePhase += (float) (playing ? dt * Math.PI / (2 * period) : dt * 0.5);
        this.drop = Math.max(drop, this.drop * (float) Math.exp(-dt * 1.5));
        env = (float) Math.exp(-sinceKick * 7);
        // A breakdown: music, but no kick for a few beats. Minimal: the rig goes almost dark.
        boolean breakdown = playing && sinceKick > Math.max(2.0, 4 * period);
        // Build-ups strobe faster and faster: a flash every 5 ticks (4 a second) up to every other tick (10).
        // Counted in ticks, like the strobe: a sine at those rates, sampled once a tick, aliases to nothing.
        boolean shutterOpen = noFlashing || tension < 0.15f || tick % Math.round(5 - 3 * tension) == 0;

        List<Scene> stored = new ArrayList<>();
        for (Scene sc : s.scenes) if (sc != null) stored.add(sc);

        for (int g = 0; g < GROUPS; g++) {
            float fader = s.faders[g], lv;
            int col = PALETTE[Math.floorMod(s.colors[g], PALETTE.length)];
            switch (s.program) {
                case CHASE -> {
                    if (stored.isEmpty()) {
                        // No scenes: a running light through the groups.
                        lv = Math.floorMod(step, GROUPS) == g ? fader : 0f;
                    } else {
                        Scene sc = stored.get(Math.floorMod(step, stored.size()));
                        lv = sc.levels()[g];
                        col = PALETTE[Math.floorMod(sc.colors()[g], PALETTE.length)];
                    }
                }
                case AUTO -> {
                    if (!playing) {
                        lv = fader * (0.15f + 0.1f * (float) Math.sin(time * 1.2 + g * 0.8));
                    } else if (breakdown) {
                        lv = fader * 0.08f;
                    } else {
                        // Odd and even groups trade the beat: a punch that dies before the next kick.
                        boolean on = (g + step) % 2 == 0;
                        lv = fader * (on ? 0.15f + 0.85f * env : 0.1f);
                        if (!shutterOpen) lv = 0f;
                        else if (tension >= 0.15f) lv = Math.max(lv, fader * tension);
                    }
                    if (this.drop > 0.02f) {
                        lv = Math.max(lv, fader * this.drop);
                        col = mix(col, 0xFFFFFF, this.drop);
                    }
                }
                default -> lv = fader;
            }
            if ((s.flash >> g & 1) != 0) lv = 1f;
            float target = s.blackout ? 0f : clamp(lv * s.master);
            // Photosensitivity: at most a tenth of full range a tick, half a second from dark to full.
            level[g] = noFlashing ? level[g] + Math.max(-GLIDE, Math.min(GLIDE, target - level[g])) : target;
            color[g] = col;
        }
    }

    private void nextStep() {
        step++;
        sinceStep = 0;
    }

    /** Where a moving head of group {@code g} points: {pan, tilt} in degrees off its facing. */
    float[] aim(Settings s, int g) {
        double t = movePhase + g * Math.PI / 4;
        return switch (s.move) {
            case FIGURE8 -> new float[] {(float) (35 * Math.sin(t)), (float) (20 * Math.sin(2 * t))};
            // All heads side by side, fanning out and back.
            case SWEEP -> new float[] {(float) ((g - 3.5) * 8 * Math.sin(movePhase)), 15f};
            // Snapping to a new spot on every chase step.
            case BALLYHOO -> {
                int k = step * 31 + g * 17;
                yield new float[] {(float) (Math.floorMod(k * 7919, 100) - 50), (float) (Math.floorMod(k * 104729, 60) - 20)};
            }
            default -> new float[] {(float) (30 * Math.cos(t)), (float) (30 * Math.sin(t))};
        };
    }

    /** How lit pixel {@code i} of an LED bar is, times its group level: a knight-rider run across the bar on each beat. */
    float pixel(Settings s, int i, int pixels) {
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
