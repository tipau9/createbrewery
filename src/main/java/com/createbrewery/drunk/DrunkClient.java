package com.createbrewery.drunk;

import com.createbrewery.Config;
import com.createbrewery.CreateBrewery;
import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.drugs.DrugServer;
import com.createbrewery.effect.HiccupsEffect;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.sound.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates all client-side effects of intoxication and drug consumption:
 * - State management and easing of blood per-mille and drug concentrations
 * - Sensory audio filters (muffled hearing, ear ringing, synaesthesia, echoes, audio wah-wah)
 * - Delegation of rendering/shaders to {@link DrugPostProcessor}
 * - Delegation of camera wobble/input to {@link DrunkMovementHandler}
 * - Delegation of hallucinations to {@link HallucinationClient}
 */
public final class DrunkClient {
    private DrunkClient() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceLocation SHADOW_FIGURE = HallucinationClient.SHADOW_FIGURE;

    /** Blood level eased towards the synced value, so water, vomiting or sleep fade instead of snap. */
    static float blood;
    /** Koks, the crash after it, and Keta, eased like the blood level (0..1 each). */
    static float stim, gray, dissoc, high, green;
    /** Psychedelics: how hard the trip hits, which share of it is mushrooms or peyote, and fear. */
    static float trip, organic, desert, bad;
    /** The clear, bright day after a trip: 0..1. */
    static float afterglow;
    /** LSD coming up: strobing at the edges before the geometry, 0..1. */
    static float comeUp, lastLsd;
    /** LSD: the view falling into itself for a few seconds (recursion), 0..1. */
    static float recur;
    private static int recurLeft, flipLeft;
    /** Keta: 0 upright, 1 mirrored, 2 upside down - for a few seconds at a time. */
    static int flip;
    /** A seizure: the body convulses, 0..1. */
    static float seizing;
    /** DMT: 0..1, 1 = the full breakthrough. */
    static float breakthrough;
    /** MDMA and meth: 0..1 each. */
    static float rolling;
    static float tweak;
    /** Heroin (and a little Xanax), and heroin withdrawal: 0..1 each. */
    static float opiate, sick;
    /** Lachgas: 0..1. */
    static float wah;
    /**
     * Lachgas: the wah-wah itself - a throb through everything heard and seen, fast at the height
     * of the hit (about 4 a second) and slowing as it fades. The phase runs on ticks.
     */
    private static double wahPhase;
    private static long wahCycle;

    private static int lastCokeBeat;
    /** Last hangover heartbeat played, and when the ears last rang (client ticks of the player). */
    private static int lastBeat = Integer.MIN_VALUE;
    private static int lastRinging = -1000;
    private static boolean wasNodding, wasBlackedOut;

    /** A new player entity (respawn, new world): the tickCount gates start over. */
    static void resetGates() {
        HallucinationClient.reset();
        DrunkMovementHandler.reset();
        BenzoClient.reset();
        CokeClient.reset();
        DmtClient.reset();
        KetaClient.reset();
        NodClient.reset();
        PoseClient.reset();
        RollClient.reset();
        TripClient.reset();
        TweakClient.reset();
        WeedClient.reset();
        com.createbrewery.drugs.DrugPose.SEEN.clear();
        com.createbrewery.drugs.DrugPose.ACTING.clear();
    }

