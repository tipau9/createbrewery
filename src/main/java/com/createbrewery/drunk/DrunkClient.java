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
    /** Psychedelics: how hard the trip hits, which share of it is mushrooms or peyote, and fear. */
    static float trip, organic, desert, bad;
    /** The clear, bright day after a trip: 0..1. */
    static float afterglow;
    /** LSD coming up: strobing at the edges before the geometry, 0..1. */
    private static float comeUp, lastLsd;
    /** LSD: the view falling into itself for a few seconds (recursion), 0..1. */
    private static float recur;
    private static int recurLeft, flipLeft;
    /** Keta: 0 upright, 1 mirrored, 2 upside down - for a few seconds at a time. */
    private static int flip;
    /** DMT: 0..1, 1 = the full breakthrough. */
    private static float breakthrough;
    /** MDMA and meth: 0..1 each. */
    static float rolling;
    private static float tweak;
    /** Heroin (and a little Xanax), and heroin withdrawal: 0..1 each. */
    private static float opiate, sick;
    /** Lachgas: 0..1. */
    private static float wah;
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
        NeoForge.EVENT_BUS.addListener(DrunkClient::onScreenMouse);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onScreenKey);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onPlaySound);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onSoundSource);
        TripClient.init();
        RollClient.init();
        TweakClient.init();
        KetaClient.init();
        MusicPulse.init();
        DrugAudio.init();
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
        rolling = ease(rolling, player == null ? 0f : DrugEffect.felt(player, ModEffects.ROLLING));
        tweak = ease(tweak, player == null ? 0f : DrugEffect.felt(player, ModEffects.TWEAK));
        opiate = ease(opiate, player == null ? 0f : Math.min(1f, DrugEffect.felt(player, ModEffects.NOD)
            + (player.hasEffect(ModEffects.RESPIRATORY_DEPRESSION) ? 0.5f : 0f))
            * (player.hasEffect(ModEffects.SPEEDBALL) ? 0.4f : 1f));
        // Lachgas hits within a breath: faster than the other channels.
        float wahTarget = player == null ? 0f : DrugEffect.strength(player, ModEffects.WAH);
        wah = Math.abs(wahTarget - wah) < 0.01f ? wahTarget : wah + (wahTarget - wah) * 0.3f;
        sick = ease(sick, player == null ? 0f : DrugEffect.strength(player, ModEffects.WITHDRAWAL));
        // Meth sharpens like Koks, only harder and for longer.
        stim = ease(stim, player == null ? 0f : Math.max(DrugEffect.felt(player, ModEffects.COKE_HIGH), tweak));
        gray = ease(gray, player == null ? 0f : Math.max(0.7f * DrugEffect.strength(player, ModEffects.COKE_CRASH),
            Math.max(Math.max(0.5f * DrugEffect.strength(player, ModEffects.COMEDOWN), 0.4f * sick),
                0.8f * DrugEffect.strength(player, ModEffects.METH_CRASH))));
        float keta = player == null ? 0f : Math.max(DrugEffect.strength(player, ModEffects.K_HOLE),
            // Koks masks the Keta: it feels clearer than it is (the K-Loch does not care).
            0.85f * DrugEffect.felt(player, ModEffects.KETA_HIGH) * (1f - 0.4f * DrugEffect.strength(player, ModEffects.COKE_HIGH)));
        if (player != null) keta = Math.max(keta, 0.15f * DrugEffect.strength(player, ModEffects.DAZED));
        // Weed deepens the dissociation.
        if (player != null) keta *= 1f + 0.3f * DrugEffect.strength(player, ModEffects.WEED_HIGH);
        dissoc = ease(dissoc, Math.min(1f, keta));
        high = ease(high, player == null ? 0f : DrugEffect.felt(player, ModEffects.WEED_HIGH));
        green = ease(green, player == null ? 0f : DrugEffect.strength(player, ModEffects.GREENING_OUT));
        float lsd = player == null ? 0f : DrugEffect.felt(player, ModEffects.LSD_TRIP);
        float shroom = player == null ? 0f : DrugEffect.felt(player, ModEffects.SHROOM_TRIP);
        float mesc = player == null ? 0f : DrugEffect.felt(player, ModEffects.MESCALINE_TRIP);
        comeUp = ease(comeUp, lsd > lastLsd && lsd < 0.9f ? 1f : 0f);
        lastLsd = lsd;
        // A flashback, days later: a short echo of the trip, LSD-coloured.
        float all = lsd + shroom + mesc + (player == null ? 0f : DrugEffect.strength(player, ModEffects.FLASHBACK));
        trip = ease(trip, Math.min(1f, all * (1f + 0.4f * rolling) + (all > 0f ? 0.3f * high : 0f)));
        // The palette only shifts while something is active, so it does not snap when the trip ends.
        if (all > 0.001f) {
            organic = ease(organic, shroom / all);
            desert = ease(desert, mesc / all);
        }
        // A meth psychosis has its own look (TweakClient); the fear of a bad trip is only part of it.
        bad = ease(bad, player == null ? 0f : Math.max(DrugEffect.strength(player, ModEffects.BAD_TRIP),
            0.4f * DrugEffect.strength(player, ModEffects.PSYCHOSIS)));
        breakthrough = ease(breakthrough, player == null ? 0f : DrugEffect.strength(player, ModEffects.BREAKTHROUGH));
        afterglow = ease(afterglow, player == null ? 0f : DrugEffect.strength(player, ModEffects.AFTERGLOW));
        if (player != null && !mc.isPaused()) {
            com.createbrewery.drugs.Hallucinations.tick(player, DmtClient.beyond, trip, bad, desert);
            TripClient.tick(mc, player);
            RollClient.tick(mc, player);
            TweakClient.tick(mc, player);
            NodClient.tick(mc, player);
            BenzoClient.tick(mc, player);
            GasClient.tick(player);
            CokeClient.tick(mc, player);
            KetaClient.tick(mc, player);
            WeedClient.tick(mc, player);
            DmtClient.tick(mc, player);
            var r = player.getRandom();
            if (recurLeft > 0) recurLeft--;
            else if (trip * Math.max(0f, 1f - organic - desert) > 0.5f && r.nextFloat() < 1f / 900f) recurLeft = 60 + r.nextInt(60);
            recur = ease(recur, recurLeft > 0 ? 1f : 0f);
            if (flipLeft > 0 && --flipLeft == 0) flip = 0;
            else if (flipLeft == 0 && dissoc > 0.5f && r.nextFloat() < 1f / 1200f) {
                flip = 1 + r.nextInt(2);
                flipLeft = 40 + r.nextInt(60);
            }
        }
        DrugAudio.tick(wah, Math.max(dissoc, Math.max(DmtClient.waiting, DmtClient.beyond)), DmtClient.crack,
            trip * Math.max(0f, 1f - organic), trip * organic, rolling);

        boolean want = player != null && !shaderFailed && screen() > 0.01f && !shaderPackActive()
            && (Intoxication.visualIntensity(blood) > 0.01f || Intoxication.mood(blood) > 0.01f
                || stim > 0.01f || gray > 0.01f || dissoc > 0.01f || high > 0.01f || green > 0.01f
                || trip > 0.01f || bad > 0.01f || breakthrough > 0.01f || rolling > 0.01f || tweak > 0.01f || TweakClient.tired > 0.01f || NodClient.sick > 0.01f || BenzoClient.calm > 0.01f || CokeClient.line > 0.01f || BenzoClient.rebound > 0.01f || NodClient.air > 0.01f || opiate > 0.01f || wah > 0.01f || afterglow > 0.01f || DmtClient.descent > 0.01f || RollClient.heat > 0.01f || RollClient.zap > 0.01f);
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
            wahWah(mc, player);
            moreMusic(mc, player);
            echoes(mc, player);
        }
        hearing(mc);
        // High, and on heroin even more: the world as if through cotton wool.
        muffle = Math.min(1f - 0.55f * high, 1f - 0.6f * opiate);
    }

    /**
     * Sounds only the drunk player hears, inside their own head: the hangover heartbeat in step
     * with the throbbing screen edge, and ringing ears when nodding off or passing out.
     */
    /** Lachgas: the famous throbbing wah-wah inside the head, about three times a second. */
    private static void wahWah(Minecraft mc, LocalPlayer player) {
        if (wah < 0.2f || player.tickCount % 7 != 0) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(),
            0.55f + 0.1f * (float) Math.sin(player.tickCount * 0.3), 0.5f * wah));
    }

    /** Moves {@code current} towards {@code target} over a couple of seconds. */
    static float ease(float current, float target) {
        float next = current + (target - current) * 0.05f;
        return Math.abs(target - next) < 0.002f ? target : next;
    }

    private static void bodySounds(Minecraft mc, LocalPlayer player) {
        paranoia(mc, player);
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

    /** Something behind you: footsteps coming closer or a hiss. Stops the moment you turn round. */
    private static final class Phantom extends SimpleSoundInstance {
        Phantom(net.minecraft.sounds.SoundEvent sound, SoundSource source, float volume, float pitch, LocalPlayer player, Vec3 at) {
            super(sound, source, volume, pitch, player.getRandom(), at.x, at.y, at.z);
        }
    }

    private static Vec3 phantomAt;
    private static float phantomYaw;
    private static int phantomSteps, nextPhantomStep;
    private static Phantom phantomSound;
    /** A shadow in the corner of the eye: where it stands (world yaw), how visible, and whether it is dissolving. */
    private static float shadowYaw, shadowAlpha;
    private static int shadowAge = -1;
    private static boolean shadowGoing;

    /**
     * Too dark to see much: night, caves, unlit rooms. Worked out here, because the client level
     * never updates its own sky darkness after loading.
     */
    static boolean dark(LocalPlayer player) {
        var pos = player.blockPosition();
        var level = player.level();
        float daylight = level instanceof net.minecraft.client.multiplayer.ClientLevel client ? client.getSkyDarken(1f) : 1f;
        return Math.max(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos),
            level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) * daylight) < 7f;
    }

    /**
     * Paranoia: from the second joint, with Koks, high in the dark, or in a bad trip. Footsteps creep up from
     * behind, or something hisses - nobody is there, and it stops the moment you turn round.
     * In the dark a figure stands at the edge of the view, and dissolves when you look at it.
     */
    private static void paranoia(Minecraft mc, LocalPlayer player) {
        boolean dark = dark(player);
        boolean paranoid = player.hasEffect(ModEffects.WEED_HIGH)
            && (DrugServer.joints(player) > 1f || player.hasEffect(ModEffects.COKE_HIGH) || (dark && high > 0.3f))
            || bad > 0.3f;
        if (phantomAt != null) {
            if (Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot() - phantomYaw)) > 110f) {
                if (phantomSound != null) mc.getSoundManager().stop(phantomSound); // turned round: silence
                phantomAt = null;
            } else if (phantomSteps > 0 && player.tickCount >= nextPhantomStep) {
                var below = net.minecraft.core.BlockPos.containing(phantomAt).below();
                var step = player.level().getBlockState(below).getSoundType().getStepSound();
                phantomSound = new Phantom(step, SoundSource.PLAYERS, 0.35f, 0.85f + player.getRandom().nextFloat() * 0.15f, player, phantomAt);
                mc.getSoundManager().play(phantomSound);
                // Each step a little closer.
                phantomAt = phantomAt.add(player.position().subtract(phantomAt).normalize().scale(0.35));
                nextPhantomStep = player.tickCount + 9 + player.getRandom().nextInt(4);
                if (--phantomSteps == 0) phantomAt = null;
            } else if (phantomSteps == 0 && phantomSound != null && !mc.getSoundManager().isActive(phantomSound)) {
                phantomAt = null;
            }
        }
        if (paranoid && phantomAt == null && player.tickCount >= nextFootstep) {
            Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
            phantomAt = player.position().subtract(look.scale(4.0));
            phantomYaw = player.getYRot();
            if (player.getRandom().nextFloat() < 0.25f) {
                // A faint hiss right behind you.
                phantomSteps = 0;
                phantomSound = new Phantom(net.minecraft.sounds.SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 0.15f, 0.5f, player,
                    player.position().subtract(look.scale(2.0)));
                mc.getSoundManager().play(phantomSound);
            } else {
                phantomSteps = 3 + player.getRandom().nextInt(3);
                nextPhantomStep = player.tickCount;
            }
            nextFootstep = player.tickCount + (dark ? 200 : 300) + player.getRandom().nextInt(dark ? 300 : 500);
        } else if (!paranoid) {
            nextFootstep = Math.max(nextFootstep, player.tickCount + 200);
        }

        // The figure: comes in slowly, gone when looked at, never longer than a few seconds.
        if (shadowAge < 0 && paranoid && dark && player.getRandom().nextFloat() < 1f / 700f) {
            shadowYaw = player.getYRot() + (player.getRandom().nextBoolean() ? 62f : -62f);
            shadowAge = 0;
            shadowGoing = false;
        }
        if (shadowAge >= 0) {
            shadowAge++;
            float rel = net.minecraft.util.Mth.wrapDegrees(shadowYaw - player.getYRot());
            if (Math.abs(rel) < 40f || Math.abs(rel) > 100f || shadowAge > 140 || !paranoid) shadowGoing = true;
            // In over 1.5 s, out over 0.6 s: always a soft fade, never a pop.
            shadowAlpha = shadowGoing ? shadowAlpha - 1f / 12f : Math.min(1f, shadowAlpha + 1f / 30f);
            if (shadowGoing && shadowAlpha <= 0f) {
                shadowAlpha = 0f;
                shadowAge = -1;
            }
        }
    }

    static final ResourceLocation SHADOW_FIGURE =
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "textures/misc/shadow_figure.png");

    private static void drawShadow(LocalPlayer player, GuiGraphics g, int w, int h) {
        if (shadowAge < 0 || shadowAlpha <= 0f) return;
        float rel = net.minecraft.util.Mth.wrapDegrees(shadowYaw - player.getYRot());
        int fh = (int) (h * 0.55f), fw = fh / 2;
        // At the very edge of the view, half out of it.
        int x = rel > 0 ? w - fw * 2 / 3 : -fw / 3;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, shadowAlpha * 0.5f * screen());
        g.blit(SHADOW_FIGURE, x, (h - fh) / 2 + h / 12, fw, fh, 0f, 0f, 64, 128, 64, 128);
        g.setColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
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
            chain.setUniform("Trip", trip * screen());
            chain.setUniform("Organic", organic);
            chain.setUniform("Desert", desert);
            chain.setUniform("BadTrip", bad * screen());
            chain.setUniform("Break", breakthrough * screen());
            chain.setUniform("ComeUp", comeUp * screen());
            chain.setUniform("Recur", recur * screen());
            chain.setUniform("Flip", screen() > 0.5f && player != null && dissoc > 0.3f ? (float) flip : 0f);
            chain.setUniform("Crack", DmtClient.crack * screen());
            chain.setUniform("Waiting", DmtClient.waiting * screen());
            chain.setUniform("Beyond", DmtClient.beyond * screen());
            chain.setUniform("Descent", DmtClient.descent * screen());
            chain.setUniform("Roll", rolling * screen());
            chain.setUniform("Rush", RollClient.rush * screen());
            chain.setUniform("Beat", RollClient.beat * screen());
            MusicPulse.update();
            // In the groove every kick hits harder, and at the peak harder still.
            chain.setUniform("Kick", MusicPulse.kick * (1f + 0.5f * RollClient.groove + RollClient.peak));
            chain.setUniform("Peak", RollClient.peak * screen());
            chain.setUniform("Level", MusicPulse.level);
            chain.setUniform("Hats", MusicPulse.hats * (1f + RollClient.peak));
            chain.setUniform("Wiggle", RollClient.wiggle * screen());
            chain.setUniform("Zap", RollClient.zap * screen());
            chain.setUniform("Faded", RollClient.faded * screen());
            chain.setUniform("Scene", RollClient.scene * screen());
            chain.setUniform("Tension", MusicPulse.song.tension);
            chain.setUniform("Drop", MusicPulse.song.drop);
            chain.setUniform("Beats", (float) (MusicPulse.song.beats % 64));
            chain.setUniform("Tempo", (float) (60.0 / MusicPulse.song.period()));
            chain.setUniform("Heat", RollClient.heat * screen());
            chain.setUniform("Tweak", tweak * screen());
            chain.setUniform("Tired", TweakClient.tired * screen());
            chain.setUniform("Nod", opiate * screen());
            chain.setUniform("Flood", NodClient.flood * screen());
            chain.setUniform("Dream", NodClient.dream * screen());
            chain.setUniform("Breath", NodClient.breath);
            chain.setUniform("Air", NodClient.air * screen());
            chain.setUniform("Sick", NodClient.sick * screen());
            chain.setUniform("Calm", BenzoClient.calm * screen());
            chain.setUniform("Rebound", BenzoClient.rebound * screen());
            chain.setUniform("Wah", wah * screen());
            chain.setUniform("Gone", GasClient.gone * screen());
            chain.setUniform("Coke", CokeClient.coke * screen());
            chain.setUniform("Line", CokeClient.line * screen());
            chain.setUniform("Stare", TripClient.stare * screen());
            chain.setUniform("Harsh", TripClient.harsh);
            chain.setUniform("Afterglow", afterglow * screen());
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
        if (sound instanceof Echo || sound instanceof Phantom) return;
        synaesthesia(player, sound);
        // Xanax: the monsters just do not matter much - and afterwards, the rebound, everything
        // is too loud.
        float benzo = 1f - 0.5f * BenzoClient.calm + 0.4f * BenzoClient.rebound;
        if (Math.abs(benzo - 1f) > 0.05f && !(sound instanceof TickableSoundInstance)
            && (source == SoundSource.HOSTILE || BenzoClient.rebound > 0.05f && source != SoundSource.MUSIC && source != SoundSource.RECORDS)) {
            event.setSound(new EnhancedSound(sound, source == SoundSource.HOSTILE ? benzo : 1f + 0.4f * BenzoClient.rebound, 1f));
            return;
        }
        // DMT: every sound bends down and stretches, as if from very far away.
        if (breakthrough > 0.05f && !(sound instanceof TickableSoundInstance) && source != SoundSource.MUSIC) {
            event.setSound(new EnhancedSound(sound, 1f, 1f - 0.4f * breakthrough));
            return;
        }
        // Keta: every sound comes from far off - quieter, and a little low.
        if (dissoc > 0.2f && !(sound instanceof TickableSoundInstance) && source != SoundSource.MUSIC && source != SoundSource.RECORDS) {
            event.setSound(new EnhancedSound(sound, 1f - 0.4f * dissoc, 1f - 0.12f * dissoc));
            return;
        }
        // Lachgas: every sound comes in bent up or down, depending on where the wah is.
        if (wah > 0.05f && !(sound instanceof TickableSoundInstance) && source != SoundSource.MUSIC) {
            event.setSound(new EnhancedSound(sound, 1f, 1f - 0.3f * wah * (float) Math.sin(player.tickCount * 0.9)));
            // ...and stutters: it comes again, and again, fading.
            if (wah > 0.3f && !sound.isLooping() && pendingEchoes.size() < 16) {
                pendingEchoes.add(new PendingEcho(sound, player.tickCount + 3, 0.5f * wah));
                pendingEchoes.add(new PendingEcho(sound, player.tickCount + 6, 0.3f * wah));
            }
            return;
        }
        // Your own mining, placing and bites ring on for a moment (not every footstep).
        if (high > 0.2f && (source == SoundSource.PLAYERS || source == SoundSource.BLOCKS)
            && !sound.getLocation().getPath().endsWith(".step")
            && !(sound instanceof TickableSoundInstance) && !sound.isLooping()
            && player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < 9.0 && pendingEchoes.size() < 16) {
            pendingEchoes.add(new PendingEcho(sound, player.tickCount + 4, 0.3f * high));
        }
        // Tripping: every sound drifts slowly up and down in pitch, as if the air were bending.
        // Mushrooms instead make everything a little deeper and warmer.
        float lsdShare = Math.max(0f, 1f - organic - desert);
        float drift = (1f + 0.12f * trip * lsdShare * (float) Math.sin(player.tickCount * 0.02)) * (1f - 0.06f * trip * organic);
        if (high > 0.02f && source != SoundSource.MASTER && source != SoundSource.MUSIC && source != SoundSource.RECORDS
            && !(sound instanceof TickableSoundInstance)) {
            // The listener gain already raises everything; ambience and weather get more on top,
            // everything else is pulled back so that music and ambience stand out.
            boolean ambience = source == SoundSource.AMBIENT || source == SoundSource.WEATHER;
            float louder = ambience ? 1f + 0.8f * high : (1f + 0.3f * high) / listenerBoost();
            event.setSound(new EnhancedSound(sound, louder, (1f - 0.06f * high) * drift));
        } else if (trip > 0.05f && source != SoundSource.MASTER && source != SoundSource.MUSIC && source != SoundSource.RECORDS
            && !(sound instanceof TickableSoundInstance)) {
            event.setSound(new EnhancedSound(sound, 1f, drift));
        }
    }

    /**
     * Tripping, sounds can be seen: a burst of coloured notes where they come from, its colour
     * following the pitch of the sound.
     */
    private static void synaesthesia(LocalPlayer player, SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        if (trip < 0.25f || mc.level == null || sound.isRelative() || sound.getSource() == SoundSource.MUSIC
            || player.getRandom().nextFloat() > trip * 0.7f) return;
        double x = sound.getX(), y = sound.getY(), z = sound.getZ();
        if (player.distanceToSqr(x, y, z) > 24 * 24) return;
        float hue = Math.abs(sound.getLocation().hashCode() % 24) / 24f;
        int n = 1 + (int) (trip * 4);
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE,
                x + (player.getRandom().nextDouble() - 0.5), y + 0.3 + player.getRandom().nextDouble() * 0.6,
                z + (player.getRandom().nextDouble() - 0.5), (hue + i * 0.07) % 1f, 0.0, 0.0);
        }
    }

    /** A sound heard again a moment later, softer. */
    private record PendingEcho(SoundInstance sound, int at, float volume) {}
    private static final java.util.List<PendingEcho> pendingEchoes = new java.util.ArrayList<>();

    /**
     * Played from the client tick, not with playDelayed from inside the sound event: the sound
     * engine may be walking its own delayed queue right then.
     */
    private static void echoes(Minecraft mc, LocalPlayer player) {
        for (var it = pendingEchoes.iterator(); it.hasNext(); ) {
            PendingEcho echo = it.next();
            if (player.tickCount < echo.at()) continue;
            it.remove();
            Sound heard = echo.sound().getSound();
            if (heard != null) mc.getSoundManager().play(new Echo(echo.sound(), heard, echo.volume()));
        }
        if (pendingEchoes.size() > 32) pendingEchoes.clear(); // a stalled tick count must not grow it forever
    }

    /** The same variant as the original (not a new random one), softer. */
    private record Echo(SoundInstance sound, Sound heard, float volume) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return sound.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager manager) { return manager.getSoundEvent(sound.getLocation()); }
        @Override public Sound getSound() { return heard; }
        @Override public SoundSource getSource() { return sound.getSource(); }
        @Override public boolean isLooping() { return false; }
        @Override public boolean isRelative() { return sound.isRelative(); }
        @Override public int getDelay() { return 0; }
        @Override public float getVolume() { return sound.getVolume() * volume; }
        @Override public float getPitch() { return sound.getPitch(); }
        @Override public double getX() { return sound.getX(); }
        @Override public double getY() { return sound.getY(); }
        @Override public double getZ() { return sound.getZ(); }
        @Override public Attenuation getAttenuation() { return sound.getAttenuation(); }
        @Override public boolean canStartSilent() { return false; }
        @Override public boolean canPlaySound() { return true; }
        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound s, boolean looping) {
            return sound.getStream(buffers, s, looping);
        }
    }

    /** High-frequency gain for sounds around you: 1 sober, lower when high. Read on the sound thread. */
    private static volatile float muffle = 1f;
    private static int muffleFilter = -1;
    /** The OpenAL context the filter belongs to; a new one (F3+T, another audio device) needs a new filter. */
    private static long muffleContext;
    private static boolean muffleFailed;
    private static java.lang.reflect.Field channelSource;

    /**
     * High: steps, tools and mobs sound softer, as through thick headphones (an OpenAL low-pass).
     * Music, ambience and weather stay clear and loud. Every source is set, the filter or none,
     * because OpenAL reuses sources. Runs on the sound thread, like everything touching a channel.
     */
    private static void onSoundSource(net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent event) {
        // Sound Physics owns the direct filter (occlusion); setting ours would undo its work.
        if (muffleFailed || DrugAudio.PHYSICS) return;
        try {
            if (channelSource == null) {
                channelSource = com.mojang.blaze3d.audio.Channel.class.getDeclaredField("source");
                channelSource.setAccessible(true);
            }
            int source = channelSource.getInt(event.getChannel());
            SoundSource category = event.getSound().getSource();
            float gainHF = muffle;
            boolean around = category == SoundSource.PLAYERS || category == SoundSource.BLOCKS
                || category == SoundSource.NEUTRAL || category == SoundSource.HOSTILE;
            if (!around || gainHF >= 0.99f) {
                if (muffleFilter >= 0) org.lwjgl.openal.AL10.alSourcei(source, org.lwjgl.openal.EXTEfx.AL_DIRECT_FILTER,
                    org.lwjgl.openal.EXTEfx.AL_FILTER_NULL);
                return;
            }
            long context = org.lwjgl.openal.ALC10.alcGetCurrentContext();
            if (muffleFilter < 0 || context != muffleContext) {
                muffleContext = context;
                long device = org.lwjgl.openal.ALC10.alcGetContextsDevice(context);
                if (!org.lwjgl.openal.ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
                    muffleFailed = true;
                    return;
                }
                muffleFilter = org.lwjgl.openal.EXTEfx.alGenFilters();
                org.lwjgl.openal.EXTEfx.alFilteri(muffleFilter, org.lwjgl.openal.EXTEfx.AL_FILTER_TYPE, org.lwjgl.openal.EXTEfx.AL_FILTER_LOWPASS);
            }
            // A filter's settings are copied when it is attached, so setting it here is per sound.
            org.lwjgl.openal.EXTEfx.alFilterf(muffleFilter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAIN, 1f);
            org.lwjgl.openal.EXTEfx.alFilterf(muffleFilter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAINHF, gainHF);
            org.lwjgl.openal.AL10.alSourcei(source, org.lwjgl.openal.EXTEfx.AL_DIRECT_FILTER, muffleFilter);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            muffleFailed = true;
            LOGGER.warn("Muffled hearing unavailable; the high sounds unfiltered", e);
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

    /** Weed or MDMA: how much the music matters right now. */
    private static float loud() {
        return Math.max(high, rolling);
    }

    /** High: music and ambience up to about 2x (+6 dB), which is where the listener gain goes. */
    private static float listenerBoost() {
        return 1f + 1.0f * loud();
    }

    /**
     * High: music sounds better, so you want more of it - the game's music keeps playing instead of
     * waiting minutes between tracks. Checked every 10 s, and only while nothing is playing - no
     * track, no jukebox (see {@link #lastMusic}).
     */
    private static void moreMusic(Minecraft mc, LocalPlayer player) {
        if (loud() < 0.35f || player.tickCount % 200 != 0) return;
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
        if (hearingFailed || (loud() <= 0.001f && !hearingBoosted)) return;
        try {
            float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
            org.lwjgl.openal.AL10.alListenerf(org.lwjgl.openal.AL10.AL_GAIN, master * listenerBoost());
            hearingBoosted = loud() > 0.001f;
        } catch (RuntimeException | LinkageError e) {
            hearingFailed = true; // no sound device: nothing to make louder
        }
    }

    /** Draws the drunk vision over the finished world, before the hand and the HUD. */
    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        com.createbrewery.drugs.Hallucinations.render(event);
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
        worldUniforms(event);
        chainFrames++;
        // Same state handling as vanilla around its own post effect.
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(event.getPartialTick().getGameTimeDeltaTicks());
        main.bindWrite(false);
        RenderSystem.enableDepthTest();
    }

    /** Last frame's camera, for tracers that only follow what really moves. */
    private static final org.joml.Matrix4f prevViewProj = new org.joml.Matrix4f();
    private static Vec3 prevCam;
    private static java.lang.reflect.Field chainPasses;
    private static boolean worldFailed;

    /**
     * Hands the camera to the shader, so it can tell where in the world each pixel is. PostChain
     * only sets float uniforms itself; matrices go straight to each pass's program.
     */
    @SuppressWarnings("unchecked")
    private static void worldUniforms(RenderLevelStageEvent event) {
        Vec3 cam = event.getCamera().getPosition();
        org.joml.Matrix4f viewProj = new org.joml.Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        if (!worldFailed) {
            try {
                if (chainPasses == null) {
                    chainPasses = PostChain.class.getDeclaredField("passes");
                    chainPasses.setAccessible(true);
                }
                Vec3 moved = prevCam == null || chainFrames < 3 ? Vec3.ZERO : cam.subtract(prevCam);
                for (net.minecraft.client.renderer.PostPass pass : (java.util.List<net.minecraft.client.renderer.PostPass>) chainPasses.get(chain)) {
                    var effect = pass.getEffect();
                    effect.safeGetUniform("InvViewProj").set(new org.joml.Matrix4f(viewProj).invert());
                    effect.safeGetUniform("PrevViewProj").set(chainFrames < 3 ? viewProj : prevViewProj);
                    effect.safeGetUniform("CamDelta").set((float) moved.x, (float) moved.y, (float) moved.z);
                    // ponytail: wrapped every 1024 blocks for float precision; the patterns jump once at each wrap.
                    effect.safeGetUniform("CamPos").set((float) (cam.x % 1024.0), (float) (cam.y % 1024.0), (float) (cam.z % 1024.0));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                worldFailed = true;
                LOGGER.warn("Trip shader cannot see the world; surface patterns and tracers fall back to the screen", e);
            }
        }
        chain.setUniform("World", worldFailed ? 0f : 1f);
        prevViewProj.set(viewProj);
        prevCam = cam;
    }

    /** The player's comfort setting, 0..1. */
    static float screen() {
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
        if (player == null || (blood <= 0f && green <= 0f && breakthrough <= 0f && sick <= 0f && wah <= 0f
            && TripClient.laughing() <= 0f && TripClient.chill <= 0.01f && RollClient.rush <= 0.01f && RollClient.beat <= 0.01f && RollClient.zap <= 0.01f && NodClient.jerk <= 0.01f && WeedClient.laugh <= 0.01f)) return;
        // Roll only: yaw/pitch offsets here would split the view from the crosshair.
        double t = seconds(player, (float) event.getPartialTick());
        float roll = noise(t * 0.45, 5) * 11f * Intoxication.visualIntensity(blood);
        // The whole body sways while retching - slowly, about twice a second, never a fast shake.
        roll += (float) Math.sin(t * 14.0) * 2f * retch(player, (float) event.getPartialTick());
        // Greening out: the head swims in slow, wide circles.
        roll += noise(t * 0.3, 41) * 13f * green;
        // DMT: the view turns slowly, as if weightless.
        roll += (float) Math.sin(t * 0.3) * 18f * breakthrough;
        // ...and as it cracks open, the whole room vibrates.
        roll += noise(t * 40.0, 71) * 1.5f * DmtClient.crack;
        // Entzug: the whole body shivers.
        roll += noise(t * 12.0, 53) * 1.2f * sick;
        // Lachgas: dizzy - the head tips over, as if about to fall.
        roll += noise(t * 0.8, 61) * 15f * wah;
        // Mushrooms: shaking with laughter, and the chills of the come-up.
        roll += (float) Math.sin(t * 22.0) * 2.5f * TripClient.laughing();
        // MDMA: goosebumps with every rush.
        roll += noise(t * 9.0, 83) * 0.7f * RollClient.rush;
        // ...and the body moves with every kick, leaning now one way, now the other.
        roll += (float) Math.sin(t * 1.3) * 1.2f * MusicPulse.kick * RollClient.beat;
        // At the peak every kick slams the head.
        roll += noise(t * 30.0, 89) * 7f * MusicPulse.kick * RollClient.beat * RollClient.peak;
        // Weed: shaking with the giggles.
        roll += (float) Math.sin(t * 20.0) * 2f * WeedClient.laugh;
        // Heroin: waking from a nod with a jolt.
        roll += noise(t * 30.0, 101) * 4f * NodClient.jerk;
        // A brain zap jerks the head.
        roll += noise(t * 40.0, 97) * 4f * RollClient.zap;
        roll += noise(t * 14.0, 71) * 0.8f * TripClient.chill;
        event.setRoll(event.getRoll() + roll * screen());
    }

    private static void onFov(ComputeFovModifierEvent event) {
        if (event.getPlayer() != Minecraft.getInstance().player) return;
        // Keta pushes the world away (wider view), Koks narrows the focus.
        MobEffectInstance racing = event.getPlayer().getEffect(ModEffects.TACHYCARDIA);
        float tunnel = racing == null ? 0f : 0.03f * (racing.getAmplifier() + 1);
        float drugs = 1f + (0.12f * dissoc - 0.04f * stim - tunnel) * screen();
        // Tripping, the whole view breathes in and out; a bad trip makes it gasp.
        double sec = event.getPlayer().tickCount / 20.0;
        drugs += (float) (Math.sin(sec * 0.9) * 0.035 * trip + noise(sec * 2.2, 11) * 0.03 * bad) * screen();
        // ...and the walls close in on it.
        drugs -= 0.12f * bad * screen();
        // MDMA, very high: for a while the view seems to come from further back.
        drugs += 0.15f * RollClient.perspective * screen();
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
        steer(player, input);
        boolean overridden = player.hasEffect(ModEffects.HEART_ATTACK) || player.hasEffect(ModEffects.BLACKOUT)
            || player.hasEffect(ModEffects.VOMITING);
        couchLock(player, input, overridden);
    }

    /** Movement as the body actually carries it out, before weed makes it heavy. */
    private static float heavyForward, heavyLeft;
    private static int lastJump = -1000;

    /**
     * Weed: a heavy, relaxed body (couch lock). Getting going takes a moment, letting go glides
     * on for about half a block, and there is no urge to jump - a pause between jumps. Only on
     * the ground: swimming up and ladders stay as they are.
     */
    private static void couchLock(LocalPlayer player, Input input, boolean overridden) {
        if (overridden || high <= 0.01f) {
            heavyForward = input.forwardImpulse;
            heavyLeft = input.leftImpulse;
            return;
        }
        float start = 1f - 0.88f * high, stop = 1f - 0.75f * high;
        heavyForward += (input.forwardImpulse - heavyForward) * (Math.abs(input.forwardImpulse) > Math.abs(heavyForward) ? start : stop);
        heavyLeft += (input.leftImpulse - heavyLeft) * (Math.abs(input.leftImpulse) > Math.abs(heavyLeft) ? start : stop);
        // Settle at rest: vanilla keeps sprinting until the forward impulse is really 0.
        if (input.forwardImpulse == 0f && Math.abs(heavyForward) < 0.05f) heavyForward = 0f;
        if (input.leftImpulse == 0f && Math.abs(heavyLeft) < 0.05f) heavyLeft = 0f;
        input.forwardImpulse = heavyForward;
        input.leftImpulse = heavyLeft;
        if (input.jumping && player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable()) {
            if (player.tickCount - lastJump < 10 + (int) (25 * high)) input.jumping = false;
            else lastJump = player.tickCount;
        }
    }

    private static void steer(LocalPlayer player, Input input) {
        if (breakthrough > 0.6f) {
            // Broken through: the body is left behind and does nothing.
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
            // The K-wobble: walking goes robotic and wobbly, the body swaying off to the side.
            input.leftImpulse += noise(seconds(player, 0f) * 0.9, 67) * 0.4f * dissoc * Math.abs(input.forwardImpulse);
            if (player.hasEffect(ModEffects.K_HOLE)) input.jumping = false;
        }
        // Weed: couchlock - after standing a while, the legs take a moment to get going.
        input.forwardImpulse *= WeedClient.legs();
        input.leftImpulse *= WeedClient.legs();
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
        // High: open a chest or a workbench and - what did I want here again? The hands stop for half a second.
        LocalPlayer player = Minecraft.getInstance().player;
        if (!event.isCanceled() && player != null && high > 0.1f && event.getNewScreen() instanceof AbstractContainerScreen<?>
            && !(event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
            && !(event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)
            && player.getRandom().nextFloat() < 0.4f * high) {
            blankUntil = net.minecraft.Util.getMillis() + 500;
        }
    }

    /** Until when (ms) the mind is blank in an open container; clicks and keys do nothing. */
    private static long blankUntil;

    private static boolean blank(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof AbstractContainerScreen<?> && net.minecraft.Util.getMillis() < blankUntil;
    }

    private static void onScreenMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (blank(event.getScreen())) event.setCanceled(true);
    }

    private static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        // Escape still closes it.
        if (blank(event.getScreen()) && event.getKeyCode() != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) event.setCanceled(true);
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

        drawShadow(player, g, w, h);
        RollClient.drawDancer(player, g, w, h);
        TweakClient.drawDart(player, g, w, h, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        KetaClient.drawGhosts(player, g, event.getPartialTick().getGameTimeDeltaPartialTick(true));

        float lid = 0f;
        if (blood >= Intoxication.DRUNK) {
            // Heavy eyelids that keep sinking; from beer five they fall shut for a moment (micro-sleep).
            float e = ramp(Intoxication.DRUNK);
            lid = (0.05f + 0.2f * e) * (0.6f + 0.4f * noise(t * 0.5, 17));
            if (blood >= Intoxication.SMASHED) {
                lid = Math.max(lid, nod(t) * 0.52f);
            }
        }
        // Heroin: on the nod the eyes sink shut and jerk open again (NodClient); Xanax makes them heavy.
        lid = Math.max(lid, Math.max(Math.max(Math.max(NodClient.lid, 0.1f * opiate), BenzoClient.lid), WeedClient.lid) * screen());
        if (lid > 0f) {
            int px = (int) (h * lid);
            int feather = h / 8;
            // Behind closed eyes, the nod's dream shows through.
            int dark = (int) (0xF5 * (1f - 0.75f * NodClient.dream * screen())) << 24;
            g.fill(0, 0, w, px, dark);
            g.fillGradient(0, px, w, px + feather, dark, 0x00000000);
            g.fill(0, h - px, w, h, dark);
            g.fillGradient(0, h - px - feather, w, h - px, 0x00000000, dark);
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
        CokeClient.drawCraving(player, event.getGuiGraphics());
        WeedClient.drawMunchies(player, event.getGuiGraphics());
        BenzoClient.drawGap(event.getGuiGraphics());
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
        event.registerMobEffect(new IClientMobEffectExtensions() {
            @Override
            public boolean isVisibleInInventory(MobEffectInstance instance) { return false; }
            @Override
            public boolean isVisibleInGui(MobEffectInstance instance) { return false; }
        }, ModEffects.FLASHBACK_PENDING);
    }
}
