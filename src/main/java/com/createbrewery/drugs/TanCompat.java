package com.createbrewery.drugs;

import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.DrunkState;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.effect.ModEffects;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import toughasnails.api.temperature.TemperatureHelper;
import toughasnails.api.temperature.TemperatureLevel;
import toughasnails.api.thirst.IThirst;
import toughasnails.api.thirst.ThirstHelper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tough As Nails (only touched when it is installed): the drugs reach its body temperature and
 * thirst. Without it the mod's own heat and water model runs alone, as before.
 * <ul>
 *   <li>MDMA and meth: the body heat ({@link DrunkState#heat}) warms the TaN temperature too;
 *       dancing and the rush make you thirsty fast.</li>
 *   <li>Cottonmouth dries you out.</li>
 *   <li>Alcohol: out in the cold you lose heat faster (the warm glow is the skin giving it away).</li>
 *   <li>Drinking with TaN - from the hand too - counts as a drink (water intoxication on MDMA,
 *       cooling off); the electrolyte drink quenches TaN thirst.</li>
 * </ul>
 */
public final class TanCompat {
    private TanCompat() {}

    private static boolean on = ModList.get().isLoaded("toughasnails");
    private static final Map<UUID, Integer> lastThirst = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> lastItemDrink = new ConcurrentHashMap<>();

    /** A player left: their bookkeeping goes with them. */
    public static void forget(UUID player) {
        lastThirst.remove(player);
        lastItemDrink.remove(player);
    }

    public static void forgetAll() {
        lastThirst.clear();
        lastItemDrink.clear();
    }

    /** Common setup: the temperature modifier. */
    public static void init() {
        if (!on) return;
        try {
            TemperatureHelper.registerPlayerTemperatureModifier(TanCompat::temperature);
            LogUtils.getLogger().info("Drug body: Tough As Nails temperature and thirst on");
        } catch (RuntimeException | LinkageError e) {
            broken(e);
        }
    }

    private static void broken(Throwable e) {
        on = false;
        LogUtils.getLogger().warn("Tough As Nails integration unavailable; the drugs keep their own heat and water", e);
    }

    private static TemperatureLevel temperature(Player player, TemperatureLevel level) {
        DrunkState s = DrunkServer.state(player);
        if (s.heat > 0.66f) level = level.increment(2);
        else if (s.heat > 0.33f) level = level.increment(1);
        if (s.blood >= Intoxication.TIPSY && level.ordinal() <= TemperatureLevel.COLD.ordinal()) level = level.decrement(1);
        return level;
    }

    /** Every second, server side. */
    public static void tick(Player player) {
        if (!on) return;
        try {
            thirst(player);
        } catch (RuntimeException | LinkageError e) {
            broken(e);
        }
    }

    private static void thirst(Player player) {
        if (!ThirstHelper.isThirstEnabled()) return;
        IThirst thirst = ThirstHelper.getThirst(player);
        float dry = 0.2f * DrugEffect.felt(player, ModEffects.ROLLING) + 0.1f * DrugEffect.felt(player, ModEffects.TWEAK)
            + (DrunkServer.state(player).heat > 0.5f ? 0.2f : 0f) + (player.hasEffect(ModEffects.COTTONMOUTH) ? 0.25f : 0f);
        if (dry > 0f) thirst.addExhaustion(dry);
        // Thirst went up without a drink item: drunk from the hand.
        Integer before = lastThirst.put(player.getUUID(), thirst.getThirst());
        int itemDrink = lastItemDrink.getOrDefault(player.getUUID(), -100);
        if (before != null && thirst.getThirst() > before && player.tickCount - itemDrink > 30) {
            DrugServer.quench(player, new ItemStack(Items.POTION));
        }
    }

    /** A drink item was finished (DrugServer.quench): not to be counted again from the thirst. */
    static void itemDrink(Player player) {
        lastItemDrink.put(player.getUUID(), player.tickCount);
    }

    /** The electrolyte drink: TaN thirst back up, too. */
    public static void electrolytes(Player player) {
        if (!on) return;
        try {
            if (ThirstHelper.isThirstEnabled()) ThirstHelper.getThirst(player).drink(6, 0.6f);
        } catch (RuntimeException | LinkageError e) {
            broken(e);
        }
    }
}
