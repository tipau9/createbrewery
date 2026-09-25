package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.drugs.Psychedelics;
import com.createbrewery.effect.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * The trip beyond the shader, client only: what LSD, mushrooms and peyote do to the world around
 * you, to sounds, and to your thoughts. Reads the eased channels of {@link DrunkClient}.
 *
 * <ul>
 *   <li>Staring: the longer the view rests on one spot, the more it melts (the shader reads {@link #stare}).</li>
 *   <li>Setting: the Nether, caves and night make it harsh ({@link #harsh}).</li>
 *   <li>Coloured motes rise from flowers, grass and leaves; animals and people breathe and stretch;
 *       your own hand ripples.</li>
 *   <li>Every step rings a note, climbing up and down a scale; at the peak a low hum and chimes.</li>
 *   <li>Thoughts above the hotbar; chat and item names come in scrambled rainbow letters.</li>
 *   <li>The third tab: at the peak you leave your body and look down on yourself for a while.</li>
 *   <li>Bad trip: monsters heard behind you, and hits that never landed.</li>
 * </ul>
 */
public final class TripClient {
    private TripClient() {}

    /** 0..1 how long the view has rested on one spot. */
    static float stare;
    /** 0..1 how bad a place this is to trip in. */
    static float harsh;

    private static float lastYaw, lastPitch, lastWalk;
    private static int stepNote, nextThought = 600, nextNoise = 300;
    private static float hurtFlash;
    /** Out of body: ticks since it began (-1 = in the body), the view to go back to, and once per trip. */
    private static final int OBE_TICKS = 240;
    private static int obeTicks = -1;
    private static CameraType obeBefore;
    private static boolean obeDone;
    /** The hand wobble already applied this frame; the off hand is drawn on the same pose and undoes it first. */
    private static final Matrix4f handApplied = new Matrix4f();
    private static int frame, handFrame = -1;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(TripClient::onChat);
        NeoForge.EVENT_BUS.addListener(TripClient::onTooltip);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, TripClient::onLivingPre);
        NeoForge.EVENT_BUS.addListener(TripClient::onHand);
        NeoForge.EVENT_BUS.addListener(TripClient::onFrame);
        NeoForge.EVENT_BUS.addListener(TripClient::onCameraDistance);
        NeoForge.EVENT_BUS.addListener(TripClient::onGui);
    }

    private static float peak() {
        return Mth.clamp((DrunkClient.trip - 0.55f) / 0.45f, 0f, 1f);
    }

    /** Every client tick, from DrunkClient once its channels are eased. */
    static void tick(Minecraft mc, LocalPlayer player) {
        float trip = DrunkClient.trip, bad = DrunkClient.bad;
        float turned = Math.abs(Mth.wrapDegrees(player.getYRot() - lastYaw)) + Math.abs(player.getXRot() - lastPitch);
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        boolean still = turned < 0.6f && player.getDeltaMovement().horizontalDistanceSqr() < 0.001;
        stare = still ? Math.min(1f, stare + 1f / 80f) : Math.max(0f, stare - 0.15f);
        harsh = DrunkClient.ease(harsh, player.level().dimensionType().ultraWarm() || DrunkClient.dark(player) ? 1f : 0f);
        hurtFlash = Math.max(0f, hurtFlash - 0.08f);
        outOfBody(mc, player);
        thoughts(player, trip, bad);
        motes(mc, player, trip);
        stepNotes(mc, player, trip);
        hum(mc, player);
        phantomMonsters(mc, player, bad);
        phantomHits(mc, player, bad);
    }

    // ---- the world ----

    /** Coloured motes rising from flowers, grass and leaves around you. */
    private static void motes(Minecraft mc, LocalPlayer player, float trip) {
        if (trip < 0.3f || mc.level == null) return;
        RandomSource r = player.getRandom();
        for (int i = 0; i < 6; i++) {
            BlockPos pos = player.blockPosition().offset(r.nextInt(17) - 8, r.nextInt(7) - 3, r.nextInt(17) - 8);
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES) || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)) || r.nextFloat() > trip) continue;
            int rgb = Mth.hsvToRgb(r.nextFloat(), 0.7f, 1f);
            mc.level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | rgb),
                pos.getX() + r.nextDouble(), pos.getY() + 0.3 + r.nextDouble() * 0.6, pos.getZ() + r.nextDouble(), 0.0, 0.03, 0.0);
        }
    }

    /** Animals and people breathe in and out, and at the peak now and then stretch up tall. */
    private static void onLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        float trip = DrunkClient.trip;
        if (trip < 0.1f) return;
        var entity = event.getEntity();
        double t = (entity.tickCount + event.getPartialTick()) / 20.0;
        float s = 1f + 0.06f * trip * (float) Math.sin(t * 1.6 + entity.getId());
        float stretch = 1f + 0.2f * peak() * Math.max(0f, (float) Math.sin(t * 0.37 + entity.getId() * 1.3));
        // No push of our own: the entity dispatcher pops its pose after the renderer, scale included.
        event.getPoseStack().scale(s / (float) Math.sqrt(stretch), s * stretch, s / (float) Math.sqrt(stretch));
    }

    private static void onFrame(RenderFrameEvent.Pre event) {
        frame++;
    }

    /** Your own hand ripples and sways, as if made of something soft. */
    private static void onHand(RenderHandEvent event) {
        float trip = DrunkClient.trip;
        LocalPlayer player = Minecraft.getInstance().player;
        if (trip < 0.05f || player == null) return;
        PoseStack pose = event.getPoseStack();
        if (handFrame == frame) pose.mulPose(new Matrix4f(handApplied).invert());
        float t = (player.tickCount + event.getPartialTick()) / 20f;
        Matrix4f wobble = new Matrix4f()
            .translate(0.02f * trip * Mth.sin(t * 1.1f), 0.03f * trip * Mth.sin(t * 1.7f), 0f)
            .rotateZ(0.12f * trip * Mth.sin(t * 0.8f))
            .scale(1f, 1f + 0.1f * trip * Mth.sin(t * 2.3f), 1f + 0.06f * trip * Mth.cos(t * 1.9f));
        pose.mulPose(wobble);
        handApplied.set(wobble);
        handFrame = frame;
    }

    /** The third tab, at the peak: the camera drifts out of you and up, then snaps back. */
    private static void outOfBody(Minecraft mc, LocalPlayer player) {
        MobEffectInstance lsd = player.getEffect(ModEffects.LSD_TRIP);
        if (lsd == null) obeDone = false;
        if (obeTicks < 0 && !obeDone && lsd != null && lsd.getAmplifier() >= Psychedelics.MAX_LEVEL
            && DrugEffect.strength(player, ModEffects.LSD_TRIP) > 0.9f) {
            obeDone = true;
            obeTicks = 0;
            obeBefore = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            player.displayClientMessage(Component.literal("Wer ist das da unten?").withStyle(ChatFormatting.ITALIC, ChatFormatting.LIGHT_PURPLE), true);
        }
        if (obeTicks >= 0 && ++obeTicks > OBE_TICKS) {
            mc.options.setCameraType(obeBefore);
            obeTicks = -1;
        }
    }

    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (obeTicks < 0) return;
        event.setDistance(event.getDistance() + 10f * Mth.sin((float) Math.PI * obeTicks / OBE_TICKS));
    }

    // ---- sounds ----

    private static final int[] SCALE = {-12, -10, -8, -5, -3, 0, 2, 4, 7, 9, 12};

    /** Every step rings a note, walking up the scale and back down. */
    private static void stepNotes(Minecraft mc, LocalPlayer player, float trip) {
        if (trip >= 0.25f && player.onGround() && (int) player.walkDist != (int) lastWalk) {
            int i = stepNote++ % (2 * SCALE.length - 2);
            if (i >= SCALE.length) i = 2 * SCALE.length - 2 - i;
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.RECORDS,
                0.3f * trip, (float) Math.pow(2.0, SCALE[i] / 12.0), player.getRandom(), player.getX(), player.getY(), player.getZ()));
        }
        lastWalk = player.walkDist;
    }

    /** At the peak: a low hum under everything, and now and then a chime from nowhere. */
    private static void hum(Minecraft mc, LocalPlayer player) {
        float peak = peak();
        if (peak <= 0.05f) return;
        if (player.tickCount % 80 == 0) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_AMBIENT, 0.5f, 0.35f * peak));
        if (player.getRandom().nextFloat() < 0.01f * peak) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f + player.getRandom().nextFloat(), 0.5f * peak));
        }
    }

    private static final SoundEvent[] MONSTERS = {SoundEvents.ZOMBIE_AMBIENT, SoundEvents.SKELETON_AMBIENT,
        SoundEvents.SPIDER_AMBIENT, SoundEvents.CREEPER_PRIMED, SoundEvents.WITCH_AMBIENT, SoundEvents.PHANTOM_AMBIENT};

    /** Bad trip: something groans, rattles or hisses a few blocks behind you. Nothing is there. */
    private static void phantomMonsters(Minecraft mc, LocalPlayer player, float bad) {
        if (bad < 0.3f || player.tickCount < nextNoise) return;
        RandomSource r = player.getRandom();
        Vec3 back = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(-(3.0 + r.nextDouble() * 4.0));
        Vec3 at = player.position().add(back).add((r.nextDouble() - 0.5) * 4.0, 0.0, (r.nextDouble() - 0.5) * 4.0);
        mc.getSoundManager().play(new SimpleSoundInstance(MONSTERS[r.nextInt(MONSTERS.length)], SoundSource.HOSTILE,
            0.6f, 0.8f + r.nextFloat() * 0.2f, r, at.x, at.y + 1.0, at.z));
        nextNoise = player.tickCount + 160 + r.nextInt(240);
    }

    /** Bad trip: you flinch from a hit that never came - the jolt, the sound, a red flash. */
    private static void phantomHits(Minecraft mc, LocalPlayer player, float bad) {
        if (bad < 0.4f || player.getRandom().nextFloat() > bad / 300f) return;
        player.hurtTime = player.hurtDuration = 10;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_HURT, 1f, 0.8f));
        hurtFlash = 1f;
    }

    private static void onGui(RenderGuiEvent.Pre event) {
        if (hurtFlash <= 0f) return;
        GuiGraphics g = event.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (hurtFlash * 90 * DrunkClient.screen()) << 24) | 0x8A0000);
    }

    // ---- the mind ----

    private static final String[] COMING_UP = {"Merkst du schon was?", "Die Farben sind… irgendwie intensiver?",
        "Ist das schon der Trip, oder bilde ich mir das ein?", "Warum kribbelt alles?"};
    private static final String[] TRIPPING = {"Die Blöcke atmen…", "Wer hat eigentlich Steine erfunden?", "Moment. Wo war ich?",
        "Das Gras ist so… grün.", "Warum ist alles aus Würfeln?", "Meine Hände sehen komisch aus.",
        "Ich hab die Musik gerade gesehen.", "Hat der Baum mich angeschaut?", "Alles ist verbunden. Alles."};
    private static final String[] PEAK = {"Ich bin der Block. Der Block ist ich.", "Zeit ist nur eine Idee.",
        "Es gibt kein Ich. Nur Minecraft.", "Ich verstehe jetzt alles. Alles!", "Wo hört mein Körper auf?",
        "Die Welt wird gerade erst geladen…"};
    private static final String[] BAD = {"Irgendwas stimmt nicht.", "Da war was. Hinter mir.", "Das hört nie wieder auf.",
        "Ich hätte das nicht nehmen sollen.", "Ruhig atmen. Es ist nur die Droge.", "Die Wände kommen näher.",
        "Die wissen, dass ich hier bin."};
    private static final String[] AFTER = {"Was für ein Tag.", "Alles fühlt sich friedlich an.", "Die Welt ist schön, eigentlich.",
        "Ich sollte öfter die Sonne anschauen."};

    /** Now and then a thought drifts past above the hotbar. */
    private static void thoughts(LocalPlayer player, float trip, float bad) {
        if (player.tickCount < nextThought) return;
        boolean comingUp = Psychedelics.comingUp(player, ModEffects.LSD_TRIP) || Psychedelics.comingUp(player, ModEffects.SHROOM_TRIP)
            || Psychedelics.comingUp(player, ModEffects.MESCALINE_TRIP);
        String[] pool = bad > 0.3f ? BAD : peak() > 0.3f ? PEAK : comingUp ? COMING_UP : trip > 0.2f ? TRIPPING
            : DrunkClient.afterglow > 0.3f ? AFTER : null;
        if (pool == null) {
            nextThought = player.tickCount + 200;
            return;
        }
        player.displayClientMessage(Component.literal(pool[player.getRandom().nextInt(pool.length)])
            .withStyle(ChatFormatting.ITALIC, pool == BAD ? ChatFormatting.DARK_RED : ChatFormatting.LIGHT_PURPLE), true);
        nextThought = player.tickCount + 500 + player.getRandom().nextInt(500);
    }

    /** Words wobble: letters swap places, and every one comes in its own colour. */
    static MutableComponent garble(String text, float trip, long seed) {
        RandomSource r = RandomSource.create(seed);
        char[] c = text.toCharArray();
        for (int i = 1; i + 2 < c.length; i++) {
            if (Character.isLetter(c[i]) && Character.isLetter(c[i + 1]) && r.nextFloat() < 0.12f * trip) {
                char swap = c[i];
                c[i] = c[i + 1];
                c[i + 1] = swap;
                i++;
            }
        }
        float hue = (Util.getMillis() % 6000L) / 6000f;
        MutableComponent out = Component.empty();
        for (int i = 0; i < c.length; i++) {
            out.append(Component.literal(String.valueOf(c[i])).withColor(Mth.hsvToRgb((hue + i * 0.04f) % 1f, 0.55f, 1f)));
        }
        return out;
    }

    private static void onChat(ClientChatReceivedEvent event) {
        float trip = DrunkClient.trip;
        if (trip < 0.3f) return;
        event.setMessage(garble(event.getMessage().getString(), trip, Util.getMillis()));
    }

    private static void onTooltip(ItemTooltipEvent event) {
        float trip = DrunkClient.trip;
        List<Component> lines = event.getToolTip();
        if (trip < 0.3f || lines.isEmpty()) return;
        String name = lines.get(0).getString();
        // Scrambled anew every two seconds, not every frame.
        lines.set(0, garble(name, trip, name.hashCode() + Util.getMillis() / 2000L));
    }
}
