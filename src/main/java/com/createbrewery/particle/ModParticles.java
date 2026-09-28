package com.createbrewery.particle;

import com.createbrewery.CreateBrewery;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Our own particles, with our own textures (assets/createbrewery/particles, textures/particle). */
public final class ModParticles {
    private ModParticles() {}

    private static final DeferredRegister<ParticleType<?>> PARTICLES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, CreateBrewery.MOD_ID);

    /** Beer foam bubbles: rise, swell and pop. Drinking, hiccups, clinking glasses. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BEER_FOAM = simple("beer_foam");
    /** Golden twinkling stars when two players clink glasses. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CHEERS_SPARK = simple("cheers_spark");
    /** Colourful paper scraps fluttering down after a toast. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CONFETTI = simple("confetti");
    /** Colourful music notes when you dance (jump) in a good mood. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PARTY_NOTE = simple("party_note");
    /** Chunks of the last meal: fly in an arc, splat on the ground and lie there a moment. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOMIT_CHUNK = simple("vomit_chunk");
    /** Droplets where a chunk lands. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOMIT_SPLASH = simple("vomit_splash");
    /** Bubbling blobs of the puddle left on the ground. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOMIT_PUDDLE = simple("vomit_puddle");

    /** Drops of blood from the nose. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NOSEBLEED = simple("nosebleed");
    /** Exhaled smoke, drifting up and spreading. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMOKE = simple("smoke");
    /** A puff of white powder. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> POWDER = simple("powder");
    /** Dense, ground-hugging club fog / haze from the fog machine. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FOG = simple("fog");
    /** A billow of a CO2 cannon's jet: fast, dense, swelling and sinking cold. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CO2 = simple("co2");
    /** A cold spark of a spark fountain. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> COLD_SPARK = simple("cold_spark");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
