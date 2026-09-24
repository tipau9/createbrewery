package com.createbrewery;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Tag-key constants used by task 8 (Step 3). Attached to items/blocks inline via Registrate's
 * own {@code Builder.tag(TagKey...)} (confirmed by decompiling
 * com.tterrag.registrate.builders.AbstractBuilder#tag — it accumulates every {@code .tag(...)}
 * call into a Registrate-managed tags provider automatically), so no hand-written
 * TagsProvider/ModTagsProvider class is needed.
 */
public class ModTags {
    /** createbrewery's own "this is a beer item" tag. */
    public static final TagKey<Item> BEER =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "beer"));

    /**
     * The optional cross-mod `selling_bin` economy hook (see task-8 brief Step 3): ships the tag
     * only, no Java dependency on a "brewery" mod being present or absent.
     */
    public static final TagKey<Item> BREWERY_BEER =
        TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("brewery", "beer"));
}
