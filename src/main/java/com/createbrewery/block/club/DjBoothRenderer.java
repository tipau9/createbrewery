package com.createbrewery.block.club;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class DjBoothRenderer implements BlockEntityRenderer<DjBoothBlockEntity> {

    private final ItemRenderer itemRenderer;

    public DjBoothRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(DjBoothBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(DjBoothBlock.FACING);

        pose.pushPose();
        // Move to centre of the block
        pose.translate(0.5, 0.5, 0.5);

        // Rotate matching blockstate model orientation
        float yRot = switch (facing) {
            case EAST -> -90f;
            case SOUTH -> -180f;
            case WEST -> -270f;
            default -> 0f; // NORTH
        };
        pose.mulPose(Axis.YP.rotationDegrees(yRot));

        // Render Deck A (left turntable platter)
        ItemStack deckA = be.getDeckA();
        if (!deckA.isEmpty()) {
            pose.pushPose();
            // Deck A platter center in model space: X=4.0/16 (offset -0.25 from center), Y=14.2/16 (offset +0.388), Z=8.0/16 (0.0)
            pose.translate(-0.25, 0.395, 0.0);
            // Lie disc flat on the platter surface
            pose.mulPose(Axis.XP.rotationDegrees(90f));

            // Spin vinyl if Deck A is actively playing
            if (be.isPlaying() && be.getActiveDeck() == 1) {
                float spin = (be.getLevel() != null ? be.getLevel().getGameTime() + partialTick : 0f) * 14.0f;
                pose.mulPose(Axis.ZP.rotationDegrees(spin));
            }

            pose.scale(0.42f, 0.42f, 0.42f);
            itemRenderer.renderStatic(deckA, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        // Render Deck B (right turntable platter)
        ItemStack deckB = be.getDeckB();
        if (!deckB.isEmpty()) {
            pose.pushPose();
            // Deck B platter center in model space: X=12.0/16 (offset +0.25 from center), Y=14.2/16 (offset +0.388), Z=8.0/16 (0.0)
            pose.translate(0.25, 0.395, 0.0);
            // Lie disc flat on the platter surface
            pose.mulPose(Axis.XP.rotationDegrees(90f));

            // Spin vinyl if Deck B is actively playing
            if (be.isPlaying() && be.getActiveDeck() == 2) {
                float spin = (be.getLevel() != null ? be.getLevel().getGameTime() + partialTick : 0f) * 14.0f;
                pose.mulPose(Axis.ZP.rotationDegrees(spin));
            }

            pose.scale(0.42f, 0.42f, 0.42f);
            itemRenderer.renderStatic(deckB, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), 0);
            pose.popPose();
        }

        pose.popPose();
    }
}
