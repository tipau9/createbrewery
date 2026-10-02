package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugServer;
import com.createbrewery.drugs.Hallucinations;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.effect.VomitingEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Handles all player movement, camera angles, FOV warping, aim drift, and input handling
 * caused by intoxication and drug effects:
 * - Camera roll and view wobble (ComputeCameraAngles)
 * - Field of view breathing and zoom distortion (ComputeFovModifier)
 * - Aim drift and retch-induced head jerking (in RenderFrame)
 * - Steer logic, drunk weaving, couch lock, and motor impairment (MovementInputUpdate)
 * - Mouse turn sensitivity, horizontal gaze nystagmus, and numbness drag (CalculatePlayerTurn)
 * - Screen blanking and click blocking when zoned out or passed out (ScreenEvent / InteractionKeyMapping)
 */
public final class DrunkMovementHandler {
    private DrunkMovementHandler() {}

    /** Aim offset already applied to the player; drift is applied as the change of this each frame. */
    static float appliedYaw, appliedPitch;
    /** Same for the head bending down while throwing up. */
    static float appliedRetch;

    /** Movement as the body actually carries it out, before weed makes it heavy. */
    private static float heavyForward, heavyLeft;
    private static int lastJump = -1000;

    /** Alcohol: yaw a few ticks ago, to spot a quick look to the side; the eyes jerking back (HGN). */
    private static final float[] yawHistory = new float[6];
    private static int hgnLeft;
    private static float hgnDir;

    /** Until when (ms) the mind is blank in an open container; clicks and keys do nothing. */
    private static long blankUntil;

    public static void reset() {
        appliedYaw = 0f;
        appliedPitch = 0f;
        appliedRetch = 0f;
        heavyForward = 0f;
        heavyLeft = 0f;
        lastJump = -1000;
        hgnLeft = 0;
        hgnDir = 0f;
        blankUntil = 0;
        for (int i = 0; i < yawHistory.length; i++) yawHistory[i] = 0f;
    }

    public static void resetAim() {
        appliedYaw = 0f;
        appliedPitch = 0f;
        appliedRetch = 0f;
    }

    /** 0..1 how far the body is doubled over right now; 0 when not throwing up. */
    public static float retch(LocalPlayer player, float partial) {
        if (player == null) return 0f;
        MobEffectInstance vomiting = player.getEffect(ModEffects.VOMITING);
        if (vomiting == null) return 0f;
        float d = Math.max(0f, vomiting.getDuration() - partial);
        float phase = (d % VomitingEffect.HEAVE) / VomitingEffect.HEAVE;
        float heave = (float) Math.pow(Math.sin(phase * Math.PI), 4);
        float envelope = Math.min(1f, d / 10f) * Math.min(1f, (VomitingEffect.DURATION - d) / 6f);
        return envelope * (0.55f + 0.45f * heave);
    }

    /**
     * Aim drift applied each frame: from the second beer the aim wanders off on its own and
     * has to be pulled back, and vomiting pulls the head down.
     */
    public static void updateAimDrift(Minecraft mc, LocalPlayer player, float partial, double t) {
        float w = DrunkClient.ramp(Intoxication.MERRY);
        float yaw = DrunkClient.noise(t * 0.35, 1) * 8f * w;
        float pitch = DrunkClient.noise(t * 0.29, 2) * 4f * w;
        float retch = retch(player, partial) * 35f;
        if (mc.screen == null && !mc.isPaused()) {
            float dYaw = yaw - appliedYaw;
            float dPitch = pitch - appliedPitch + retch - appliedRetch;
            if (dYaw != 0f || dPitch != 0f) player.turn(dYaw / 0.15, dPitch / 0.15);
        }
        appliedYaw = yaw;
        appliedPitch = pitch;
        appliedRetch = retch;
    }

