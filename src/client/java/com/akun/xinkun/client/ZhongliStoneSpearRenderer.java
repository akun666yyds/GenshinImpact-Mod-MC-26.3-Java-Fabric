package com.akun.xinkun.client;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.entity.ZhongliStoneSpearEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * 岩枪的几何渲染：一根四棱石柱，下端收成枪尖，整体带一层极淡的金色辉光，
 * 夜里也看得清落点。坐标与实体的碰撞盒一致——原点在枪身正中，枪尖在下方两格半。
 */
public class ZhongliStoneSpearRenderer
        extends EntityRenderer<ZhongliStoneSpearEntity, EntityRenderState> {
    private static final RenderType STONE = RenderTypes.entityCutout(
            XinkunGenshinMod.id("textures/entity/zhongli/stone_spear.png"));
    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(
            XinkunGenshinMod.id("textures/entity/zhongli/stone_spear.png"));
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final int STONE_COLOR = 0xFFFFFFFF;
    private static final int GLOW_COLOR = 0x46E8C46A;

    private static final float HALF = 0.19F;
    private static final float SHAFT_BOTTOM = -0.6F;
    private static final float SHAFT_TOP = 2.5F;

    public ZhongliStoneSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, GLOW,
                (pose, buffer) -> buildSpear(pose, buffer, HALF + 0.035F,
                        SHAFT_BOTTOM - 0.05F, SHAFT_TOP + 0.06F, GLOW_COLOR));
        collector.submitCustomGeometry(poseStack, STONE,
                (pose, buffer) -> buildSpear(pose, buffer, HALF, SHAFT_BOTTOM, SHAFT_TOP, STONE_COLOR));
        super.submit(state, poseStack, collector, camera);
    }

    private static void buildSpear(PoseStack.Pose pose, VertexConsumer buffer,
                                   float half, float bottom, float top, int color) {
        // 四棱柱枪身
        quad(buffer, pose,
                -half, bottom, -half, -half, top, -half,
                half, top, -half, half, bottom, -half,
                0.0F, 0.0F, -1.0F, color);
        quad(buffer, pose,
                half, bottom, half, half, top, half,
                -half, top, half, -half, bottom, half,
                0.0F, 0.0F, 1.0F, color);
        quad(buffer, pose,
                -half, bottom, half, -half, top, half,
                -half, top, -half, -half, bottom, -half,
                -1.0F, 0.0F, 0.0F, color);
        quad(buffer, pose,
                half, bottom, -half, half, top, -half,
                half, top, half, half, bottom, half,
                1.0F, 0.0F, 0.0F, color);

        // 向下的枪尖
        float apexY = bottom - 1.9F;
        triangle(buffer, pose,
                -half, bottom, -half, half, bottom, -half, 0.0F, apexY, 0.0F,
                0.0F, -0.707F, -0.707F, color);
        triangle(buffer, pose,
                half, bottom, half, -half, bottom, half, 0.0F, apexY, 0.0F,
                0.0F, -0.707F, 0.707F, color);
        triangle(buffer, pose,
                -half, bottom, half, -half, bottom, -half, 0.0F, apexY, 0.0F,
                -0.707F, -0.707F, 0.0F, color);
        triangle(buffer, pose,
                half, bottom, -half, half, bottom, half, 0.0F, apexY, 0.0F,
                0.707F, -0.707F, 0.0F, color);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz, int color) {
        vertex(buffer, pose, x0, y0, z0, 0.0F, 1.0F, nx, ny, nz, color);
        vertex(buffer, pose, x1, y1, z1, 0.0F, 0.0F, nx, ny, nz, color);
        vertex(buffer, pose, x2, y2, z2, 1.0F, 0.0F, nx, ny, nz, color);
        vertex(buffer, pose, x3, y3, z3, 1.0F, 1.0F, nx, ny, nz, color);
    }

    private static void triangle(VertexConsumer buffer, PoseStack.Pose pose,
                                 float x0, float y0, float z0,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float nx, float ny, float nz, int color) {
        vertex(buffer, pose, x0, y0, z0, 0.0F, 1.0F, nx, ny, nz, color);
        vertex(buffer, pose, x1, y1, z1, 1.0F, 1.0F, nx, ny, nz, color);
        vertex(buffer, pose, x2, y2, z2, 0.5F, 0.0F, nx, ny, nz, color);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int color) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT_LIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
