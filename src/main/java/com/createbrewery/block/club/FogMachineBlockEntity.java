package com.createbrewery.block.club;

import com.createbrewery.ModBlockEntities;
import com.createbrewery.particle.ModParticles;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

public class FogMachineBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public static final int TANK_CAPACITY = 3000;
    protected SmartFluidTankBehaviour tank;
    private int tickCount = 0;

    public FogMachineBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 1, TANK_CAPACITY, true)
            .allowInsertion().forbidExtraction();
        // Fog juice is water; lava or beer would make for a different kind of party.
        tank.getPrimaryHandler().setValidator(f -> f.is(FluidTags.WATER));
        behaviours.add(tank);
    }

    public SmartFluidTankBehaviour getTank() {
        return tank;
    }

    public int getFluidAmount() {
        return tank == null ? 0 : tank.getPrimaryHandler().getFluidAmount();
    }

    /** Fills only if all of {@code amount} fits, so a bucket is never half-emptied into the void. */
    public boolean fillFluid(int amount) {
        if (tank == null) return false;
        FluidStack water = new FluidStack(Fluids.WATER, amount);
        if (tank.getPrimaryHandler().fill(water, IFluidHandler.FluidAction.SIMULATE) < amount) return false;
        tank.getPrimaryHandler().fill(water, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        tickCount++;

        BlockState state = getBlockState();
        if (!state.getValue(FogMachineBlock.EMITTING)) return;

        int currentFluid = getFluidAmount();
        if (currentFluid <= 0) return;

        Direction facing = state.getValue(FogMachineBlock.FACING);

        // Client particle spawning
        if (level != null && level.isClientSide) {
            // A steady jet, with a thicker blast when the music drops.
            int puffs = 2 + (ClubStates.at(level, worldPosition, null).dropLevel > 0.3f ? 2 : 0);
            for (int i = 0; i < puffs; i++) {
                // Just past the brass nozzle; the particle draws its puff a little above this point (see FogParticle).
                double nozzleX = worldPosition.getX() + 0.5 + facing.getStepX() * 0.85;
                double nozzleY = worldPosition.getY() + 0.01;
                double nozzleZ = worldPosition.getZ() + 0.5 + facing.getStepZ() * 0.85;

                // Shot out fast in a narrow cone; drag slows it over ~9 blocks.
                double speed = 0.34 + level.random.nextDouble() * 0.14;
                double velX = facing.getStepX() * speed + (level.random.nextDouble() - 0.5) * 0.08;
                double velY = level.random.nextDouble() * 0.03;
                double velZ = facing.getStepZ() * speed + (level.random.nextDouble() - 0.5) * 0.08;

                level.addParticle(ModParticles.FOG.get(), nozzleX, nozzleY, nozzleZ, velX, velY, velZ);
            }
        } else if (level != null) {
            // Server fluid consumption
            if (tickCount % 12 == 0) {
                tank.getPrimaryHandler().drain(1, IFluidHandler.FluidAction.EXECUTE);
                setChanged();
            }
            // Ambient steam hiss sound
            if (tickCount % 50 == 0) {
                level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f, 0.65f);
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("createbrewery.goggles.fog_machine.header"));
        containedFluidTooltip(tooltip, isPlayerSneaking, getTank().getCapability());
        return true;
    }
}
