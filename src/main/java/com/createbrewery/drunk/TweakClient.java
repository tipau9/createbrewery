package com.createbrewery.drunk;

import com.createbrewery.drugs.DrugEffect;
import com.createbrewery.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Meth beyond the cold sharp look, client only. The shader reads {@link #tired}.
 *
 * <p>Methamphetamine (PsychonautWiki; studies of meth psychosis): the high is focus, drive, a
 * racing mind and no need for sleep - vision vibrates, pupils are wide, the rest of the world
 * falls away around what you look at. The longer you stay up on it, the more the sleepless
 * brain misreads the edges of the view: movement and figures in the corner of the eye, bugs
 * crawling over the skin ("meth mites"), footsteps and doors that are not there, and the
 * certainty that everyone is watching. In a psychosis all of that at once.
 *
 * <ul>
 *   <li>Racing thoughts: plans, ideas, cleaning, sorting - many, fast.</li>
 *   <li>Tired (awake too long on it, or psychotic): visual snow, double vision, shadows darting
 *       past at the edge of the view, bugs on the screen, footsteps behind you, doors, a
 *       scratching in the walls - and every creature turns its head to stare at you.</li>
 *   <li>The time flies: suddenly it is night again.</li>
 * </ul>
 */
public final class TweakClient {
    private TweakClient() {}

    /** Sleepless on meth, 0..1: its misread edges of the view (drunk.fsh). */
    static float tired;
    private static int awake, nextThought = 200, dartAge = -1;

    /** A new player entity (respawn, new world): the tickCount gates start over. */
    static void reset() {
        nextThought = 200; dartAge = -1;
    }
    private static float dartYaw, dartDir;

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, RenderLivingEvent.Pre.class, TweakClient::onLivingPre);
    }

    /** Every client tick, from DrunkClient. */
    static void tick(Minecraft mc, LocalPlayer player) {
        RandomSource r = player.getRandom();
        boolean on = player.hasEffect(ModEffects.TWEAK);
        // Like the server's count towards psychosis (Stimulants#body): only sleep resets it.
        if (on) awake++;
        else if (player.isSleeping()) awake = 0;
        float psychosis = DrugEffect.strength(player, ModEffects.PSYCHOSIS);
        // From about three minutes up the edges of the view start to lie; ten minutes, fully.
        float sleepless = on ? Mth.clamp((awake / 20f - 180f) / 420f, 0f, 1f) * Math.max(0.5f, DrugEffect.strength(player, ModEffects.TWEAK)) : 0f;
        // Xanax takes the fear out of it (as it keeps the psychosis away on the server).
        sleepless *= 1f - DrugEffect.strength(player, ModEffects.CALM);
        tired = DrunkClient.ease(tired, Math.max(sleepless, psychosis));

        // Movement in the corner of the eye: something darts past, and is gone.
        if (dartAge < 0 && tired > 0.2f && r.nextFloat() < tired / 500f) {
            dartYaw = player.getYRot() + (r.nextBoolean() ? 58f : -58f);
            dartDir = r.nextBoolean() ? 1f : -1f;
            dartAge = 0;
            if (r.nextBoolean()) think(player, WHAT);
        }
        if (dartAge >= 0 && ++dartAge > 10) dartAge = -1;

        // Sounds that are not there: steps behind you, a door, scratching in the walls.
        if (tired > 0.25f && r.nextFloat() < tired / 400f) {
            Vec3 behind = player.position().add(Vec3.directionFromRotation(0f, player.getYRot() + 150f + r.nextFloat() * 60f)
                .scale(3.0 + r.nextFloat() * 4.0));
            SoundEvent[] fake = {SoundEvents.STONE_STEP, SoundEvents.GRAVEL_STEP, SoundEvents.WOODEN_DOOR_OPEN,
                SoundEvents.WOODEN_DOOR_CLOSE, SoundEvents.SILVERFISH_STEP, SoundEvents.WOOD_STEP};
            SoundEvent sound = fake[r.nextInt(fake.length)];
            int steps = sound == SoundEvents.WOODEN_DOOR_OPEN || sound == SoundEvents.WOODEN_DOOR_CLOSE ? 1 : 3;
            for (int i = 0; i < steps; i++) {
                mc.getSoundManager().playDelayed(new SimpleSoundInstance(sound, SoundSource.AMBIENT, 0.35f + 0.2f * tired,
                    0.9f + r.nextFloat() * 0.2f, r, behind.x, behind.y, behind.z), i * 7);
            }
            if (r.nextFloat() < 0.4f) think(player, HEARD);
        }
        // The bugs under the skin: now and then you hear them too.
        if (tired > 0.5f && r.nextFloat() < tired / 900f) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SILVERFISH_AMBIENT, 1.6f, 0.15f));
            think(player, MITES);
        }

        // Time flies: the night is there before the day even started.
        if (on && mc.level != null && mc.level.getDayTime() % 24000L == 13000L) think(player, NIGHT);

        thoughts(player, on, r);
    }

    /** From the HUD: a figure darting past at the very edge of the view, for half a second. */
    static void drawDart(LocalPlayer player, GuiGraphics g, int w, int h, float partial) {
        if (dartAge < 0) return;
        float rel = Mth.wrapDegrees(dartYaw - player.getYRot());
        if (Math.abs(rel) < 35f || Math.abs(rel) > 110f) return; // looked at: nothing there
        float age = (dartAge + partial) / 10f;
        int fh = (int) (h * 0.6f), fw = fh / 2;
        int edge = rel > 0 ? w - fw / 2 : -fw / 2;
        int x = edge + (int) (dartDir * (age - 0.5f) * fw * 1.6f);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(0f, 0f, 0f, (float) Math.sin(Math.PI * Math.min(1f, age)) * 0.7f * tired * DrunkClient.screen());
        g.blit(DrunkClient.SHADOW_FIGURE, x, (h - fh) / 2 + h / 14, fw, fh, 0f, 0f, 64, 128, 64, 128);
        g.setColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    /** They are watching: tired enough, every creature near you turns its head to stare. */
    private static void onLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        LocalPlayer player = Minecraft.getInstance().player;
        if (tired < 0.4f || player == null || e == player || e == player.getVehicle() || e.distanceToSqr(player) > 24 * 24) return;
        Vec3 to = player.getEyePosition().subtract(e.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        // Not quite all at once: the head swings over, then stays.
        e.yHeadRot = e.yHeadRotO = Mth.rotLerp(Math.min(1f, tired * 1.5f), e.yHeadRot, yaw);
        e.setXRot(pitch);
        e.xRotO = pitch;
    }

    // ---- the mind: fast ----

    private static final String[] SPEED = {"createbrewery.thought.tweak.speed.0", "createbrewery.thought.tweak.speed.1",
        "createbrewery.thought.tweak.speed.2", "createbrewery.thought.tweak.speed.3", "createbrewery.thought.tweak.speed.4",
        "createbrewery.thought.tweak.speed.5", "createbrewery.thought.tweak.speed.6", "createbrewery.thought.tweak.speed.7",
        "createbrewery.thought.tweak.speed.8", "createbrewery.thought.tweak.speed.9"};
    private static final String[] TWEAKING = {"createbrewery.thought.tweak.tweaking.0", "createbrewery.thought.tweak.tweaking.1",
        "createbrewery.thought.tweak.tweaking.2", "createbrewery.thought.tweak.tweaking.3", "createbrewery.thought.tweak.tweaking.4",
        "createbrewery.thought.tweak.tweaking.5", "createbrewery.thought.tweak.tweaking.6", "createbrewery.thought.tweak.tweaking.7"};
    private static final String[] WHAT = {"createbrewery.thought.tweak.what.0", "createbrewery.thought.tweak.what.1", "createbrewery.thought.tweak.what.2"};
    private static final String[] HEARD = {"createbrewery.thought.tweak.heard.0", "createbrewery.thought.tweak.heard.1", "createbrewery.thought.tweak.heard.2"};
    private static final String[] MITES = {"createbrewery.thought.tweak.mites.0", "createbrewery.thought.tweak.mites.1", "createbrewery.thought.tweak.mites.2"};
    private static final String[] NIGHT = {"createbrewery.thought.tweak.night.0", "createbrewery.thought.tweak.night.1", "createbrewery.thought.tweak.night.2"};
    private static final String[] CRASH = {"createbrewery.thought.tweak.crash.0", "createbrewery.thought.tweak.crash.1", "createbrewery.thought.tweak.crash.2",
        "createbrewery.thought.tweak.crash.3", "createbrewery.thought.tweak.crash.4"};

    private static void thoughts(LocalPlayer player, boolean on, RandomSource r) {
        if (player.tickCount < nextThought || DrunkClient.trip > 0.2f) return;
        String[] pool = on && tired > 0.4f ? TWEAKING
            : on && DrugEffect.strength(player, ModEffects.TWEAK) > 0.3f ? SPEED
            : DrugEffect.strength(player, ModEffects.METH_CRASH) > 0.3f ? CRASH : null;
        if (pool == null) {
            nextThought = player.tickCount + 200;
            return;
        }
        think(player, pool);
        // A racing mind: on the high one thought chases the next.
        if (pool == SPEED) nextThought = player.tickCount + 160 + r.nextInt(240);
    }

    private static void think(LocalPlayer player, String[] pool) {
        player.displayClientMessage(Component.translatable(pool[player.getRandom().nextInt(pool.length)]).withStyle(ChatFormatting.ITALIC)
            .withColor(pool == CRASH ? 0x8A8A9A : pool == SPEED ? 0x9FE3FF : 0xB07CFF), true);
        nextThought = player.tickCount + 500 + player.getRandom().nextInt(500);
    }
}
