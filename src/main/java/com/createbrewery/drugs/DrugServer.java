package com.createbrewery.drugs;

import com.createbrewery.Config;
import com.createbrewery.CreateBrewery;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.particle.ModParticles;
import com.createbrewery.sound.ModSounds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Server-side coordinator for drugs, intoxication, and emergency toxicology:
 * - Central entry point for dosing and consumption dispatch
 * - Toxicology & emergency lifelines: cardiac load, CPR, airway obstruction, aspiration
 * - Weed consumption, munchies, and edible metabolism
 * - Delegation of discrete drug classes to {@link StimulantHandler}, {@link DissociativeHandler},
 *   {@link PsychedelicHandler}, and {@link OpioidHandler}.
 */
public final class DrugServer {
    private DrugServer() {}

    public static final int COKE_TICKS = StimulantHandler.COKE_TICKS;
    public static final int KETA_TICKS = DissociativeHandler.KETA_TICKS;
    public static final int K_HOLE_TICKS = DissociativeHandler.K_HOLE_TICKS;
    public static final int WAH_TICKS = DissociativeHandler.WAH_TICKS;
    public static final int HEART_ATTACK_TICKS = 300;
    public static final int ASPIRATION_TICKS = 200;

    public static final int WEED_TICKS = 4800;
    public static final int GREENING_TICKS = 800;
    public static final int COTTONMOUTH_TICKS = 3600;
    public static final int SNACK_BLISS_TICKS = 600;
    public static final int HITS_PER_JOINT = 10;
    private static final int MAX_HITS = 3 * HITS_PER_JOINT;
    private static final int HITS_PER_BROWNIE = 8;

