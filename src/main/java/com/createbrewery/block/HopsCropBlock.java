package com.createbrewery.block;

import com.createbrewery.ModItems;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;

public class HopsCropBlock extends CropBlock {
    public HopsCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.HOP_CONES.get();
    }
}
