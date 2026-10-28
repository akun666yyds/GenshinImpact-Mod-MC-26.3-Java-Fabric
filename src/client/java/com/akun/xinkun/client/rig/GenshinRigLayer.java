package com.akun.xinkun.client.rig;

import com.akun.xinkun.client.GenshinNpcRenderState;
import com.akun.xinkun.client.PlayerNpcModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 用角色自己的骨骼网格替换玩家皮肤模型。
 *
 * <p>每个部件都按四骨骼线性混合蒙皮在 CPU 上算出顶点，再以退化四边形提交
 * （26.3 的实体管线固定为 QUADS 图元）。骨骼角度直接来自 {@link PlayerNpcModel}
 * 当前帧的原版动作，所以走路、挥击、拉弓、潜行、游泳、钟离/纳西妲的技能姿势
 * 全部沿用原版模板，不会出现另一套手工动画。</p>
 */
public final class GenshinRigLayer extends RenderLayer<GenshinNpcRenderState, PlayerNpcModel> {
    private final RigModel rig;

    public GenshinRigLayer(RenderLayerParent<GenshinNpcRenderState, PlayerNpcModel> parent, RigModel rig) {
        super(parent);
        this.rig = rig;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                       GenshinNpcRenderState state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        RigPose pose = this.getParentModel().rigPose();
        if (pose == null) {
            return;
        }
        int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(state.hasRedOverlay));
        float[] matrices = pose.snapshot();
        float blink = state.blink;
        // 布料与长发随风摆动的相位；口型由骨架层平滑后的开合度驱动。
        float windTime = state.ageInTicks * 0.06F;
        float mouthOpen = pose.mouthOpen();

