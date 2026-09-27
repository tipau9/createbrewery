package com.createbrewery.block.club;

import com.createbrewery.drunk.DrunkClient;
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
import net.neoforged.neoforge.common.NeoForge;

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
    private static float light, glare;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(StrobeFlash::onTick);
        NeoForge.EVENT_BUS.addListener(StrobeFlash::onGui);
    }

    /** How strongly the room is lit by strobes right now, 0..1 (read by the lightmap mixin). */
    public static float light() {
        return light;
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
        // Behind the strobe only the bounce off the walls reaches you, behind a wall a little glow.
        Vec3 aim = Vec3.atLowerCornerOf(facing.getNormal());
        if (dist > 0.1 && toEye.dot(aim) < 0) exposure *= 0.5f;
        boolean seen = mc.player != null && level.clip(new ClipContext(lens, eye, ClipContext.Block.VISUAL,
            ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
        if (!seen) exposure *= 0.3f;
        // A flash in a lit room hardly registers; in the dark it blinds.
        int lightAtEye = level.getMaxLocalRawBrightness(BlockPos.containing(eye));
        float dark = 1f - lightAtEye / 15f * 0.75f;
        // From "Extreme" up it blinds in daylight too.
        if (p >= 3) dark = 1f;
        float lit = intensity * exposure * dark * ROOM[p];

        // Glare: staring into the lens hurts more than seeing its flash on the wall.
        float look = dist < 0.1 ? 1f : (float) Math.max(0, -mc.gameRenderer.getMainCamera().getLookVector().dot(toEye.toVector3f().normalize()));
        float stare = seen ? look * look * look : 0f;

        nextLight = Math.max(nextLight, lit);
        // "Blinding" whites out the whole screen whenever you can see it, wherever you look.
        // A steady (redstone) light only glares when you stare into it; a white screen for minutes is no fun.
        float glareHit = steady ? 0.3f * stare : p == 4 ? (seen ? 1f : 0.35f) : 0.35f + 0.65f * stare;
        nextGlare = Math.max(nextGlare, intensity * exposure * dark * GLARE[p] * glareHit);
    }

    private static void onTick(ClientTickEvent.Post event) {
        // The block entities of this tick have offered; take the result for the frames until the next tick.
        light = Mth.clamp(nextLight, 0f, 1f);
        glare = Mth.clamp(nextGlare, 0f, 1f);
        nextLight = nextGlare = 0f;
        if (Minecraft.getInstance().level == null) light = glare = 0f;
    }

    private static void onGui(RenderGuiEvent.Pre event) {
        // Shaderpacks usually light the world themselves and ignore the lightmap: then the screen flash does it all.
        float a = Math.min(0.97f, DrunkClient.shaderPack() ? Math.max(glare, light * 0.8f) : glare);
        if (a < 0.01f) return;
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (a * 255) << 24 | 0xF2F6FF);
    }
}
