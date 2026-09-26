package com.createbrewery.drunk;

import com.createbrewery.CreateBrewery;
import com.createbrewery.drugs.HallucinationEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The hallucinations drawn and animated with GeckoLib (only loaded when it is installed): the
 * shadow person and the crawling bugs. Models, animations and textures under
 * assets/createbrewery/{geo,animations,textures}/entity. The entities themselves stay plain
 * ({@link HallucinationEntity}); GeckoLib only draws them, as "replaced" entities.
 */
final class GeoVisions {
    private GeoVisions() {}

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation VANISH = RawAnimation.begin().thenPlayAndHold("vanish");

    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HallucinationEntity.SHADOW_PERSON.get(),
            context -> new Renderer(context, new Animated("shadow_person", HallucinationEntity.SHADOW_PERSON.get())));
        event.registerEntityRenderer(HallucinationEntity.CRAWLER.get(),
            context -> new Renderer(context, new Animated("crawler", HallucinationEntity.CRAWLER.get())));
    }

    private static final class Renderer extends GeoReplacedEntityRenderer<HallucinationEntity, Animated> {
        Renderer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context, Animated animated) {
            super(context, new DefaultedEntityGeoModel<>(CreateBrewery.ID(animated.name)), animated);
            shadowRadius = 0f;
        }

        // Translucent: the shadow person is not quite solid.
        @Override
        public RenderType getRenderType(Animated animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
            return RenderType.entityTranslucent(texture);
        }
    }

    /** Stands in for a hallucination entity when GeckoLib draws it: one per type, state per entity. */
    private static final class Animated implements GeoReplacedEntity {
        final String name;
        private final EntityType<?> type;
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        Animated(String name, EntityType<?> type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public EntityType<?> getReplacingEntityType() {
            return type;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "main", 3, state -> {
                HallucinationEntity entity = state.getData(DataTickets.ENTITY) instanceof HallucinationEntity e ? e : null;
                if (entity != null && entity.vanishing) return state.setAndContinue(VANISH);
                return state.setAndContinue(entity != null && entity.moving ? WALK : IDLE);
            }));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }
    }
}
