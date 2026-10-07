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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.biome.Biomes;
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
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import com.createbrewery.sound.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Animal;
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
 *   <li>Mushrooms: spores in the air, feeling one with animals, music deeper, eyes-closed visions,
 *       laughing fits, chills on the come-up, and with a heroic dose in silent darkness the
 *       world grows in over you.</li>
 * </ul>
 */
public final class TripClient {
    private TripClient() {}

    /** 0..1 how long the view has rested on one spot. */
    static float stare;
    /** 0..1 how bad a place this is to trip in. */
    static float harsh;

    private static float lastYaw, lastPitch, lastWalk;
    private static float hurtFlash, purgaFlash;
    private static boolean wasAbsorption;
    private static int sprintTicks;
    private static int stepNote, nextThought = 600, nextNoise = 300;

    /** A new player entity (respawn, new world): the tickCount gates start over. */
    static void reset() {
        nextThought = 600; nextNoise = 300;
    }
    /** Out of body: ticks since it began (-1 = in the body), the view to go back to, and once per trip. */
    private static final int OBE_TICKS = 240;
    private static int obeTicks = -1;
    private static CameraType obeBefore;
    private static boolean obeDone;
    /** The hand wobble already applied this frame; the off hand is drawn on the same pose and undoes it first. */
    private static final Matrix4f handApplied = new Matrix4f();
    private static int frame, handFrame = -1;
    /** Mushrooms: ticks left of a laughing fit, the come-up chills, and the heroic dose overgrowing the view. */
    private static int laugh;
    static float chill;
    private static float overgrown;
    private static boolean forestSpoke;

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

    static float lsdShare() {
        return Math.max(0f, 1f - DrunkClient.organic - DrunkClient.desert);
    }