    // ---- view: camera angles + FOV ----

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        float subShake = com.createbrewery.block.club.SubwooferBlockEntity.getSubwooferBassShake(player);
        if (DrunkClient.blood <= 0f && DrunkClient.green <= 0f && DrunkClient.breakthrough <= 0f && DrunkClient.sick <= 0f && DrunkClient.wah <= 0f
            && TripClient.laughing() <= 0f && TripClient.chill <= 0.01f && RollClient.rush <= 0.01f && RollClient.beat <= 0.01f && RollClient.zap <= 0.01f && NodClient.jerk <= 0.01f && DrunkClient.seizing <= 0.01f && subShake <= 0.01f) return;
        // Roll only: yaw/pitch offsets here would split the view from the crosshair.
        double t = DrunkClient.seconds(player, (float) event.getPartialTick());
        float roll = DrunkClient.noise(t * 0.45, 5) * 11f * Intoxication.visualIntensity(DrunkClient.blood);
        // The whole body sways while retching - slowly, about twice a second, never a fast shake.
        roll += (float) Math.sin(t * 14.0) * 2f * retch(player, (float) event.getPartialTick());
        // Greening out: the head swims in slow, wide circles.
        roll += DrunkClient.noise(t * 0.3, 41) * 13f * DrunkClient.green;
        // DMT: the view turns slowly, as if weightless.
        roll += (float) Math.sin(t * 0.3) * 26f * DrunkClient.breakthrough;
        // ...and as it cracks open, the whole room vibrates.
        roll += DrunkClient.noise(t * 40.0, 71) * 1.5f * DmtClient.crack;
        // Drunk in bed: the room spins - one way while the alcohol still rises, the other once it
        // falls (positional alcohol nystagmus, phases I and II).
        if (player.isSleeping() && DrunkClient.blood >= Intoxication.MERRY) {
            float phase = player.getData(ModAttachments.DRUNK).stomach > 0.05f ? 1f : -1f;
            roll += (float) (t * 50.0 % 360.0) * phase * Math.min(1f, (DrunkClient.blood - Intoxication.MERRY) / 0.8f + 0.3f);
        }
        // A seizure: violent, fast shaking.
        roll += DrunkClient.noise(t * 25.0, 83) * 20f * DrunkClient.seizing;
        // Entzug: the whole body shivers.
        roll += DrunkClient.noise(t * 12.0, 53) * 1.2f * DrunkClient.sick;
        // Lachgas: dizzy - the head tips over, as if about to fall.
        roll += DrunkClient.noise(t * 0.8, 61) * 5f * DrunkClient.wah;
        // Mushrooms: shaking with laughter, and the chills of the come-up.
        roll += (float) Math.sin(t * 22.0) * 2.5f * TripClient.laughing();
        // MDMA: goosebumps with every rush.
        roll += DrunkClient.noise(t * 9.0, 83) * 0.7f * RollClient.rush;
        // ...and the body moves with every kick, leaning now one way, now the other.
        roll += (float) Math.sin(t * 1.3) * 1.2f * MusicPulse.kick * RollClient.beat;
        // At the peak every kick slams the head.
        roll += DrunkClient.noise(t * 30.0, 89) * 7f * MusicPulse.kick * RollClient.beat * RollClient.peak;
        // Heroin: waking from a nod with a jolt.
        roll += DrunkClient.noise(t * 30.0, 101) * 4f * NodClient.jerk;
        // A brain zap jerks the head.
        roll += DrunkClient.noise(t * 40.0, 97) * 4f * RollClient.zap;
        roll += DrunkClient.noise(t * 14.0, 71) * 0.8f * TripClient.chill;
        // Subwoofer bass punch vibrating the chest and camera
        if (subShake > 0.01f) {
            roll += DrunkClient.noise(t * 35.0, 97) * 2.2f * subShake;
        }
        event.setRoll(event.getRoll() + roll * DrunkClient.screen());
    }

    public static void onFov(ComputeFovModifierEvent event) {
        if (event.getPlayer() != Minecraft.getInstance().player) return;
        // Keta pushes the world away (wider view), Koks narrows the focus.
        MobEffectInstance racing = event.getPlayer().getEffect(ModEffects.TACHYCARDIA);
        float tunnel = racing == null ? 0f : 0.03f * (racing.getAmplifier() + 1);
        float drugs = 1f + (0.12f * DrunkClient.dissoc - 0.04f * DrunkClient.stim - tunnel) * DrunkClient.screen();
        // Tripping, the whole view breathes in and out; a bad trip makes it gasp.
        double sec = event.getPlayer().tickCount / 20.0;
        drugs += (float) (Math.sin(sec * 0.9) * 0.035 * DrunkClient.trip + DrunkClient.noise(sec * 2.2, 11) * 0.03 * DrunkClient.bad) * DrunkClient.screen();
        // DMT: the view tears wide open on the other side - the hall has no end - and lurches in the waiting room.
        drugs += (0.35f * DmtClient.beyond + 0.15f * DmtClient.waiting * (float) Math.sin(sec * 1.5)) * DrunkClient.screen();
        // ...and the walls close in on it.
        drugs -= 0.12f * DrunkClient.bad * DrunkClient.screen();
        // MDMA, very high: for a while the view seems to come from further back.
        drugs += 0.15f * RollClient.perspective * DrunkClient.screen();
        if (drugs != 1f) event.setNewFovModifier(event.getNewFovModifier() * drugs);
        if (DrunkClient.blood <= 0f) return;
        // Slow breathing of the view, at most about 7 %.
        float breath = DrunkClient.noise(event.getPlayer().tickCount / 20.0 * 0.8, 7) * 0.07f * Intoxication.visualIntensity(DrunkClient.blood);
        event.setNewFovModifier(event.getNewFovModifier() * (1f + breath * DrunkClient.screen()));
    }

    // ---- legs + hands ----

    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        Input input = event.getInput();
        steer(player, input);
        boolean overridden = player.hasEffect(ModEffects.HEART_ATTACK) || player.hasEffect(ModEffects.BLACKOUT)
            || player.hasEffect(ModEffects.VOMITING);
        couchLock(player, input, overridden);
    }

    /**
     * Weed: a heavy, relaxed body (couch lock). Getting going takes a moment, letting go glides
     * on for about half a block, and there is no urge to jump - a pause between jumps. Only on
     * the ground: swimming up and ladders stay as they are.
     */
    private static void couchLock(LocalPlayer player, Input input, boolean overridden) {
        if (overridden || DrunkClient.high <= 0.01f) {
            heavyForward = input.forwardImpulse;
            heavyLeft = input.leftImpulse;
            return;
        }
        float start = 1f - 0.88f * DrunkClient.high, stop = 1f - 0.75f * DrunkClient.high;
        heavyForward += (input.forwardImpulse - heavyForward) * (Math.abs(input.forwardImpulse) > Math.abs(heavyForward) ? start : stop);
        heavyLeft += (input.leftImpulse - heavyLeft) * (Math.abs(input.leftImpulse) > Math.abs(heavyLeft) ? start : stop);
        // Settle at rest: vanilla keeps sprinting until the forward impulse is really 0.
        if (input.forwardImpulse == 0f && Math.abs(heavyForward) < 0.05f) heavyForward = 0f;
        if (input.leftImpulse == 0f && Math.abs(heavyLeft) < 0.05f) heavyLeft = 0f;
        input.forwardImpulse = heavyForward;
        input.leftImpulse = heavyLeft;
        if (input.jumping && player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable()) {
            if (player.tickCount - lastJump < 10 + (int) (25 * DrunkClient.high)) input.jumping = false;
            else lastJump = player.tickCount;
        }
    }

    /**
     * Drunk, a quick look to the side and the eyes cannot hold it: they drift back and jerk out
     * again, a few times (horizontal gaze nystagmus). The more drunk, the smaller the look that
     * does it - about 50 degrees minus ten per per-mille.
     */
    public static void nystagmus(LocalPlayer player) {
        float yaw = player.getYRot();
        float moved = Mth.wrapDegrees(yaw - yawHistory[player.tickCount % yawHistory.length]);
        yawHistory[player.tickCount % yawHistory.length] = yaw;
        if (hgnLeft > 0) {
            hgnLeft--;
            // Slow drift back, then a fast jerk out: the typical sawtooth.
            float step = hgnLeft % 4 == 0 ? 1.5f * hgnDir : -0.5f * hgnDir;
            player.turn(step / 0.15f, 0.0);
        } else if (DrunkClient.blood >= 0.5f && Math.abs(moved) > Math.max(15f, 50f - 10f * DrunkClient.blood)) {
            hgnLeft = 12;
            hgnDir = Math.signum(moved);
        }
    }

    private static void steer(LocalPlayer player, Input input) {
        if (DrunkClient.breakthrough > 0.6f || DrunkClient.seizing > 0f) {
            // Broken through (or convulsing): the body is left behind and does nothing.
            input.forwardImpulse = input.leftImpulse = 0f;
            input.up = input.down = input.left = input.right = input.jumping = false;
            player.setSprinting(false);
            return;
        }
        if (player.hasEffect(ModEffects.HEART_ATTACK)) {
            // Collapsed: the legs give way. A friend sneaking next to you is doing CPR.
            input.forwardImpulse = input.leftImpulse = 0f;
            input.up = input.down = input.left = input.right = input.jumping = false;
            input.shiftKeyDown = true;
            player.setSprinting(false);
            return;
        }
        if (player.hasEffect(ModEffects.BLACKOUT)) {
            // Filmriss: the body walks on by itself, weaving, and nobody is steering. Other
            // players see it stagger off; the player sees nothing (see onGuiPost).
            input.up = true;
            input.down = input.left = input.right = false;
            input.shiftKeyDown = false;
            input.forwardImpulse = 1f;
            input.leftImpulse = DrunkClient.noise(DrunkClient.seconds(player, 0f) * 0.7, 9) * 0.6f;
            input.jumping = player.horizontalCollision && player.onGround();
            player.setSprinting(false);
            return;
        }
        if (player.hasEffect(ModEffects.VOMITING)) {
            // Doubled over: barely shuffling, no jumping.
            input.forwardImpulse *= 0.15f;
            input.leftImpulse *= 0.15f;
            input.jumping = false;
            player.setSprinting(false);
            return;
        }
        if (player.hasEffect(ModEffects.CK_MIX) && DrunkClient.noise(DrunkClient.seconds(player, 0f) * 0.25, 31) > 0.55f) {
            // CK: for a few seconds at a time, left and right swap places.
            input.leftImpulse = -input.leftImpulse;
            boolean left = input.left;
            input.left = input.right;
            input.right = left;
        }
        if (DrunkClient.green > 0f) {
            // Greening out: the legs are jelly and pull you sideways.
            input.forwardImpulse *= 1f - 0.55f * DrunkClient.green;
            input.leftImpulse = input.leftImpulse * (1f - 0.55f * DrunkClient.green)
                + DrunkClient.noise(DrunkClient.seconds(player, 0f) * 0.5, 43) * 0.35f * DrunkClient.green * Math.abs(input.forwardImpulse);
        }
        if (DrunkClient.dissoc > 0f) {
            // Keta: the legs are somewhere far away; in the K-Loch they barely move at all.
            float slow = 1f - 0.5f * DrunkClient.dissoc - (player.hasEffect(ModEffects.K_HOLE) ? 0.35f : 0f);
            input.forwardImpulse *= Math.max(0.1f, slow);
            input.leftImpulse *= Math.max(0.1f, slow);
            // The K-wobble: walking goes robotic and wobbly, the body swaying off to the side.
            input.leftImpulse += DrunkClient.noise(DrunkClient.seconds(player, 0f) * 0.9, 67) * 0.4f * DrunkClient.dissoc * Math.abs(input.forwardImpulse);
            if (player.hasEffect(ModEffects.K_HOLE)) input.jumping = false;
        }
        // Koks: the legs jumped by themselves.
        if (CokeClient.hop) {
            input.jumping = true;
            CokeClient.hop = false;
        }
        if (DrunkClient.blood < Intoxication.MERRY) return;

        float w = DrunkClient.ramp(Intoxication.MERRY);
        double t = DrunkClient.seconds(player, 0f);
        float walking = Math.abs(input.forwardImpulse);
        // Weaving walk: the legs pull sideways while you walk, stronger with every beer.
        float weave = DrunkClient.noise(t * 0.7, 9) * (0.25f + 0.75f * w);
        // Past the fourth beer the body lurches hard now and then.
        if (DrunkClient.blood >= Intoxication.WASTED) {
            float lurch = DrunkClient.noise(t * 1.9, 13);
            if (Math.abs(lurch) > 0.7f) weave += Math.signum(lurch) * (Math.abs(lurch) - 0.7f) * 4f;
        }
        input.leftImpulse = Math.max(-1f, Math.min(1f, input.leftImpulse + weave * walking));
        // Heavier legs, but a fully pressed key stays at 0.8 or above so sprinting (and tripping) still works.
        if (DrunkClient.blood >= Intoxication.DRUNK) input.forwardImpulse *= 1f - 0.18f * DrunkClient.ramp(Intoxication.DRUNK);
    }

    public static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (DrunkClient.blackedOut()) {
            // The mouse does nothing: MouseHandler turns by (s * 0.6 + 0.2)^3, which is 0 here.
            event.setMouseSensitivity(-0.20000000298023224 / 0.6000000238418579);
            event.setCinematicCameraEnabled(false);
            return;
        }
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasEffect(ModEffects.HEART_ATTACK)) {
            event.setMouseSensitivity(event.getMouseSensitivity() * 0.15f);
            event.setCinematicCameraEnabled(true);
            return;
        }
        // Koks makes the hands twitchy, Keta makes them distant and slow.
        if (DrunkClient.stim > 0f) event.setMouseSensitivity(event.getMouseSensitivity() * (1f + 0.25f * DrunkClient.stim));
        // Weed slows time down and the view floats; now and then you zone out, staring, and the
        // hands barely follow for a few seconds. Greening out makes the head spin and the hands limp.
        if (DrunkClient.high > 0f) {
            LocalPlayer me = Minecraft.getInstance().player;
            double t = me == null ? 0.0 : DrunkClient.seconds(me, 0f);
            float zone = Math.max(0f, Math.min(1f, (DrunkClient.noise(t * 0.12, 51) - 0.55f) / 0.25f)) * DrunkClient.high;
            event.setMouseSensitivity(event.getMouseSensitivity() * (1f - 0.1f * DrunkClient.high) * (1f - 0.75f * zone));
            if (DrunkClient.high > 0.6f) event.setCinematicCameraEnabled(true);
        }
        if (DrunkClient.green > 0f) event.setMouseSensitivity(event.getMouseSensitivity() * (1f - 0.3f * DrunkClient.green));
        // Mushrooms: the body heavy and a little clumsy.
        if (DrunkClient.trip * DrunkClient.organic > 0f) event.setMouseSensitivity(event.getMouseSensitivity() * (1f - 0.15f * DrunkClient.trip * DrunkClient.organic));
        // B12 gone: numb hands, the mouse drags.
        var numb = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getEffect(ModEffects.NUMBNESS);
        if (numb != null) event.setMouseSensitivity(event.getMouseSensitivity() * (0.75f - 0.25f * numb.getAmplifier()));
        if (DrunkClient.dissoc > 0f) {
            event.setMouseSensitivity(event.getMouseSensitivity() * (1f - 0.6f * DrunkClient.dissoc));
            if (DrunkClient.dissoc > 0.5f) event.setCinematicCameraEnabled(true);
        }
        if (DrunkClient.blood < Intoxication.MERRY) return;
        // Hands lag behind the head: duller mouse, and from beer four the view drags after it.
        event.setMouseSensitivity(event.getMouseSensitivity() * (1f - 0.4f * DrunkClient.ramp(Intoxication.MERRY)));
        if (DrunkClient.blood >= Intoxication.WASTED) event.setCinematicCameraEnabled(true);
    }

    /** No attacking, using or placing while passed out. */
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (DrunkClient.blackedOut()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** No rummaging through inventories or chests while passed out; pause and chat still work. */
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (DrunkClient.blackedOut() && event.getNewScreen() instanceof AbstractContainerScreen<?>) event.setCanceled(true);
        // High: open a chest or a workbench and - what did I want here again? The hands stop for half a second.
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.isCanceled() && player != null && DrunkClient.high > 0.1f && event.getNewScreen() instanceof AbstractContainerScreen<?>
            && !(event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
            && !(event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)
            && player.getRandom().nextFloat() < 0.4f * DrunkClient.high) {
            blankUntil = net.minecraft.Util.getMillis() + 500;
        }
    }

    private static boolean blank(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof AbstractContainerScreen<?> && net.minecraft.Util.getMillis() < blankUntil;
    }

    public static void onScreenMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (blank(event.getScreen())) event.setCanceled(true);
    }

    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        // Escape still closes it.
        if (blank(event.getScreen()) && event.getKeyCode() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) event.setCanceled(true);
    }
}
