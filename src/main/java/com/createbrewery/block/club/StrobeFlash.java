package com.createbrewery.block.club;

import com.createbrewery.drunk.DrunkClient;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Map;

/**
 * What the strobes do to your eyes: every strobe that fires this tick offers how hard it hits the
 * camera, the strongest one wins. That lights the whole lightmap up for the tick (the room is lit
 * as if every block were at light 15, see LightTextureMixin) and washes the screen with glare.
 * Client only.
 */
public final class StrobeFlash {
    private StrobeFlash() {}

    /** Per brightness 1..5: how far it reaches (blocks), how much it lights the room, how much it glares. */
    private static final double[] REACH = {16, 22, 28, 34, 40};
    private static final float[] ROOM = {0.35f, 0.6f, 0.85f, 1f, 1f};
    private static final float[] GLARE = {0.12f, 0.25f, 0.45f, 0.75f, 1f};

    private static float nextLight, nextGlare;

    private static final int HIDDEN = 0, BOUNCE = 1, DIRECT = 2;
    /** How long a line-of-sight result is kept while the eye stays put: a door swinging shut shows within a quarter second. */
    private static final int SIGHT_TICKS = 5;

    /** The last line of sight from a strobe's lens to the eye (see {@link #sight}). */
    private record Sight(Vec3 eye, long tick, int seen) {}

    private static final Map<BlockPos, Sight> SIGHTS = new HashMap<>();
    private static Level sightLevel;

    /**
     * Whether the eye sees the lens ({@code DIRECT}), only the light bouncing around it ({@code BOUNCE}),
     * or nothing ({@code HIDDEN}). Up to four raycasts, so a strobe keeps its answer for a few ticks
     * while the eye stays within half a block: a club full of strobes firing ten times a second
     * would otherwise cast thousands of rays a second.
     */
    private static int sight(Level level, BlockPos pos, Vec3 lens, Vec3 eye, Entity viewer) {
        if (viewer == null) return HIDDEN;
        if (level != sightLevel) {
            SIGHTS.clear();
            sightLevel = level;
        }
        long now = level.getGameTime();
        Sight last = SIGHTS.get(pos);
        if (last != null && now - last.tick < SIGHT_TICKS && last.eye.distanceToSqr(eye) < 0.25) return last.seen;

        int seen;
        if (!isLightOccluded(level, lens, eye, viewer)) {
            seen = DIRECT;
        } else if (!isLightOccluded(level, lens, eye.add(0.0, 1.2, 0.0), viewer)
            || !isLightOccluded(level, lens, eye.add(0.8, 0.0, 0.8), viewer)
            || !isLightOccluded(level, lens, eye.add(-0.8, 0.0, -0.8), viewer)) {
            // Not the lens itself, but its flash on the space around you (overhead, the sides).
            seen = BOUNCE;
        } else {
            seen = HIDDEN;
        }
        SIGHTS.put(pos.immutable(), new Sight(eye, now, seen));
        return seen;
    }
    private static float light, glare;

