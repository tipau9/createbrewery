package com.createbrewery;

import com.createbrewery.recipe.FermentingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;

/**
 * Modelled directly on Create's own {@code com.simibubi.create.AllRecipeTypes}, decompiled
 * from the resolved create-1.21.1-6.0.10-280-slim.jar to confirm the pattern (there is no
 * "ProcessingRecipeSerializer" class in Create 6 — see task-2-4-report.md). Each enum
 * constant eagerly registers its own serializer and type via a DeferredRegister held in a
 * separate nested class, so referencing them from the enum constructor doesn't race the
 * enclosing enum's own <clinit> (enum constants initialize before textually-later static
 * fields of the same class, which would NPE if the DeferredRegisters lived directly here).
 */
public enum ModRecipeTypes implements IRecipeTypeInfo {
    FERMENTING(FermentingRecipe::new);

    private final ResourceLocation id;
    private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializerObject;
    private final DeferredHolder<RecipeType<?>, RecipeType<?>> typeObject;

    ModRecipeTypes(StandardProcessingRecipe.Factory<?> factory) {
        String name = name().toLowerCase(Locale.ROOT);
        this.id = CreateBrewery.ID(name);
        this.serializerObject = Registers.SERIALIZER_REGISTER.register(name,
            () -> new StandardProcessingRecipe.Serializer<>(factory));
        this.typeObject = Registers.TYPE_REGISTER.register(name, () -> RecipeType.simple(this.id));
    }

    public static void register(IEventBus modEventBus) {
        Registers.SERIALIZER_REGISTER.register(modEventBus);
        Registers.TYPE_REGISTER.register(modEventBus);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializerObject.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
        return (RecipeType<R>) typeObject.get();
    }

    private static class Registers {
        private static final DeferredRegister<RecipeSerializer<?>> SERIALIZER_REGISTER =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, CreateBrewery.MOD_ID);
        private static final DeferredRegister<RecipeType<?>> TYPE_REGISTER =
            DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_TYPE, CreateBrewery.MOD_ID);
    }
}
