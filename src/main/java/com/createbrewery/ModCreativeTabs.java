package com.createbrewery;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateBrewery.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
        CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.createbrewery"))
            .icon(() -> new ItemStack(ModItems.BEER_BOTTLE.get()))
            .displayItems((parameters, output) -> {
                // Brauerei-Maschinen & Landwirtschaft
                output.accept(ModBlocks.FERMENTER.get());
                output.accept(ModItems.BARLEY_SEEDS.get());
                output.accept(ModItems.BARLEY.get());
                output.accept(ModItems.GREEN_MALT.get());
                output.accept(ModItems.MALT.get());
                output.accept(ModItems.GRIST.get());
                output.accept(ModItems.SPENT_GRAIN.get());
                output.accept(ModItems.HOP_CONES.get());
                output.accept(ModItems.YEAST.get());
                output.accept(ModItems.EMPTY_CAN.get());

                // Getränke & Edibles
                output.accept(ModItems.BEER_BOTTLE.get());
                output.accept(ModItems.SEALED_CAN.get());
                output.accept(ModItems.SPACE_BROWNIE.get());
                output.accept(ModItems.ELECTROLYTE.get());

                // Apotheke & Medizin
                output.accept(ModItems.IBUPROFEN.get());
                output.accept(ModItems.NALOXON.get());

                // Substanzen & Zubehör
                output.accept(ModItems.JOINT.get());
                output.accept(ModItems.BALLOON.get());
                output.accept(ModItems.LACHGAS_BALLOON.get());
                output.accept(ModItems.MAGIC_MUSHROOM.get());
                output.accept(ModItems.LSD.get());
                output.accept(ModItems.PEYOTE.get());
                output.accept(ModItems.DMT.get());
                output.accept(ModItems.MDMA.get());
                output.accept(ModItems.KOKS.get());
                output.accept(ModItems.METH.get());
                output.accept(ModItems.KETA.get());
                output.accept(ModItems.HEROIN.get());
                output.accept(ModItems.XANAX.get());
            })
            .build()
        );

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
