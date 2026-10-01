package com.createbrewery.block;

import com.createbrewery.ModBlockEntities;
import com.createbrewery.particle.ModParticles;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public class BeerTapBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public static final int TANK_CAPACITY = 2000;
    protected SmartFluidTankBehaviour tank;

    public BeerTapBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 1, TANK_CAPACITY, true)
            .allowInsertion().allowExtraction();
        // Pipes push whatever they carry; only beer may come out of the tap as beer.
        tank.getPrimaryHandler().setValidator(f -> f.getFluid().isSame(com.createbrewery.ModFluids.BEER.getSource()));
        behaviours.add(tank);
    }

    public SmartFluidTankBehaviour getTank() {
        return tank;
    }

    public KegBlockEntity findConnectedKeg() {
        if (level == null) return null;
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos below = worldPosition.below(dy);
            if (level.getBlockEntity(below) instanceof KegBlockEntity keg) {
                return keg;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && !level.isClientSide && tank != null) {
            int space = TANK_CAPACITY - tank.getPrimaryHandler().getFluidAmount();
            if (space > 0) {
                KegBlockEntity keg = findConnectedKeg();
                if (keg != null) {
                    var kegHandler = keg.getTank().getPrimaryHandler();
                    if (kegHandler.getFluidAmount() > 0 && kegHandler.getFluid().getFluid().isSame(com.createbrewery.ModFluids.BEER.getSource())) {
                        net.neoforged.neoforge.fluids.FluidStack drained = kegHandler.drain(Math.min(space, 100), IFluidHandler.FluidAction.EXECUTE);
                        if (!drained.isEmpty()) {
                            tank.getPrimaryHandler().fill(drained, IFluidHandler.FluidAction.EXECUTE);
                            setChanged();
                            keg.notifyUpdate();
                        }
                    }
                }
            }
        }
    }

    /** True if {@code amount} is there; only the server actually drains it (the client copy resyncs). */
    public boolean dispenseBeer(int amount) {
        if (tank != null && tank.getPrimaryHandler().getFluidAmount() >= amount) {
            if (level != null && !level.isClientSide) {
                tank.getPrimaryHandler().drain(amount, IFluidHandler.FluidAction.EXECUTE);
                setChanged();
            }
            return true;
        }

        // Direct draw fallback from connected Keg underneath
        KegBlockEntity keg = findConnectedKeg();
        if (keg != null) {
            var kegHandler = keg.getTank().getPrimaryHandler();
            if (kegHandler.getFluidAmount() >= amount && kegHandler.getFluid().getFluid().isSame(com.createbrewery.ModFluids.BEER.getSource())) {
                if (level != null && !level.isClientSide) {
                    kegHandler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
                    keg.notifyUpdate();
                }
                return true;
            }
        }

        return false;
    }

    public void spawnFoamParticles(Level level, BlockPos pos, Direction facing) {
        double px = pos.getX() + 0.5 + facing.getStepX() * 0.25;
        double py = pos.getY() + 0.35;
        double pz = pos.getZ() + 0.5 + facing.getStepZ() * 0.25;

        for (int i = 0; i < 6; i++) {
            level.addParticle(ModParticles.BEER_FOAM.get(), px, py, pz,
                (level.random.nextDouble() - 0.5) * 0.04,
                0.02 + level.random.nextDouble() * 0.03,
                (level.random.nextDouble() - 0.5) * 0.04);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("createbrewery.goggles.beer_tap.header"));
        containedFluidTooltip(tooltip, isPlayerSneaking, getTank().getCapability());
        KegBlockEntity keg = findConnectedKeg();
        if (keg != null) {
            var handler = keg.getTank().getPrimaryHandler();
            tooltip.add(Component.translatable("createbrewery.goggles.beer_tap.connected_keg",
                handler.getFluidAmount(), KegBlockEntity.TANK_CAPACITY).withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        return true;
    }
}