    public static final TagKey<Item> SWEETS = TagKey.create(
        Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "sweets"));

    private static final ResourceKey<DamageType> HEART_ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "heart_attack"));
    private static final ResourceKey<DamageType> ASPIRATION = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "aspiration"));

    public enum Kind { COKE, KETA, WEED, LSD, SHROOMS, MESCALINE, DMT, MDMA, METH, HEROIN, XANAX, LACHGAS }

    public static DamageSource heartAttack(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(HEART_ATTACK));
    }

    /** Both at once: Koks and Keta (or the K-Loch) active together. */
    public static boolean mixed(LivingEntity entity) {
        return entity.hasEffect(ModEffects.COKE_HIGH)
            && (entity.hasEffect(ModEffects.KETA_HIGH) || entity.hasEffect(ModEffects.K_HOLE));
    }

    /** Whether drugs are enabled in the config (off: the items do nothing). */
    public static boolean enabled() {
        return !Config.SPEC.isLoaded() || Config.ENABLE_DRUGS.get();
    }

    /** Joints' worth of weed in the body: 1 after a whole joint, 0.5 after three hits. */
    public static float joints(LivingEntity entity) {
        MobEffectInstance weed = entity.getEffect(ModEffects.WEED_HIGH);
        return weed == null ? 0f : (weed.getAmplifier() + 1) / (float) HITS_PER_JOINT;
    }

    /** One hit of a joint: a tenth of it. The high builds hit by hit, and so does the nausea. */
    public static void hit(Player player) {
        MobEffectInstance before = player.getEffect(ModEffects.WEED_HIGH);
        int hits = before == null ? 0 : Math.min(MAX_HITS - 1, before.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(ModEffects.WEED_HIGH, DrugEffect.doseTicks(before, ModEffects.WEED_HIGH, WEED_TICKS), hits, false, false, true));
        DrunkState s = DrunkServer.state(player);
        s.weedHabit = Math.min(1f, s.weedHabit + 0.012f);
        float j = joints(player);
        float sick = j >= 2.5f ? 1f : j > 2f ? 0.45f : j > 1.5f ? 0.15f : 0f;
        if (DrunkServer.state(player).blood >= Intoxication.MERRY) sick = Math.max(sick, 0.15f + 0.1f * j);
        if (player.getRandom().nextFloat() < sick) greenOut(player);
        smoke(player);
        com.createbrewery.event.BreweryCommonEvents.reduceDuration(player, ModEffects.HANGOVER, 800);
        player.addEffect(new MobEffectInstance(ModEffects.COTTONMOUTH, COTTONMOUTH_TICKS, 0, false, false, true));
    }

    /** A line goes up the nose, a hit is smoked, or a dose taken. Stacks with what is still active. */
    public static void take(Player player, Kind kind) {
        switch (kind) {
            case WEED -> hit(player);
            case LSD, SHROOMS, MESCALINE, DMT -> PsychedelicHandler.take(player, kind);
            case KETA, LACHGAS -> DissociativeHandler.take(player, kind);
            case HEROIN, XANAX -> OpioidHandler.take(player, kind);
            case COKE, MDMA, METH -> StimulantHandler.take(player, kind);
        }
    }

    /** Exhaling: a cloud others can see, and now and then a coughing fit. */
    private static void smoke(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        Vec3 look = player.getLookAngle();
        for (int i = 0; i < 10; i++) {
            double speed = 0.04 + player.getRandom().nextDouble() * 0.04;
            level.sendParticles(ModParticles.SMOKE.get(), player.getX() + look.x * 0.35, player.getEyeY() - 0.1,
                player.getZ() + look.z * 0.35, 0, look.x + (player.getRandom().nextDouble() - 0.5) * 0.4,
                0.25, look.z + (player.getRandom().nextDouble() - 0.5) * 0.4, speed);
        }
        if (player.getRandom().nextFloat() < (player.hasEffect(ModEffects.COTTONMOUTH) ? 0.4f : 0.25f)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.COUGH.get(),
                SoundSource.PLAYERS, 1.0f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        }
    }

    public static void greenOut(Player player) {
        if (!player.hasEffect(ModEffects.GREENING_OUT)) {
            player.addEffect(new MobEffectInstance(ModEffects.GREENING_OUT, GREENING_TICKS, 0, false, false, true));
        }
    }

    // ---- Drug ticks (called every 20 ticks from effects) ----

    public static void cokeTick(LivingEntity entity, int level) {
        StimulantHandler.cokeTick(entity, level);
    }

    public static void cravingTick(LivingEntity entity, int level) {
        StimulantHandler.cravingTick(entity, level);
    }

    public static void crashTick(LivingEntity entity, int level) {
        StimulantHandler.crashTick(entity, level);
    }

    public static void mixTick(LivingEntity entity, int level) {
        StimulantHandler.mixTick(entity, level);
    }

    public static void ketaTick(LivingEntity entity, int level) {
        DissociativeHandler.ketaTick(entity, level);
    }

    public static void wahTick(LivingEntity entity, int level) {
        DissociativeHandler.wahTick(entity, level);
    }

    public static void weedTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        float felt = DrugEffect.felt(player, ModEffects.WEED_HIGH);
        player.causeFoodExhaustion(0.2f * felt);
        float j = joints(player);
        float blood = DrunkServer.state(player).blood;
        float sick = (blood >= Intoxication.MERRY ? 0.01f + 0.01f * j : 0f) + (j > 1.5f ? 0.01f * j : 0f);
        if (player.getRandom().nextFloat() < sick) greenOut(player);
    }

    public static void greeningTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        float strength = DrugEffect.strength(player, ModEffects.GREENING_OUT);
        player.causeFoodExhaustion(0.5f * strength);
        if (player.getRandom().nextFloat() < 0.12f * strength) DrunkServer.vomit(player);
    }

    // ---- Munchies and eating ----

    private static boolean munchable(ItemStack stack, Player player) {
        return stack.getFoodProperties(player) != null && stack.getUseDuration(player) > 0
            && !(stack.getItem() instanceof com.createbrewery.item.BeerDrinkItem)
            && (stack.getItem().getClass() == Item.class
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft"));
    }

    @SubscribeEvent
    public static void onFinishEating(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
            && player.hasEffect(ModEffects.WEED_HIGH)
            && munchable(event.getItem(), player)) {
            player.getFoodData().eat(2, 0.3f);
            if (event.getItem().is(SWEETS)) sweet(player);
        }
    }

    @SubscribeEvent
    public static void onRightClickFood(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!player.hasEffect(ModEffects.WEED_HIGH)) return;
        ItemStack stack = event.getItemStack();
        FoodProperties food = stack.getFoodProperties(player);
        if (food != null && munchable(stack, player) && !player.canEat(food.canAlwaysEat())) {
            player.startUsingItem(event.getHand());
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEatCake(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || event.getHand() != InteractionHand.MAIN_HAND
            || !player.hasEffect(ModEffects.WEED_HIGH)
            || !(player.level().getBlockState(event.getPos()).getBlock() instanceof CakeBlock)) {
            return;
        }
        if (!player.canEat(false)) {
            var pos = event.getPos();
            BlockState state = player.level().getBlockState(pos);
            player.awardStat(net.minecraft.stats.Stats.EAT_CAKE_SLICE);
            player.getFoodData().eat(2, 0.1F);
            bliss(player);
            int bites = state.getValue(CakeBlock.BITES);
            player.level().gameEvent(player, GameEvent.EAT, pos);
            if (bites < 6) {
                player.level().setBlock(pos, state.setValue(CakeBlock.BITES, bites + 1), 3);
            } else {
                player.level().removeBlock(pos, false);
                player.level().gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        bliss(player);
    }

    public static void sweet(Player player) {
        player.getFoodData().eat(2, 0.3f);
        bliss(player);
    }

    private static void bliss(Player player) {
        player.addEffect(new MobEffectInstance(ModEffects.SNACK_BLISS, SNACK_BLISS_TICKS, 0, false, false, true));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    public static void quench(Player player, ItemStack drink) {
        if (drink.getUseAnimation() != UseAnim.DRINK) return;
        TanCompat.itemDrink(player);
        if (!(drink.getItem() instanceof ElectrolyteItem)) Stimulants.drank(player);
        Stimulants.cool(player);
        if (!player.hasEffect(ModEffects.COTTONMOUTH)) return;
        player.removeEffect(ModEffects.COTTONMOUTH);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 0.8f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 0.6f);
    }

    // ---- Expiry & Delay queue ----

    private static final List<Runnable> AFTER_EXPIRY = new ArrayList<>();

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        LivingEntity entity = event.getEntity();
        if (instance == null || entity.level().isClientSide) return;
        AFTER_EXPIRY.add(() -> expired(entity, instance));
    }

    @SubscribeEvent
    public static void afterExpiry(ServerTickEvent.Post event) {
        runAfterExpiry();
    }

    public static void runAfterExpiry() {
        if (AFTER_EXPIRY.isEmpty()) return;
        List<Runnable> now = new ArrayList<>(AFTER_EXPIRY);
        AFTER_EXPIRY.clear();
        for (Runnable r : now) r.run();
    }

    private static void expired(LivingEntity entity, MobEffectInstance instance) {
        if (entity.isRemoved()) return;
        PsychedelicHandler.expired(entity, instance);
        Stimulants.expired(entity, instance);
        Mixes.expired(entity, instance);

        float worse = entity.hasEffect(ModEffects.CK_MIX) ? 1.5f : 1f;
        if (entity.hasEffect(ModEffects.COCAETHYLENE)) worse *= 1.5f;

        StimulantHandler.expired(entity, instance, worse);
        DissociativeHandler.expired(entity, instance, worse);
        OpioidHandler.expired(entity, instance);

        if (instance.is(ModEffects.HEART_ATTACK) && entity.isAlive()) {
            award(entity, "zweites_leben", "survived");
        } else if (instance.is(ModEffects.EDIBLE_PENDING) && entity instanceof Player player) {
            edibleKicksIn(player, instance.getAmplifier() + 1);
        } else if (instance.is(ModEffects.SEIZURE) && entity instanceof Player player) {
            think(player, "Was… war das? Alles tut weh. Ich hab mir auf die Zunge gebissen.", 0xB090D0);
            player.addEffect(new MobEffectInstance(ModEffects.DAZED, 600, 0));
        }
    }

    public static void edibleKicksIn(Player player, int brownies) {
        MobEffectInstance before = player.getEffect(ModEffects.WEED_HIGH);
        int hits = Math.min(MAX_HITS - 1, (before == null ? -1 : before.getAmplifier()) + HITS_PER_BROWNIE * brownies);
        DrugEffect weed = (DrugEffect) ModEffects.WEED_HIGH.value();
        player.addEffect(new MobEffectInstance(ModEffects.WEED_HIGH, weed.total() - weed.onset(), hits, false, false, true));
        player.addEffect(new MobEffectInstance(ModEffects.COTTONMOUTH, COTTONMOUTH_TICKS, 0, false, false, true));
        think(player, "Oh. Oh nein. Da ist es. Das ist… viel.", 0x8FCF5A);
        if (joints(player) >= 2.5f || player.getRandom().nextFloat() < 0.3f * (brownies - 1)) greenOut(player);
    }

    public static void edibleTick(LivingEntity entity, int level) {
        MobEffectInstance pending = entity.getEffect(ModEffects.EDIBLE_PENDING);
        if (entity instanceof Player player && pending != null && pending.getDuration() / 20 == EdibleItem.DELAY / 40) {
            think(player, "Merk nix. Gar nix. …vielleicht noch einen?", 0x8FCF5A);
        }
    }

    private static void spins(Player player) {
        if (player.isSleeping() && DrunkServer.state(player).blood >= Intoxication.DRUNK && player.getSleepTimer() >= 60) {
            player.stopSleeping();
            think(player, "Alles dreht sich… Bett, Decke, alles… ich muss…", 0xC8B070);
            DrunkServer.vomit(player);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        StimulantHandler.onBreak(event);
    }

    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        StimulantHandler.onSleep(event);
    }

    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        OpioidHandler.onBreathe(event);
    }

    @SubscribeEvent
    public static void onDrown(LivingDrownEvent event) {
        OpioidHandler.onDrown(event);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        OpioidHandler.onIncomingDamage(event);
    }

    public static void think(Player player, String text, int colour) {
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(text)
            .withStyle(net.minecraft.ChatFormatting.ITALIC).withColor(colour), true);
    }

    public static void award(LivingEntity entity, String path, String criterion) {
        if (!(entity instanceof ServerPlayer player)) return;
        var advancement = player.server.getAdvancements()
            .get(ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "drugs/" + path));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        TestCommand.register(event.getDispatcher());
    }

    // ---- Player Tick & Delayed actions ----

    private record Later(long due, Consumer<Player> then) {}
    private static final Map<UUID, List<Later>> LATER = new HashMap<>();

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LATER.remove(id);
        com.createbrewery.block.club.MicrophoneBlockEntity.ROUTES.remove(id);
        TanCompat.forget(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LATER.clear();
        AFTER_EXPIRY.clear();
        com.createbrewery.block.club.MicrophoneBlockEntity.ROUTES.clear();
        TanCompat.forgetAll();
        com.createbrewery.block.club.SpeakerBlockEntity.clearServerLinks();
    }

    public static void later(Player player, int ticks, Consumer<Player> then) {
        LATER.computeIfAbsent(player.getUUID(), id -> new ArrayList<>())
            .add(new Later(player.level().getGameTime() + ticks, then));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        List<Later> waiting = player.level().isClientSide ? null : LATER.get(player.getUUID());
        if (waiting != null) {
            long now = player.level().getGameTime();
            waiting.removeIf(l -> {
                if (l.due() > now && player.isAlive()) return false;
                if (player.isAlive()) l.then().accept(player);
                return true;
            });
            if (waiting.isEmpty()) LATER.remove(player.getUUID());
        }
        if (!player.level().isClientSide && player.tickCount % 20 == 0) {
            heartTick(player);
            weedBody(player);
            Stimulants.body(player);
            OpioidHandler.body(player);
            Mixes.tick(player);
            habits(player);
            DissociativeHandler.wear(player);
            spins(player);
            TanCompat.tick(player);
        }
    }

    public static void weedBody(Player player) {
        float felt = DrugEffect.felt(player, ModEffects.WEED_HIGH);
        if (felt <= 0f) return;
        com.createbrewery.event.BreweryCommonEvents.reduceDuration(player, ModEffects.HANGOVER, 40);
        com.createbrewery.event.BreweryCommonEvents.reduceDuration(player, ModEffects.GREENING_OUT, 20);
    }

    public static int stomach(Player player, Holder<MobEffect> effect, int ticks) {
        if (player.hasEffect(effect) || !(effect.value() instanceof DrugEffect drug)) return ticks;
        if (player.getFoodData().getSaturationLevel() >= 8f) return ticks + drug.onset();
        if (player.getFoodData().getFoodLevel() <= 6) return ticks - drug.onset() / 3;
        return ticks;
    }

    public static void habits(Player player) {
        DrunkState s = DrunkServer.state(player);
        s.weedHabit = habit(player, s.weedHabit, ModEffects.WEED_HIGH, ModEffects.WEED_HABIT);
        s.cokeHabit = habit(player, s.cokeHabit, ModEffects.COKE_HIGH, ModEffects.COKE_HABIT);
        s.methHabit = habit(player, s.methHabit, ModEffects.TWEAK, ModEffects.METH_HABIT);
    }

    private static float habit(Player player, float level, Holder<MobEffect> high, Holder<MobEffect> shown) {
        if (level <= 0f) return 0f;
        int tier = level >= 0.8f ? 2 : level >= 0.55f ? 1 : level >= 0.3f ? 0 : -1;
        if (tier >= 0) player.addEffect(new MobEffectInstance(shown, 60, tier, false, false, false));
        return player.hasEffect(high) ? level : Math.max(0f, level - 0.0003f);
    }

    private static int amp(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    public static void heartTick(Player player) {
        DrunkState s = DrunkServer.state(player);
        boolean hole = player.hasEffect(ModEffects.K_HOLE);
        float target = Pharmacology.heartLoad(
            amp(player, ModEffects.COKE_HIGH), DrugEffect.strength(player, ModEffects.COKE_HIGH),
            hole ? Math.max(1, amp(player, ModEffects.KETA_HIGH)) : amp(player, ModEffects.KETA_HIGH),
            Math.max(DrugEffect.strength(player, ModEffects.KETA_HIGH), DrugEffect.strength(player, ModEffects.K_HOLE)),
            DrugEffect.felt(player, ModEffects.WEED_HIGH), s.blood,
            player.isSprinting(), player.level().dimensionType().ultraWarm()) + Stimulants.heartLoad(player);
        if (target <= 0f && s.heart <= 0f) return;
        s.heart += (target - s.heart) * (target > s.heart ? 0.2f : 0.05f);
        if (target <= 0f && s.heart < 0.05f) s.heart = 0f;
        if (s.heart >= Pharmacology.HEART_RACING) {
            player.addEffect(new MobEffectInstance(ModEffects.TACHYCARDIA, 60,
                s.heart >= Pharmacology.HEART_STRAINED ? 1 : 0, false, false, true));
        }
        if (player.getRandom().nextFloat() < Pharmacology.heartAttackChance(s.heart)) heartAttack(player);
    }

    public static void heartAttack(Player player) {
        if (player.hasEffect(ModEffects.HEART_ATTACK)) return;
        player.setSprinting(false);
        player.addEffect(new MobEffectInstance(ModEffects.HEART_ATTACK, HEART_ATTACK_TICKS, 0, false, false, true));
        DrugPose.act(player, DrugPose.COLLAPSE, HEART_ATTACK_TICKS);
        player.hurt(heartAttack(player.level()), 6f);
    }

    public static void heartAttackTick(LivingEntity entity, int level) {
        if (!helped(entity)) entity.hurt(heartAttack(entity.level()), 1f);
    }

    // ---- Airway & Choking ----

    public static boolean unconscious(LivingEntity entity) {
        return entity.hasEffect(ModEffects.BLACKOUT) || entity.hasEffect(ModEffects.K_HOLE)
            || entity.hasEffect(ModEffects.HEART_ATTACK) || entity.hasEffect(ModEffects.RESPIRATORY_DEPRESSION);
    }

    public static boolean helped(LivingEntity entity) {
        return !entity.level().getEntitiesOfClass(Player.class, entity.getBoundingBox().inflate(1.5),
            p -> p != entity && p.isCrouching() && !unconscious(p)).isEmpty();
    }

    public static void aspirate(Player player) {
        if (!helped(player)) {
            player.addEffect(new MobEffectInstance(ModEffects.ASPIRATION, ASPIRATION_TICKS, 0, false, false, true));
        }
    }

    public static void aspirationTick(LivingEntity entity, int level) {
        if (entity.getAirSupply() <= 0 && !helped(entity)) {
            entity.hurt(new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ASPIRATION)), 2f);
        }
    }
}
