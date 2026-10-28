package com.akun.xinkun.client;

import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * 水灵共用的模型基类：只保留恼鬼的悬浮运动，外形、骨骼和动作全部重做。
 */
public abstract class WaterSpiritModel extends EntityModel<WaterSpiritRenderState>
        implements ArmedModel<WaterSpiritRenderState> {
    protected static final float ROOT_Y = -1.5F;

    protected WaterSpiritModel(ModelPart root) {
        super(root);
    }

    protected static float radians(float degrees) {
        return degrees * (float) (Math.PI / 180.0);
    }

    protected static float wave(float ticks, float speed, float amplitude) {
        return Mth.sin(ticks * speed) * amplitude;
    }

    protected static void applyLook(WaterSpiritRenderState state, ModelPart head, float yawFactor, float pitchFactor) {
        head.yRot = radians(state.yRot) * yawFactor;
        head.xRot = radians(state.xRot) * pitchFactor;
    }
}
