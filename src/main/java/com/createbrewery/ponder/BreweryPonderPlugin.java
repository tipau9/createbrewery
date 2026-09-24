package com.createbrewery.ponder;

import com.createbrewery.CreateBrewery;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

public class BreweryPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreateBrewery.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> entryHelper = helper.withKeyFunction(DeferredHolder::getId);
        ModPonderScenes.register(entryHelper);
    }
}