        poseStack.pushPose();
        RigModel.applyModelTransform(poseStack);
        float gripLeft = state.gripLeft;
        float gripRight = state.gripRight;
        for (RigModel.Part part : this.rig.parts) {
            if (part.mouthPatch && mouthOpen <= 0.01F) {
                continue;
            }
            collector.submitCustomGeometry(poseStack, part.renderType,
                    (poseState, buffer) -> emit(this.rig, poseState, buffer, part, matrices, overlay,
                            lightCoords, blink, windTime, mouthOpen, gripLeft, gripRight));
        }
        poseStack.popPose();
    }

    private static void emit(RigModel rig, PoseStack.Pose poseState, VertexConsumer buffer, RigModel.Part part,
                             float[] matrices, int overlay, int lightCoords, float blink,
                             float windTime, float mouthOpen, float gripLeft, float gripRight) {
        Matrix4f poseMatrix = poseState.pose();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        int color = part.mouthPatch
                ? packColor(part.tint, Math.clamp(mouthOpen * 1.6F, 0.0F, 1.0F))
                : packColor(part.tint);
        for (int index = 0; index < part.indices.length; index += 3) {
            int a = part.indices[index];
            int b = part.indices[index + 1];
            int c = part.indices[index + 2];
            // 三角形 -> 退化四边形（a, b, c, c）
            vertex(rig, part, matrices, poseState, poseMatrix, buffer, a, color, overlay, lightCoords,
                    blink, windTime, mouthOpen, gripLeft, gripRight, position, normal);
            vertex(rig, part, matrices, poseState, poseMatrix, buffer, b, color, overlay, lightCoords,
                    blink, windTime, mouthOpen, gripLeft, gripRight, position, normal);
            vertex(rig, part, matrices, poseState, poseMatrix, buffer, c, color, overlay, lightCoords,
                    blink, windTime, mouthOpen, gripLeft, gripRight, position, normal);
            vertex(rig, part, matrices, poseState, poseMatrix, buffer, c, color, overlay, lightCoords,
                    blink, windTime, mouthOpen, gripLeft, gripRight, position, normal);
        }
    }

    private static void vertex(RigModel rig, RigModel.Part part, float[] matrices, PoseStack.Pose poseState,
                               Matrix4f poseMatrix, VertexConsumer buffer, int index, int color,
                               int overlay, int lightCoords, float blink, float windTime, float mouthOpen,
                               float gripLeft, float gripRight, Vector3f position, Vector3f normal) {
        float x = part.positions[index * 3];
        float y = part.positions[index * 3 + 1];
        float z = part.positions[index * 3 + 2];
        if (part.eyes && blink > 0.001F) {
            // 眨眼：眼睛网格沿竖直方向向中心压扁，和皮肤模型的眨眼贴图同一节奏。
            y = part.eyeCenterY + (y - part.eyeCenterY) * (1.0F - 0.92F * blink);
        }
        if (part.sway != null) {
            float weight = part.sway[index];
            if (weight > 0.001F) {
                // 两个不同频率的正弦叠加，避免布料像钟摆一样机械地来回。
                float phase = part.swayPhase;
                float wave = Mth.sin(windTime + phase + y * 0.45F) * 0.6F
                        + Mth.sin(windTime * 1.7F + phase * 1.3F) * 0.25F;
                x += wave * weight * part.swayScale;
                z += Mth.cos(windTime * 0.8F + x * 0.35F + phase) * 0.25F * weight * part.swayScale;
            }
        }
        if (part.mouthPatch) {
            // 口型贴片：以中心为轴上下张开，闭嘴时由调用方跳过提交。
            y = part.mouthCenterY + (y - part.mouthCenterY) * (0.15F + 0.85F * mouthOpen);
        }
        float nx = part.normals[index * 3];
        float ny = part.normals[index * 3 + 1];
        float nz = part.normals[index * 3 + 2];

        // 握持：手上有物品时，手指按各自的权重绕指根轴向掌心卷起。
        // 权重沿手指方向递增，指尖卷得最多，于是手指弯成握住柄的弧线，
        // 而不是整只手僵硬地贴在武器上。空手时 grip 为 0，完全保持绑定姿势。
        float grip = 0.0F;
        float[] gripAxis = null;
        float[] gripPivot = null;
        if (part.gripLeft != null && gripLeft > 0.001F) {
            float weight = part.gripLeft[index] * gripLeft;
            if (weight > grip) {
                grip = weight;
                gripAxis = rig.gripAxis[0];
                gripPivot = rig.gripPivot[0];
            }
        }
        if (part.gripRight != null && gripRight > 0.001F) {
            float weight = part.gripRight[index] * gripRight;
            if (weight > grip) {
                grip = weight;
                gripAxis = rig.gripAxis[1];
                gripPivot = rig.gripPivot[1];
            }
        }
        if (grip > 0.001F) {
            float angle = RigModel.GRIP_ANGLE * grip;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            float ax = gripAxis[0];
            float ay = gripAxis[1];
            float az = gripAxis[2];
            float rx = x - gripPivot[0];
            float ry = y - gripPivot[1];
            float rz = z - gripPivot[2];
            float dot = rx * ax + ry * ay + rz * az;
            float one = 1.0F - cos;
            float cx = ay * rz - az * ry;
            float cy = az * rx - ax * rz;
            float cz = ax * ry - ay * rx;
            x = gripPivot[0] + rx * cos + cx * sin + ax * dot * one;
            y = gripPivot[1] + ry * cos + cy * sin + ay * dot * one;
            z = gripPivot[2] + rz * cos + cz * sin + az * dot * one;
            float ndot = nx * ax + ny * ay + nz * az;
            float ncx = ay * nz - az * ny;
            float ncy = az * nx - ax * nz;
            float ncz = ax * ny - ay * nx;
            nx = nx * cos + ncx * sin + ax * ndot * one;
            ny = ny * cos + ncy * sin + ay * ndot * one;
            nz = nz * cos + ncz * sin + az * ndot * one;
        }

        float px = 0.0F;
        float py = 0.0F;
        float pz = 0.0F;
        float vx = 0.0F;
        float vy = 0.0F;
        float vz = 0.0F;
        for (int slot = 0; slot < 4; slot++) {
            float weight = part.weights[index * 4 + slot];
            if (weight == 0.0F) {
                continue;
            }
            int joint = part.joints[index * 4 + slot] & 0xFF;
            int offset = joint * 16;
            px += weight * (matrices[offset] * x + matrices[offset + 4] * y + matrices[offset + 8] * z + matrices[offset + 12]);
            py += weight * (matrices[offset + 1] * x + matrices[offset + 5] * y + matrices[offset + 9] * z + matrices[offset + 13]);
            pz += weight * (matrices[offset + 2] * x + matrices[offset + 6] * y + matrices[offset + 10] * z + matrices[offset + 14]);
            vx += weight * (matrices[offset] * nx + matrices[offset + 4] * ny + matrices[offset + 8] * nz);
            vy += weight * (matrices[offset + 1] * nx + matrices[offset + 5] * ny + matrices[offset + 9] * nz);
            vz += weight * (matrices[offset + 2] * nx + matrices[offset + 6] * ny + matrices[offset + 10] * nz);
        }

        poseMatrix.transformPosition(px, py, pz, position);
        poseState.transformNormal(vx, vy, vz, normal);
        if (normal.lengthSquared() < 1.0E-8F) {
            normal.set(0.0F, 1.0F, 0.0F);
        } else {
            normal.normalize();
        }

        float u = part.uvs[index * 2];
        float v = part.uvs[index * 2 + 1];
        buffer.addVertex(position.x, position.y, position.z, color, u, v, overlay, lightCoords,
                normal.x, normal.y, normal.z);
    }

    private static int packColor(float[] tint) {
        return packColor(tint, 1.0F);
    }

    private static int packColor(float[] tint, float alphaScale) {
        int alpha = Math.round(Math.clamp(tint[3] * alphaScale, 0.0F, 1.0F) * 255.0F);
        int red = Math.round(Math.clamp(tint[0], 0.0F, 1.0F) * 255.0F);
        int green = Math.round(Math.clamp(tint[1], 0.0F, 1.0F) * 255.0F);
        int blue = Math.round(Math.clamp(tint[2], 0.0F, 1.0F) * 255.0F);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
