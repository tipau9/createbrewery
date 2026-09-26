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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/**
 * Koks and Keta, server side. Registered on NeoForge.EVENT_BUS.
 *
 * <p>Koks (stimulant): faster, stronger at mining, no hunger, no sleep, racing heart. Too much -
 * a third line while still high - risks a heart attack, and together with alcohol far more so
 * (the body makes cocaethylene, which is harder on the heart than either). It also masks how
 * drunk you are, so you drink more. Then the crash: tired, slow, grey, hungry.
 *
 * <p>Keta (dissociative): slow motion, the world far away, pain dulled. Too much tips into the
 * K-Loch, where the body barely obeys. With alcohol both depress the body together: blackouts
 * and vomiting become far likelier. Afterwards: dazed.
 *
 * <p>Both together ("CK"): Keta raises heart rate and blood pressure as well, so the heart is
 * strained even at small doses and beats irregularly; Koks masks the dissociation, so it feels
 * clearer than it is and the next dose comes sooner; orientation flips (left and right swap for
 * moments); two powders up the same nose make it bleed; and the comedown is longer and harder.
 *
 * <p>Weed (smoked): relaxed, colours richer, time slower, the munchies. Too much, or on top of
 * alcohol, and the circulation gives up ("greening out"): dizzy, green, throwing up - unpleasant
 * but never deadly on its own. Mixed: with alcohol both hit harder; with Koks the heart works a
 * little harder and paranoia creeps in; with Keta the dissociation deepens and the K-Loch comes
 * a dose sooner. Ibu and weed do not interact.
 *
 * <p>Realism across all of them: every dose comes up, holds and fades (DrugEffect); every
 * further dose feels like less but strains the heart just as much. The heart has one load
 * (Pharmacology#heartLoad) that every stimulant, alcohol on top, sprinting and heat add to:
 * first it races (Herzrasen), then it can give out (Herzinfarkt) - a friend sneaking next to you
 * is doing CPR. Throwing up while out cold (Filmriss, K-Loch, collapse) fills the airway unless
 * someone turns you on your side the same way. Order matters: weed on top of alcohol greens you
 * out, alcohol on top of weed is absorbed more slowly.
 */
public final class DrugServer {
    private DrugServer() {}

    public static final int COKE_TICKS = 3600;
    public static final int KETA_TICKS = 2400;
    public static final int K_HOLE_TICKS = 600;
    /** Doses stack up to this amplifier. */
    private static final int MAX_LEVEL = 3;

