package com.akun.xinkun.client;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.entity.MoonPhantomEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * 巨型月影的渲染器。月盘永远正对镜头，用“自发光半透明”材质绘制，
 * 因此不会受月光、火把或阴影影响，夜里看上去像一轮真正悬在空中的月亮。
 *
 * <p>贴图是一张 4×2 的月相图集，每一格对应原版月相 0～7；渲染时按实体的
 * {@code phase} 取样，所以同一晚召唤出的月亮形状与天上完全一致。</p>
 */
public class MoonPhantomRenderer extends EntityRenderer<MoonPhantomEntity, MoonPhantomRenderer.MoonRenderState> {
    private static final RenderType MOON_TEXTURE = RenderTypes.entityTranslucentEmissive(
            XinkunGenshinMod.id("textures/entity/moon/moon_phases.png"));
    /** 图集为 4×2 格、每格 256px；留 8px 内边距，避免 mipmap 把相邻月相的像素吸进来。 */
    private static final float UV_PAD_U = 8.0F / 1024.0F;
    private static final float UV_PAD_V = 8.0F / 512.0F;
    private static final float CELL_U = 0.25F;
    private static final float CELL_V = 0.5F;
    private static final int FADE_IN = 12;
    private static final int FADE_OUT = 30;
    /** 天空光 15、方块光 15 的打包值，让月盘不受夜色明暗影响。 */
    private static final int FULL_BRIGHT_LIGHT = 0xF000F0;

    public MoonPhantomRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.shadowStrength = 0.0F;
    }

    public static class MoonRenderState extends EntityRenderState {
        public int phase;
        public float scale;
        public int life;
        public int maxLife;
    }

    @Override
    public MoonRenderState createRenderState() {
        return new MoonRenderState();
    }

    /**
     * 实体碰撞箱只有 0.2 格，直接用它会按小方块做视锥剔除：
     * 月盘中心稍微偏出屏幕时整轮月亮就会消失。这里按视觉半径给出包围盒。
     */
    @Override
    protected AABB getBoundingBoxForCulling(MoonPhantomEntity entity, float partialTick) {
        float scale = Math.max(1.0F, entity.getScale());
        return entity.getBoundingBox().inflate(scale * 0.85);
    }

    @Override
    public void extractRenderState(MoonPhantomEntity entity, MoonRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.phase = entity.getPhase();
        state.scale = entity.getScale();
        state.life = entity.getLife();
        state.maxLife = entity.getMaxLife();
    }

    @Override
    public void submit(MoonRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        float fadeIn = Mth.clamp(state.life / (float) FADE_IN, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((state.maxLife - state.life) / (float) FADE_OUT, 0.0F, 1.0F);
        float alpha = Math.min(fadeIn, fadeOut) * 0.92F;
        if (alpha <= 0.01F) {
            return;
        }

        int phase = Math.floorMod(state.phase, 8);
        int column = phase % 4;
        int row = phase / 4;
        float u0 = column * CELL_U + UV_PAD_U;
        float u1 = u0 + CELL_U - UV_PAD_U * 2.0F;
        float v0 = row * CELL_V + UV_PAD_V;
        float v1 = v0 + CELL_V - UV_PAD_V * 2.0F;

        poseStack.pushPose();
        // 月盘正对镜头，无论从哪个方向看都是一轮完整的圆月。
        poseStack.rotate(camera.orientation);
        float scale = Math.max(1.0F, state.scale);
        // 先画两层更大更淡的同贴图光晕，月盘边缘因此有一圈溢出的月华。
        this.submitDisc(poseStack, collector, scale * 1.62F, alpha * 0.14F, u0, u1, v0, v1);
        this.submitDisc(poseStack, collector, scale * 1.28F, alpha * 0.30F, u0, u1, v0, v1);
        this.submitDisc(poseStack, collector, scale, alpha, u0, u1, v0, v1);
        poseStack.popPose();

        super.submit(state, poseStack, collector, camera);
    }

    private void submitDisc(PoseStack poseStack, SubmitNodeCollector collector,
                            float scale, float alpha, float u0, float u1, float v0, float v1) {
        if (alpha <= 0.01F) {
            return;
        }
        int argb = ((int) (alpha * 255.0F) << 24) | 0xFFFFFF;
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        collector.submitCustomGeometry(poseStack, MOON_TEXTURE, (pose, buffer) -> {
            // 逆时针：左上 → 左下 → 右下 → 右上，法线朝镜头，避免被背面剔除。
            vertex(buffer, pose, -0.5F, 0.5F, u0, v0, argb);
            vertex(buffer, pose, -0.5F, -0.5F, u0, v1, argb);
            vertex(buffer, pose, 0.5F, -0.5F, u1, v1, argb);
            vertex(buffer, pose, 0.5F, 0.5F, u1, v0, argb);
        });
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
                               float x, float y, float u, float v, int argb) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT_LIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