    public static void init() {
        DrugPostProcessor.initIntegrations();
        NeoForge.EVENT_BUS.addListener(DrunkClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(DrugPostProcessor::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(DrunkMovementHandler::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(DrunkMovementHandler::onFov);
        NeoForge.EVENT_BUS.addListener(DrunkMovementHandler::onMovementInput);
        NeoForge.EVENT_BUS.addListener(DrunkMovementHandler::onPlayerTurn);
        NeoForge.EVENT_BUS.addListener(DrugPostProcessor::onGuiPre);
        NeoForge.EVENT_BUS.addListener(DrugPostProcessor::onGuiPost);
        NeoForge.EVENT_BUS.addListener(DrugPostProcessor::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, DrugPostProcessor::onGuiChain);
        NeoForge.EVENT_BUS.addListener(DrunkMovementHandler::onInteract);
        NeoForge.EVENT_BUS.addListener(DrunkMovementHandler::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(DrunkMovementHandler::onScreenMouse);
        NeoForge.EVENT_BUS.addListener(DrunkMovementHandler::onScreenKey);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onPlaySound);
        NeoForge.EVENT_BUS.addListener(DrunkClient::onSoundSource);
        TripClient.init();
        RollClient.init();
        TweakClient.init();
        KetaClient.init();
        MusicPulse.init();
        DrugAudio.init();
        PoseClient.init();
        SniffFirstPerson.init();
        HiccupsEffect.clientKick = entity -> {
            if (entity == Minecraft.getInstance().player) {
                float side = (entity.getRandom().nextFloat() - 0.5f) * 3f;
                entity.turn(side / 0.15, -5.0 / 0.15);
            }
        };
    }

    /** Layered sines in -1..1. {@code seed} picks an independent curve. */
    public static float noise(double t, int seed) {
        double s = seed * 12.9898;
        return (float) (Math.sin(t + s) * 0.5 + Math.sin(t * 2.31 + s * 1.7) * 0.3 + Math.sin(t * 4.13 + s * 2.9) * 0.2);
    }

    public static float ramp(float from) {
        return Intoxication.ramp(blood, from);
    }

    public static boolean blackedOut() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(ModEffects.BLACKOUT);
    }

    public static double seconds(LocalPlayer player, float partial) {
        return (player.tickCount + partial) / 20.0;
    }

    private static java.lang.ref.WeakReference<LocalPlayer> lastPlayer = new java.lang.ref.WeakReference<>(null);

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (lastPlayer.get() != player) {
            lastPlayer = new java.lang.ref.WeakReference<>(player);
            resetGates();
        }
        SniffProps.tick(mc);
        float target = player == null ? 0f : DrunkServer.feltFor(player, player.getData(ModAttachments.DRUNK));
        blood = player == null ? 0f : blood + (target - blood) * 0.1f;
        if (Math.abs(target - blood) < 0.001f) blood = target;

        rolling = ease(rolling, player == null ? 0f : DrugEffect.felt(player, ModEffects.ROLLING)
            * (1f - 0.35f * DrugEffect.strength(player, ModEffects.COKE_HIGH)));
        tweak = ease(tweak, player == null ? 0f : DrugEffect.felt(player, ModEffects.TWEAK));
        opiate = ease(opiate, player == null ? 0f : Math.min(1f, DrugEffect.felt(player, ModEffects.NOD)
            + (player.hasEffect(ModEffects.RESPIRATORY_DEPRESSION) ? 0.5f : 0f))
            * (player.hasEffect(ModEffects.SPEEDBALL) ? 0.4f : 1f));

        float wahTarget = player == null ? 0f : DrugEffect.strength(player, ModEffects.WAH);
        wah = Math.abs(wahTarget - wah) < 0.01f ? wahTarget : wah + (wahTarget - wah) * 0.3f;
        if (wah > 0.01f && !mc.isPaused()) wahPhase += wahStep();
        sick = ease(sick, player == null ? 0f : DrugEffect.strength(player, ModEffects.WITHDRAWAL));
        stim = ease(stim, player == null ? 0f : Math.max(DrugEffect.felt(player, ModEffects.COKE_HIGH), tweak));

        gray = ease(gray, player == null ? 0f : Math.max(0.7f * DrugEffect.strength(player, ModEffects.COKE_CRASH),
            Math.max(Math.max(0.5f * DrugEffect.strength(player, ModEffects.COMEDOWN), 0.4f * sick),
                0.8f * DrugEffect.strength(player, ModEffects.METH_CRASH))));
        float keta = player == null ? 0f : Math.max(DrugEffect.strength(player, ModEffects.K_HOLE),
            0.85f * DrugEffect.felt(player, ModEffects.KETA_HIGH) * (1f - 0.4f * DrugEffect.strength(player, ModEffects.COKE_HIGH)));
        if (player != null) keta = Math.max(keta, 0.15f * DrugEffect.strength(player, ModEffects.DAZED));
        if (player != null) keta *= 1f + 0.3f * DrugEffect.strength(player, ModEffects.WEED_HIGH);
        dissoc = ease(dissoc, Math.min(1f, keta));
        high = ease(high, player == null ? 0f : DrugEffect.felt(player, ModEffects.WEED_HIGH));
        green = ease(green, player == null ? 0f : DrugEffect.strength(player, ModEffects.GREENING_OUT));
        float lsd = player == null ? 0f : DrugEffect.felt(player, ModEffects.LSD_TRIP);
        float shroom = player == null ? 0f : DrugEffect.felt(player, ModEffects.SHROOM_TRIP);
        float mesc = player == null ? 0f : DrugEffect.felt(player, ModEffects.MESCALINE_TRIP);
        comeUp = ease(comeUp, lsd > lastLsd && lsd < 0.9f ? 1f : 0f);
        lastLsd = lsd;

        float all = lsd + shroom + mesc + (player == null ? 0f : DrugEffect.strength(player, ModEffects.FLASHBACK));
        trip = ease(trip, Math.min(1f, all * (1f + 0.4f * rolling) + (all > 0f ? 0.3f * high : 0f)));
        if (all > 0.001f) {
            organic = ease(organic, shroom / all);
            desert = ease(desert, mesc / all);
        }
        bad = ease(bad, player == null ? 0f : Math.max(DrugEffect.strength(player, ModEffects.BAD_TRIP),
            0.4f * DrugEffect.strength(player, ModEffects.PSYCHOSIS)));
        breakthrough = ease(breakthrough, player == null ? 0f : DrugEffect.strength(player, ModEffects.BREAKTHROUGH));
        afterglow = ease(afterglow, player == null ? 0f : DrugEffect.strength(player, ModEffects.AFTERGLOW));
        if (player != null && !mc.isPaused()) {
            MobEffectInstance coke = player.getEffect(ModEffects.COKE_HIGH);
            float bugs = Math.max(TweakClient.tired, coke != null && coke.getAmplifier() >= 2 ? CokeClient.coke : 0f);
            com.createbrewery.drugs.Hallucinations.tick(player, DmtClient.beyond, trip, bad, desert, bugs);
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
            PoseClient.tick(player);
            var r = player.getRandom();
            seizing = player.hasEffect(ModEffects.SEIZURE) ? 1f : Math.max(0f, seizing - 0.1f);
            DrunkMovementHandler.nystagmus(player);
            HallucinationClient.tick(mc, player);
            if (seizing > 0.5f) player.turn((r.nextFloat() - 0.5f) * 10f / 0.15f, (r.nextFloat() - 0.5f) * 8f / 0.15f);
            if (recurLeft > 0) recurLeft--;
            else if (trip * Math.max(0f, 1f - organic - desert) > 0.75f && r.nextFloat() < 1f / 900f) recurLeft = 60 + r.nextInt(60);
            recur = ease(recur, recurLeft > 0 ? 1f : 0f);
            if (flipLeft > 0 && --flipLeft == 0) flip = 0;
            else if (flipLeft == 0 && dissoc > 0.5f && r.nextFloat() < 1f / 1200f) {
                flip = 1 + r.nextInt(2);
                flipLeft = 40 + r.nextInt(60);
            }
        }
        DrugAudio.tick(wah * (0.3f + 0.7f * wahPulse(0f)), Math.max(dissoc, Math.max(DmtClient.waiting, DmtClient.beyond)), DmtClient.crack,
            trip * Math.max(0f, 1f - organic), trip * organic, rolling);

        DrugPostProcessor.updateChain(mc, player);

        if (player != null && !mc.isPaused()) {
            bodySounds(mc, player);
            wahWah(mc, player);
            moreMusic(mc, player);
            echoes(mc, player);
        }
        hearing(mc);
        muffle = Math.min(1f - 0.55f * high, 1f - 0.6f * opiate);
        boolean atMic = player != null && com.createbrewery.block.club.MicrophoneBlock.isAtActiveMicrophone(player);
        VoiceFx.params = player == null ? VoiceFx.Params.NONE : new VoiceFx.Params(Intoxication.visualIntensity(blood), wah, wahPulse(0f),
            Math.max(opiate, BenzoClient.calm), dissoc, Math.max(CokeClient.coke, tweak), Math.max(DmtClient.waiting, DmtClient.beyond),
            atMic ? 1.0f : 0.0f);
        DrugPostProcessor.debugLog(mc, player);
    }

    private static void wahWah(Minecraft mc, LocalPlayer player) {
        long cycle = (long) Math.floor(wahPhase / (Math.PI * 2));
        if (cycle == wahCycle) return;
        wahCycle = cycle;
        if (wah < 0.2f) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(),
            0.5f + 0.15f * wah, 0.35f * wah));
    }

    private static double wahStep() {
        return Math.PI * 2 * (1.5 + 2.5 * wah) / 20.0;
    }

    static float wahPulse(float partial) {
        return 0.5f + 0.5f * (float) Math.sin(wahPhase + partial * wahStep());
    }

    public static float ease(float current, float target) {
        float next = current + (target - current) * 0.05f;
        return Math.abs(target - next) < 0.002f ? target : next;
    }

    private static void bodySounds(Minecraft mc, LocalPlayer player) {
        MobEffectInstance coke = player.getEffect(ModEffects.COKE_HIGH);
        MobEffectInstance racing = player.getEffect(ModEffects.TACHYCARDIA);
        boolean failing = player.hasEffect(ModEffects.HEART_ATTACK);
        boolean audible = failing || racing != null || (coke != null && stim > 0.2f);
        if (audible) {
            int interval = coke == null ? 10 : Math.max(8, 12 - 2 * coke.getAmplifier());
            if (racing != null) interval = racing.getAmplifier() >= 1 ? 6 : 7;
            if (player.hasEffect(ModEffects.CK_MIX)) interval = 6 + player.getRandom().nextInt(10);
            if (failing) interval = 5 + player.getRandom().nextInt(25);
            if (player.tickCount - lastCokeBeat >= interval) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(),
                    failing ? 0.8f : 1.25f, failing ? 0.35f : 0.45f));
                lastCokeBeat = player.tickCount;
            }
        }
        if (player.hasEffect(ModEffects.HANGOVER)) {
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

    public static boolean dark(LocalPlayer player) {
        var pos = player.blockPosition();
        var level = player.level();
        float daylight = level instanceof net.minecraft.client.multiplayer.ClientLevel client ? client.getSkyDarken(1f) : 1f;
        return Math.max(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos),
            level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) * daylight) < 7f;
    }

    static float nod(double t) {
        return Math.max(0f, Math.min(1f, (noise(t * 0.4, 21) - 0.55f) / 0.3f));
    }

    private static void onPlaySound(PlaySoundEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        SoundInstance sound = event.getSound();
        if (player == null || sound == null) return;
        if (DrugPostProcessor.numb(player) && sound.getLocation().getPath().startsWith("entity.player.hurt")
            && player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < 4.0) {
            event.setSound(null);
            return;
        }
        SoundSource source = sound.getSource();
        if (source == SoundSource.MUSIC || source == SoundSource.RECORDS) lastMusic = sound;
        if (sound instanceof Echo || sound.getClass().getSimpleName().equals("Phantom")) return;
        synaesthesia(player, sound);
        float benzo = 1f - 0.5f * BenzoClient.calm + 0.4f * BenzoClient.rebound;
        if (Math.abs(benzo - 1f) > 0.05f && !(sound instanceof TickableSoundInstance)
            && (source == SoundSource.HOSTILE || BenzoClient.rebound > 0.05f && source != SoundSource.MUSIC && source != SoundSource.RECORDS)) {
            event.setSound(new EnhancedSound(sound, source == SoundSource.HOSTILE ? benzo : 1f + 0.4f * BenzoClient.rebound, 1f));
            return;
        }
        if (breakthrough > 0.05f && !(sound instanceof TickableSoundInstance) && source != SoundSource.MUSIC) {
            event.setSound(new EnhancedSound(sound, 1f, 1f - 0.4f * breakthrough));
            return;
        }
        if (dissoc > 0.2f && !(sound instanceof TickableSoundInstance) && source != SoundSource.MUSIC && source != SoundSource.RECORDS) {
            event.setSound(new EnhancedSound(sound, 1f - 0.4f * dissoc, 1f - 0.12f * dissoc));
            return;
        }
        if (wah > 0.05f && !(sound instanceof TickableSoundInstance) && source != SoundSource.MUSIC) {
            if (wah > 0.3f && !sound.isLooping() && pendingEchoes.size() < 16) {
                pendingEchoes.add(new PendingEcho(sound, player.tickCount + 3, 0.5f * wah));
                pendingEchoes.add(new PendingEcho(sound, player.tickCount + 6, 0.3f * wah));
            }
            return;
        }
        if (high > 0.2f && (source == SoundSource.PLAYERS || source == SoundSource.BLOCKS)
            && !sound.getLocation().getPath().endsWith(".step")
            && !(sound instanceof TickableSoundInstance) && !sound.isLooping()
            && player.distanceToSqr(sound.getX(), sound.getY(), sound.getZ()) < 9.0 && pendingEchoes.size() < 16) {
            pendingEchoes.add(new PendingEcho(sound, player.tickCount + 4, 0.3f * high));
        }
        float lsdShare = Math.max(0f, 1f - organic - desert);
        float drift = (1f + 0.12f * trip * lsdShare * (float) Math.sin(player.tickCount * 0.02)) * (1f - 0.06f * trip * organic);
        if (high > 0.02f && source != SoundSource.MASTER && source != SoundSource.MUSIC && source != SoundSource.RECORDS
            && !(sound instanceof TickableSoundInstance)) {
            boolean ambience = source == SoundSource.AMBIENT || source == SoundSource.WEATHER;
            float louder = ambience ? (2.2f + 2.5f * high) : (1.15f + 0.35f * high);
            event.setSound(new EnhancedSound(sound, louder, (1f - 0.04f * high) * drift));
        } else if (trip > 0.05f && source != SoundSource.MASTER && source != SoundSource.MUSIC && source != SoundSource.RECORDS
            && !(sound instanceof TickableSoundInstance)) {
            event.setSound(new EnhancedSound(sound, 1f, drift));
        }
    }

    private static void synaesthesia(LocalPlayer player, SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        if (trip < 0.25f || mc.level == null || sound.isRelative() || sound.getSource() == SoundSource.MUSIC
            || player.getRandom().nextFloat() > trip * 0.7f) return;
        double x = sound.getX(), y = sound.getY(), z = sound.getZ();
        if (player.distanceToSqr(x, y, z) > 24 * 24) return;
        float hue = Math.abs(sound.getLocation().hashCode() % 24) / 24f;
        int n = 1 + (int) (trip * 4);
        for (int i = 0; i < n; i++) {
            mc.level.addParticle(ParticleTypes.NOTE,
                x + (player.getRandom().nextDouble() - 0.5), y + 0.3 + player.getRandom().nextDouble() * 0.6,
                z + (player.getRandom().nextDouble() - 0.5), (hue + i * 0.07) % 1f, 0.0, 0.0);
        }
    }

    private record PendingEcho(SoundInstance sound, int at, float volume) {}
    private static final List<PendingEcho> pendingEchoes = new ArrayList<>();

    private static void echoes(Minecraft mc, LocalPlayer player) {
        for (var it = pendingEchoes.iterator(); it.hasNext(); ) {
            PendingEcho echo = it.next();
            if (player.tickCount < echo.at()) continue;
            it.remove();
            Sound heard = echo.sound().getSound();
            if (heard != null) mc.getSoundManager().play(new Echo(echo.sound(), heard, echo.volume()));
        }
        if (pendingEchoes.size() > 32) pendingEchoes.clear();
    }

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

    private static volatile float muffle = 1f;
    private static int muffleFilter = -1;
    private static long muffleContext;
    private static boolean muffleFailed;
    private static Field channelSource;

    private static void onSoundSource(PlaySoundSourceEvent event) {
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
            org.lwjgl.openal.EXTEfx.alFilterf(muffleFilter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAIN, 1f);
            org.lwjgl.openal.EXTEfx.alFilterf(muffleFilter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAINHF, gainHF);
            org.lwjgl.openal.AL10.alSourcei(source, org.lwjgl.openal.EXTEfx.AL_DIRECT_FILTER, muffleFilter);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            muffleFailed = true;
            LOGGER.warn("Muffled hearing unavailable; the high sounds unfiltered", e);
        }
    }

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

    private static float loud() {
        return Math.max(Math.max(high, rolling), 0.6f * trip * organic);
    }

    private static float listenerBoost() {
        return 1f + 1.4f * loud();
    }

    private static void moreMusic(Minecraft mc, LocalPlayer player) {
        if (loud() < 0.35f || player.tickCount % 200 != 0) return;
        if (lastMusic != null && mc.getSoundManager().isActive(lastMusic)) return;
        var music = mc.getSituationalMusic();
        if (music != null) mc.getMusicManager().startPlaying(music);
    }

    private static SoundInstance lastMusic;
    private static boolean hearingBoosted;
    private static boolean hearingFailed;

    private static void hearing(Minecraft mc) {
        if (hearingFailed || (loud() <= 0.001f && wah <= 0.001f && !hearingBoosted)) return;
        try {
            float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
            float throb = 1f - 0.5f * wah * (1f - wahPulse(0f));
            org.lwjgl.openal.AL10.alListenerf(org.lwjgl.openal.AL10.AL_GAIN, master * listenerBoost() * throb);
            hearingBoosted = loud() > 0.001f || wah > 0.001f;
        } catch (RuntimeException | LinkageError e) {
            hearingFailed = true;
        }
    }

    public static float screen() {
        return Config.CLIENT_SPEC.isLoaded() ? Config.SCREEN_EFFECTS.get().floatValue() : 1f;
    }

    public static boolean shaderPack() {
        return DrugPostProcessor.shaderPack();
    }

    public static void exhale(int entity) {
        DrugPostProcessor.exhale(entity);
    }

    public static boolean quasar() {
        return DrugPostProcessor.quasar();
    }

    public static boolean veilParticles(String emitter, double x, double y, double z) {
        return DrugPostProcessor.veilParticles(emitter, x, y, z);
    }

    // ---- renderers + layer definitions ----

    public static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        if (com.createbrewery.drugs.Hallucinations.GEO) {
            GeoVisions.registerRenderers(event);
        } else {
            event.registerEntityRenderer(com.createbrewery.drugs.HallucinationEntity.SHADOW_PERSON.get(),
                net.minecraft.client.renderer.entity.NoopRenderer::new);
            event.registerEntityRenderer(com.createbrewery.drugs.HallucinationEntity.CRAWLER.get(),
                net.minecraft.client.renderer.entity.NoopRenderer::new);
        }
        event.registerBlockEntityRenderer(com.createbrewery.ModBlockEntities.STROBE_LIGHT.get(),
            com.createbrewery.block.club.StrobeLightRenderer::new);
        event.registerBlockEntityRenderer(com.createbrewery.ModBlockEntities.LASER_PROJECTOR.get(),
            com.createbrewery.block.club.LaserProjectorRenderer::new);
        event.registerBlockEntityRenderer(com.createbrewery.ModBlockEntities.FIXTURE.get(),
            com.createbrewery.block.club.FixtureRenderer::new);
        event.registerEntityRenderer(com.createbrewery.entity.ModEntities.BOUNCER.get(),
            com.createbrewery.entity.client.BouncerRenderer::new);
        event.registerBlockEntityRenderer(com.createbrewery.ModBlockEntities.DJ_BOOTH.get(),
            com.createbrewery.block.club.DjBoothRenderer::new);
        event.registerBlockEntityRenderer(com.createbrewery.ModBlockEntities.AMP_RACK.get(),
            com.createbrewery.block.club.AmpRackRenderer::new);
    }

    public static void registerLayerDefinitions(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(com.createbrewery.entity.client.BouncerRenderer.LAYER,
            () -> net.minecraft.client.model.geom.builders.LayerDefinition.create(
                net.minecraft.client.model.HumanoidModel.createMesh(
                    new net.minecraft.client.model.geom.builders.CubeDeformation(0f), 0f), 64, 64));
    }

    public static void addLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        DrugEyes.addLayers(event);
        SniffProps.addLayers(event);
    }

    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(new IClientMobEffectExtensions() {
            @Override
            public boolean renderInventoryText(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                                GuiGraphics g, int x, int y, int blitOffset) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return false;
                DrunkState s = mc.player.getData(ModAttachments.DRUNK);
                int secs = Intoxication.ticksUntilSober(s.blood, s.stomach, s.tolerance) / 20;
                String rising = s.stomach > 0.02f ? " ↑" : "";
                g.drawString(mc.font, instance.getEffect().value().getDisplayName(), x + 28, y + 6, 0xFFFFFF);
                g.drawString(mc.font, String.format(Locale.GERMAN, "%.2f‰%s · %d:%02d",
                    s.blood, rising, secs / 60, secs % 60), x + 28, y + 16, 0x7F7F7F);
                return true;
            }
        }, ModEffects.INEBRIATION);
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