    /*
     * Retinal burn-in: staring into a firing lens bleaches the eye. The frame of that flash is held
     * (the strobe_burn pass) and lingers as a complementary ghost fixed on the screen, fading over
     * 4..6 seconds (drunk_final.fsh). The strongest stare this tick wins.
     */
    private static float nextBurn;
    private static Vec3 nextLens;
    private static float burnPeak, capture;
    private static long burnAt, toppedAt;
    private static float burnU = 0.5f, burnV = 0.5f, burnR = 0.1f;
    /** The field of view the world is drawn with (sprinting, potions and the drug effects included). */
    private static double fov = 70.0;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(StrobeFlash::onTick);
        NeoForge.EVENT_BUS.addListener(StrobeFlash::onGui);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, StrobeFlash::onFov);
    }

    /** How much burn-in is left, 0..1: slow to start fading, slow to go. */
    public static float burnIn() {
        if (burnPeak <= 0f) return 0f;
        float f = Mth.clamp(1f - (Util.getMillis() - burnAt) / 1000f / (4f + 2f * burnPeak), 0f, 1f);
        return burnPeak * f * f * (3f - 2f * f);
    }

    /** Where the burnt lens sits on screen (u, v) and its size in screen heights. */
    public static float[] burnPos() {
        return new float[] {burnU, burnV, burnR};
    }

    /**
     * How much of this frame to burn into the held picture (1 a fresh ghost, a fraction a top-up),
     * taken once. Waits for a frame the flash lights, or the held picture would be the dark room.
     */
    public static float takeCapture() {
        if (capture <= 0f || glare < 0.01f) return 0f;
        float c = capture;
        capture = 0f;
        return c;
    }

    /** The held picture is gone (the chain's targets were resized): so is the ghost. */
    public static void loseBurn() {
        burnPeak = capture = 0f;
    }

    /** How strongly the room is lit by strobes right now, 0..1 (read by the lightmap mixin). */
    public static float light() {
        return light;
    }

    /** Real physical raycasting: determines if light can travel from {@code from} to {@code to} through blocks. */
    public static boolean isLightOccluded(Level level, Vec3 from, Vec3 to, Entity entity) {
        Vec3 current = from;
        Vec3 totalDir = to.subtract(from);
        double totalDistSq = totalDir.lengthSqr();
        if (totalDistSq < 1e-4) return false;

        for (int step = 0; step < 8; step++) {
            BlockHitResult hit = level.clip(new ClipContext(current, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (hit.getType() == HitResult.Type.MISS) {
                return false;
            }
            BlockPos hitPos = hit.getBlockPos();
            BlockState state = level.getBlockState(hitPos);

            // Tinted glass, closed doors and trapdoors explicitly block light
            if (state.getBlock() instanceof TintedGlassBlock || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock) {
                return true;
            }

            // Transparent materials: clear glass, stained glass, iron bars, glass panes let light through
            if (state.getLightBlock(level, hitPos) == 0 && (state.getBlock() instanceof TransparentBlock || state.getBlock() instanceof IronBarsBlock)) {
                Vec3 stepDir = to.subtract(current);
                if (stepDir.lengthSqr() < 1e-4) return false;
                current = hit.getLocation().add(stepDir.normalize().scale(0.05));
                if (current.distanceToSqr(from) >= totalDistSq) {
                    return false;
                }
                continue;
            }

            // Opaque blocks: walls, closed doors, trapdoors, stone, wood, concrete, etc.
            return true;
        }
        return true;
    }

    /**
     * A strobe at {@code pos} flashing with {@code intensity} at brightness {@code power} (1..5);
     * {@code steady} is the non-flashing redstone mode.
     */
    static void offer(Level level, BlockPos pos, Direction facing, float intensity, int power, boolean steady) {
        if (intensity <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        // Photosensitivity: "Hide Lightning Flashes" turns the flashing off; a steady light may stay.
        if (!steady && mc.options.hideLightningFlash().get()) return;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 lens = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.55));
        Vec3 toEye = eye.subtract(lens);
        double dist = toEye.length();
        int p = Mth.clamp(power, 1, 5) - 1;
        if (dist > REACH[p]) return;

        float reach = (float) (1 - dist / REACH[p]);
        float exposure = reach * reach * (3 - 2 * reach);
        // Behind the strobe only the bounce off the walls reaches you
        Vec3 aim = Vec3.atLowerCornerOf(facing.getNormal());
        if (dist > 0.1 && toEye.dot(aim) < 0) exposure *= 0.5f;

        int seen = sight(level, pos, lens, eye, mc.player);
        // Fully occluded by walls/doors: in another room, behind a closed door or outside - no flash.
        if (seen == HIDDEN) return;
        boolean directSight = seen == DIRECT;
        if (!directSight) exposure *= 0.35f;

        // A flash in a lit room hardly registers; in the dark it blinds.
        int lightAtEye = level.getMaxLocalRawBrightness(BlockPos.containing(eye));
        float dark = 1f - lightAtEye / 15f * 0.75f;
        // From "Extreme" up it blinds in daylight too.
        if (p >= 3) dark = 1f;
        float lit = intensity * exposure * dark * ROOM[p];

        // Glare: staring into the lens hurts more than seeing its flash on the wall.
        float look = dist < 0.1 ? 1f : (float) Math.max(0, -mc.gameRenderer.getMainCamera().getLookVector().dot(toEye.toVector3f().normalize()));
        float stare = directSight ? look * look * look : 0f;

        nextLight = Math.max(nextLight, lit);
        // Only a real flash looked into burns in; the steady (redstone) light never does.
        if (directSight && !steady) {
            float burn = intensity * exposure * dark * GLARE[p] * stare;
            if (burn > nextBurn) {
                nextBurn = burn;
                nextLens = lens;
            }
        }
        // "Blinding" whites out the whole screen whenever you can see it, wherever you look.
        // A steady (redstone) light only glares when you stare into it; a white screen for minutes is no fun.
        float glareHit = steady ? 0.3f * stare : p == 4 ? (directSight ? 1f : 0.2f) : (directSight ? 0.35f + 0.65f * stare : 0.15f);
        nextGlare = Math.max(nextGlare, intensity * exposure * dark * GLARE[p] * glareHit);
    }

    private static void onTick(ClientTickEvent.Post event) {
        // The block entities of this tick have offered; take the result for the frames until the next tick.
        light = Mth.clamp(nextLight, 0f, 1f);
        glare = Mth.clamp(nextGlare, 0f, 1f);
        nextLight = nextGlare = 0f;
        if (nextBurn > 0.05f) burn(Minecraft.getInstance().gameRenderer.getMainCamera(), Math.min(1f, nextBurn * 1.5f), nextLens);
        nextBurn = 0f;
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            light = glare = 0f;
            loseBurn();
            SIGHTS.clear();
        } else if (level.getGameTime() % 200 == 0) {
            // Strobes broken or left behind.
            long now = level.getGameTime();
            SIGHTS.values().removeIf(sight -> now - sight.tick > 100);
        }
    }

    private static void burn(Camera cam, float hit, Vec3 lens) {
        long now = Util.getMillis();
        float left = burnIn();
        if (left < 0.2f) {
            // A fresh ghost: hold this flash, the lens where it is on screen now.
            Vec3 d = lens.subtract(cam.getPosition());
            double z = d.dot(new Vec3(cam.getLookVector()));
            if (z < 0.1) return;
            double tan = Math.tan(Math.toRadians(fov) / 2);
            var window = Minecraft.getInstance().getWindow();
            double aspect = (double) window.getWidth() / Math.max(1, window.getHeight());
            burnU = (float) (0.5 - 0.5 * d.dot(new Vec3(cam.getLeftVector())) / z / (tan * aspect));
            burnV = (float) (0.5 + 0.5 * d.dot(new Vec3(cam.getUpVector())) / z / tan);
            // The lens glows about a block wide; at least a spot, at most a third of the view.
            burnR = (float) Mth.clamp(0.5 / (z * tan), 0.03, 0.3);
            capture = 1f;
            toppedAt = now;
            burnPeak = Math.max(hit, left);
            burnAt = now;
            return;
        } else if (now - toppedAt >= 1000) {
            // Strobing on and on: top the held picture up once a second instead of replacing it on
            // every flash, so the ghost stays a ghost and does not turn into a choppy live copy.
            capture = Math.max(capture, 0.25f);
            toppedAt = now;
        }
        if (hit > left) {
            burnPeak = hit;
            burnAt = now;
        }
    }

    private static void onFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) fov = event.getFOV();
    }

    private static void onGui(RenderGuiEvent.Pre event) {
        // Shaderpacks usually light the world themselves and ignore the lightmap: then the screen flash does it all.
        float a = Math.min(0.97f, DrunkClient.shaderPack() ? Math.max(glare, light * 0.8f) : glare);
        if (a < 0.01f) return;
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (a * 255) << 24 | 0xF2F6FF);
    }
}
