package com.createbrewery.particle;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drunk.DrunkClient;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Fog machine haze: a soft puff shot out of the nozzle that slows in the air, swells, cools and
 * sinks to hang low over the floor. It slides along the walls it runs into instead of sticking,
 * swirls a little, and parts around the player walking through it. Client only.
 */
public class FogParticle extends TextureSheetParticle {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static ShaderInstance shader;

    /**
     * Drawn without depth writes (puffs behind puffs still show) and with a shader that keeps the
     * faint edges vanilla's particle shader cuts off. Under an Iris shaderpack the pack's particle
     * shader is kept, so the fog still goes through the pack's lighting.
     */
    static final ParticleRenderType SOFT = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (shader != null && !DrunkClient.shaderPack()) RenderSystem.setShader(() -> shader);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return CreateBrewery.MOD_ID + ":soft_fog";
        }
    };

    private final float startSize, endSize, peakAlpha;
    private final float swirl;
    /** Where it came out, and how far to the side of straight away from there it flows (radians). */
    protected final double originX, originZ;
    protected final float fan;

    FogParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        // The box it collides with: wide and tall enough that the puff hovers instead of cutting into walls and floor.
        setSize(0.5f, 0.8f);
        this.lifetime = 420 + random.nextInt(220);
        this.startSize = 0.25f + random.nextFloat() * 0.1f;
        this.endSize = 1.8f + random.nextFloat() * 0.8f;
        this.originX = x;
        this.originZ = z;
        this.fan = (random.nextFloat() - 0.5f) * 2.6f;
        this.quadSize = startSize;
        this.peakAlpha = 0.16f + random.nextFloat() * 0.08f;
        this.alpha = 0f;
        this.swirl = random.nextFloat() * Mth.TWO_PI;
        float grey = 0.86f + random.nextFloat() * 0.08f;
        setColor(grey, grey, grey + 0.02f);
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.hasPhysics = true;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        // Warm out of the machine it rises a touch; once cooled it is heavier than air and settles.
        yd += age < 25 ? 0.0012 : -0.0009;
        // Turbulence: a slow swirl plus a little noise, so the haze keeps moving and mixing.
        float t = age * 0.05f + swirl;
        xd += Mth.sin(t) * 0.0012 + (random.nextFloat() - 0.5f) * 0.003;
        zd += Mth.cos(t * 1.3f) * 0.0012 + (random.nextFloat() - 0.5f) * 0.003;
        yd += (random.nextFloat() - 0.5f) * 0.001;
        // Cooled on the floor it flows out over it like a liquid, to the sides as much as ahead.
        if (age > 25) spread(0.045, 6);
        stir();

        move(xd, yd, zd);
        // Air drag: the jet carries it far (~9 blocks), then it drifts.
        xd *= 0.955;
        zd *= 0.955;
        yd *= 0.9;

        oRoll = roll;
        roll += (swirl - Mth.PI) * 0.001f;
        float grown = 1f - (float) Math.exp(-age / 70.0);
        quadSize = startSize + (endSize - startSize) * grown;
        float in = Math.min(1f, age / 12f);
        float out = Math.min(1f, (lifetime - age) / 90f);
        alpha = peakAlpha * in * out;
    }

    /**
     * A gravity current: it keeps flowing away from where it came out, fanned out to the sides,
     * slowing as the layer thins (half the {@code speed} at {@code halfway} blocks out), so the
     * fog covers the floor instead of hanging as a ball in front of the machine.
     */
    protected void spread(double speed, double halfway) {
        double ox = x - originX, oz = z - originZ, r = Math.sqrt(ox * ox + oz * oz);
        if (r < 0.3) return;
        double c = Mth.cos(fan), s = Mth.sin(fan);
        double v = speed / (1 + r / halfway) * 0.05;
        xd += (ox * c - oz * s) / r * v;
        zd += (ox * s + oz * c) / r * v;
    }

    /** The local player pushes through the haze and drags some of it along. */
    protected void stir() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        double dx = x - player.getX(), dy = y + lift() - (player.getY() + 0.9), dz = z - player.getZ();
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 > 2.5 || d2 < 1e-4) return;
        double d = Math.sqrt(d2);
        double push = (1 - d / 1.58) * 0.03;
        Vec3 walk = player.getDeltaMovement();
        xd += dx / d * push + walk.x * 0.08;
        zd += dz / d * push + walk.z * 0.08;
        yd += Math.max(0, dy / d) * push * 0.5;
    }

    /**
     * Moves like vanilla but never freezes on contact: a puff that hits a wall is turned along it,
     * one that hits the floor or ceiling spreads out sideways.
     */
    @Override
    public void move(double dx, double dy, double dz) {
        Vec3 got = Entity.collideBoundingBox(null, new Vec3(dx, dy, dz), getBoundingBox(), level, List.of());
        if (got.lengthSqr() > 0) {
            setBoundingBox(getBoundingBox().move(got));
            setLocationFromBoundingbox();
        }
        if (got.x != dx) {
            zd += Math.copySign(Math.abs(dx) * 0.6, random.nextBoolean() ? 1 : -1);
            xd = -dx * 0.1;
        }
        if (got.z != dz) {
            xd += Math.copySign(Math.abs(dz) * 0.6, random.nextBoolean() ? 1 : -1);
            zd = -dz * 0.1;
        }
        if (got.y != dy) {
            double spread = Math.abs(dy) * 0.8;
            xd += (random.nextDouble() - 0.5) * spread;
            zd += (random.nextDouble() - 0.5) * spread;
            yd = 0;
        }
        onGround = got.y != dy && dy < 0;
    }

    /** The quad floats above the bottom of the collision box (y), lifting as it swells. */
    protected float lift() {
        return 0.25f + 0.35f * Math.min(1f, age / 120f);
    }

    @Override
    protected void renderRotatedQuad(VertexConsumer buffer, Camera camera, Quaternionf rotation, float partialTick) {
        Vec3 cam = camera.getPosition();
        renderRotatedQuad(buffer, rotation,
            (float) (Mth.lerp(partialTick, xo, x) - cam.x()),
            (float) (Mth.lerp(partialTick, yo, y) + lift() - cam.y()),
            (float) (Mth.lerp(partialTick, zo, z) - cam.z()), partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return SOFT;
    }

    static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites) {
        return (type, level, x, y, z, xd, yd, zd) -> new FogParticle(level, x, y, z, xd, yd, zd, sprites);
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "fog_particle"), DefaultVertexFormat.PARTICLE),
                s -> shader = s);
        } catch (IOException e) {
            // Falls back to the vanilla particle shader (harder edges, still fog).
            LOGGER.warn("Soft fog shader unavailable", e);
        }
    }
}
