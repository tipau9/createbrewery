package com.createbrewery.drugs;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Things only the tripping player sees, client side only: entities that are never added to the
 * world, just posed and drawn after the real ones.
 *
 * <ul>
 *   <li>DMT: glowing beings circling you at the peak of the breakthrough (the "machine elves").</li>
 *   <li>LSD/mushrooms, heroic dose: now and then a bright parrot flies past.</li>
 *   <li>Bad trip: a tall dark figure at the edge of your view that is gone when you look at it.</li>
 * </ul>
 */
public final class Hallucinations {
    private Hallucinations() {}

    private static final class Vision {
        final Entity entity;
        double angle;
        final double radius, height, speed;
        final int until;
        /** Vanishes when looked at. */
        final boolean shy;
        final boolean glowing;

        Vision(Entity entity, double angle, double radius, double height, double speed, int until, boolean shy, boolean glowing) {
            this.entity = entity;
            this.angle = angle;
            this.radius = radius;
            this.height = height;
            this.speed = speed;
            this.until = until;
            this.shy = shy;
            this.glowing = glowing;
        }
    }

    private static final List<Vision> visions = new ArrayList<>();
    private static Level visionLevel;

    /** Every client tick, with the eased channels from DrunkClient. */
    public static void tick(LocalPlayer player, float breakthrough, float trip, float bad, float desert) {
        Level level = player.level();
        if (level != visionLevel) {
            visions.clear();
            visionLevel = level;
        }
        var random = player.getRandom();
        int now = player.tickCount;
        visions.removeIf(v -> now > v.until
            || (v.entity.getType() == EntityType.ALLAY && breakthrough < 0.1f)
            || ((v.entity.getType() == EntityType.WOLF || v.entity.getType() == EntityType.FOX) && desert < 0.2f)
            || (v.shy && lookedAt(player, v.entity)));

        if (breakthrough > 0.4f && count(EntityType.ALLAY) < 7 && random.nextFloat() < 0.2f) {
            add(EntityType.ALLAY, level, random.nextDouble() * Math.PI * 2, 2.5 + random.nextDouble() * 1.5,
                0.4 + random.nextDouble() * 2.0, (random.nextBoolean() ? 1 : -1) * (0.03 + random.nextDouble() * 0.04),
                now + 400, false, true);
        }
        // Things that are not there at all are rare on a trip - only on a heroic dose.
        if (trip > 0.9f && random.nextFloat() < 0.002f) {
            add(EntityType.PARROT, level, random.nextDouble() * Math.PI * 2, 6 + random.nextDouble() * 3,
                1.5 + random.nextDouble() * 2, 0.06, now + 80, false, true);
        }
        if (bad > 0.3f && count(EntityType.ENDERMAN) == 0 && random.nextFloat() < 0.01f) {
            // Just outside where you are looking, off to one side.
            double yaw = Math.toRadians(player.getYRot() + 90 + (random.nextBoolean() ? 1 : -1) * (65 + random.nextInt(30)));
            add(EntityType.ENDERMAN, level, yaw, 10 + random.nextDouble() * 6, 0, 0, now + 400, true, false);
        }
        // Peyote: a spirit animal (wolf or fox) walks around you for 30s
        if (desert > 0.6f && count(EntityType.WOLF) == 0 && count(EntityType.FOX) == 0 && random.nextFloat() < 0.04f) {
            EntityType<?> spirit = random.nextBoolean() ? EntityType.WOLF : EntityType.FOX;
            add(spirit, level, random.nextDouble() * Math.PI * 2, 4.0, 0.0,
                (random.nextBoolean() ? 1 : -1) * 0.025, now + 600, false, true);
        }

        for (Vision v : visions) {
            Entity e = v.entity;
            e.setOldPosAndRot();
            boolean isSpirit = e.getType() == EntityType.WOLF || e.getType() == EntityType.FOX;
            boolean paused = isSpirit && (e.tickCount % 200 > 140);
            double actualSpeed = paused ? 0.0 : v.speed;
            v.angle += actualSpeed;
            double x = player.getX() + Math.cos(v.angle) * v.radius;
            double z = player.getZ() + Math.sin(v.angle) * v.radius;
            e.setPos(x, player.getY() + v.height, z);
            // Circling ones walk along their path; paused/still ones stare at you.
            float yaw = actualSpeed != 0
                ? (float) Math.toDegrees(v.angle) + (v.speed > 0 ? 180f : 0f)
                : (float) Math.toDegrees(Math.atan2(player.getZ() - z, player.getX() - x)) - 90f;
            e.setYRot(yaw);
            e.setYHeadRot(yaw);
            e.setYBodyRot(yaw);
            if (e instanceof net.minecraft.world.entity.LivingEntity living && actualSpeed != 0) {
                living.walkAnimation.update((float) Math.abs(v.speed * v.radius * 2.0), 0.4f);
            }
            e.tickCount++;
        }
    }

    /** Drawn after the real entities, so they sit in the world like any other. */
    public static void render(RenderLevelStageEvent event) {
        if (visions.isEmpty() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        var dispatcher = mc.getEntityRenderDispatcher();
        for (Vision v : visions) {
            Entity e = v.entity;
            int light = v.glowing ? LightTexture.FULL_BRIGHT : dispatcher.getPackedLightCoords(e, partial);
            try {
                dispatcher.render(e, Mth.lerp(partial, e.xo, e.getX()) - cam.x, Mth.lerp(partial, e.yo, e.getY()) - cam.y,
                    Mth.lerp(partial, e.zo, e.getZ()) - cam.z, e.getYRot(), partial, pose, buffers, light);
            } catch (RuntimeException ex) {
                v.entity.discard(); // a renderer that needs a real world: drop that vision
            }
        }
        buffers.endBatch();
        visions.removeIf(v -> v.entity.isRemoved());
    }

    private static void add(EntityType<?> type, Level level, double angle, double radius, double height, double speed,
                            int until, boolean shy, boolean glowing) {
        Entity entity = type.create(level);
        if (entity != null) visions.add(new Vision(entity, angle, radius, height, speed, until, shy, glowing));
    }

    private static int count(EntityType<?> type) {
        int n = 0;
        for (Vision v : visions) if (v.entity.getType() == type) n++;
        return n;
    }

    private static boolean lookedAt(LocalPlayer player, Entity entity) {
        Vec3 to = entity.position().add(0, entity.getBbHeight() * 0.7, 0).subtract(player.getEyePosition()).normalize();
        return player.getViewVector(1f).dot(to) > 0.93;
    }
}
