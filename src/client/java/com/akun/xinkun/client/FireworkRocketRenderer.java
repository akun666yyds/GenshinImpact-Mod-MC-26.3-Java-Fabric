package com.akun.xinkun.client;

import com.akun.xinkun.entity.projectile.FireworkRocketProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownItemRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 烟花火箭的渲染器。原版投掷物渲染器把物品模型固定在屏幕正上方，火箭头
 * 永远朝上，与真正的飞行方向脱节。这里先在相机平面内摆正贴图，再沿视线
 * 轴滚转，让烟花头指向火箭实际飞行的方向；迎着相机飞来时投影过短，则
 * 保留竖直姿态，避免贴图乱转。
 */
public class FireworkRocketRenderer
        extends EntityRenderer<FireworkRocketProjectile, FireworkRocketRenderer.RenderState> {
    /** 与默认投掷物渲染一致的尺寸。 */
    private static final float SCALE = 1.0F;

    private final ItemModelResolver itemModelResolver;

    public FireworkRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(FireworkRocketProjectile entity, RenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(),
                ItemDisplayContext.GROUND, entity);
        Vec3 velocity = entity.getDeltaMovement();
        state.directionX = (float) velocity.x;
        state.directionY = (float) velocity.y;
        state.directionZ = (float) velocity.z;
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        // 先把贴图摆到相机平面，再绕视线轴滚转，贴图仍然正对玩家。
        poseStack.rotate(camera.orientation);
        Vector3f view = new Vector3f(state.directionX, state.directionY, state.directionZ);
        if (view.lengthSquared() > 1.0E-6F) {
            view.rotate(new Quaternionf(camera.orientation).conjugate());
            float planar = view.x * view.x + view.y * view.y;
            // 只取投影在屏幕上的部分；正对或背对相机时投影太短，保持竖直。
            if (planar > 0.04F * view.lengthSquared()) {
                float roll = (float) -Math.atan2(view.x, view.y);
                poseStack.rotate(Axis.ZP.rotation(roll));
            }
        }
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY,
                state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** 在原版投掷物状态上补充飞行方向。 */
    public static class RenderState extends ThrownItemRenderState {
        public float directionX;
        public float directionY;
        public float directionZ;
    }
}
