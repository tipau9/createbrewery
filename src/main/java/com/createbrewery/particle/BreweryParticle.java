package com.createbrewery.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Client side of every brewery particle: one particle class, tuned per type by a {@link Style}.
 * Client only; registered from the mod bus in CreateBrewery's client block.
 */
public class BreweryParticle extends TextureSheetParticle {

    /**
     * How a particle type behaves.
     *
     * @param animated  sprite follows the age (bubble swelling and popping, star twinkling)
     *                  instead of one random sprite per particle
     * @param tint      random bright colour per particle (confetti, notes)
     * @param glow      full brightness, also in the dark
     * @param splats    spawns a splash where it hits the ground and then lies still
     */
    public record Style(float gravity, float friction, int minLife, int maxLife, float size,
                        float spin, float sway, boolean animated, boolean tint, boolean glow,
                        boolean physics, boolean splats, boolean fade) {}

    public static final Style FOAM = new Style(-0.03f, 0.9f, 18, 30, 0.10f, 0f, 0.004f, true, false, false, false, false, true);
    public static final Style SPARK = new Style(0.01f, 0.88f, 14, 24, 0.14f, 0.2f, 0f, true, false, true, false, false, true);
    public static final Style CONFETTI = new Style(0.035f, 0.93f, 60, 100, 0.08f, 0.35f, 0.012f, false, true, false, true, false, true);
    public static final Style NOTE = new Style(-0.015f, 0.92f, 26, 36, 0.16f, 0f, 0.008f, false, true, true, false, false, true);
    public static final Style CHUNK = new Style(0.9f, 0.98f, 40, 60, 0.09f, 0.15f, 0f, false, false, false, true, true, true);
    public static final Style SPLASH = new Style(0.6f, 0.95f, 8, 12, 0.06f, 0f, 0f, true, false, false, true, false, false);
    public static final Style POWDER = new Style(-0.005f, 0.85f, 14, 24, 0.05f, 0.1f, 0.003f, false, false, false, false, false, true);
    public static final Style BLOOD = new Style(0.7f, 0.96f, 18, 30, 0.035f, 0f, 0f, false, false, false, true, false, true);
    public static final Style SMOKE = new Style(-0.012f, 0.93f, 50, 80, 0.16f, 0.06f, 0.004f, true, false, false, false, false, true);
    public static final Style PUDDLE = new Style(0f, 0.5f, 24, 40, 0.14f, 0f, 0f, false, false, false, true, false, true);

    private static final float[][] PARTY_COLOURS = {
        { 1f, 0.3f, 0.35f }, { 1f, 0.8f, 0.2f }, { 0.3f, 0.85f, 0.4f }, { 0.3f, 0.6f, 1f },
        { 0.85f, 0.4f, 1f }, { 1f, 0.55f, 0.15f }, { 0.2f, 0.95f, 0.95f }
    };

    private final Style style;
    private final SpriteSet sprites;
    private final float spinSpeed;
    private final float swayPhase;
    private boolean landed;

    protected BreweryParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                              Style style, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.style = style;
        this.sprites = sprites;
        // Keep the velocity we were given; the super constructor adds its own random jitter.
        this.xd = xd;
        this.yd = style == PUDDLE ? 0 : yd;
        this.zd = zd;
        this.gravity = style.gravity();
        this.friction = style.friction();
        this.hasPhysics = style.physics();
        this.lifetime = style.minLife() + random.nextInt(style.maxLife() - style.minLife() + 1);
        this.quadSize = style.size() * (0.75f + random.nextFloat() * 0.5f);
        this.spinSpeed = (random.nextFloat() - 0.5f) * 2f * style.spin();
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI * (style.spin() > 0 ? 1 : 0);
        if (style.tint()) {
            float[] c = PARTY_COLOURS[random.nextInt(PARTY_COLOURS.length)];
            setColor(c[0], c[1], c[2]);
        }
        if (style.animated()) setSpriteFromAge(sprites);
        else pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        if (style.animated()) setSpriteFromAge(sprites);
        if (style.sway() > 0) {
            // Flutter and wobble sideways: confetti and bubbles never fall straight.
            float s = Mth.sin(age * 0.35f + swayPhase) * style.sway();
            xd += s;
            zd += Mth.cos(age * 0.3f + swayPhase) * style.sway();
        }
        oRoll = roll;
        if (!landed) roll += spinSpeed;
        if (style.splats() && onGround && !landed) {
            // Splat: a few droplets, then the chunk lies flat where it landed.
            landed = true;
            for (int i = 0; i < 3; i++) {
                level.addParticle(ModParticles.VOMIT_SPLASH.get(), x, y + 0.05, z,
                    (random.nextDouble() - 0.5) * 0.15, 0.08 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.15);
            }
            age = Math.max(age, lifetime - 30);
        }
        if (landed) {
            xd = zd = 0;
        }
        if (style.fade()) {
            int left = lifetime - age;
            alpha = left < 10 ? left / 10f : 1f;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return style.glow() ? 0xF000F0 : super.getLightColor(partialTick);
    }

    private static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites, Style style) {
        return (type, level, x, y, z, xd, yd, zd) -> new BreweryParticle(level, x, y, z, xd, yd, zd, style, sprites);
    }

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BEER_FOAM.get(), s -> provider(s, FOAM));
        event.registerSpriteSet(ModParticles.CHEERS_SPARK.get(), s -> provider(s, SPARK));
        event.registerSpriteSet(ModParticles.CONFETTI.get(), s -> provider(s, CONFETTI));
        event.registerSpriteSet(ModParticles.PARTY_NOTE.get(), s -> provider(s, NOTE));
        event.registerSpriteSet(ModParticles.VOMIT_CHUNK.get(), s -> provider(s, CHUNK));
        event.registerSpriteSet(ModParticles.VOMIT_SPLASH.get(), s -> provider(s, SPLASH));
        event.registerSpriteSet(ModParticles.VOMIT_PUDDLE.get(), s -> provider(s, PUDDLE));
        event.registerSpriteSet(ModParticles.POWDER.get(), s -> provider(s, POWDER));
        event.registerSpriteSet(ModParticles.NOSEBLEED.get(), s -> provider(s, BLOOD));
        event.registerSpriteSet(ModParticles.SMOKE.get(), s -> provider(s, SMOKE));
    }
}
