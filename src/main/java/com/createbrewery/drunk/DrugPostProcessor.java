package com.createbrewery.drunk;

import com.createbrewery.Config;
import com.createbrewery.CreateBrewery;
import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.drugs.DrugServer;
import com.createbrewery.effect.ModEffects;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Handles all shader post-processing pipelines and GUI overlays:
 * - PostChain lifecycle, dynamic resizing, and uniform bindings for all 12 drug channels
 * - World-space projection matrices, Distant Horizons depth, and Veil light/normal attachments
 * - Iris shaderpack compatibility detection and GUI chain pass
 * - GUI HUD overlays: blood per-mille, heavy eyelids, retch green, tachycardia/heart attack,
 *   nausea wave, aspiration choking, and daylight hangover glare.
 */
public final class DrugPostProcessor {
    private DrugPostProcessor() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation SHADER =
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "shaders/post/drunk.json");

    /** Set once the shader failed to load (old GPU...): never try again this session. */
    static boolean shaderFailed;

    /**
     * Our own post chain. Deliberately not GameRenderer's single post-effect slot: other mods in
     * the pack (Moonlight, Supplementaries, KubeJS...) also use that slot and swap it out, and a
     * chain that keeps getting recreated would flash its black first frame again and again.
     */
    static PostChain chain;

    /** Veil comes with Sable / Create Aeronautics; without it there are no dynamic lights. */
    static boolean veil = net.neoforged.fml.ModList.get().isLoaded("veil");
    /** Distant Horizons draws the far landscape; without it the vanilla depth is all there is. */
    static boolean dh = net.neoforged.fml.ModList.get().isLoaded("distanthorizons");

    private static int chainWidth, chainHeight;
    /** Frames drawn since the chain was (re)built; the afterimage waits until it has a real previous frame. */
    private static int chainFrames;
    private static int chainBuilds;

    /** True while an Iris shaderpack draws the world; updated each tick. */
    static boolean irisPack;

    /** The float uniforms of the last frame, kept while the debug log runs. */
    private static final Map<String, Float> uniforms = new TreeMap<>();
    private static boolean debugging;

    /** How far off the spot looked at is, eased, so the depth of field refocuses like an eye. */
    private static float focus;

    /** Last frame's camera, for tracers that only follow what really moves. */
    private static final Matrix4f prevViewProj = new Matrix4f();
    private static Vec3 prevCam;
    private static Field chainPasses;
    private static boolean worldFailed;

    public static void initIntegrations() {
        if (dh) {
            try {
                DhDepth.init();
                LOGGER.info("Drug vision: Distant Horizons depth on");
            } catch (RuntimeException | LinkageError e) {
                dh = false;
                LOGGER.warn("Distant Horizons depth unavailable", e);
            }
        }
        if (veil) LOGGER.info("Drug vision: Veil lights, normals and Quasar particles on");
        if (com.createbrewery.drugs.Hallucinations.GEO) LOGGER.info("Drug vision: GeckoLib hallucinations on");
    }

    public static void updateChain(Minecraft mc, LocalPlayer player) {
        boolean pack = shaderPackActive();
        if (pack != irisPack && chain != null) chainFrames = 0;
        irisPack = pack;

        boolean want = player != null && !shaderFailed && DrunkClient.screen() > 0.01f
            && (Intoxication.visualIntensity(DrunkClient.blood) > 0.01f || Intoxication.mood(DrunkClient.blood) > 0.01f
                || DrunkClient.stim > 0.01f || DrunkClient.gray > 0.01f || DrunkClient.dissoc > 0.01f || DrunkClient.high > 0.01f || DrunkClient.green > 0.01f
                || DrunkClient.trip > 0.01f || DrunkClient.bad > 0.01f || DrunkClient.breakthrough > 0.01f || DrunkClient.rolling > 0.01f || DrunkClient.tweak > 0.01f
                || TweakClient.tired > 0.01f || NodClient.sick > 0.01f || BenzoClient.calm > 0.01f || CokeClient.line > 0.01f || BenzoClient.rebound > 0.01f
                || NodClient.air > 0.01f || DrunkClient.opiate > 0.01f || DrunkClient.wah > 0.01f || DrunkClient.afterglow > 0.01f || DmtClient.descent > 0.01f
                || RollClient.heat > 0.01f || RollClient.zap > 0.01f);

        if (want && chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), SHADER);
                chainBuilds++;
                if (chainBuilds <= 5 || chainBuilds % 100 == 0) LOGGER.info("Drunk shader chain built (#{})", chainBuilds);
                chainWidth = chainHeight = -1;
                chainFrames = 0;
            } catch (Exception e) {
                shaderFailed = true;
                LOGGER.warn("Drunk shader could not be loaded; drunk vision falls back to overlays only", e);
            }
        } else if (want) {
            unwantedFor = 0;
        } else if (chain != null && ++unwantedFor >= CLOSE_AFTER) {
            // Not on the first quiet call: a value easing around the threshold would rebuild (recompile) the chain over and over.
            unwantedFor = 0;
            chain.close();
            chain = null;
        }
    }

    private static final int CLOSE_AFTER = 40;
    private static int unwantedFor;

    public static void closeChain() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
    }

    /** Sets a float uniform on every pass of the chain, and keeps it for the debug log. */
    static void uniform(String name, float value) {
        if (chain != null) chain.setUniform(name, value);
        if (debugging) uniforms.put(name, value);
    }

    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            DrunkMovementHandler.resetAim();
            if (veil) VeilLights.clear();
            com.createbrewery.block.club.StrobeLightBlockEntity.clearRoomLights();
            com.createbrewery.drugs.DrugPose.SEEN.clear();
            com.createbrewery.drugs.DrugPose.ACTING.clear();
            MusicPulse.clearClub();
            com.createbrewery.block.club.DjBoothBlockEntity.clearClientBooths();
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        if (veil) {
            try {
                VeilLights.frame(player, partial);
                VeilFx.tick(!irisPack && (DrunkClient.trip > 0.05f || DmtClient.crack > 0.01f), player.tickCount);
            } catch (RuntimeException | LinkageError e) {
                veil = false;
                LOGGER.warn("Veil lights unavailable", e);
            }
        }
        double t = DrunkClient.seconds(player, partial);
        if (numb(player)) player.hurtTime = 0;

        MusicPulse.update();

        if (chain != null) {
            float s = DrunkClient.screen();
            uniform("Intensity", Intoxication.visualIntensity(DrunkClient.blood) * s);
            uniform("Mood", Intoxication.mood(DrunkClient.blood) * s);
            uniform("Stim", DrunkClient.stim * s);
            uniform("Gray", DrunkClient.gray * s);
            uniform("Dissoc", DrunkClient.dissoc * s);
            uniform("High", DrunkClient.high * s);
            uniform("Green", DrunkClient.green * s);
            uniform("Trip", DrunkClient.trip * s);
            uniform("Organic", DrunkClient.organic);
            uniform("Desert", DrunkClient.desert);
            uniform("BadTrip", DrunkClient.bad * s);
            uniform("Break", DrunkClient.breakthrough * s);
            uniform("ComeUp", DrunkClient.comeUp * s);
            uniform("Recur", DrunkClient.recur * s);
            uniform("Eyes", HallucinationClient.eyes * s);
            uniform("Flip", s > 0.5f && player != null && DrunkClient.dissoc > 0.3f ? (float) DrunkClient.flip : 0f);
            uniform("Crack", DmtClient.crack * s);
            uniform("Waiting", DmtClient.waiting * s);
            uniform("Beyond", DmtClient.beyond * s);
            uniform("Descent", DmtClient.descent * s);
            uniform("Roll", DrunkClient.rolling * s);
            uniform("Rush", RollClient.rush * s);
            uniform("Beat", RollClient.beat * s);
            uniform("Kick", MusicPulse.kick * (1f + 0.5f * RollClient.groove + RollClient.peak));
            uniform("Peak", RollClient.peak * s);
            uniform("Level", MusicPulse.level);
            uniform("Hats", MusicPulse.hats * (1f + RollClient.peak));
            uniform("Wiggle", RollClient.wiggle * s);
            uniform("Zap", RollClient.zap * s);
            uniform("Faded", RollClient.faded * s);
            uniform("Scene", RollClient.scene * s);
            uniform("Tension", MusicPulse.song.tension);
            uniform("Drop", MusicPulse.song.drop);
            uniform("Beats", (float) (MusicPulse.song.beats % 64));
            uniform("Tempo", (float) (60.0 / MusicPulse.song.period()));
            uniform("Heat", RollClient.heat * s);
            uniform("Tweak", DrunkClient.tweak * s);
            uniform("Tired", TweakClient.tired * s);
            uniform("Nod", DrunkClient.opiate * s);
            uniform("Flood", NodClient.flood * s);
            uniform("Dream", NodClient.dream * s);
            uniform("Breath", NodClient.breath);
            uniform("Air", NodClient.air * s);
            uniform("Sick", NodClient.sick * s);
            uniform("Calm", BenzoClient.calm * s);
            uniform("Focus", focus(mc, player));
            uniform("FrameScale", Mth.clamp(0.333f / Math.max(0.01f, event.getPartialTick().getRealtimeDeltaTicks()), 0.5f, 4f));
            uniform("Rebound", BenzoClient.rebound * s);
            uniform("Wah", DrunkClient.wah * s);
            uniform("WahPulse", DrunkClient.wahPulse(partial));
            uniform("Gone", GasClient.gone * s);
            uniform("Coke", CokeClient.coke * s);
            uniform("Line", CokeClient.line * s);
            uniform("Stare", TripClient.stare * s);
            uniform("Harsh", TripClient.harsh);
            uniform("Afterglow", DrunkClient.afterglow * s);
            uniform("DrunkTime", (float) (t % 3600.0));
        }

        DrunkMovementHandler.updateAimDrift(mc, player, partial, t);
    }

    public static boolean numb(LocalPlayer player) {
        return DrunkServer.ketamine(player) > 0.3f || DrunkClient.blood >= Intoxication.WASTED;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        HallucinationClient.onRenderLevelStage(event);
        if (chain == null || irisPack || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        prepareChain();
        worldUniforms(event);
        runChain(event.getPartialTick().getGameTimeDeltaTicks());
    }

    public static void onGuiChain(RenderGuiEvent.Pre event) {
        if (chain == null || !irisPack) return;
        prepareChain();
        uniform("World", 0f);
        runChain(event.getPartialTick().getGameTimeDeltaTicks());
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    public static boolean shaderPack() {
        return irisPack;
    }

    private static void prepareChain() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main.width != chainWidth || main.height != chainHeight) {
            chain.resize(main.width, main.height);
            chainWidth = main.width;
            chainHeight = main.height;
            chainFrames = 0;
        }
        uniform("Trail", chainFrames < 3 ? 0f : 1f);
    }

    private static void runChain(float partialTicks) {
        chainFrames++;
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(partialTicks);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        RenderSystem.enableDepthTest();
    }

    static int veilNormals() {
        if (!veil) return -1;
        try {
            return VeilFx.normalTexture();
        } catch (RuntimeException | LinkageError e) {
            veil = false;
            LOGGER.warn("Veil normals unavailable", e);
            return -1;
        }
    }

    public static void exhale(int entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(entity) instanceof net.minecraft.world.entity.LivingEntity who)) return;
        Vec3 mouth = who.getEyePosition().add(who.getLookAngle().scale(0.3)).subtract(0, 0.1, 0);
        if (quasar()) veilParticles("joint_smoke", mouth.x, mouth.y, mouth.z);
    }

    static boolean quasar() {
        return veil && !irisPack;
    }

    static boolean veilParticles(String emitter, double x, double y, double z) {
        if (!veil) return false;
        try {
            boolean ok = VeilFx.emit(emitter, x, y, z);
            if (debugging) LOGGER.info("[Brewery debug] quasar {} -> {}", emitter, ok ? "ok" : "unknown emitter");
            return ok;
        } catch (RuntimeException | LinkageError e) {
            veil = false;
            LOGGER.warn("Veil particles unavailable", e);
            return false;
        }
    }

    private static Matrix4f dhInvViewProj(Matrix4f modelView) {
        try {
            return DhDepth.invViewProj(modelView);
        } catch (RuntimeException | LinkageError e) {
            dh = false;
            LOGGER.warn("Distant Horizons depth unavailable", e);
            return null;
        }
    }

    private static float focus(Minecraft mc, LocalPlayer player) {
        float target = mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS ? 64f
            : (float) mc.hitResult.getLocation().distanceTo(player.getEyePosition());
        focus += (target - focus) * 0.08f;
        return focus;
    }

    @SuppressWarnings("unchecked")
    private static void worldUniforms(RenderLevelStageEvent event) {
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f viewProj = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        if (!worldFailed) {
            try {
                if (chainPasses == null) {
                    chainPasses = PostChain.class.getDeclaredField("passes");
                    chainPasses.setAccessible(true);
                }
                Vec3 moved = prevCam == null || chainFrames < 3 ? Vec3.ZERO : cam.subtract(prevCam);
                Matrix4f dhInv = dh ? dhInvViewProj(event.getModelViewMatrix()) : null;
                uniform("Dh", dhInv == null ? 0f : 1f);
                int normals = veilNormals();
                uniform("Normals", normals < 0 ? 0f : 1f);
                for (PostPass pass : (List<PostPass>) chainPasses.get(chain)) {
                    var effect = pass.getEffect();
                    effect.safeGetUniform("InvViewProj").set(new Matrix4f(viewProj).invert());
                    effect.safeGetUniform("PrevViewProj").set(chainFrames < 3 ? viewProj : prevViewProj);
                    if (normals >= 0) effect.setSampler("NormalSampler", () -> normals);
                    if (dhInv != null) {
                        effect.setSampler("DhDepthSampler", DhDepth::texture);
                        effect.safeGetUniform("DhInvViewProj").set(dhInv);
                    }
                    effect.safeGetUniform("CamDelta").set((float) moved.x, (float) moved.y, (float) moved.z);
                    effect.safeGetUniform("CamPos").set((float) (cam.x % 1024.0), (float) (cam.y % 1024.0), (float) (cam.z % 1024.0));
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                worldFailed = true;
                LOGGER.warn("Trip shader cannot see the world; surface patterns and tracers fall back to the screen", e);
            }
        }
        uniform("World", worldFailed ? 0f : 1f);
        prevViewProj.set(viewProj);
        prevCam = cam;
    }

    private static Boolean irisPresent;

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

    public static void debugLog(Minecraft mc, LocalPlayer player) {
        boolean on = com.createbrewery.drugs.DrugDebug.on() && player != null;
        if (on && !debugging) {
            LOGGER.info("[Brewery debug] start: veil={} dh={} irisPack={} shaderFailed={} worldFailed={} quasarSpores={} geckolib={} playerAnimator={}",
                veil, dh, irisPack, shaderFailed, worldFailed, TripClient.quasarSpores, com.createbrewery.drugs.Hallucinations.GEO,
                net.neoforged.fml.ModList.get().isLoaded("playeranimator"));
        } else if (!on && debugging) {
            LOGGER.info("[Brewery debug] end");
            uniforms.clear();
        }
        debugging = on;
        if (!on || player.tickCount % 20 != 0) return;
        StringBuilder effects = new StringBuilder();
        for (var e : player.getActiveEffects()) {
            var key = e.getEffect().unwrapKey().map(k -> k.location()).orElse(null);
            if (key == null || !key.getNamespace().equals(CreateBrewery.MOD_ID)) continue;
            effects.append(String.format(" %s:%d/%ds", key.getPath(), e.getAmplifier() + 1, e.getDuration() / 20));
            if (e.getEffect().value() instanceof DrugEffect) effects.append(String.format("(felt %.2f)", DrugEffect.felt(player, e.getEffect())));
        }
        StringBuilder live = new StringBuilder();
        uniforms.forEach((name, value) -> {
            if (Math.abs(value) > 0.005f) live.append(String.format(" %s=%.2f", name, value));
        });
        var pose = com.createbrewery.drugs.DrugPose.SEEN.get(player.getId());
        var action = com.createbrewery.drugs.DrugPose.ACTING.get(player.getId());
        LOGGER.info("[Brewery debug] blood={}‰ chain={} frames={} screen={} normals={} pose={}/{} eyes={} action={} visions={} |{} |{}", String.format("%.2f", DrunkClient.blood),
            chain != null, chainFrames, String.format("%.2f", DrunkClient.screen()), veilNormals(), pose == null ? 0 : pose.kind(),
            pose == null ? 0 : pose.amount() & 0xFF, pose == null ? 0 : pose.eyes(), action == null ? "-" : action.kind(), com.createbrewery.drugs.Hallucinations.seen(), effects, live);
    }

    // ---- GUI Overlays ----

    public static void onGuiPre(RenderGuiEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double t = DrunkClient.seconds(player, partial);

        HallucinationClient.drawShadow(player, g, w, h);
        RollClient.drawDancer(player, g, w, h);
        TweakClient.drawDart(player, g, w, h, partial);
        KetaClient.drawGhosts(player, g, partial);

        float s = DrunkClient.screen();

        // Heavy eyelids
        float lid = 0f;
        if (DrunkClient.blood >= Intoxication.DRUNK) {
            float e = DrunkClient.ramp(Intoxication.DRUNK);
            lid = (0.05f + 0.2f * e) * (0.6f + 0.4f * DrunkClient.noise(t * 0.5, 17));
            if (DrunkClient.blood >= Intoxication.SMASHED) {
                lid = Math.max(lid, DrunkClient.nod(t) * 0.52f);
            }
        }
        lid = Math.max(lid, Math.max(Math.max(Math.max(NodClient.lid, 0.1f * DrunkClient.opiate), BenzoClient.lid), WeedClient.lid) * s);
        if (lid > 0f) {
            int px = (int) (h * lid);
            int feather = h / 8;
            int dark = (int) (0xF5 * (1f - 0.75f * NodClient.dream * s)) << 24;
            g.fill(0, 0, w, px, dark);
            g.fillGradient(0, px, w, px + feather, dark, 0x00000000);
            g.fill(0, h - px, w, h, dark);
            g.fillGradient(0, h - px - feather, w, h - px, 0x00000000, dark);
        }

        // Retch
        float retch = DrunkMovementHandler.retch(player, partial);
        if (retch > 0f) {
            int a = (int) (retch * 150 * s);
            int edge = h / 3;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x4A5A10, 0x004A5A10);
            g.fillGradient(0, h - edge, w, h, 0x004A5A10, (a << 24) | 0x4A5A10);
            g.fill(0, 0, w, h, ((int) (retch * 60 * s) << 24) | 0x303A08);
        }

        // Tachycardia
        MobEffectInstance racing = player.getEffect(ModEffects.TACHYCARDIA);
        if (racing != null) {
            int a = (int) ((35 + 35 * racing.getAmplifier()) * s);
            int edge = h / 5;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x3A0008, 0x003A0008);
            g.fillGradient(0, h - edge, w, h, 0x003A0008, (a << 24) | 0x3A0008);
        }

        // Heart attack
        MobEffectInstance failing = player.getEffect(ModEffects.HEART_ATTACK);
        if (failing != null) {
            float fade = Math.min(1f, Math.min((DrugServer.HEART_ATTACK_TICKS - failing.getDuration()) / 20f, failing.getDuration() / 40f));
            g.fill(0, 0, w, h, ((int) (fade * 170 * s) << 24) | 0x0A0004);
        }

        // Greening out nausea
        if (DrunkClient.green > 0.01f) {
            float wave = 0.75f + 0.25f * DrunkClient.noise(t * 0.4, 47);
            int a = (int) (DrunkClient.green * wave * 130 * s);
            int edge = h / 3;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x5C6E1E, 0x005C6E1E);
            g.fillGradient(0, h - edge, w, h, 0x005C6E1E, (a << 24) | 0x5C6E1E);
        }

        // Aspiration
        if (player.hasEffect(ModEffects.ASPIRATION)) {
            int a = (int) (160 * s);
            int edge = h / 3;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x4A5A10, 0x004A5A10);
            g.fillGradient(0, h - edge, w, h, 0x004A5A10, (a << 24) | 0x4A5A10);
        }

        // Hangover
        MobEffectInstance hangover = player.getEffect(ModEffects.HANGOVER);
        if (hangover != null) {
            float masked = (1f - 0.7f * Math.min(1f, DrunkClient.blood / Intoxication.MERRY)) * (1f - 0.7f * DrunkClient.stim);
            float beat = (float) Math.pow(Math.max(0.0, Math.sin(t * Math.PI * 1.6)), 6.0);
            int a = (int) (beat * (90 + 40 * Math.min(hangover.getAmplifier(), 2)) * s * masked);
            int edge = h / 4;
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x2A0000, 0x002A0000);
            g.fillGradient(0, h - edge, w, h, 0x002A0000, (a << 24) | 0x2A0000);
            if (player.level().isDay() && player.level().canSeeSky(player.blockPosition())) {
                int glare = (int) ((60 + 30 * DrunkClient.noise(t * 0.9, 25)) * s * masked);
                g.fill(0, 0, w, h, (glare << 24) | 0xFFF8E0);
            }
        }

        drawPerMille(mc, player, g);
    }

    private static void drawPerMille(Minecraft mc, LocalPlayer player, GuiGraphics g) {
        DrunkState s = player.getData(ModAttachments.DRUNK);
        if (s.total() < 0.005f) return;
        String text = String.format(Locale.GERMAN, "%.2f ‰%s", s.blood, s.stomach > 0.02f ? " ↑" : "");
        if (s.tolerance >= 0.05f) text += String.format(Locale.GERMAN, "  ·  Toleranz %d %%", Math.round(s.tolerance * 100));
        int colour = s.blood < Intoxication.TIPSY ? 0xB0E8B0
            : s.blood < Intoxication.DRUNK ? 0xFFD35A
            : s.blood < Intoxication.POISONING ? 0xFF8A30 : 0xFF3A3A;
        g.drawString(mc.font, text, (g.guiWidth() - mc.font.width(text)) / 2, g.guiHeight() - 82, colour, true);
    }

    public static void onGuiPost(RenderGuiEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        CokeClient.drawCraving(player, event.getGuiGraphics());
        WeedClient.drawMunchies(player, event.getGuiGraphics());
        BenzoClient.drawGap(event.getGuiGraphics());
        MobEffectInstance blackout = player.getEffect(ModEffects.BLACKOUT);
        if (blackout == null) return;
        float alpha = Math.min(1f, blackout.getDuration() / 30f);
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (alpha * 255) << 24);
    }
}
