package com.createbrewery.ponder;

import com.createbrewery.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;

public class ModPonderScenes {
    public static void register(PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> helper) {
        helper.forComponents(ModBlocks.FERMENTER)
            .addStoryBoard(Create.asResource("basin"), BreweryScenes::fermenter)
            .addStoryBoard(Create.asResource("mechanical_mixer/mixing"), BreweryScenes::brewingHeat);

        helper.forComponents(AllBlocks.BASIN)
            .addStoryBoard(Create.asResource("mechanical_mixer/mixing"), BreweryScenes::brewingHeat);
    }
}
