package com.createbrewery.drunk;

import com.createbrewery.Config;
import com.createbrewery.CreateBrewery;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.drugs.DrugServer;
import com.createbrewery.effect.HiccupsEffect;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.effect.VomitingEffect;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import com.createbrewery.sound.ModSounds;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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
    /** Koks, the crash after it, and Keta, eased like the blood level (0..1 each). */
    private static float stim, gray, dissoc, high, green;
    /** Next tick a paranoid footstep plays behind the player (weed, worse with Koks). */
    private static int nextFootstep = 400;
    private static int lastCokeBeat;
    /** Aim offset already applied to the player; drift is applied as the change of this each frame. */
    private static float appliedYaw, appliedPitch;
    /** Same for the head bending down while throwing up. */
    private static float appliedRetch;
    /** Last hangover heartbeat played, and when the ears last rang (client ticks of the player). */
    private static int lastBeat = Integer.MIN_VALUE;
    private static int lastRinging = -1000;
    private static boolean wasNodding, wasBlackedOut;
    /** Set once the shader failed to load (old GPU...): never try again this session. */
    private static boolean shaderFailed;
    /**
     * Our own post chain. Deliberately not GameRenderer's single post-effect slot: other mods in
     * the pack (Moonlight, Supplementaries, KubeJS...) also use that slot and swap it out, and a
     * chain that keeps getting recreated would flash its black first frame again and again.
     */
    private static PostChain chain;
    private static int chainWidth, chainHeight;
    /** Frames drawn since the chain was (re)built; the afterimage waits until it has a real previous frame. */
    private static int chainFrames;
    private static int chainBuilds;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(DrunkClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onFov);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onMovementInput);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onPlayerTurn);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onGuiPre);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onGuiPost);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onInteract);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onPlaySound);
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

    // ---- state + shader ----

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        float target = player == null ? 0f : DrunkServer.feltFor(player, player.getData(ModAttachments.DRUNK));
        blood = player == null ? 0f : blood + (target - blood) * 0.1f;
        if (Math.abs(target - blood) < 0.001f) blood = target;
        // Every drug comes up, holds and fades (DrugEffect.strength); the screen follows that curve.
        stim = ease(stim, player == null ? 0f : DrugEffect.felt(player, ModEffects.COKE_HIGH));
        gray = ease(gray, player == null ? 0f : 0.7f * DrugEffect.strength(player, ModEffects.COKE_CRASH));
        float keta = player == null ? 0f : Math.max(DrugEffect.strength(player, ModEffects.K_HOLE),
            // Koks masks the Keta: it feels clearer than it is (the K-Loch does not care).
            0.85f * DrugEffect.felt(player, ModEffects.KETA_HIGH) * (1f - 0.4f * DrugEffect.strength(player, ModEffects.COKE_HIGH)));
        if (player != null) keta = Math.max(keta, 0.15f * DrugEffect.strength(player, ModEffects.DAZED));
        // Weed deepens the dissociation.
        if (player != null) keta *= 1f + 0.3f * DrugEffect.strength(player, ModEffects.WEED_HIGH);
        dissoc = ease(dissoc, Math.min(1f, keta));
        high = ease(high, player == null ? 0f : DrugEffect.felt(player, ModEffects.WEED_HIGH));
        green = ease(green, player == null ? 0f : DrugEffect.strength(player, ModEffects.GREENING_OUT));

        boolean want = player != null && !shaderFailed && screen() > 0.01f && !shaderPackActive()
            && (Intoxication.visualIntensity(blood) > 0.01f || Intoxication.mood(blood) > 0.01f
                || stim > 0.01f || gray > 0.01f || dissoc > 0.01f || high > 0.01f || green > 0.01f);
        if (want && chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), SHADER);
                chainBuilds++;
                // A rebuild should happen once per drinking session; many of these point at a conflict.
                if (chainBuilds <= 5 || chainBuilds % 100 == 0) LOGGER.info("Drunk shader chain built (#{})", chainBuilds);
                chainWidth = chainHeight = -1;
                chainFrames = 0;
            } catch (Exception e) {
                shaderFailed = true;
                LOGGER.warn("Drunk shader could not be loaded; drunk vision falls back to overlays only", e);
            }
        } else if (!want && chain != null) {
            chain.close();
            chain = null;
        }
        if (player != null && !mc.isPaused()) {
            bodySounds(mc, player);
            moreMusic(mc, player);
        }
        hearing(mc);
    }

    /**
     * Sounds only the drunk player hears, inside their own head: the hangover heartbeat in step
     * with the throbbing screen edge, and ringing ears when nodding off or passing out.
     */
    /** Moves {@code current} towards {@code target} over a couple of seconds. */
    private static float ease(float current, float target) {
        float next = current + (target - current) * 0.05f;
        return Math.abs(target - next) < 0.002f ? target : next;
    }

    private static void bodySounds(Minecraft mc, LocalPlayer player) {
        // Paranoia: from the second joint, or weed with Koks, footsteps behind you - nobody is there.
        MobEffectInstance weed = player.getEffect(ModEffects.WEED_HIGH);
        boolean paranoid = weed != null && (DrugServer.joints(player) > 1f || player.hasEffect(ModEffects.COKE_HIGH));
        if (paranoid && player.tickCount >= nextFootstep) {
            Vec3 behind = player.position().subtract(player.getLookAngle().multiply(3.0, 0.0, 3.0));
            mc.getSoundManager().play(new SimpleSoundInstance(net.minecraft.sounds.SoundEvents.GRAVEL_STEP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.9f + player.getRandom().nextFloat() * 0.2f,
                player.getRandom(), behind.x, behind.y, behind.z));
            nextFootstep = player.tickCount + 300 + player.getRandom().nextInt(500);
        } else if (!paranoid) {
            nextFootstep = player.tickCount + 300;
        }
        // The heart you can hear: fast on Koks, racing when overloaded, stumbling with Keta on
        // top, and weak and chaotic when it gives out. Sound only - the screen never pulses with it.
        MobEffectInstance coke = player.getEffect(ModEffects.COKE_HIGH);
        MobEffectInstance racing = player.getEffect(ModEffects.TACHYCARDIA);
        boolean failing = player.hasEffect(ModEffects.HEART_ATTACK);
        boolean audible = failing || racing != null || (coke != null && stim > 0.2f);
        if (audible) {
            int interval = coke == null ? 10 : Math.max(8, 12 - 2 * coke.getAmplifier());
            if (racing != null) interval = racing.getAmplifier() >= 1 ? 6 : 7;
            // With Keta on top the rhythm stumbles: beats come early and late.
            if (player.hasEffect(ModEffects.CK_MIX)) interval = 6 + player.getRandom().nextInt(10);
            if (failing) interval = 5 + player.getRandom().nextInt(25);
            if (player.tickCount - lastCokeBeat >= interval) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(),
                    failing ? 0.8f : 1.25f, failing ? 0.35f : 0.45f));
                lastCokeBeat = player.tickCount;
            }
        }
        if (player.hasEffect(ModEffects.HANGOVER)) {
            // The overlay throbs at sin(pi * 1.6 t); this counts its peaks and fires just before each.
            int beat = (int) Math.floor((seconds(player, 0f) * 1.6 - 0.35) / 2.0);
            if (beat != lastBeat) {
                if (lastBeat != Integer.MIN_VALUE) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(), 1.0f, 0.7f));
                }
                lastBeat = beat;
            }
        } else {
            lastBeat = Integer.MIN_VALUE;
        }

        boolean nodding = blood >= Intoxication.SMASHED && nod(seconds(player, 0f)) > 0.5f;
        boolean blackedOut = player.hasEffect(ModEffects.BLACKOUT);
        if (((nodding && !wasNodding) || (blackedOut && !wasBlackedOut)) && player.tickCount - lastRinging > 80) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.EAR_RINGING.get(), 1.0f, 0.5f));
            lastRinging = player.tickCount;
        }
        wasNodding = nodding;
        wasBlackedOut = blackedOut;
    }

    /** 0..1 how far the eyes have fallen shut in a micro-sleep (used from beer five on). */
    private static float nod(double t) {
        return Math.max(0f, Math.min(1f, (noise(t * 0.4, 21) - 0.55f) / 0.3f));
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
        // Numb: Keta (and a lot of alcohol) dull the pain, not the injury. The hit lands in full,
        // but the view does not flinch and you do not hear yourself - only the hearts tell.
        if (numb(player)) player.hurtTime = 0;

        if (chain != null) {
            chain.setUniform("Intensity", Intoxication.visualIntensity(blood) * screen());
            chain.setUniform("Mood", Intoxication.mood(blood) * screen());
            chain.setUniform("Stim", stim * screen());
            chain.setUniform("Gray", gray * screen());
            chain.setUniform("Dissoc", dissoc * screen());
            chain.setUniform("High", high * screen());
            chain.setUniform("Green", green * screen());
            chain.setUniform("DrunkTime", (float) (t % 3600.0));
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
        if (player == null) return 0f;
        MobEffectInstance vomiting = player.getEffect(ModEffects.VOMITING);
        if (vomiting == null) return 0f;
        float d = Math.max(0f, vomiting.getDuration() - partial);
        float phase = (d % VomitingEffect.HEAVE) / VomitingEffect.HEAVE;
        float heave = (float) Math.pow(Math.sin(phase * Math.PI), 4);
        float envelope = Math.min(1f, d / 10f) * Math.min(1f, (VomitingEffect.DURATION - d) / 6f);
        return envelope * (0.55f + 0.45f * heave);
    }

    private static boolean numb(LocalPlayer player) {
        return DrunkServer.ketamine(player) > 0.3f || blood >= Intoxication.WASTED;
    }

    /**
     * Numb: your own hurt sounds stay silent (see onRenderFrame). High: every sound is richer -
     * music, ambience, rain, blocks and animals louder and heard from farther away (a louder
     * sound carries further in Minecraft), and a touch slower, as time stretches.
     */
    private static void onPlaySound(PlaySoundEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        SoundInstance sound = event.getSound();
        if (player == null || sound == null) return;
        if (numb(player) && sound.getLocation().getPath().startsWith("entity.player.hurt")
            && player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < 4.0) {
            event.setSound(null);
            return;
        }
        // Only one-shot sounds are wrapped. Tickable ones set their own volume every tick, and music
        // and records are stopped later by their instance (the music manager, a jukebox), which a
        // wrapper would hide - the listener gain makes those louder instead.
        SoundSource source = sound.getSource();
        if (source == SoundSource.MUSIC || source == SoundSource.RECORDS) lastMusic = sound;
        if (high > 0.02f && source != SoundSource.MASTER && source != SoundSource.MUSIC && source != SoundSource.RECORDS
            && !(sound instanceof TickableSoundInstance)) {
            // The listener gain already raises everything; ambience and weather get more on top,
            // everything else is pulled back so that music and ambience stand out.
            boolean ambience = source == SoundSource.AMBIENT || source == SoundSource.WEATHER;
            float louder = ambience ? 1f + 0.8f * high : (1f + 0.3f * high) / listenerBoost();
            event.setSound(new EnhancedSound(sound, louder, 1f - 0.04f * high));
        }
    }

    /** A sound played as it is, only louder and a little slower. */
    private record EnhancedSound(SoundInstance sound, float louder, float slower) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return sound.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager manager) { return sound.resolve(manager); }
        @Override public Sound getSound() { return sound.getSound(); }
        @Override public SoundSource getSource() { return sound.getSource(); }
        @Override public boolean isLooping() { return sound.isLooping(); }
        @Override public boolean isRelative() { return sound.isRelative(); }
        @Override public int getDelay() { return sound.getDelay(); }
        @Override public float getVolume() { return sound.getVolume() * louder; }
        @Override public float getPitch() { return sound.getPitch() * slower; }
        @Override public double getX() { return sound.getX(); }
        @Override public double getY() { return sound.getY(); }
        @Override public double getZ() { return sound.getZ(); }
        @Override public Attenuation getAttenuation() { return sound.getAttenuation(); }
        @Override public boolean canStartSilent() { return sound.canStartSilent(); }
        @Override public boolean canPlaySound() { return sound.canPlaySound(); }
        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound s, boolean looping) {
            return sound.getStream(buffers, s, looping);
        }
    }

    /** High: music and ambience up to about 2x (+6 dB), which is where the listener gain goes. */
    private static float listenerBoost() {
        return 1f + 1.0f * high;
    }

    /**
     * High: music sounds better, so you want more of it - the game's music keeps playing instead of
     * waiting minutes between tracks. Checked every 10 s, and only while nothing is playing - no
     * track, no jukebox (see {@link #lastMusic}).
     */
    private static void moreMusic(Minecraft mc, LocalPlayer player) {
        if (high < 0.35f || player.tickCount % 200 != 0) return;
        if (lastMusic != null && mc.getSoundManager().isActive(lastMusic)) return;
        var music = mc.getSituationalMusic();
        if (music != null) mc.getMusicManager().startPlaying(music);
    }

    /** The last music track or record that started; never wrapped, so it can be asked whether it still plays. */
    private static SoundInstance lastMusic;

    /** Whether the listener gain is currently raised (see {@link #hearing}). */
    private static boolean hearingBoosted;
    private static boolean hearingFailed;

    /**
     * High: the whole soundscape a little louder, music playing already included. Minecraft caps
     * each sound at its slider, so this goes through the listener gain on top of the master
     * volume, which OpenAL lets rise above 1.
     */
    private static void hearing(Minecraft mc) {
        if (hearingFailed || (high <= 0.001f && !hearingBoosted)) return;
        try {
            float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
            org.lwjgl.openal.AL10.alListenerf(org.lwjgl.openal.AL10.AL_GAIN, master * listenerBoost());
            hearingBoosted = high > 0.001f;
        } catch (RuntimeException | LinkageError e) {
            hearingFailed = true; // no sound device: nothing to make louder
        }
    }

    /** Draws the drunk vision over the finished world, before the hand and the HUD. */
    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (chain == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width != chainWidth || main.height != chainHeight) {
            chain.resize(main.width, main.height);
            chainWidth = main.width;
            chainHeight = main.height;
            chainFrames = 0;
        }
        chain.setUniform("Trail", chainFrames < 3 ? 0f : 1f);
        chainFrames++;
        // Same state handling as vanilla around its own post effect.
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(event.getPartialTick().getGameTimeDeltaTicks());
        main.bindWrite(false);
        RenderSystem.enableDepthTest();
    }

    /** The player's comfort setting, 0..1. */
    private static float screen() {
        return Config.CLIENT_SPEC.isLoaded() ? Config.SCREEN_EFFECTS.get().floatValue() : 1f;
    }

    private static Boolean irisPresent;

    /**
     * An Iris shaderpack draws the world its own way, and a vanilla post chain on top of it can
     * break the picture. Looked up by reflection, so Iris stays optional.
     */
    private static boolean shaderPackActive() {
        try {
            if (irisPresent == null) {
                irisPresent = net.neoforged.fml.ModList.get().isLoaded("iris");
            }
            if (!irisPresent) return false;
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    // ---- view ----

    private static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || (blood <= 0f && green <= 0f)) return;
        // Roll only: yaw/pitch offsets here would split the view from the crosshair.
        double t = seconds(player, (float) event.getPartialTick());
        float roll = noise(t * 0.45, 5) * 11f * Intoxication.visualIntensity(blood);
        // The whole body sways while retching - slowly, about twice a second, never a fast shake.
        roll += (float) Math.sin(t * 14.0) * 2f * retch(player, (float) event.getPartialTick());
        // Greening out: the head swims in slow, wide circles.
        roll += noise(t * 0.3, 41) * 13f * green;
        event.setRoll(event.getRoll() + roll * screen());
    }

    private static void onFov(ComputeFovModifierEvent event) {
        if (event.getPlayer() != Minecraft.getInstance().player) return;
        // Keta pushes the world away (wider view), Koks narrows the focus.
        MobEffectInstance racing = event.getPlayer().getEffect(ModEffects.TACHYCARDIA);
        float tunnel = racing == null ? 0f : 0.03f * (racing.getAmplifier() + 1);
        float drugs = 1f + (0.12f * dissoc - 0.04f * stim - tunnel) * screen();
        if (drugs != 1f) event.setNewFovModifier(event.getNewFovModifier() * drugs);
        if (blood <= 0f) return;
        // Slow breathing of the view, at most about 7 %.
        float breath = noise(event.getPlayer().tickCount / 20.0 * 0.8, 7) * 0.07f * Intoxication.visualIntensity(blood);
        event.setNewFovModifier(event.getNewFovModifier() * (1f + breath * screen()));
    }

    // ---- legs + hands ----

    private static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        Input input = event.getInput();
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
        if (player.hasEffect(ModEffects.CK_MIX) && noise(seconds(player, 0f) * 0.25, 31) > 0.55f) {
            // CK: for a few seconds at a time, left and right swap places.
            input.leftImpulse = -input.leftImpulse;
            boolean left = input.left;
            input.left = input.right;
            input.right = left;
        }
        if (green > 0f) {
            // Greening out: the legs are jelly and pull you sideways.
            input.forwardImpulse *= 1f - 0.55f * green;
            input.leftImpulse = input.leftImpulse * (1f - 0.55f * green)
                + noise(seconds(player, 0f) * 0.5, 43) * 0.35f * green * Math.abs(input.forwardImpulse);
        }
        if (dissoc > 0f) {
            // Keta: the legs are somewhere far away; in the K-Loch they barely move at all.
            float slow = 1f - 0.5f * dissoc - (player.hasEffect(ModEffects.K_HOLE) ? 0.35f : 0f);
            input.forwardImpulse *= Math.max(0.1f, slow);
            input.leftImpulse *= Math.max(0.1f, slow);
            if (player.hasEffect(ModEffects.K_HOLE)) input.jumping = false;
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
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasEffect(ModEffects.HEART_ATTACK)) {
            event.setMouseSensitivity(event.getMouseSensitivity() * 0.15f);
            event.setCinematicCameraEnabled(true);
            return;
        }
        // Koks makes the hands twitchy, Keta makes them distant and slow.
        if (stim > 0f) event.setMouseSensitivity(event.getMouseSensitivity() * (1f + 0.25f * stim));
        // Weed slows time down and the view floats; now and then you zone out, staring, and the
        // hands barely follow for a few seconds. Greening out makes the head spin and the hands limp.
        if (high > 0f) {
            LocalPlayer me = Minecraft.getInstance().player;
            double t = me == null ? 0.0 : seconds(me, 0f);
            float zone = Math.max(0f, Math.min(1f, (noise(t * 0.12, 51) - 0.55f) / 0.25f)) * high;
            event.setMouseSensitivity(event.getMouseSensitivity() * (1f - 0.1f * high) * (1f - 0.75f * zone));
            if (high > 0.6f) event.setCinematicCameraEnabled(true);
        }
        if (green > 0f) event.setMouseSensitivity(event.getMouseSensitivity() * (1f - 0.3f * green));
        if (dissoc > 0f) {
            event.setMouseSensitivity(event.getMouseSensitivity() * (1f - 0.6f * dissoc));
            if (dissoc > 0.5f) event.setCinematicCameraEnabled(true);
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
                lid = Math.max(lid, nod(t) * 0.52f);
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
            int a = (int) (retch * 150 * screen());
            int edge = h / 3;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x4A5A10, 0x004A5A10);
            g.fillGradient(0, h - edge, w, h, 0x004A5A10, (a << 24) | 0x4A5A10);
            g.fill(0, 0, w, h, ((int) (retch * 60 * screen()) << 24) | 0x303A08);
        }

        MobEffectInstance racing = player.getEffect(ModEffects.TACHYCARDIA);
        if (racing != null) {
            // Herzrasen: a steady tightness at the edges of the view - it does not pulse.
            int a = (int) ((35 + 35 * racing.getAmplifier()) * screen());
            int edge = h / 5;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x3A0008, 0x003A0008);
            g.fillGradient(0, h - edge, w, h, 0x003A0008, (a << 24) | 0x3A0008);
        }
        MobEffectInstance failing = player.getEffect(ModEffects.HEART_ATTACK);
        if (failing != null) {
            // Collapsed: the world goes grey-dark, fading in over a second and out at the end.
            float fade = Math.min(1f, Math.min((DrugServer.HEART_ATTACK_TICKS - failing.getDuration()) / 20f,
                failing.getDuration() / 40f));
            g.fill(0, 0, w, h, ((int) (fade * 170 * screen()) << 24) | 0x0A0004);
        }
        if (green > 0.01f) {
            // Nausea: a pale, clammy green creeping in from the edges, rising and ebbing in slow waves.
            float wave = 0.75f + 0.25f * noise(t * 0.4, 47);
            int a = (int) (green * wave * 130 * screen());
            int edge = h / 3;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x5C6E1E, 0x005C6E1E);
            g.fillGradient(0, h - edge, w, h, 0x005C6E1E, (a << 24) | 0x5C6E1E);
        }
        if (player.hasEffect(ModEffects.ASPIRATION)) {
            // Choking: sick green closing in, steady.
            int a = (int) (160 * screen());
            int edge = h / 3;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x4A5A10, 0x004A5A10);
            g.fillGradient(0, h - edge, w, h, 0x004A5A10, (a << 24) | 0x4A5A10);
        }

        MobEffectInstance hangover = player.getEffect(ModEffects.HANGOVER);
        if (hangover != null) {
            // Konterbier and Koks both mask the hangover for a while - it is still there afterwards.
            float masked = (1f - 0.7f * Math.min(1f, blood / Intoxication.MERRY)) * (1f - 0.7f * stim);
            // Throbbing headache: the edges of the view darken with every heartbeat.
            float beat = (float) Math.pow(Math.max(0.0, Math.sin(t * Math.PI * 1.6)), 6.0);
            int a = (int) (beat * (90 + 40 * Math.min(hangover.getAmplifier(), 2)) * screen() * masked);
            int edge = h / 4;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x2A0000, 0x002A0000);
            g.fillGradient(0, h - edge, w, h, 0x002A0000, (a << 24) | 0x2A0000);
            // Daylight glare: bright sky hurts.
            if (player.level().isDay() && player.level().canSeeSky(player.blockPosition())) {
                int glare = (int) ((60 + 30 * noise(t * 0.9, 25)) * screen() * masked);
                g.fill(0, 0, w, h, (glare << 24) | 0xFFF8E0);
            }
        }

        drawPerMille(mc, player, g);
    }

    /**
     * The per-mille reading above the hotbar while there is any alcohol in you, coloured by how
     * bad it is, with an arrow while the stomach is still feeding the blood. Drawn in Pre after
     * the eyelids so it stays readable, and under the blackout, which hides everything.
     */
    private static void drawPerMille(Minecraft mc, LocalPlayer player, GuiGraphics g) {
        DrunkState s = player.getData(ModAttachments.DRUNK);
        if (s.total() < 0.005f) return;
        String text = String.format(Locale.GERMAN, "%.2f ‰%s", s.blood, s.stomach > 0.02f ? " ↑" : "");
        // Show the tolerance once it is worth mentioning.
        if (s.tolerance >= 0.05f) text += String.format(Locale.GERMAN, "  ·  Toleranz %d %%", Math.round(s.tolerance * 100));
        int colour = s.blood < Intoxication.TIPSY ? 0xB0E8B0
            : s.blood < Intoxication.DRUNK ? 0xFFD35A
            : s.blood < Intoxication.POISONING ? 0xFF8A30 : 0xFF3A3A;
        g.drawString(mc.font, text, (g.guiWidth() - mc.font.width(text)) / 2, g.guiHeight() - 82, colour, true);
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
                int secs = Intoxication.ticksUntilSober(s.blood, s.stomach, s.tolerance) / 20;
                // The blood level itself, with an arrow while the stomach is still feeding it.
                String rising = s.stomach > 0.02f ? " ↑" : "";
                g.drawString(mc.font, instance.getEffect().value().getDisplayName(), x + 28, y + 6, 0xFFFFFF);
                g.drawString(mc.font, String.format(Locale.GERMAN, "%.2f‰%s · %d:%02d",
                    s.blood, rising, secs / 60, secs % 60), x + 28, y + 16, 0x7F7F7F);
                return true;
            }
        }, ModEffects.INEBRIATION);
        // Weed counts hits, not levels: "Bekifft" with how many joints' worth, not "Bekifft XIV".
        event.registerMobEffect(new IClientMobEffectExtensions() {
            @Override
            public boolean renderInventoryText(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                               GuiGraphics g, int x, int y, int blitOffset) {
                Minecraft mc = Minecraft.getInstance();
                int secs = instance.getDuration() / 20;
                float joints = (instance.getAmplifier() + 1) / (float) DrugServer.HITS_PER_JOINT;
                g.drawString(mc.font, instance.getEffect().value().getDisplayName(), x + 28, y + 6, 0xFFFFFF);
                g.drawString(mc.font, String.format(Locale.GERMAN, "%.1f Joints · %d:%02d", joints, secs / 60, secs % 60),
                    x + 28, y + 16, 0x7F7F7F);
                return true;
            }
        }, ModEffects.WEED_HIGH);
    }
}
