package com.createbrewery;

import com.createbrewery.ponder.BreweryPonderPlugin;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class CreateBreweryClient {
    public static void onClientSetup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new BreweryPonderPlugin());
    }
}
