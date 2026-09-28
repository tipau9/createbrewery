package com.createbrewery.entity.client;

import com.createbrewery.entity.BouncerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

public class BouncerModel extends HumanoidModel<BouncerEntity> {
    public BouncerModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(BouncerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (entity.isArmsCrossed()) {
            this.rightArm.xRot = -0.75f;
            this.rightArm.yRot = -0.4f;
            this.rightArm.zRot = 0.35f;
            this.leftArm.xRot = -0.75f;
            this.leftArm.yRot = 0.4f;
            this.leftArm.zRot = -0.35f;
        }
    }
}