    private static final ResourceKey<DamageType> HEART_ATTACK = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "heart_attack"));
    private static final ResourceKey<DamageType> NOSEBLEED = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "nosebleed"));
    private static final ResourceKey<DamageType> ASPIRATION = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "aspiration"));

    public static final int HEART_ATTACK_TICKS = 300;
    /** Lachgas lasts a minute or two in real life: here 15 seconds. */
    public static final int WAH_TICKS = 300;
    public static final int ASPIRATION_TICKS = 200;

    public static final int WEED_TICKS = 4800;
    public static final int GREENING_TICKS = 800;
    public static final int COTTONMOUTH_TICKS = 3600;
    public static final int SNACK_BLISS_TICKS = 600;
    /** Sweets taste like heaven when high (the munchies). */
    public static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> SWEETS = net.minecraft.tags.TagKey.create(
        Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "sweets"));
    /** A joint lasts this many hits (its durability); each hit is one amplifier step of the high. */
    public static final int HITS_PER_JOINT = 6;
    /** Three joints' worth of hits in the body at most. */
    private static final int MAX_HITS = 3 * HITS_PER_JOINT;

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

    /** One hit of a joint: a sixth of it. The high builds hit by hit, and so does the nausea. */
    public static void hit(Player player) {
        MobEffectInstance before = player.getEffect(ModEffects.WEED_HIGH);
        int hits = before == null ? 0 : Math.min(MAX_HITS - 1, before.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(ModEffects.WEED_HIGH, DrugEffect.doseTicks(before, ModEffects.WEED_HIGH, WEED_TICKS), hits, false, false, true));
        DrunkState s = DrunkServer.state(player);
        s.weedHabit = Math.min(1f, s.weedHabit + 0.012f);
        float joints = joints(player);
        // The circulation gives up from the second joint on, and past two and a half for sure.
        float sick = joints >= 2.5f ? 1f : joints > 2f ? 0.45f : joints > 1.5f ? 0.15f : 0f;
        // Erst saufen, dann kiffen: weed on top of alcohol tips it over far more easily.
        if (DrunkServer.state(player).blood >= Intoxication.MERRY) sick = Math.max(sick, 0.15f + 0.1f * joints);
        if (player.getRandom().nextFloat() < sick) greenOut(player);
        smoke(player);
        // The spit dries up: Pappmaul until you drink something.
        player.addEffect(new MobEffectInstance(ModEffects.COTTONMOUTH, COTTONMOUTH_TICKS, 0, false, false, true));
    }

    /** A line goes up the nose, or a joint is smoked (see {@link #hit}). Stacks with what is still active. */
    public static void take(Player player, Kind kind) {
        if (kind == Kind.WEED) {
            hit(player);
            return;
        }
        if (kind == Kind.LSD || kind == Kind.SHROOMS || kind == Kind.MESCALINE || kind == Kind.DMT) {
            Psychedelics.take(player, kind);
            return;
        }
        if (kind == Kind.LACHGAS) {
            // Each balloon only prolongs the high a little; three in a row and the air runs short.
            MobEffectInstance before = player.getEffect(ModEffects.WAH);
            player.addEffect(new MobEffectInstance(ModEffects.WAH, DrugEffect.doseTicks(before, ModEffects.WAH, WAH_TICKS),
                before == null ? 0 : Math.min(MAX_LEVEL, before.getAmplifier() + 1), false, false, true));
            // Every balloon uses up a little vitamin B12.
            DrunkServer.state(player).b12 = Math.min(1f, DrunkServer.state(player).b12 + 0.05f);
            return;
        }
        if (kind == Kind.HEROIN || kind == Kind.XANAX) {
            Opioids.take(player, kind);
            return;
        }
        if (kind == Kind.MDMA || kind == Kind.METH) {
            Stimulants.take(player, kind);
            return;
        }
        Holder<MobEffect> high = switch (kind) {
            case COKE -> ModEffects.COKE_HIGH;
            case KETA -> ModEffects.KETA_HIGH;
            default -> ModEffects.WEED_HIGH;
        };
        int ticks = switch (kind) {
            case COKE -> COKE_TICKS;
            case KETA -> KETA_TICKS;
            default -> WEED_TICKS;
        };
        // Koks on alcohol: the liver makes cocaethylene, which lasts about three times as long.
        if (kind == Kind.COKE && DrunkServer.state(player).blood >= Intoxication.TIPSY) ticks = ticks * 3 / 2;
        // Keta wears the bladder, dose by dose.
        if (kind == Kind.KETA) DrunkServer.state(player).bladder = Math.min(1f, DrunkServer.state(player).bladder + 0.06f);
        MobEffectInstance before = player.getEffect(high);
        int level = before == null ? 0 : Math.min(MAX_LEVEL, before.getAmplifier() + 1);
        player.addEffect(new MobEffectInstance(high, DrugEffect.doseTicks(before, high, ticks), level, false, false, true));
        if (kind == Kind.COKE) {
            DrunkServer.state(player).cokeHabit = Math.min(1f, DrunkServer.state(player).cokeHabit + 0.06f);
            player.removeEffect(ModEffects.COKE_CRASH); // a new line pushes the crash back
            player.removeEffect(ModEffects.CRAVING);    // ...and stills the craving, for now
        }
        // Keta: the K-Loch from the third dose - or the second, with weed deepening it.
        int holeAt = player.hasEffect(ModEffects.WEED_HIGH) ? 1 : 2;
        if (kind == Kind.KETA && level >= holeAt) {
            // Already in the hole: it deepens instead of coming up again.
            MobEffectInstance hole = player.getEffect(ModEffects.K_HOLE);
            player.addEffect(new MobEffectInstance(ModEffects.K_HOLE, DrugEffect.doseTicks(hole, ModEffects.K_HOLE, K_HOLE_TICKS),
                hole == null ? 0 : 1, false, false, true));
        }
        if (player.level() instanceof ServerLevel level1) {
            // The lining gives up: often with the other powder still in there, and line after line.
            float bleed = mixed(player) ? 0.35f : level >= 2 ? 0.08f * (level - 1) : 0f;
            if (player.getRandom().nextFloat() < bleed) nosebleed(player, level1);
            // The sniff itself and its puff of powder come on the client, when the straw reaches the nose (SniffProps).
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
        // A dry throat coughs more.
        if (player.getRandom().nextFloat() < (player.hasEffect(ModEffects.COTTONMOUTH) ? 0.4f : 0.25f)) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.COUGH.get(),
                SoundSource.PLAYERS, 1.0f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        }
    }

    /** Greening out: the circulation gives up for a while. */
    public static void greenOut(Player player) {
        if (!player.hasEffect(ModEffects.GREENING_OUT)) {
            player.addEffect(new MobEffectInstance(ModEffects.GREENING_OUT, GREENING_TICKS, 0, false, false, true));
        }
    }

    private static void nosebleed(Player player, ServerLevel level) {
        player.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(NOSEBLEED)), 1f);
        Vec3 look = player.getLookAngle();
        level.sendParticles(ModParticles.NOSEBLEED.get(), player.getX() + look.x * 0.25, player.getEyeY() - 0.15,
            player.getZ() + look.z * 0.25, 6, 0.03, 0.02, 0.03, 0.0);
    }

    /** Keeps the CK-Mix indicator up while both are active. */
    private static void checkMix(LivingEntity entity) {
        if (mixed(entity)) entity.addEffect(new MobEffectInstance(ModEffects.CK_MIX, 60, 0, false, false, true));
    }

    /** Koks and Keta at once. The heart strain is part of the heart load (Pharmacology#heartLoad). */
    public static void mixTick(LivingEntity entity, int level) {
        if (entity.tickCount % 40 != 0) return;
        // Nosebleeds keep coming back while both are in the system.
        if (entity instanceof Player player && player.level() instanceof ServerLevel level1
            && player.getRandom().nextFloat() < 0.05f) {
            nosebleed(player, level1);
        }
    }

    private static int amp(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    // ---- ticks, called from the effects every 20 ticks ----

    public static void cokeTick(LivingEntity entity, int level) {
        // No appetite while it acts. The heart: see heartTick.
        if (entity instanceof Player player && DrugEffect.strength(entity, ModEffects.COKE_HIGH) > 0.3f) {
            player.getFoodData().setExhaustion(0f);
        }
        checkMix(entity);
    }

    private static final String[] CRAVING = {"Nur noch eine Line…", "Eine noch, dann hör ich auf.", "Wo krieg ich jetzt noch was her?",
        "Ohne ist alles so flach.", "Ich bin so unruhig."};

    /** Gier, every second: now and then the thought of the next line. Another dose ends it - for now. */
    public static void cravingTick(LivingEntity entity, int level) {
        if (entity instanceof Player player && player.getRandom().nextFloat() < 1f / 40f) {
            think(player, CRAVING[player.getRandom().nextInt(CRAVING.length)], 0xB0B0C8);
        }
    }

    public static void crashTick(LivingEntity entity, int level) {
        if (entity instanceof Player player) player.causeFoodExhaustion(0.4f); // ravenous
    }

    public static void ketaTick(LivingEntity entity, int level) {
        checkMix(entity);
        if (!(entity instanceof Player player)) return;
        float blood = DrunkServer.state(player).blood;
        float strength = DrugEffect.strength(player, ModEffects.KETA_HIGH);
        // Keta and alcohol together: the body switches off, and it all comes back up.
        if (blood >= Intoxication.MERRY && player.getRandom().nextFloat() < (0.02f + 0.02f * level) * strength) {
            DrunkServer.blackout(player);
        }
        if (blood >= Intoxication.TIPSY && player.getRandom().nextFloat() < 0.015f * strength) DrunkServer.vomit(player);
    }

    /** Weed, every 20 ticks: the munchies, and alcohol tipping the circulation over. */
    public static void weedTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        float felt = DrugEffect.felt(player, ModEffects.WEED_HIGH);
        player.causeFoodExhaustion(0.2f * felt); // the munchies
        // Giggle fits, about once a minute when properly high - and laughing is contagious.
        boolean company = !player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(8.0),
            p -> p != player).isEmpty();
        if (player.getRandom().nextFloat() < 0.015f * felt * (company ? 2f : 1f)) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GIGGLE.get(),
                SoundSource.PLAYERS, 1.0f, 0.95f + player.getRandom().nextFloat() * 0.1f);
        }
        // Alcohol after weed is gentler than weed after alcohol (see hit), but not harmless; and
        // with two joints in you the nausea can come any time.
        float joints = joints(player);
        float blood = DrunkServer.state(player).blood;
        float sick = (blood >= Intoxication.MERRY ? 0.01f + 0.01f * joints : 0f) + (joints > 1.5f ? 0.01f * joints : 0f);
        if (player.getRandom().nextFloat() < sick) greenOut(player);
    }

    /** Greening out, every 20 ticks: waves of nausea, and it keeps coming back up. */
    public static void greeningTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        float strength = DrugEffect.strength(player, ModEffects.GREENING_OUT);
        player.causeFoodExhaustion(0.5f * strength);
        if (player.getRandom().nextFloat() < 0.12f * strength) DrunkServer.vomit(player);
    }

    /** The munchies: food tastes twice as good and fills more; sweets most of all. */
    @SubscribeEvent
    public static void onFinishEating(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
            && player.hasEffect(ModEffects.WEED_HIGH)
            && event.getItem().getFoodProperties(player) != null) {
            player.getFoodData().eat(2, 0.3f);
            if (event.getItem().is(SWEETS)) sweet(player);
        }
    }

    /**
     * Cake is eaten off the block, not from the hand, and only after this event: the slice itself
     * feeds as usual (extra food here would fill you up before vanilla takes the slice).
     */
    @SubscribeEvent
    public static void onEatCake(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
            || !player.hasEffect(ModEffects.WEED_HIGH) || !player.canEat(false)
            || !(player.level().getBlockState(event.getPos()).getBlock() instanceof net.minecraft.world.level.block.CakeBlock)) {
            return;
        }
        bliss(player);
    }

    /** Sugar when high: fills you up at once, and pure happiness for a while. */
    public static void sweet(Player player) {
        player.getFoodData().eat(2, 0.3f);
        bliss(player);
    }

    private static void bliss(Player player) {
        player.addEffect(new MobEffectInstance(ModEffects.SNACK_BLISS, SNACK_BLISS_TICKS, 0, false, false, true));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /** A drink after smoking: the dry mouth is gone, and it feels amazing. */
    public static void quench(Player player, net.minecraft.world.item.ItemStack drink) {
        if (drink.getUseAnimation() != net.minecraft.world.item.UseAnim.DRINK) return;
        TanCompat.itemDrink(player);
        if (!(drink.getItem() instanceof ElectrolyteItem)) Stimulants.drank(player);
        Stimulants.cool(player);
        if (!player.hasEffect(ModEffects.COTTONMOUTH)) return;
        player.removeEffect(ModEffects.COTTONMOUTH);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0f, 0.8f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 0.6f);
    }

    /** Lachgas, every second: laughing fits; with the air gone from too many balloons, you faint. */
    public static void wahTick(LivingEntity entity, int level) {
        if (!(entity instanceof Player player)) return;
        float strength = DrugEffect.strength(player, ModEffects.WAH);
        if (player.getRandom().nextFloat() < 0.25f * strength) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.GIGGLE.get(),
                SoundSource.PLAYERS, 1.0f, 1.1f + player.getRandom().nextFloat() * 0.2f);
        }
        if (player.getAirSupply() <= 0) DrunkServer.blackout(player);
        // With alcohol: the legs go.
        if (DrunkServer.state(player).blood >= Intoxication.TIPSY) {
            player.addEffect(new MobEffectInstance(ModEffects.STUMBLE, 40, 0, false, false, true));
        }
    }

    /** Lachgas pushes the oxygen out: from the third balloon in a row the air runs down. */
    private static boolean hypoxic(LivingEntity entity) {
        MobEffectInstance wah = entity.getEffect(ModEffects.WAH);
        return wah != null && wah.getAmplifier() >= 2;
    }

    // ---- events ----

    /** Every high ends in something: the crash after Koks, a daze after Keta. */
    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        LivingEntity entity = event.getEntity();
        if (instance == null || entity.level().isClientSide) return;
        Psychedelics.expired(entity, instance);
        Stimulants.expired(entity, instance);
        Mixes.expired(entity, instance);
        // After a CK session everything lasts half again as long.
        float worse = entity.hasEffect(ModEffects.CK_MIX) ? 1.5f : 1f;
        if (instance.is(ModEffects.COKE_HIGH)) {
            entity.addEffect(new MobEffectInstance(ModEffects.COKE_CRASH, (int) ((1200 + 600 * instance.getAmplifier()) * worse), 0));
            // After a binge (more than one line), or on a habit, the craving sets in; longer the more it was.
            boolean hooked = entity instanceof Player p && DrunkServer.state(p).cokeHabit >= 0.3f;
            if (instance.getAmplifier() >= 1 || hooked) {
                entity.addEffect(new MobEffectInstance(ModEffects.CRAVING, 2400 * Math.max(1, instance.getAmplifier()), 0));
            }
        } else if (instance.is(ModEffects.KETA_HIGH) || instance.is(ModEffects.K_HOLE)) {
            entity.addEffect(new MobEffectInstance(ModEffects.DAZED, (int) (1200 * worse), 0));
        } else if (instance.is(ModEffects.HEART_ATTACK) && entity.isAlive()) {
            award(entity, "zweites_leben", "survived");
        } else if (instance.is(ModEffects.EDIBLE_PENDING) && entity instanceof Player player) {
            edibleKicksIn(player, instance.getAmplifier() + 1);
        } else if (instance.is(ModEffects.NALOXONE) && entity.hasEffect(ModEffects.NOD) && entity instanceof Player player) {
            // The naloxon is gone, the heroin is not.
            think(player, "Es… kommt zurück. Warm. Schwer.", 0xC8A060);
        } else if (instance.is(ModEffects.SEIZURE) && entity instanceof Player player) {
            think(player, "Was… war das? Alles tut weh. Ich hab mir auf die Zunge gebissen.", 0xB090D0);
            player.addEffect(new MobEffectInstance(ModEffects.DAZED, 600, 0));
        }
    }

    /** Hits of a joint one brownie is worth: eaten, THC hits about twice as hard. */
    private static final int HITS_PER_BROWNIE = 8;

    /** The brownie (or brownies) arrive, all at once. Two and you green out for sure. */
    public static void edibleKicksIn(Player player, int brownies) {
        MobEffectInstance before = player.getEffect(ModEffects.WEED_HIGH);
        int hits = Math.min(MAX_HITS - 1, (before == null ? -1 : before.getAmplifier()) + HITS_PER_BROWNIE * brownies);
        // Straight to the peak: the come-up already happened, unnoticed, in the stomach.
        DrugEffect weed = (DrugEffect) ModEffects.WEED_HIGH.value();
        player.addEffect(new MobEffectInstance(ModEffects.WEED_HIGH, weed.total() - weed.onset(), hits, false, false, true));
        player.addEffect(new MobEffectInstance(ModEffects.COTTONMOUTH, COTTONMOUTH_TICKS, 0, false, false, true));
        think(player, "Oh. Oh nein. Da ist es. Das ist… viel.", 0x8FCF5A);
        if (joints(player) >= 2.5f || player.getRandom().nextFloat() < 0.3f * (brownies - 1)) greenOut(player);
    }

    /** Waiting for the brownie, every second: halfway there, nothing yet - maybe another? */
    public static void edibleTick(LivingEntity entity, int level) {
        MobEffectInstance pending = entity.getEffect(ModEffects.EDIBLE_PENDING);
        if (entity instanceof Player player && pending != null && pending.getDuration() / 20 == EdibleItem.DELAY / 40) {
            think(player, "Merk nix. Gar nix. …vielleicht noch einen?", 0x8FCF5A);
        }
    }

    /**
     * Drunk in bed, the room spins (positional alcohol nystagmus) - you cannot fall asleep, and
     * it ends with you throwing up. The client turns the view (see DrunkClient).
     */
    private static void spins(Player player) {
        if (player.isSleeping() && DrunkServer.state(player).blood >= Intoxication.DRUNK && player.getSleepTimer() >= 60) {
            player.stopSleeping();
            think(player, "Alles dreht sich… Bett, Decke, alles… ich muss…", 0xC8B070);
            DrunkServer.vomit(player);
        }
    }

    /**
     * Meth: punding - doing the same small thing over and over, and it feels right. Mining one
     * kind of block again and again speeds you up; being made to switch after a long run grates.
     */
    @SubscribeEvent
    public static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide || !player.hasEffect(ModEffects.TWEAK)) return;
        DrunkState s = DrunkServer.state(player);
        net.minecraft.world.level.block.Block block = event.getState().getBlock();
        if (block == s.pundBlock) {
            s.pundStreak++;
            if (s.pundStreak % 6 == 0) {
                player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SPEED, 200, 1, false, false, true));
                think(player, PUND[player.getRandom().nextInt(PUND.length)], 0xA0E0FF);
            }
        } else {
            if (s.pundStreak >= 12) {
                player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN, 200, 0, false, false, true));
                think(player, "Nein, nein, NEIN. Ich war noch nicht fertig. Das war noch nicht fertig.", 0xE08080);
            }
            s.pundBlock = block;
            s.pundStreak = 1;
        }
    }

    private static final String[] PUND = {"Noch einen. Perfekt. Noch einen.", "Genau so. Jeder gleich. Wunderschön.",
        "Ich könnte das ewig machen.", "Ordnung. Endlich Ordnung."};

    /** A thought over the hotbar, from the server. */
    public static void think(Player player, String text, int colour) {
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(text)
            .withStyle(net.minecraft.ChatFormatting.ITALIC).withColor(colour), true);
    }

    /** Grants an "Apotheke" advancement criterion that no vanilla trigger can express. */
    public static void award(LivingEntity entity, String path, String criterion) {
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) return;
        var advancement = player.server.getAdvancements()
            .get(ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "drugs/" + path));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
    }

    private static final ResourceKey<DamageType> BLADDER = ResourceKey.create(Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "keta_bladder"));
    private static final String[] NUMB = {"Meine Zehen kribbeln… schon den ganzen Tag.", "Ich spür meine Finger nicht richtig.",
        "Warum lauf ich so komisch?"};

    /**
     * Every second: what builds up over many sessions. Lachgas uses up vitamin B12 - numb hands
     * and feet, then a clumsy, stumbling walk - which heals over time. Keta wears the bladder:
     * always needing to go, then it burns and bleeds.
     */
    static void wear(Player player) {
        DrunkState s = DrunkServer.state(player);
        if (s.b12 > 0f) {
            s.b12 = Math.max(0f, s.b12 - 0.0002f);
            if (s.b12 >= 0.35f) {
                player.addEffect(new MobEffectInstance(ModEffects.NUMBNESS, 60, s.b12 >= 0.7f ? 1 : 0, false, false, true));
                if (s.b12 >= 0.7f) player.addEffect(new MobEffectInstance(ModEffects.STUMBLE, 40, 0, false, false, false));
                if (player.getRandom().nextFloat() < 1f / 180f) think(player, NUMB[player.getRandom().nextInt(NUMB.length)], 0xA0B8C8);
            }
        }
        if (s.bladder > 0f) {
            s.bladder = Math.max(0f, s.bladder - 0.00005f);
            if (s.bladder >= 0.25f && player.getRandom().nextFloat() < 0.006f * s.bladder) {
                if (s.bladder >= 0.6f) {
                    player.hurt(new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(BLADDER)), 1f);
                    think(player, "Au… das brennt. Und da ist Blut.", 0xD06060);
                } else {
                    think(player, "Ich muss schon wieder… schon wieder?!", 0xC8C890);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        TestCommand.register(event.getDispatcher());
    }

    /** Wide awake on Koks, Meth and Ecstasy: no sleeping. */
    @SubscribeEvent
    public static void onSleep(CanPlayerSleepEvent event) {
        if (event.getEntity().hasEffect(ModEffects.COKE_HIGH) || event.getEntity().hasEffect(ModEffects.TWEAK)
            || event.getEntity().hasEffect(ModEffects.ROLLING)) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    // ---- the heart ----

    /** Server: what happens to a player a moment later - the line goes in once the straw is at the nose. */
    private record Later(long due, java.util.function.Consumer<Player> then) {}
    private static final java.util.Map<java.util.UUID, java.util.List<Later>> LATER = new java.util.HashMap<>();

    public static void later(Player player, int ticks, java.util.function.Consumer<Player> then) {
        LATER.computeIfAbsent(player.getUUID(), id -> new java.util.ArrayList<>())
            .add(new Later(player.level().getGameTime() + ticks, then));
    }

    /** Every tick: what was waiting; every second: the heart load follows what is in the body and what the body is doing. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        java.util.List<Later> waiting = player.level().isClientSide ? null : LATER.get(player.getUUID());
        if (waiting != null) {
            long now = player.level().getGameTime();
            // Dead in the meantime: it never went in.
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
            Opioids.body(player);
            Mixes.tick(player);
            habits(player);
            wear(player);
            spins(player);
            TanCompat.tick(player);
        }
    }

    /**
     * Weed, every second, outside the effect ticks (effects may be swapped here): sprinting is
     * twice the effort for a heavy body, and sitting or crouching down lets a hangover and the
     * nausea pass twice as fast.
     */
    public static void weedBody(Player player) {
        float felt = DrugEffect.felt(player, ModEffects.WEED_HIGH);
        if (felt <= 0f) return;
        if (player.isSprinting()) player.causeFoodExhaustion(0.55f * felt); // about what sprinting costs anyway
        if (player.isPassenger() || player.isShiftKeyDown()) {
            com.createbrewery.event.BreweryCommonEvents.reduceDuration(player, ModEffects.HANGOVER, 20);
            com.createbrewery.event.BreweryCommonEvents.reduceDuration(player, ModEffects.GREENING_OUT, 20);
        }
    }

    /**
     * A first pill or mushroom meets the stomach: right after a meal (saturation still high) it
     * waits under the food and comes up a whole come-up later; on an empty stomach (hungry) it
     * starts sooner. Top-ups are not affected.
     */
    public static int stomach(Player player, Holder<MobEffect> effect, int ticks) {
        if (player.hasEffect(effect) || !(effect.value() instanceof DrugEffect drug)) return ticks;
        if (player.getFoodData().getSaturationLevel() >= 8f) return ticks + drug.onset();
        if (player.getFoodData().getFoodLevel() <= 6) return ticks - drug.onset() / 3;
        return ticks;
    }

    /**
     * Gewöhnung to weed, Koks and meth, every second: shown (both sides read it, see
     * DrugEffect#felt) from a third of the way up, and wearing off while clean of that drug -
     * a full habit in about an hour. The heart takes no discount (heartLoad reads strength).
     */
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
        // Up within seconds, back down over a minute.
        s.heart += (target - s.heart) * (target > s.heart ? 0.2f : 0.05f);
        if (target <= 0f && s.heart < 0.05f) s.heart = 0f;
        if (s.heart >= Pharmacology.HEART_RACING) {
            player.addEffect(new MobEffectInstance(ModEffects.TACHYCARDIA, 60,
                s.heart >= Pharmacology.HEART_STRAINED ? 1 : 0, false, false, true));
        }
        if (player.getRandom().nextFloat() < Pharmacology.heartAttackChance(s.heart)) heartAttack(player);
    }

    /** The heart gives out: you collapse, and without CPR it keeps failing. */
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

    // ---- choking ----

    /** Out cold: a Filmriss, the K-Loch, or collapsed with a failing heart. */
    public static boolean unconscious(LivingEntity entity) {
        return entity.hasEffect(ModEffects.BLACKOUT) || entity.hasEffect(ModEffects.K_HOLE)
            || entity.hasEffect(ModEffects.HEART_ATTACK) || entity.hasEffect(ModEffects.RESPIRATORY_DEPRESSION);
    }

    /** Someone awake is crouched right next to you: CPR, or the recovery position. */
    public static boolean helped(LivingEntity entity) {
        return !entity.level().getEntitiesOfClass(Player.class, entity.getBoundingBox().inflate(1.5),
            p -> p != entity && p.isCrouching() && !unconscious(p)).isEmpty();
    }

    /** Throwing up while out cold: it goes into the airway, unless someone turns you over. */
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

    /** No air gets through while choking - the bubbles run out like under water. */
    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        LivingEntity entity = event.getEntity();
        boolean choking = entity.hasEffect(ModEffects.ASPIRATION);
        // The breath stops more slowly than choking: the bubbles run out over about ten seconds.
        if ((choking || entity.hasEffect(ModEffects.RESPIRATORY_DEPRESSION) || hypoxic(entity)) && !helped(entity)) {
            event.setCanBreathe(false);
            event.setConsumeAirAmount(entity.getAirSupply() > 0 ? (choking ? 5 : 2) : 0);
        }
    }

    /** The choking does its own damage (aspirationTick), not vanilla drowning every tick. */
    @SubscribeEvent
    public static void onDrown(LivingDrownEvent event) {
        if (event.getEntity().hasEffect(ModEffects.ASPIRATION) || event.getEntity().hasEffect(ModEffects.RESPIRATORY_DEPRESSION)
            || hypoxic(event.getEntity())) {
            event.setDrowning(false);
        }
    }

    /** Heroin: the pain barely reaches you. Ecstasy: it hurts less, too. */
    @SubscribeEvent
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity().hasEffect(ModEffects.NOD)) event.setAmount(event.getAmount() * Opioids.painFactor(event.getEntity()));
        if (event.getEntity().hasEffect(ModEffects.ROLLING)) {
            event.setAmount(event.getAmount() * (1f - 0.3f * DrugEffect.felt(event.getEntity(), ModEffects.ROLLING)));
        }
    }
}