    private static boolean isNearFire(net.minecraft.world.level.Level level, BlockPos center) {
        if (level == null) return false;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -2, -3), center.offset(3, 2, 3))) {
            BlockState s = level.getBlockState(pos);
            if (s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE) || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.LAVA)) {
                return true;
            }
        }
        return false;
    }

    /** Every client tick, from DrunkClient once its channels are eased. */
    static void tick(Minecraft mc, LocalPlayer player) {
        float trip = DrunkClient.trip, bad = DrunkClient.bad;
        float turned = Math.abs(Mth.wrapDegrees(player.getYRot() - lastYaw)) + Math.abs(player.getXRot() - lastPitch);
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        boolean still = turned < 0.6f && player.getDeltaMovement().horizontalDistanceSqr() < 0.001;
        boolean nearFire = isNearFire(mc.level, player.blockPosition());
        float stareSpeed = (nearFire && DrunkClient.desert > 0.3f ? 2f : 1f) / 80f;
        stare = still ? Math.min(1f, stare + stareSpeed) : Math.max(0f, stare - 0.15f);
        harsh = DrunkClient.ease(harsh, player.level().dimensionType().ultraWarm() || DrunkClient.dark(player) ? 1f : 0f);
        hurtFlash = Math.max(0f, hurtFlash - 0.08f);
        boolean hasAbsorption = player.hasEffect(MobEffects.ABSORPTION);
        if (hasAbsorption && !wasAbsorption && DrunkClient.desert > 0.2f) {
            purgaFlash = 1f;
            player.displayClientMessage(Component.translatable("createbrewery.thought.trip.lifted")
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.GOLD), true);
        }
        wasAbsorption = hasAbsorption;
        purgaFlash = Math.max(0f, purgaFlash - 0.03f);
        if (DrunkClient.desert > 0.4f && player.isSprinting()) {
            boolean inDanger = !player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, player.getBoundingBox().inflate(12.0)).isEmpty();
            if (!inDanger) {
                if (++sprintTicks > 60) {
                    player.setSprinting(false);
                    sprintTicks = 0;
                }
            } else {
                sprintTicks = 0;
            }
        } else {
            sprintTicks = 0;
        }
        if (still && DrunkClient.desert > 0.3f && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr && mc.level != null) {
            BlockPos bPos = bhr.getBlockPos();
            if (player.getRandom().nextFloat() < 0.35f * DrunkClient.desert) {
                int rgb = DESERT_COLORS[player.getRandom().nextInt(DESERT_COLORS.length)];
                mc.level.addParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | rgb),
                    bPos.getX() + player.getRandom().nextDouble(), bPos.getY() + 1.05, bPos.getZ() + player.getRandom().nextDouble(), 0.0, 0.02, 0.0);
            }
        }
        outOfBody(mc, player);
        thoughts(player, trip, bad);
        motes(mc, player, trip);
        stepNotes(mc, player, trip);
        hum(mc, player);
        phantomMonsters(mc, player, bad);
        phantomHits(mc, player, bad);
        mushrooms(mc, player, trip * DrunkClient.organic);
        mescaline(mc, player, trip * DrunkClient.desert);
    }

    /** 0..1 how hard you are laughing right now. */
    static float laughing() {
        return Math.min(1f, laugh / 20f);
    }

    // ---- the world ----

    private static final int[] DESERT_COLORS = {0xD32F2F, 0xF57C00, 0xFBC02D, 0x26A69A};

    /** Coloured motes rising from flowers, grass and leaves around you. */
    private static void motes(Minecraft mc, LocalPlayer player, float trip) {
        if (trip < 0.3f || mc.level == null) return;
        RandomSource r = player.getRandom();
        for (int i = 0; i < 6; i++) {
            BlockPos pos = player.blockPosition().offset(r.nextInt(17) - 8, r.nextInt(7) - 3, r.nextInt(17) - 8);
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.is(BlockTags.FLOWERS) || state.is(BlockTags.LEAVES) || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)) || r.nextFloat() > trip) continue;
            // LSD: every colour. Mushrooms: greens and golds. Peyote: red, orange, gold, turquoise.
            int rgb;
            if (DrunkClient.desert > 0.5f) {
                rgb = DESERT_COLORS[r.nextInt(DESERT_COLORS.length)];
            } else if (DrunkClient.organic > 0.5f) {
                rgb = Mth.hsvToRgb(0.12f + 0.3f * r.nextFloat(), 0.7f, 1f);
            } else {
                rgb = Mth.hsvToRgb(r.nextFloat(), 0.7f, 1f);
            }
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
        float stretch = 1f + 0.2f * peak() * lsdShare() * Math.max(0f, (float) Math.sin(t * 0.37 + entity.getId() * 1.3));
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
        float away = KetaClient.away;
        if ((trip < 0.05f && away < 0.05f) || player == null) return;
        PoseStack pose = event.getPoseStack();
        if (handFrame == frame) pose.mulPose(new Matrix4f(handApplied).invert());
        float t = (player.tickCount + event.getPartialTick()) / 20f;
        Matrix4f wobble = new Matrix4f()
            .translate(0.02f * trip * Mth.sin(t * 1.1f), 0.03f * trip * Mth.sin(t * 1.7f), 0f)
            .rotateZ(0.12f * trip * Mth.sin(t * 0.8f))
            .scale(1f, 1f + 0.1f * trip * Mth.sin(t * 2.3f), 1f + 0.06f * trip * Mth.cos(t * 1.9f))
            // Keta: your own hand is not quite yours - it hangs further off, lower, as if at the end of a long arm.
            .translate(0.1f * away, -0.2f * away, -0.7f * away);
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
            player.displayClientMessage(Component.translatable("createbrewery.thought.trip.below").withStyle(ChatFormatting.ITALIC, ChatFormatting.LIGHT_PURPLE), true);
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

    // ---- mushrooms ----

    /** Veil's glowing spores until they fail once; then vanilla spores. */
    static boolean quasarSpores = true;

    private static void mushrooms(Minecraft mc, LocalPlayer player, float shroom) {
        RandomSource r = player.getRandom();
        chill = DrunkClient.ease(chill, Psychedelics.comingUp(player, ModEffects.SHROOM_TRIP) ? 1f : 0f);
        boolean heroic = Psychedelics.silentDarkness(player) && DrugEffect.strength(player, ModEffects.SHROOM_TRIP) > 0.7f;
        overgrown = heroic ? Math.min(1f, overgrown + 1f / 200f) : Math.max(0f, overgrown - 1f / 60f);
        if (overgrown >= 1f && !forestSpoke) {
            player.displayClientMessage(Component.translatable("createbrewery.thought.trip.forest").withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GREEN), true);
            forestSpoke = true;
        } else if (overgrown <= 0f) forestSpoke = false;
        if (shroom < 0.2f || mc.level == null) {
            laugh = 0;
            return;
        }
        // Spores drift through the air; mushrooms, mycelium and moss glow. With Veil, glowing
        // spores in the colours of the trip, a burst each second; without it, vanilla spores.
        if (quasarSpores && DrunkClient.quasar()) {
            if (player.tickCount % 20 == 0 && r.nextFloat() < shroom) {
                quasarSpores = DrunkClient.veilParticles("spores", player.getX(), player.getY() + 1.5, player.getZ());
            }
        } else if (r.nextFloat() < shroom) {
            mc.level.addParticle(r.nextBoolean() ? ParticleTypes.SPORE_BLOSSOM_AIR : ParticleTypes.WARPED_SPORE,
                player.getX() + (r.nextDouble() - 0.5) * 12.0, player.getY() + r.nextDouble() * 4.0,
                player.getZ() + (r.nextDouble() - 0.5) * 12.0, 0.0, 0.0, 0.0);
        }
        for (int i = 0; i < 4; i++) {
            BlockPos pos = player.blockPosition().offset(r.nextInt(13) - 6, r.nextInt(5) - 2, r.nextInt(13) - 6);
            BlockState state = mc.level.getBlockState(pos);
            if (state.is(Blocks.MYCELIUM) || state.is(Blocks.PODZOL) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)) {
                mc.level.addParticle(ParticleTypes.GLOW, pos.getX() + r.nextDouble(), pos.getY() + 1.05, pos.getZ() + r.nextDouble(), 0.0, 0.02, 0.0);
            }
        }
        if (mc.crosshairPickEntity instanceof Animal && shroom > 0.35f && r.nextFloat() < 1f / 300f) {
            player.displayClientMessage(Component.translatable(ANIMALS[r.nextInt(ANIMALS.length)])
                .withStyle(ChatFormatting.ITALIC, ChatFormatting.DARK_GREEN), true);
        }
        // Laughing fits: out of nowhere, for a few seconds, you cannot stop - and cannot run.
        if (laugh <= 0 && shroom > 0.4f && r.nextFloat() < 1f / 1500f) laugh = 100;
        if (laugh > 0) {
            laugh--;
            player.setSprinting(false);
            if (laugh % 8 == 0) mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.GIGGLE.get(), 1.05f + r.nextFloat() * 0.2f, 0.8f));
        }
    }

    /** Peyote / Mescaline: ceremonial drum, holy fire spirals, and sand/ash dust particles. */
    private static void mescaline(Minecraft mc, LocalPlayer player, float mesc) {
        if (mesc < 0.2f || mc.level == null) return;
        RandomSource r = player.getRandom();
        // Ceremonial drum: slow heartbeat drum (~60 bpm) + occasional rattle
        if (player.tickCount % 20 == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.65f, 0.45f * mesc));
        }
        if ((player.tickCount % 20 == 10 || r.nextFloat() < 0.12f) && r.nextFloat() < mesc) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.1f + r.nextFloat() * 0.2f, 0.25f * mesc));
        }
        // Sand / dust and white ash particles around player, denser in desert/badlands biomes
        boolean desertBiome = player.level().getBiome(player.blockPosition()).is(Biomes.DESERT)
            || player.level().getBiome(player.blockPosition()).is(BiomeTags.IS_BADLANDS);
        int dustCount = desertBiome ? 4 : 2;
        for (int i = 0; i < dustCount; i++) {
            if (r.nextFloat() < mesc) {
                double x = player.getX() + (r.nextDouble() - 0.5) * 14.0;
                double y = player.getY() + r.nextDouble() * 3.5;
                double z = player.getZ() + (r.nextDouble() - 0.5) * 14.0;
                if (r.nextBoolean()) {
                    mc.level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()),
                        x, y, z, 0.0, -0.02, 0.0);
                } else {
                    mc.level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, (r.nextDouble() - 0.5) * 0.02, -0.01, (r.nextDouble() - 0.5) * 0.02);
                }
            }
        }
        // Holy fire: extra flame and small flame spiral upward near fire/campfire/lava
        BlockPos pPos = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(pPos.offset(-4, -2, -4), pPos.offset(4, 2, 4))) {
            BlockState s = mc.level.getBlockState(pos);
            if (s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE) || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.LAVA)) {
                for (int i = 0; i < 2; i++) {
                    double angle = (player.tickCount * 0.2 + i * Math.PI);
                    double rad = 0.35 + 0.1 * Math.sin(player.tickCount * 0.15);
                    double px = pos.getX() + 0.5 + Math.cos(angle) * rad;
                    double py = pos.getY() + 0.2 + ((player.tickCount * 2 + i * 10) % 30) * 0.04;
                    double pz = pos.getZ() + 0.5 + Math.sin(angle) * rad;
                    mc.level.addParticle(r.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, px, py, pz, 0.0, 0.04, 0.0);
                    if (r.nextFloat() < 0.25f) {
                        mc.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py + 0.2, pz, 0.0, 0.03, 0.0);
                    }
                }
                break;
            }
        }
    }

    /** Mushrooms: looking an animal in the eye, the feeling of being one with it (not words - a thought). */
    private static final String[] ANIMALS = {"createbrewery.thought.trip.animals.0", "createbrewery.thought.trip.animals.1",
        "createbrewery.thought.trip.animals.2", "createbrewery.thought.trip.animals.3", "createbrewery.thought.trip.animals.4"};

    private static final ResourceLocation FROST = ResourceLocation.withDefaultNamespace("textures/misc/powder_snow_outline.png");
    private static final ResourceLocation VINE = ResourceLocation.withDefaultNamespace("textures/block/vine.png");

    /** Chills at the edges of the view during the come-up, vines growing in with the heroic dose. */
    private static void mushroomOverlay(GuiGraphics g) {
        int w = g.guiWidth(), h = g.guiHeight();
        float frost = 0.35f * chill * DrunkClient.screen();
        if (frost <= 0.01f && overgrown <= 0f) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (frost > 0.01f) {
            g.setColor(1f, 1f, 1f, frost);
            g.blit(FROST, 0, 0, 0f, 0f, w, h, w, h);
        }
        if (overgrown > 0f) {
            g.setColor(0.3f, 0.55f, 0.22f, Math.min(1f, overgrown * 1.5f));
            int size = 32, rows = Mth.ceil(overgrown * 4f);
            for (int row = 0; row < rows; row++) {
                // The innermost row slides in as it grows.
                int in = row * size - (row == rows - 1 ? (int) ((rows - overgrown * 4f) * size) : 0);
                for (int x = 0; x < w; x += size) {
                    g.blit(VINE, x, in, 0f, 0f, size, size, size, size);
                    g.blit(VINE, x, h - size - in, 0f, 0f, size, size, size, size);
                }
                for (int y = 0; y < h; y += size) {
                    g.blit(VINE, in, y, 0f, 0f, size, size, size, size);
                    g.blit(VINE, w - size - in, y, 0f, 0f, size, size, size, size);
                }
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // ---- sounds ----

    private static final int[] SCALE = {-12, -10, -8, -5, -3, 0, 2, 4, 7, 9, 12};

    /** Every step rings a note, walking up the scale and back down. */
    private static void stepNotes(Minecraft mc, LocalPlayer player, float trip) {
        // LSD's synaesthesia; mushrooms and peyote have their own sounds.
        if (trip >= 0.25f && lsdShare() > 0.5f && player.onGround() && (int) player.walkDist != (int) lastWalk) {
            int i = stepNote++ % (2 * SCALE.length - 2);
            if (i >= SCALE.length) i = 2 * SCALE.length - 2 - i;
            mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.RECORDS,
                0.3f * trip, (float) Math.pow(2.0, SCALE[i] / 12.0), player.getRandom(), player.getX(), player.getY(), player.getZ()));
        }
        lastWalk = player.walkDist;
    }

    /** At the peak: a low hum under everything, and now and then a chime from nowhere. */
    private static void hum(Minecraft mc, LocalPlayer player) {
        float peak = peak() * lsdShare();
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
        GuiGraphics g = event.getGuiGraphics();
        mushroomOverlay(g);
        if (hurtFlash > 0f) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (hurtFlash * 90 * DrunkClient.screen()) << 24) | 0x8A0000);
        }
        if (purgaFlash > 0f) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (purgaFlash * 65 * DrunkClient.screen()) << 24) | 0xE67E22);
        }
    }

    // ---- the mind ----

    private static final String[] COMING_UP = {"createbrewery.thought.trip.coming_up.0", "createbrewery.thought.trip.coming_up.1",
        "createbrewery.thought.trip.coming_up.2", "createbrewery.thought.trip.coming_up.3"};
    private static final String[] TRIPPING = {"createbrewery.thought.trip.tripping.0", "createbrewery.thought.trip.tripping.1", "createbrewery.thought.trip.tripping.2",
        "createbrewery.thought.trip.tripping.3", "createbrewery.thought.trip.tripping.4", "createbrewery.thought.trip.tripping.5",
        "createbrewery.thought.trip.tripping.6", "createbrewery.thought.trip.tripping.7", "createbrewery.thought.trip.tripping.8"};
    private static final String[] PEAK = {"createbrewery.thought.trip.peak.0", "createbrewery.thought.trip.peak.1",
        "createbrewery.thought.trip.peak.2", "createbrewery.thought.trip.peak.3", "createbrewery.thought.trip.peak.4",
        "createbrewery.thought.trip.peak.5"};
    private static final String[] BAD = {"createbrewery.thought.trip.bad.0", "createbrewery.thought.trip.bad.1", "createbrewery.thought.trip.bad.2",
        "createbrewery.thought.trip.bad.3", "createbrewery.thought.trip.bad.4", "createbrewery.thought.trip.bad.5",
        "createbrewery.thought.trip.bad.6"};
    private static final String[] SHROOM_COMING_UP = {"createbrewery.thought.trip.shroom_coming_up.0", "createbrewery.thought.trip.shroom_coming_up.1", "createbrewery.thought.trip.shroom_coming_up.2",
        "createbrewery.thought.trip.shroom_coming_up.3"};
    private static final String[] SHROOM_TRIPPING = {"createbrewery.thought.trip.shroom_tripping.0", "createbrewery.thought.trip.shroom_tripping.1",
        "createbrewery.thought.trip.shroom_tripping.2", "createbrewery.thought.trip.shroom_tripping.3", "createbrewery.thought.trip.shroom_tripping.4", "createbrewery.thought.trip.shroom_tripping.5",
        "createbrewery.thought.trip.shroom_tripping.6", "createbrewery.thought.trip.shroom_tripping.7", "createbrewery.thought.trip.shroom_tripping.8"};
    private static final String[] SHROOM_PEAK = {"createbrewery.thought.trip.shroom_peak.0", "createbrewery.thought.trip.shroom_peak.1",
        "createbrewery.thought.trip.shroom_peak.2", "createbrewery.thought.trip.shroom_peak.3"};
    private static final String[] MESC_COMING_UP = {"createbrewery.thought.trip.mesc_coming_up.0", "createbrewery.thought.trip.mesc_coming_up.1",
        "createbrewery.thought.trip.mesc_coming_up.2", "createbrewery.thought.trip.mesc_coming_up.3"};
    private static final String[] MESC_TRIPPING = {"createbrewery.thought.trip.mesc_tripping.0", "createbrewery.thought.trip.mesc_tripping.1",
        "createbrewery.thought.trip.mesc_tripping.2", "createbrewery.thought.trip.mesc_tripping.3", "createbrewery.thought.trip.mesc_tripping.4",
        "createbrewery.thought.trip.mesc_tripping.5", "createbrewery.thought.trip.mesc_tripping.6"};
    private static final String[] MESC_PEAK = {"createbrewery.thought.trip.mesc_peak.0", "createbrewery.thought.trip.mesc_peak.1",
        "createbrewery.thought.trip.mesc_peak.2", "createbrewery.thought.trip.mesc_peak.3",
        "createbrewery.thought.trip.mesc_peak.4"};
    private static final String[] AFTER = {"createbrewery.thought.trip.after.0", "createbrewery.thought.trip.after.1", "createbrewery.thought.trip.after.2",
        "createbrewery.thought.trip.after.3"};

    /** Now and then a thought drifts past above the hotbar. */
    private static void thoughts(LocalPlayer player, float trip, float bad) {
        if (player.tickCount < nextThought) return;
        boolean comingUp = Psychedelics.comingUp(player, ModEffects.LSD_TRIP) || Psychedelics.comingUp(player, ModEffects.SHROOM_TRIP)
            || Psychedelics.comingUp(player, ModEffects.MESCALINE_TRIP);
        boolean mesc = DrunkClient.desert > 0.5f;
        boolean shroom = DrunkClient.organic > 0.5f;
        String[] pool = bad > 0.3f ? BAD : peak() > 0.3f ? (mesc ? MESC_PEAK : (shroom ? SHROOM_PEAK : PEAK))
            : comingUp ? (mesc ? MESC_COMING_UP : (shroom ? SHROOM_COMING_UP : COMING_UP))
            : trip > 0.2f ? (mesc ? MESC_TRIPPING : (shroom ? SHROOM_TRIPPING : TRIPPING))
            : DrunkClient.afterglow > 0.3f ? AFTER : null;
        if (pool == null) {
            nextThought = player.tickCount + 200;
            return;
        }
        ChatFormatting color = pool == BAD ? ChatFormatting.DARK_RED : mesc ? ChatFormatting.GOLD : shroom ? ChatFormatting.DARK_GREEN : ChatFormatting.LIGHT_PURPLE;
        player.displayClientMessage(Component.translatable(pool[player.getRandom().nextInt(pool.length)])
            .withStyle(ChatFormatting.ITALIC, color), true);
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
            if (DrunkClient.desert > 0.5f) {
                int col = DESERT_COLORS[(int) Math.floorMod((long) (hue * 4f) + i, DESERT_COLORS.length)];
                out.append(Component.literal(String.valueOf(c[i])).withColor(col));
            } else {
                // Mushrooms keep the letters in earthy greens and golds instead of the full rainbow.
                float h = (hue + i * 0.04f) % 1f;
                if (DrunkClient.organic > 0.5f) h = 0.12f + 0.3f * (0.5f + 0.5f * Mth.sin(h * 6.2832f));
                out.append(Component.literal(String.valueOf(c[i])).withColor(Mth.hsvToRgb(h, 0.55f, 1f)));
            }
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
