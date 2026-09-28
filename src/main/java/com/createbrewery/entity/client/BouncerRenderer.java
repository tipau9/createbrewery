package com.createbrewery.entity.client;

import com.createbrewery.CreateBrewery;
import com.createbrewery.entity.BouncerEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class BouncerRenderer extends HumanoidMobRenderer<BouncerEntity, BouncerModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "bouncer"), "main");
    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "textures/entity/bouncer.png");

    public BouncerRenderer(EntityRendererProvider.Context context) {
        super(context, new BouncerModel(context.bakeLayer(LAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(BouncerEntity entity) {
        return TEXTURE;
    }
}
