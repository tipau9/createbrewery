package com.createbrewery;

import com.createbrewery.drugs.RollJointRecipe;
import com.createbrewery.drunk.DrunkServer;
import com.createbrewery.drunk.Intoxication;
import com.createbrewery.item.BeerDrinkItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Wine and spirits come off their lines; a bottle is drunk in sips; tobacco stretches a joint. */
@GameTestHolder(CreateBrewery.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpiritGameTests {

    @GameTest(template = "platform")
    public static void everySpiritHasItsWholeLine(GameTestHelper helper) {
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        String[] ids = {
            "compacting/grape_must", "fermenting/fermenting_wine", "mixing/distilling_brouillis", "mixing/distilling_eau_de_vie",
            "fermenting/aging_cognac", "fermenting/fermenting_wash", "mixing/distilling_low_wines", "mixing/distilling_new_make",
            "fermenting/aging_whiskey", "mixing/grain_mash", "fermenting/fermenting_grain_wash", "mixing/distilling_raw_korn",
            "mixing/distilling_doppelkorn", "mixing/potato_mash", "fermenting/fermenting_potato_wash", "mixing/rectifying_neutral_spirit",
            "mixing/vodka", "mixing/gin", "mixing/molasses", "fermenting/fermenting_rum_wash", "mixing/distilling_raw_rum",
            "fermenting/aging_rum", "roasting_agave", "milling/agave_pulp", "mixing/agave_juice", "fermenting/fermenting_agave_wash",
            "mixing/distilling_ordinario", "mixing/distilling_tequila",
        };
        for (String id : ids) if (recipes.byKey(CreateBrewery.ID(id)).isEmpty()) helper.fail("missing recipe " + id);
        Object[][] bottles = {
            {"filling/bottling_wine", ModItems.WINE_BOTTLE.get()}, {"filling/bottling_cognac", ModItems.COGNAC_BOTTLE.get()},
            {"filling/bottling_whiskey", ModItems.WHISKEY_BOTTLE.get()}, {"filling/bottling_doppelkorn", ModItems.DOPPELKORN_BOTTLE.get()},
            {"filling/bottling_vodka", ModItems.VODKA_BOTTLE.get()}, {"filling/bottling_gin", ModItems.GIN_BOTTLE.get()},
            {"filling/bottling_rum", ModItems.RUM_BOTTLE.get()}, {"filling/bottling_tequila", ModItems.TEQUILA_BOTTLE.get()},
        };
        for (Object[] b : bottles) {
            RecipeHolder<?> r = recipes.byKey(CreateBrewery.ID((String) b[0])).orElse(null);
            if (r == null) helper.fail("missing recipe " + b[0]);
            Item made = r.value().getResultItem(helper.getLevel().registryAccess()).getItem();
            if (made != b[1]) helper.fail(b[0] + " makes " + made);
        }
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void aBottleIsDrunkInSips(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack beer = new ItemStack(ModItems.BEER_BOTTLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, beer);
        for (int sip = 1; sip < beer.getMaxDamage(); sip++) {
            ItemStack left = beer.finishUsingItem(helper.getLevel(), player);
            if (left != beer || beer.getDamageValue() != sip) helper.fail("sip " + sip + " left " + left + " at damage " + beer.getDamageValue());
        }
        ItemStack last = beer.finishUsingItem(helper.getLevel(), player);
        if (!last.is(Items.GLASS_BOTTLE)) helper.fail("the last sip left " + last);
        // A whole beer is still one beer's worth; a whole bottle of vodka about eleven.
        float drunk = DrunkServer.state(player).total();
        if (Math.abs(drunk - Intoxication.PER_BEER) > 0.01f) helper.fail("a whole beer brought " + drunk);
        float vodka = BeerDrinkItem.perSip(0.7f, 0.40f, 16) * 16;
        if (vodka < 3.0f || vodka > 3.5f) helper.fail("a bottle of vodka is " + vodka + " per mille");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void tobaccoStretchesTheWeed(GameTestHelper helper) {
        if (Math.abs(RollJointRecipe.mixed(1.2f, 2) - 0.4f) > 1e-5 || RollJointRecipe.mixed(0.9f, 0) != 0.9f) {
            helper.fail("mixing is not a plain spread");
        }
        helper.succeed();
    }
}
