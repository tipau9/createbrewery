package com.createbrewery.data;

import com.createbrewery.CreateBrewery;
import net.minecraft.core.HolderLookup;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * Task-8 Step 3 (composting half): makes spent_grain, green_malt and hop_cones usable in a
 * composter. As of 1.21.1 this is not a tag but a NeoForge "data map" — confirmed via javap on
 * neoforge-21.1.228-universal.jar: {@code NeoForgeDataMaps.COMPOSTABLES} is a
 * {@code DataMapType<Item, Compostable>}, populated at datagen time through a
 * {@link DataMapProvider} subclass (there is no runtime/codec registration step needed, unlike
 * the loot modifiers in {@link ModLootModifiers} — data maps are pure datapack content).
 */
public class ModDataMaps {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModDataMaps::gatherData);
    }

    private static void gatherData(GatherDataEvent event) {
        event.createProvider((output, lookup) -> new DataMapProvider(output, lookup) {
            @Override
            protected void gather(HolderLookup.Provider provider) {
                var compostables = builder(NeoForgeDataMaps.COMPOSTABLES);
                compostables.add(CreateBrewery.ID("spent_grain"), new Compostable(0.3F), false);
                compostables.add(CreateBrewery.ID("green_malt"), new Compostable(0.5F), false);
                compostables.add(CreateBrewery.ID("hop_cones"), new Compostable(0.5F), false);
            }
        });
    }
}
