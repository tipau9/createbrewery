package com.createbrewery.drunk;

import com.createbrewery.CreateBrewery;
import com.createbrewery.effect.HiccupsEffect;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.effect.VomitingEffect;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

import java.util.Locale;

/**
 * Everything the drunk player feels on their own screen, derived from the synced blood level.
 * Client only. No vanilla effects: the view, aim and legs are driven directly, and the world is
 * drawn through our own post shader ({@code createbrewery:drunk}).
 *
 * <p>All wobble comes from {@link #noise}, a few layered sines at unrelated frequencies. It never
 * repeats visibly and never runs away, so the player keeps fighting the same slow drift instead
 * of being spun around.
 */
public final class DrunkClient {
    private DrunkClient() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation SHADER =
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "shaders/post/drunk.json");

    /** Blood level eased towards the synced value, so water, vomiting or sleep fade instead of snap. */
    private static float blood;
    /** Aim offset already applied to the player; drift is applied as the change of this each frame. */
    private static float appliedYaw, appliedPitch;
    /** Same for the head bending down while throwing up. */
    private static float appliedRetch;
    /** Set once the shader failed to load (old GPU, shaderpack...): never try again this session. */
    private static boolean shaderFailed;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(DrunkClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onFov);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onMovementInput);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onPlayerTurn);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onGuiPre);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onGuiPost);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onInteract);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onScreenOpening);
        HiccupsEffect.clientKick = entity -> {
            if (entity == Minecraft.getInstance().player) {
                // The whole body jerks: the view snaps up and a little aside.
                float side = (entity.getRandom().nextFloat() - 0.5f) * 3f;
                entity.turn(side / 0.15, -5.0 / 0.15);
            }
        };
    }

    /** Layered sines in -1..1. {@code seed} picks an independent curve. */
    private static float noise(double t, int seed) {
        double s = seed * 12.9898;
        return (float) (Math.sin(t + s) * 0.5 + Math.sin(t * 2.31 + s * 1.7) * 0.3 + Math.sin(t * 4.13 + s * 2.9) * 0.2);
    }

    private static float ramp(float from) {
        return Intoxication.ramp(blood, from);
    }

    private static boolean blackedOut() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(ModEffects.BLACKOUT);
    }

    private static double seconds(LocalPlayer player, float partial) {
        return (player.tickCount + partial) / 20.0;
    }

    private static boolean isOurs(PostChain chain) {
        return chain != null && SHADER.toString().equals(chain.getName());
    }

    // ---- state + shader ----

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        float target = player == null ? 0f : player.getData(ModAttachments.DRUNK).blood;
        blood = player == null ? 0f : blood + (target - blood) * 0.1f;
        if (Math.abs(target - blood) < 0.001f) blood = target;

        PostChain current = mc.gameRenderer.currentEffect();
        boolean want = player != null && !shaderFailed && Intoxication.visualIntensity(blood) > 0.01f;
        if (want && current == null) {
            // Only when no other post effect runs (spectating a creeper etc. keeps its own).
            mc.gameRenderer.loadEffect(SHADER);
            if (!isOurs(mc.gameRenderer.currentEffect())) {
                shaderFailed = true;
                LOGGER.warn("Drunk shader could not be loaded; drunk vision falls back to overlays only");
            }
        } else if (!want && isOurs(current)) {
            mc.gameRenderer.shutdownEffect();
            if (mc.getCameraEntity() != null) mc.gameRenderer.checkEntityPostEffect(mc.getCameraEntity());
        }
    }

    // ---- aim drift + shader uniforms, every frame ----

    private static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            appliedYaw = appliedPitch = appliedRetch = 0f;
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double t = seconds(player, partial);

        PostChain current = mc.gameRenderer.currentEffect();
        if (isOurs(current)) {
            current.setUniform("Intensity", Intoxication.visualIntensity(blood));
            current.setUniform("DrunkTime", (float) (t % 3600.0));
        }

        // From the second beer the aim wanders off on its own and has to be pulled back.
        float w = ramp(Intoxication.MERRY);
        float yaw = noise(t * 0.35, 1) * 8f * w;
        float pitch = noise(t * 0.29, 2) * 4f * w;
        // Throwing up folds you over: the head drops and jerks further down with every heave.
        float retch = retch(player, partial) * 35f;
        if (mc.screen == null && !mc.isPaused()) {
            float dYaw = yaw - appliedYaw;
            float dPitch = pitch - appliedPitch + retch - appliedRetch;
            if (dYaw != 0f || dPitch != 0f) player.turn(dYaw / 0.15, dPitch / 0.15);
        }
        // Tracked even while a screen is open, so closing it does not snap the view.
        appliedYaw = yaw;
        appliedPitch = pitch;
        appliedRetch = retch;
    }

    /** 0..1 how far the body is doubled over right now; 0 when not throwing up. */
    private static float retch(LocalPlayer player, float partial) {
        MobEffectInstance vomiting = player.getEffect(ModEffects.VOMITING);
        if (vomiting == null) return 0f;
        float d = Math.max(0f, vomiting.getDuration() - partial);
        float phase = (d % VomitingEffect.HEAVE) / VomitingEffect.HEAVE;
        float heave = (float) Math.pow(Math.sin(phase * Math.PI), 4);
        float envelope = Math.min(1f, d / 10f) * Math.min(1f, (VomitingEffect.DURATION - d) / 6f);
        return envelope * (0.55f + 0.45f * heave);
    }

    // ---- view ----

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || blood <= 0f) return;
        // Roll only: yaw/pitch offsets here would split the view from the crosshair.
        double t = seconds(player, (float) event.getPartialTick());
        float roll = noise(t * 0.45, 5) * 11f * Intoxication.visualIntensity(blood);
        // The whole body shudders while retching.
        roll += (float) Math.sin(t * 55.0) * 2.5f * retch(player, (float) event.getPartialTick());
        event.setRoll(event.getRoll() + roll);
    }

    private static void onFov(ComputeFovModifierEvent event) {
        if (event.getPlayer() != Minecraft.getInstance().player || blood <= 0f) return;
        // Slow breathing of the view, at most about 7 %.
        float breath = noise(event.getPlayer().tickCount / 20.0 * 0.8, 7) * 0.07f * Intoxication.visualIntensity(blood);
        event.setNewFovModifier(event.getNewFovModifier() * (1f + breath));
    }

    // ---- legs + hands ----

    private static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        Input input = event.getInput();
        if (player.hasEffect(ModEffects.BLACKOUT)) {
            // Filmriss: the body walks on by itself, weaving, and nobody is steering. Other
            // players see it stagger off; the player sees nothing (see onGuiPost).
            input.up = true;
            input.down = input.left = input.right = false;
            input.shiftKeyDown = false;
            input.forwardImpulse = 1f;
            input.leftImpulse = noise(seconds(player, 0f) * 0.7, 9) * 0.6f;
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
        if (blood < Intoxication.MERRY) return;

        float w = ramp(Intoxication.MERRY);
        double t = seconds(player, 0f);
        float walking = Math.abs(input.forwardImpulse);
        // Weaving walk: the legs pull sideways while you walk, stronger with every beer.
        float weave = noise(t * 0.7, 9) * (0.25f + 0.75f * w);
        // Past the fourth beer the body lurches hard now and then.
        if (blood >= Intoxication.WASTED) {
            float lurch = noise(t * 1.9, 13);
            if (Math.abs(lurch) > 0.7f) weave += Math.signum(lurch) * (Math.abs(lurch) - 0.7f) * 4f;
        }
        input.leftImpulse = Math.max(-1f, Math.min(1f, input.leftImpulse + weave * walking));
        // Heavier legs, but a fully pressed key stays at 0.8 or above so sprinting (and tripping) still works.
        if (blood >= Intoxication.DRUNK) input.forwardImpulse *= 1f - 0.18f * ramp(Intoxication.DRUNK);
    }

    private static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        if (blackedOut()) {
            // The mouse does nothing: MouseHandler turns by (s * 0.6 + 0.2)^3, which is 0 here.
            event.setMouseSensitivity(-0.20000000298023224 / 0.6000000238418579);
            event.setCinematicCameraEnabled(false);
            return;
        }
        if (blood < Intoxication.MERRY) return;
        // Hands lag behind the head: duller mouse, and from beer four the view drags after it.
        event.setMouseSensitivity(event.getMouseSensitivity() * (1f - 0.4f * ramp(Intoxication.MERRY)));
        if (blood >= Intoxication.WASTED) event.setCinematicCameraEnabled(true);
    }

    /** No attacking, using or placing while passed out. */
    private static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        if (blackedOut()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** No rummaging through inventories or chests while passed out; pause and chat still work. */
    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (blackedOut() && event.getNewScreen() instanceof AbstractContainerScreen<?>) event.setCanceled(true);
    }

    // ---- overlays ----

    private static void onGuiPre(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        double t = seconds(player, event.getPartialTick().getGameTimeDeltaPartialTick(true));

        if (blood >= Intoxication.DRUNK) {
            // Heavy eyelids that keep sinking; from beer five they fall shut for a moment (micro-sleep).
            float e = ramp(Intoxication.DRUNK);
            float lid = (0.05f + 0.2f * e) * (0.6f + 0.4f * noise(t * 0.5, 17));
            if (blood >= Intoxication.SMASHED) {
                float nod = Math.max(0f, Math.min(1f, (noise(t * 0.4, 21) - 0.55f) / 0.3f));
                lid = Math.max(lid, nod * 0.52f);
            }
            int px = (int) (h * lid);
            int feather = h / 8;
            g.fill(0, 0, w, px, 0xF5000000);
            g.fillGradient(0, px, w, px + feather, 0xF5000000, 0x00000000);
            g.fill(0, h - px, w, h, 0xF5000000);
            g.fillGradient(0, h - px - feather, w, h - px, 0x00000000, 0xF5000000);
        }

        float retch = retch(player, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (retch > 0f) {
            // Sick green creeping in from the edges, and the eyes going dark in the middle of a heave.
            int a = (int) (retch * 150);
            int edge = h / 3;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x4A5A10, 0x004A5A10);
            g.fillGradient(0, h - edge, w, h, 0x004A5A10, (a << 24) | 0x4A5A10);
            g.fill(0, 0, w, h, ((int) (retch * 60) << 24) | 0x303A08);
        }

        MobEffectInstance hangover = player.getEffect(ModEffects.HANGOVER);
        if (hangover != null) {
            // Throbbing headache: the edges of the view darken with every heartbeat.
            float beat = (float) Math.pow(Math.max(0.0, Math.sin(t * Math.PI * 1.6)), 6.0);
            int a = (int) (beat * (90 + 40 * Math.min(hangover.getAmplifier(), 2)));
            int edge = h / 4;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x2A0000, 0x002A0000);
            g.fillGradient(0, h - edge, w, h, 0x002A0000, (a << 24) | 0x2A0000);
            // Daylight glare: bright sky hurts.
            if (player.level().isDay() && player.level().canSeeSky(player.blockPosition())) {
                int glare = (int) (60 + 30 * noise(t * 0.9, 25));
                g.fill(0, 0, w, h, (glare << 24) | 0xFFF8E0);
            }
        }
    }

    private static void onGuiPost(RenderGuiEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        MobEffectInstance blackout = player.getEffect(ModEffects.BLACKOUT);
        if (blackout == null) return;
        // Filmriss: pitch black, the world only fades back in during the last second and a half.
        float alpha = Math.min(1f, blackout.getDuration() / 30f);
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (alpha * 255) << 24);
    }

    // ---- inventory: the effect shows the actual level ----

    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(new IClientMobEffectExtensions() {
            @Override
            public boolean renderInventoryText(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                               GuiGraphics g, int x, int y, int blitOffset) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return false;
                DrunkState s = mc.player.getData(ModAttachments.DRUNK);
                int secs = Intoxication.ticksUntilSober(s.blood, s.stomach) / 20;
                // The blood level itself, with an arrow while the stomach is still feeding it.
                String rising = s.stomach > 0.02f ? " ↑" : "";
                g.drawString(mc.font, instance.getEffect().value().getDisplayName(), x + 28, y + 6, 0xFFFFFF);
                g.drawString(mc.font, String.format(Locale.GERMAN, "%.2f‰%s · %d:%02d",
                    s.blood, rising, secs / 60, secs % 60), x + 28, y + 16, 0x7F7F7F);
                return true;
            }
        }, ModEffects.INEBRIATION);
    }
}
