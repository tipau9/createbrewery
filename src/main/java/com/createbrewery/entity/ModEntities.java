package com.createbrewery.entity;

import com.createbrewery.CreateBrewery;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    private ModEntities() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, CreateBrewery.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BouncerEntity>> BOUNCER = ENTITIES.register("bouncer",
        () -> EntityType.Builder.<BouncerEntity>of(BouncerEntity::new, MobCategory.CREATURE)
            .sized(0.7f, 2.0f)
            .clientTrackingRange(10)
            .build("bouncer"));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(ModEntities::registerAttributes);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BOUNCER.get(), BouncerEntity.createAttributes().build());
    }
}
