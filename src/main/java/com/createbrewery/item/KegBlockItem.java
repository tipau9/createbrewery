package com.createbrewery.item;

import com.createbrewery.block.KegBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public class KegBlockItem extends BlockItem {

    public KegBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        CustomData customData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        boolean hasFluid = false;
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (context.registries() != null) {
                // Check possible tank keys used by SmartFluidTankBehaviour
                for (String key : new String[]{"TankContent", "Tank", "Tanks"}) {
                    if (tag.contains(key)) {
                        CompoundTag tankTag = tag.getCompound(key);
                        FluidStack fluid = FluidStack.parseOptional(context.registries(), tankTag);
                        if (!fluid.isEmpty()) {
                            tooltip.add(Component.translatable("createbrewery.keg.stored_fluid",
                                fluid.getHoverName(), fluid.getAmount(), KegBlockEntity.TANK_CAPACITY)
                                .withStyle(ChatFormatting.AQUA));
                            hasFluid = true;
                            break;
                        }
                    }
                }
            }
        }

        if (!hasFluid) {
            tooltip.add(Component.translatable("createbrewery.keg.capacity_hint", KegBlockEntity.TANK_CAPACITY)
                .withStyle(ChatFormatting.GRAY));
        }
    }
}
