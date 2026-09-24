package com.createbrewery.drunk;

import com.createbrewery.ModItems;
import com.createbrewery.effect.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
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
    private static final int BLACKOUT_TICKS = 120;

    public static DrunkState state(Player player) {
        return player.getData(ModAttachments.DRUNK);
    }

    /** Whether another drink would go down. Safe on both sides: the state is synced. */
    public static boolean canDrink(Player player) {
        return state(player).total() < Intoxication.MAX_DRINKABLE;
    }

    /** A beer lands in the stomach; the blood follows over the next ~20 seconds. */
    public static void drink(Player player, float perMille) {
        DrunkState s = state(player);
        s.stomach += perMille;
        showIndicator(player);
        sync(player, s);
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

        float absorbed = Intoxication.absorbed(s.stomach);
        s.stomach -= absorbed;
        s.blood = Intoxication.eliminated(s.blood + absorbed);
        s.peak = Math.max(s.peak, s.blood);

        if (s.blood <= 0f && s.stomach <= 0f) {
            soberUp(player, s);
            return;
        }
        if (player.tickCount % 20 == 0) symptoms(player, s, player.getRandom());
        if (player.tickCount % 10 == 0) sync(player, s);
    }

    private static void soberUp(Player player, DrunkState s) {
        s.blood = 0f;
        s.stomach = 0f;
        player.removeEffect(ModEffects.INEBRIATION);
        int hangover = Intoxication.hangoverTicks(s.peak);
        boolean wasDrunk = s.peak > 0f;
        s.peak = 0f;
        if (hangover > 0) {
            player.addEffect(new MobEffectInstance(ModEffects.HANGOVER, hangover, 0));
            player.displayClientMessage(Component.literal(
                "§6Der Rausch ist weg... §cund jetzt kommt der Kater."), true);
        } else if (wasDrunk) {
            player.displayClientMessage(Component.literal("§aDu bist wieder nüchtern."), true);
        }
        sync(player, s);
    }

    private static void symptoms(Player player, DrunkState s, RandomSource random) {
        float bac = s.blood;
        showIndicator(player);

        if (bac >= Intoxication.DRUNK) refresh(player, ModEffects.DELIRIUM, bac >= Intoxication.SMASHED ? 1 : 0);
        if (bac >= Intoxication.WASTED) refresh(player, ModEffects.STUMBLE, bac >= Intoxication.SMASHED ? 1 : 0);

        // Hiccups come in fits, roughly once every 50 s once merry.
        if (bac >= Intoxication.MERRY && !player.hasEffect(ModEffects.HICCUPS) && random.nextFloat() < 0.02f) {
            player.addEffect(new MobEffectInstance(ModEffects.HICCUPS, 200,
                bac >= Intoxication.WASTED ? 1 : 0, false, false, true));
        }

        if (bac >= Intoxication.WASTED && random.nextFloat() < (bac >= Intoxication.SMASHED ? 0.012f : 0.006f)) {
            vomit(player, s);
        }

        if (bac >= Intoxication.BLACKOUT && !player.hasEffect(ModEffects.BLACKOUT) && random.nextFloat() < 0.015f) {
            player.addEffect(new MobEffectInstance(ModEffects.BLACKOUT, BLACKOUT_TICKS, 0, false, false, true));
        }
    }

    /**
     * Throwing up: empties the stomach (the one real upside - unabsorbed alcohol never reaches
     * the blood), costs most of the player's food, and leaves a brown mess on the ground.
     */
    private static void vomit(Player player, DrunkState s) {
        s.stomach = 0f;
        FoodData food = player.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - 6));
        food.setSaturation(0f);

        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(player.getDeltaMovement().multiply(0.2, 1.0, 0.2));
        player.hurtMarked = true;
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ModItems.SPENT_GRAIN.asStack()),
                player.getX() + look.x * 0.5, player.getEyeY() - 0.3, player.getZ() + look.z * 0.5,
                40, 0.15, 0.1, 0.15, 0.12);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.3f, 0.45f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0f, 0.6f);
        player.displayClientMessage(Component.literal(
            "§2§l*BÖÖÖRGH!* §aDas war zu viel. Wenigstens ist der Magen jetzt leer..."), true);
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
