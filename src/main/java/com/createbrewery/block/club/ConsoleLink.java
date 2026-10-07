package com.createbrewery.block.club;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Linking effects to a console with the same item that links fixtures (tag {@code LinkedConsole}). */
final class ConsoleLink {
    private ConsoleLink() {}

    /** Blocks that take the console's link item: fixtures, the five effects (the cold spark is a CO2 jet) and the hazer. */
    static boolean linkable(Block block) {
        return block instanceof FixtureBlock || block instanceof StrobeLightBlock || block instanceof LaserProjectorBlock
            || block instanceof FogMachineBlock || block instanceof Co2JetBlock || block instanceof HazerBlock;
    }

    static boolean holdsLink(ItemStack stack) {
        return FixtureBlock.linkOf(stack).isPresent();
    }

    /**
     * Server: links the effect at {@code pos} to the console {@code stack} names. Used again on an
     * effect already linked to that console, it moves the effect on to the next group.
     */
    static boolean applyLink(Level level, BlockPos pos, ItemStack stack) {
        Optional<BlockPos> console = FixtureBlock.linkOf(stack);
        BlockEntity be = level.getBlockEntity(pos);
        if (console.isEmpty() || !(be instanceof ConsoleLinked linked)) return false;
        if (!console.get().closerThan(pos, FixtureBlock.MAX_LINK) || !level.isLoaded(console.get())
            || !(level.getBlockEntity(console.get()) instanceof DmxConsoleBlockEntity)) return false;
        ConsoleLinkData data = linked.consoleLink();
        if (console.get().equals(data.console)) {
            setGroup(be, data, data.group + 1);
        } else {
            data.console = console.get().immutable();
            setGroup(be, data, 0);
        }
        return true;
    }

    /** Server: puts the effect in {@code group} (wrapping round) and sends it to the clients. */
    static void setGroup(BlockEntity be, ConsoleLinkData data, int group) {
        data.group = Math.floorMod(group, DmxProgram.GROUPS);
        be.setChanged();
        be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
        if (be instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart) smart.sendData();
    }

    /** The shared {@code useItemOn} hook: call it first, and return its result when {@link #holdsLink} is true. */
    static ItemInteractionResult use(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (!level.isClientSide) {
            boolean ok = applyLink(level, pos, stack);
            int group = level.getBlockEntity(pos) instanceof ConsoleLinked l ? l.consoleLink().group + 1 : 1;
            player.displayClientMessage(ok ? Component.translatable("createbrewery.dmx.effect_linked", group)
                : Component.translatable("createbrewery.dmx.too_far", FixtureBlock.MAX_LINK), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** {@code setPlacedBy} hook: an effect placed from a linked stack comes up linked (group 1). */
    static void onPlaced(Level level, BlockPos pos, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !holdsLink(stack)) return;
        if (!applyLink(level, pos, stack) && placer instanceof Player player) {
            player.displayClientMessage(Component.translatable("createbrewery.dmx.too_far", FixtureBlock.MAX_LINK), true);
        }
    }
}
