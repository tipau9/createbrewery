package com.createbrewery;

import com.createbrewery.drugs.DrugServer;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import com.createbrewery.item.IbuprofenItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What a player's drug state does when they die, log out, drink milk or use a totem; and the saves it must survive. */
@GameTestHolder(CreateBrewery.MOD_ID)
@PrefixGameTestTemplate(false)
public class LifecycleGameTests {

    @GameTest(template = "platform")
    public static void totemClearsWhatKeepsKillingYou(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DrunkState s = DrunkServer.state(player);
        s.blood = Intoxication.POISONING + 2f;
        s.stomach = 1f;
        s.heart = 3f;
        s.heat = 1.8f;
        s.water = 2f;
        NeoForge.EVENT_BUS.post(new LivingUseTotemEvent(player, player.damageSources().generic(),
            new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING), InteractionHand.MAIN_HAND));
        helper.assertTrue(s.blood < Intoxication.POISONING, "still poisoned: " + s.blood);
        helper.assertTrue(s.stomach == 0f && s.heart == 0f && s.heat == 0f && s.water == 0f, "totem left heart/heat/stomach/water");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void deathKeepsWhatTheBodyGotUsedTo(GameTestHelper helper) {
        Player old = helper.makeMockPlayer(GameType.SURVIVAL);
        Player fresh = helper.makeMockPlayer(GameType.SURVIVAL);
        DrunkState o = DrunkServer.state(old);
        o.blood = 2f;
        o.tolerance = 0.5f;
        o.dependence = 0.6f;
        o.benzo = 0.4f;
        o.cokeHabit = 0.3f;
        o.heat = 1f;
        DrunkServer.onClone(new PlayerEvent.Clone(fresh, old, true));
        DrunkState f = DrunkServer.state(fresh);
        helper.assertTrue(f.tolerance == 0.5f && f.dependence == 0.6f && f.benzo == 0.4f && f.cokeHabit == 0.3f,
            "dependence/habits lost on death");
        helper.assertTrue(f.blood == 0f && f.heat == 0f, "blood or heat came back from the dead");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void stateRoundTripsThroughItsCodec(GameTestHelper helper) {
        DrunkState s = new DrunkState();
        s.heat = 0.8f;
        s.water = 1.2f;
        s.awake = 900;
        helper.assertTrue(!s.isEmpty(), "a body with only heat counts as empty and would not be saved");
        DrunkState back = DrunkState.CODEC.parse(NbtOps.INSTANCE, DrunkState.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow()).getOrThrow();
        helper.assertTrue(back.heat == 0.8f && back.water == 1.2f && back.awake == 900, "heat/water/awake lost in the codec");
        // An old save has no "body" at all.
        DrunkState old = DrunkState.CODEC.parse(NbtOps.INSTANCE, oldSave()).getOrThrow();
        helper.assertTrue(old.blood == 1f && old.heat == 0f, "old save no longer loads");
        helper.succeed();
    }

    private static CompoundTag oldSave() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("blood", 1f);
        tag.putFloat("stomach", 0f);
        tag.putFloat("peak", 1f);
        return tag;
    }

    @GameTest(template = "platform")
    public static void milkDoesNotCureADrug(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(ModEffects.COKE_HIGH, 2000, 0));
        player.addEffect(new MobEffectInstance(ModEffects.COMEDOWN_PENDING, 2000, 0));
        player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200, 0));
        player.removeEffectsCuredBy(EffectCures.MILK);
        helper.assertTrue(player.hasEffect(ModEffects.COKE_HIGH), "milk cured the high");
        helper.assertTrue(player.hasEffect(ModEffects.COMEDOWN_PENDING), "milk cleared the pending comedown");
        helper.assertTrue(!player.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "milk no longer cures vanilla poison");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void painkillerAmplifierIsCapped(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack pills = new ItemStack(ModItems.IBUPROFEN.get());
        for (int i = 0; i < 300; i++) pills.getItem().finishUsingItem(pills, player.level(), player);
        MobEffectInstance effect = player.getEffect(ModEffects.PAINKILLER);
        helper.assertTrue(effect != null && effect.getAmplifier() <= IbuprofenItem.MAX_PILLS, "amplifier " + (effect == null ? -1 : effect.getAmplifier()));
        // The amplifier is saved as an unsigned byte: this used to throw.
        player.saveWithoutId(new CompoundTag());
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void cokeOnAlcoholStillComesUpAtOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DrunkServer.state(player).blood = Intoxication.TIPSY + 0.5f;
        DrugServer.take(player, DrugServer.Kind.COKE);
        MobEffectInstance high = player.getEffect(ModEffects.COKE_HIGH);
        helper.assertTrue(high != null && high.getDuration() <= DrugServer.COKE_TICKS,
            "the dose was stretched past its own length: the come-up would start 90 s late");
        helper.succeed();
    }

    private static final java.util.concurrent.atomic.AtomicInteger EXPIRED = new java.util.concurrent.atomic.AtomicInteger();

    static {
        NeoForge.EVENT_BUS.addListener((MobEffectEvent.Expired e) -> {
            if (e.getEffectInstance() != null && e.getEffectInstance().is(ModEffects.COKE_HIGH)) EXPIRED.incrementAndGet();
        });
    }

    /** Mock players do not tick their effects; a pig does. The crash added from the Expired handler must not make vanilla fire it twice. */
    @GameTest(template = "platform")
    public static void anExpiredHighEndsOnce(GameTestHelper helper) {
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 1);
        EXPIRED.set(0);
        pig.addEffect(new MobEffectInstance(ModEffects.COKE_HIGH, 1, 0));
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(EXPIRED.get() == 1, "Expired fired " + EXPIRED.get() + " times");
            helper.assertTrue(!pig.hasEffect(ModEffects.COKE_HIGH), "the expired high is stuck in the effect map");
            helper.assertTrue(pig.hasEffect(ModEffects.COKE_CRASH), "no crash after the high");
            helper.succeed();
        });
    }

    @GameTest(template = "platform")
    public static void whatWaitedForAPlayerIsGoneWhenTheyLogOut(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        boolean[] ran = {false};
        DrugServer.later(player, 1, p -> ran[0] = true);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        helper.runAfterDelay(3, () -> {
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
            helper.assertTrue(!ran[0], "a dose queued before logout still landed");
            helper.succeed();
        });
    }
}
