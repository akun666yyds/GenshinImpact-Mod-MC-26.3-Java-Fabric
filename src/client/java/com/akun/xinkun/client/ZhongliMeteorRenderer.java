package com.akun.xinkun.client;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.entity.ZhongliMeteorEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;

/**
 * 天星：一坨由基岩碎块拼成的不规则巨岩，下落时整体缓慢翻滚。
 * 为了让九格半径的落点在远处也看得见，实体本身没有碰撞，渲染器按视觉
 * 尺寸放宽了视锥包围盒。
 */
public class ZhongliMeteorRenderer
        extends EntityRenderer<ZhongliMeteorEntity, EntityRenderState> {
    private static final RenderType ROCK = RenderTypes.entityCutout(
            XinkunGenshinMod.id("textures/entity/zhongli/meteor.png"));
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;
    private static final int COLOR = 0xFFFFFFFF;

    public ZhongliMeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    protected AABB getBoundingBoxForCulling(ZhongliMeteorEntity entity, float partialTick) {
        return entity.getBoundingBox().inflate(5.0);
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        float spin = state.ageInTicks * 2.2F;
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(spin));
        poseStack.rotate(Axis.XP.rotationDegrees(16.0F + state.ageInTicks * 1.1F));
        // 主岩体
        cube(poseStack, collector, 0.0F, 0.0F, 0.0F, 2.35F, 2.15F, 2.25F);
        // 四块附着的小岩块，让轮廓不规则
        cube(poseStack, collector, 2.05F, 0.75F, 0.55F, 1.05F, 0.95F, 0.9F);
        cube(poseStack, collector, -1.85F, -0.85F, 1.25F, 0.9F, 0.8F, 0.95F);
        cube(poseStack, collector, 0.55F, 1.85F, -1.95F, 0.95F, 0.85F, 0.8F);
        cube(poseStack, collector, -1.15F, 2.05F, -0.35F, 0.7F, 0.65F, 0.75F);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void cube(PoseStack poseStack, SubmitNodeCollector collector,
                             float ox, float oy, float oz,
                             float hx, float hy, float hz) {
        poseStack.pushPose();
        poseStack.translate(ox, oy, oz);
        collector.submitCustomGeometry(poseStack, ROCK,
                (pose, buffer) -> buildCube(pose, buffer, hx, hy, hz));
        poseStack.popPose();
    }

    private static void buildCube(PoseStack.Pose pose, VertexConsumer buffer,
                                  float hx, float hy, float hz) {
        quad(buffer, pose,
                -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz, hx, -hy, -hz,
                0.0F, 0.0F, -1.0F);
        quad(buffer, pose,
                hx, -hy, hz, hx, hy, hz, -hx, hy, hz, -hx, -hy, hz,
                0.0F, 0.0F, 1.0F);
        quad(buffer, pose,
                -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz, -hx, -hy, -hz,
                -1.0F, 0.0F, 0.0F);
        quad(buffer, pose,
                hx, -hy, -hz, hx, hy, -hz, hx, hy, hz, hx, -hy, hz,
                1.0F, 0.0F, 0.0F);
        quad(buffer, pose,
                -hx, hy, -hz, -hx, hy, hz, hx, hy, hz, hx, hy, -hz,
                0.0F, 1.0F, 0.0F);
        quad(buffer, pose,
                -hx, -hy, hz, -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz,
                0.0F, -1.0F, 0.0F);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        vertex(buffer, pose, x0, y0, z0, 0.0F, 1.0F, nx, ny, nz);
        vertex(buffer, pose, x1, y1, z1, 0.0F, 0.0F, nx, ny, nz);
        vertex(buffer, pose, x2, y2, z2, 1.0F, 0.0F, nx, ny, nz);
        vertex(buffer, pose, x3, y3, z3, 1.0F, 1.0F, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(COLOR)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT_LIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
