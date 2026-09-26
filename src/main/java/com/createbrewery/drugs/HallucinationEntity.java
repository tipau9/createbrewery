package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Something only a tripping player sees (see {@link Hallucinations}): made on that client, never
 * added to any world, never saved. Drawn with GeckoLib when it is installed; the types are
 * registered either way, so the registries match between client and server.
 */
public class HallucinationEntity extends Entity {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CreateBrewery.MOD_ID);

    /** Tall, black, faceless: at the edge of the view, gone when looked at. */
    public static final DeferredHolder<EntityType<?>, EntityType<HallucinationEntity>> SHADOW_PERSON = TYPES.register("shadow_person",
        () -> EntityType.Builder.<HallucinationEntity>of(HallucinationEntity::new, MobCategory.MISC).sized(0.8f, 2.9f)
            .noSummon().noSave().clientTrackingRange(0).build("shadow_person"));
    /** A little beetle scuttling over the ground, fleeing when looked at. */
    public static final DeferredHolder<EntityType<?>, EntityType<HallucinationEntity>> CRAWLER = TYPES.register("crawler",
        () -> EntityType.Builder.<HallucinationEntity>of(HallucinationEntity::new, MobCategory.MISC).sized(0.2f, 0.1f)
            .noSummon().noSave().clientTrackingRange(0).build("crawler"));

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    /** Looked at: it is going (the renderer plays it fading away). */
    public boolean vanishing;
    /** Moving (the renderer plays it walking). */
    public boolean moving;

    public HallucinationEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
