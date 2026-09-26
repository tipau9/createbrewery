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
 *   <li>Bad trip, meth psychosis, paranoia: a tall dark figure at the edge of your view that is
 *       gone when you look at it - with GeckoLib the faceless shadow person, else an Enderman.</li>
 *   <li>Meth mites and coke bugs (with GeckoLib): little beetles scuttling over the ground around
 *       you, fleeing when looked at.</li>
 * </ul>
 */
public final class Hallucinations {
    private Hallucinations() {}

    private static final class Vision {
        final Entity entity;
        double angle;
        final double radius, height, speed;
        int until;
        /** Vanishes when looked at. */
        final boolean shy;
        final boolean glowing;
        /** Crawls over the ground instead of circling; {@link #angle} is its heading. */
        boolean crawl;

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
    /** GeckoLib draws the shadow person and the bugs (see HallucinationEntity). */
    public static final boolean GEO = net.neoforged.fml.ModList.get().isLoaded("geckolib");
    private static Level visionLevel;
    private static final java.util.Set<EntityType<?>> warned = new java.util.HashSet<>();

    /** Every client tick, with the eased channels from DrunkClient. */
    public static void tick(LocalPlayer player, float breakthrough, float trip, float bad, float desert, float bugs) {
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
            || (v.shy && lookedAt(player, v.entity) && !GEO));
        for (Vision v : visions) {
            // With GeckoLib, looked at, it fades away instead of blinking out (the bugs flee).
            if (v.shy && GEO && v.entity instanceof HallucinationEntity h && !h.vanishing && lookedAt(player, v.entity)) {
                h.vanishing = !v.crawl;
                v.until = Math.min(v.until, now + (v.crawl ? 25 : 8));
            }
        }

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
        if (bad > 0.3f && count(figure()) == 0 && random.nextFloat() < 0.01f) shadow(player, 65 + random.nextInt(30));
        // Sleepless on meth, it stands there too, now and then.
        if (GEO && bugs > 0.5f && random.nextFloat() < 0.002f * bugs) shadow(player, 60 + random.nextInt(30));
        // Meth mites, coke bugs: they scuttle over the ground around you.
        if (GEO && bugs > 0.3f && count(HallucinationEntity.CRAWLER.get()) < 2 + (int) (6 * bugs) && random.nextFloat() < 0.04f * bugs) {
            double heading = random.nextDouble() * Math.PI * 2, r = 1.5 + random.nextDouble() * 3;
            double x = player.getX() + Math.cos(heading) * r, z = player.getZ() + Math.sin(heading) * r;
            Double y = ground(level, x, player.getY(), z);
            if (y != null) {
                add(HallucinationEntity.CRAWLER.get(), level, random.nextDouble() * Math.PI * 2, 0, 0, 0.03, now + 200 + random.nextInt(200), true, false);
                Vision v = visions.get(visions.size() - 1);
                v.crawl = true;
                v.entity.setPos(x, y, z);
            }
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
            if (v.crawl) {
                crawl(player, v, random);
                e.tickCount++;
                continue;
            }
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
                if (warned.add(e.getType())) org.slf4j.LoggerFactory.getLogger(Hallucinations.class)
                    .warn("Hallucination {} could not be drawn", EntityType.getKey(e.getType()), ex);
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

    /**
     * The tall dark figure, {@code off} degrees to one side of where you look, shy. Used for bad
     * trips here and for the paranoid figure in DrunkClient; true if it could appear.
     */
    public static boolean shadow(LocalPlayer player, float off) {
        if (count(figure()) > 0) return false;
        var random = player.getRandom();
        double yaw = Math.toRadians(player.getYRot() + 90 + (random.nextBoolean() ? 1 : -1) * off);
        add(figure(), player.level(), yaw, 10 + random.nextDouble() * 6, 0, 0, player.tickCount + 300, true, false);
        return true;
    }

    private static EntityType<?> figure() {
        return GEO ? HallucinationEntity.SHADOW_PERSON.get() : EntityType.ENDERMAN;
    }

    /** A bug's step: wandering, or running from your look, always on the ground. */
    private static void crawl(LocalPlayer player, Vision v, net.minecraft.util.RandomSource random) {
        Entity e = v.entity;
        boolean fleeing = v.until - player.tickCount <= 25;
        if (fleeing) {
            v.angle = Math.atan2(e.getZ() - player.getZ(), e.getX() - player.getX()) + (random.nextDouble() - 0.5) * 0.4;
        } else {
            v.angle += (random.nextDouble() - 0.5) * 0.5;
        }
        double step = fleeing ? 0.12 : random.nextFloat() < 0.15f ? 0 : v.speed;
        double x = e.getX() + Math.cos(v.angle) * step, z = e.getZ() + Math.sin(v.angle) * step;
        Double y = ground(e.level(), x, e.getY(), z);
        if (y == null) {
            v.angle += Math.PI; // a wall or an edge: turn round
            y = e.getY();
            x = e.getX();
            z = e.getZ();
        }
        e.setPos(x, y, z);
        float yaw = (float) Math.toDegrees(v.angle) - 90f;
        e.setYRot(yaw);
        if (e instanceof HallucinationEntity h) h.moving = step > 0;
    }

    /** The top of the ground at x, z, within a step of y; null if there is none (a wall, a drop). */
    private static Double ground(Level level, double x, double y, double z) {
        net.minecraft.core.BlockPos.MutableBlockPos pos = net.minecraft.core.BlockPos.containing(x, y + 1, z).mutable();
        for (int i = 0; i < 4; i++, pos.move(0, -1, 0)) {
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
            return top - y > 1.01 ? null : top;
        }
        return null;
    }

    /** For the debug log: what is being seen, e.g. "crawler×3 shadow_person×1". */
    public static String seen() {
        java.util.Map<String, Integer> n = new java.util.TreeMap<>();
        for (Vision v : visions) n.merge(EntityType.getKey(v.entity.getType()).getPath(), 1, Integer::sum);
        StringBuilder s = new StringBuilder();
        n.forEach((k, c) -> s.append(k).append('×').append(c).append(' '));
        return s.length() == 0 ? "-" : s.toString().trim();
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
