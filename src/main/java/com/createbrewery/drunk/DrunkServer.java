package com.createbrewery.drunk;

import com.createbrewery.CreateBrewery;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.effect.PainkillerEffect;
import com.createbrewery.effect.VomitingEffect;
import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server side of drunkenness. Owns the blood-alcohol bookkeeping and decides when symptoms
 * start; everything a player sees or feels on screen is derived client-side from the synced
 * {@link DrunkState}. Registered on {@code NeoForge.EVENT_BUS}.
 */
public final class DrunkServer {
    private DrunkServer() {}

    /** Symptom indicator effects are refreshed every second and expire 5 s after the cause stops. */
    private static final int INDICATOR_TICKS = 100;
    /** Long enough to wake up somewhere else: the body keeps walking while the screen is black. */
    private static final int BLACKOUT_TICKS = 240;
    private static final ResourceKey<DamageType> POISON_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "alcohol_poisoning"));

    private static final ResourceKey<DamageType> OVERDOSE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "painkiller_overdose"));

    /** Damage from swallowing too many painkillers. */
    public static DamageSource overdoseSource(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(OVERDOSE_DAMAGE));
    }

    /** Throw up now, e.g. a painkiller on top of too much alcohol. */
    public static void vomit(Player player) {
        if (!player.hasEffect(ModEffects.VOMITING)) vomit(player, state(player));
    }

    /** Damage from alcohol poisoning: ignores armour, no knockback, own death message. */
    public static DamageSource poisonSource(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(POISON_DAMAGE));
    }

    public static DrunkState state(Player player) {
        return player.getData(ModAttachments.DRUNK);
    }

    /** A beer lands in the stomach; the blood follows over the next Minecraft hour (~50 s). */
    public static void drink(Player player, float perMille) {
        DrunkState s = state(player);
        s.stomach += perMille;
        s.lastDrinkTime = player.level().getGameTime();
        showIndicator(player);
        cheers(player, s);
        sync(player, s);
    }

    /**
     * Prost! Two players drinking within 5 seconds of each other, close enough to clink glasses,
     * both earn Geselligkeit. Drinking alone gets you nothing of the sort.
     */
    private static void cheers(Player player, DrunkState s) {
        if (!(player.level() instanceof ServerLevel level)) return;
        for (Player other : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(6.0), p -> p != player)) {
            if (s.lastDrinkTime - state(other).lastDrinkTime > 100) continue;
            for (Player p : new Player[] { player, other }) {
                p.addEffect(new MobEffectInstance(ModEffects.CHEERS, 1200, 0, false, true, true));
            }
            // Where the glasses meet: a burst of golden sparks and foam, then confetti raining down.
            Vec3 mid = player.getEyePosition().add(other.getEyePosition()).scale(0.5);
            level.sendParticles(ModParticles.CHEERS_SPARK.get(), mid.x, mid.y, mid.z, 18, 0.25, 0.2, 0.25, 0.08);
            level.sendParticles(ModParticles.BEER_FOAM.get(), mid.x, mid.y - 0.1, mid.z, 14, 0.15, 0.05, 0.15, 0.03);
            level.sendParticles(ModParticles.CONFETTI.get(), mid.x, mid.y + 1.2, mid.z, 40, 0.9, 0.3, 0.9, 0.02);
            level.playSound(null, mid.x, mid.y, mid.z, ModSounds.GLASS_CLINK.get(), SoundSource.PLAYERS, 1.3f,
                0.95f + player.getRandom().nextFloat() * 0.1f);
        }
    }

    /** Bierlaune makes you dance: every jump throws music notes. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && player.level() instanceof ServerLevel level
            && player.hasEffect(ModEffects.GOOD_MOOD)) {
            level.sendParticles(ModParticles.PARTY_NOTE.get(), player.getX(), player.getY() + 2.0, player.getZ(),
                3, 0.4, 0.15, 0.4, 0.02);
        }
    }

    /** Water dilutes what is still in the stomach and takes the edge off the blood level. */
    public static void water(Player player) {
        DrunkState s = state(player);
        s.stomach *= 0.5f;
        s.blood = Math.max(0f, s.blood - 0.15f);
        sync(player, s);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        DrunkState s = state(player);
        if (s.isEmpty() && !s.clientSawAlcohol) return; // sober: nothing to do

        float absorbed = Intoxication.absorbed(s.stomach, player.getFoodData().getFoodLevel());
        s.stomach -= absorbed;
        s.blood = Intoxication.eliminated(s.blood + absorbed);
        s.peak = Math.max(s.peak, s.blood);

        if (s.blood <= 0f && s.stomach <= 0f) {
            soberUp(player, s);
            return;
        }
        if (Intoxication.hangoverStarts(s.blood, s.stomach, s.peak)) startHangover(player, s);
        if (player.tickCount % 20 == 0) symptoms(player, s, player.getRandom());
        if (player.tickCount % 10 == 0) sync(player, s);
    }

    private static void soberUp(Player player, DrunkState s) {
        s.blood = 0f;
        s.stomach = 0f;
        player.removeEffect(ModEffects.INEBRIATION);
        startHangover(player, s);
        sync(player, s);
    }

    /**
     * The Kater, earned by the session's peak. It starts while the last of the alcohol wears off
     * (or on waking up after sleeping it off). A working painkiller prevents it; either way the
     * peak is spent, so the same session never gives two hangovers.
     */
    private static void startHangover(Player player, DrunkState s) {
        int ticks = Intoxication.hangoverTicks(s.peak);
        int level = Intoxication.hangoverLevel(s.peak);
        s.peak = 0f;
        if (ticks > 0 && !PainkillerEffect.working(player)) {
            player.addEffect(new MobEffectInstance(ModEffects.HANGOVER, ticks, level));
        }
    }

    private static void symptoms(Player player, DrunkState s, RandomSource random) {
        float bac = s.blood;
        showIndicator(player);

        if (Intoxication.inGoodMood(bac)) refresh(player, ModEffects.GOOD_MOOD, 0);
        if (bac >= Intoxication.DRUNK) refresh(player, ModEffects.DELIRIUM, bac >= Intoxication.SMASHED ? 1 : 0);
        if (bac >= Intoxication.WASTED) refresh(player, ModEffects.STUMBLE, bac >= Intoxication.SMASHED ? 1 : 0);

        // Hiccups come in fits, roughly once every 50 s once merry.
        if (bac >= Intoxication.MERRY && !player.hasEffect(ModEffects.HICCUPS) && random.nextFloat() < 0.02f) {
            player.addEffect(new MobEffectInstance(ModEffects.HICCUPS, 200,
                bac >= Intoxication.WASTED ? 1 : 0, false, false, true));
        }

        // Alkoholvergiftung: the body is being poisoned and takes damage until the level drops.
        boolean poisoned = bac >= Intoxication.POISONING;
        if (poisoned) {
            refresh(player, ModEffects.POISONING, 0);
            if (player.tickCount % Intoxication.POISON_INTERVAL == 0) {
                player.hurt(poisonSource(player.level()), Intoxication.poisonDamage(bac));
            }
        }

        float vomitChance = poisoned ? 0.03f : bac >= Intoxication.SMASHED ? 0.012f : 0.006f;
        if (bac >= Intoxication.WASTED && !player.hasEffect(ModEffects.VOMITING) && random.nextFloat() < vomitChance) {
            vomit(player, s);
        }

        if (bac >= Intoxication.BLACKOUT && !player.hasEffect(ModEffects.BLACKOUT) && random.nextFloat() < 0.015f) {
            player.addEffect(new MobEffectInstance(ModEffects.BLACKOUT, BLACKOUT_TICKS, 0, false, false, true));
        }
    }

    /**
     * Throwing up: empties the stomach (the one real upside - unabsorbed alcohol never reaches
     * the blood) and costs most of the player's food. The heaving itself is the Kotzanfall
     * effect; what lands on the ground stays there as a puddle for half a minute.
     */
    private static void vomit(Player player, DrunkState s) {
        s.stomach = 0f;
        FoodData food = player.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - 6));
        food.setSaturation(0f);

        player.setSprinting(false);
        player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(ModEffects.VOMITING, VomitingEffect.DURATION, 0, false, false, true));

        if (player.level() instanceof ServerLevel level) {
            Vec3 look = player.getLookAngle();
            AreaEffectCloud puddle = new AreaEffectCloud(level,
                player.getX() + look.x * 1.1, player.getY(), player.getZ() + look.z * 1.1);
            puddle.setParticle(ModParticles.VOMIT_PUDDLE.get());
            puddle.setRadius(0.9f);
            puddle.setRadiusPerTick(0f);
            puddle.setWaitTime(15);
            puddle.setDuration(600);
            level.addFreshEntity(puddle);
        }
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
            && instance != null && instance.is(ModEffects.BLACKOUT)) {
            player.displayClientMessage(Component.literal(
                "§8§l...Filmriss. §7Wie bist du hierhergekommen? Was ist passiert?"), false);
        }
    }

    /** Sleeping it off: a full night burns 2 per mille, and waking up sober brings the hangover. */
    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || event.wakeImmediately()) return; // left the bed early
        DrunkState s = state(player);
        if (s.isEmpty()) return;
        s.stomach = 0f;
        s.blood = Math.max(0f, s.blood - 2.0f);
        if (s.blood <= 0f) soberUp(player, s);
        else sync(player, s);
    }

    /** Hangover: the shaking, aching body mines noticeably slower. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity().hasEffect(ModEffects.HANGOVER)) {
            event.setNewSpeed(event.getNewSpeed() * 0.7f);
        }
    }

    /**
     * The status icon for drunkenness. Infinite, because its real duration lives in the blood
     * level (a shorter re-add at the same level would be ignored by MobEffectInstance.update);
     * re-added if something like milk strips it, since milk does not sober anyone up.
     */
    private static void showIndicator(Player player) {
        if (!player.hasEffect(ModEffects.INEBRIATION)) {
            player.addEffect(new MobEffectInstance(ModEffects.INEBRIATION,
                MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
        }
    }

    private static void refresh(Player player, Holder<MobEffect> effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, INDICATOR_TICKS, amplifier, true, false, true));
    }

    private static void sync(Player player, DrunkState s) {
        boolean hasAlcohol = s.blood > 0f || s.stomach > 0f;
        if (hasAlcohol || s.clientSawAlcohol) {
            player.syncData(ModAttachments.DRUNK);
            s.clientSawAlcohol = hasAlcohol;
        }
    }
}
