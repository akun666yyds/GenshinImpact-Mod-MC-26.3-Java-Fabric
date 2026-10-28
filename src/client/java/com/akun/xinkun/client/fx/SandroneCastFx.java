package com.akun.xinkun.client.fx;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.client.GenshinNpcRenderState;
import com.akun.xinkun.entity.SandroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 桑多涅施法时的两件视觉表现：
 *
 * <ol>
 *   <li><b>连续冰霜光束</b>——不是粒子流，而是三层同心光柱（宽而淡的外晕、淡蓝中层、
 *       雪白核心），沿着从双掌到真实落点的方向绘制。光束截面贴图横向就是
 *       “白心 → 浅蓝 → 透明”的渐变，纵向贴图缓慢滚动，看上去像信标光束一样连续。</li>
 *   <li><b>金色机械傀儡</b>——紧贴她身后从地面升起的一台金色金属机器人，
 *       双手向前撑着一块半透明青色全息屏。生成与消失都按权重平滑缩放，
 *       不会凭空闪现。</li>
 * </ol>
 *
 * <p>光束落点来自服务端同步的真实命中位置（方块或实体），所以光束永远刚好停在
 * 命中处；每一层的透明度都乘以施法权重，停火时整条光束自然淡出。</p>
 */
public final class SandroneCastFx {
    private static final Identifier BEAM_TEXTURE =
            XinkunGenshinMod.id("textures/entity/fx/frost_beam.png");
    private static final Identifier GOLD_TEXTURE =
            XinkunGenshinMod.id("textures/entity/fx/robot_gold.png");
    private static final Identifier HOLO_TEXTURE =
            XinkunGenshinMod.id("textures/entity/fx/holo_screen.png");

    // 光束与全息屏都用实体半透明材质逐顶点满亮度，既自发光又不会被夜色压暗；
    // 26.3 的 entity translucent emissive 管线在自定义几何上不参与 OIT 合成，
    // 这里统一使用与机械傀儡相同的半透明管线，保证任何视角都能看见。
    private static final RenderType BEAM_TYPE = RenderTypes.entityTranslucent(BEAM_TEXTURE);
    private static final RenderType GOLD_TYPE = RenderTypes.entityTranslucent(GOLD_TEXTURE);
    private static final RenderType GLOW_TYPE = RenderTypes.entityTranslucent(HOLO_TEXTURE);
    /** 天空光 15、方块光 15：光束与全息屏不受夜色影响。 */
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final Map<Integer, Float> CAST_WEIGHT = new HashMap<>();
    private static final Map<Integer, Float> ROBOT_WEIGHT = new HashMap<>();
    private static final Map<Integer, Vec3> BEAM_TARGET = new HashMap<>();

    private SandroneCastFx() {
    }

    /** 在实体渲染状态抽取阶段更新平滑权重与光束落点。每帧调用一次。 */
    public static void extract(GenshinNpcRenderState state, SandroneEntity entity) {
        int id = entity.getId();
        boolean active = entity.isCastingFrost();
        state.castWeight = approach(CAST_WEIGHT, id, active ? 1.0F : 0.0F, 0.22F);
        state.robotWeight = approach(ROBOT_WEIGHT, id, active ? 1.0F : 0.0F, 0.11F);

        Vec3 raw = entity.getBeamTarget().subtract(entity.position());
        Vec3 previous = BEAM_TARGET.get(id);
        // 敌人每两刻被重新瞄准一次，落点小步平滑，避免光束末端抖动。
        Vec3 smoothed = previous == null ? raw : previous.lerp(raw, 0.45);
        BEAM_TARGET.put(id, smoothed);
        state.beamX = (float) smoothed.x;
        state.beamY = (float) smoothed.y;
        state.beamZ = (float) smoothed.z;
    }

    private static float approach(Map<Integer, Float> map, int id, float target, float rate) {
        Float stored = map.get(id);
        float value = stored == null ? target : stored + (target - stored) * rate;
        if (Math.abs(value - target) < 0.004F) {
            value = target;
        }
        map.put(id, value);
        return value;
    }

    /** 在角色本体提交之后绘制傀儡与光束。{@code lift} 是角色此刻的离地高度。 */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector,
                              GenshinNpcRenderState state, float lift) {
        if (state.robotWeight > 0.004F) {
            submitRobot(poseStack, collector, state, lift);
        }
        if (state.castWeight > 0.004F) {
            submitBeam(poseStack, collector, state, lift);
        }
    }

    // ---------------------------------------------------------------- 光束

    private static void submitBeam(PoseStack poseStack, SubmitNodeCollector collector,
                                   GenshinNpcRenderState state, float lift) {
        float yaw = state.yRot * Mth.DEG_TO_RAD;
        // 坐标已经处在“离地后的角色空间”里（poseStack 已上移 lift），所以手掌高度
        // 直接取 1.58 即可；落点则要减掉 lift，才能贴在真实的世界坐标上。
        Vec3 from = new Vec3(-Mth.sin(yaw) * 0.62F, 1.58F, Mth.cos(yaw) * 0.62F);
        Vec3 to = new Vec3(state.beamX, state.beamY - lift, state.beamZ);
        Vec3 axis = to.subtract(from);
        double distance = axis.length();
        if (distance < 0.8 || distance > 64.0) {
            return;
        }
        Vec3 unit = axis.scale(1.0 / distance);
        Vec3 side = unit.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
        }
        side = side.normalize();
        Vec3 up = side.cross(unit).normalize();

        float weight = state.castWeight;
        float pulse = 0.92F + 0.08F * Mth.sin(state.ageInTicks * 1.7F);
        // 贴图本身已经是“白心 + 厚实的浅蓝主体”，顶点色只需给出整体亮度与外晕。
        int haze = pack(0.22F * weight * pulse, 0xB6E4FF);
        int body = pack(1.0F * weight * pulse, 0xFFFFFF);

        Vec3 start = from;
        Vec3 end = to;
        Vec3 right = side;
        Vec3 top = up;
        collector.submitCustomGeometry(poseStack, BEAM_TYPE, (pose, buffer) -> {
            // 三片互成 60° 的宽外晕，给光束一圈柔和的寒气辉光。
            for (int i = 0; i < 3; i++) {
                double angle = i * Math.PI / 3.0;
                ribbon(pose, buffer, start, end, offsetDir(right, top, angle), 0.40F, haze);
            }
            // 三片主体光带（双面），中心是贴图自带的白核，外圈浅蓝。
            for (int i = 0; i < 3; i++) {
                double angle = i * Math.PI / 3.0;
                ribbon(pose, buffer, start, end, offsetDir(right, top, angle), 0.24F, body);
            }
        });
    }

    private static Vec3 offsetDir(Vec3 right, Vec3 top, double angle) {
        return right.scale(Math.cos(angle)).add(top.scale(Math.sin(angle)));
    }

    private static int pack(float alpha, int rgb) {
        int a = Math.min(255, Math.round(alpha * 255.0F));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * 双面光带：同一片几何正反各画一次，无论渲染类型是否开启背面剔除都能看见。
     * 贴图横向就是“白心 → 浅蓝 → 透明”的截面渐变，所以一片光带本身自带白核。
     */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vec3 from, Vec3 to,
                               Vec3 offDir, float halfWidth, int argb) {
        Vec3 o = offDir.scale(halfWidth);
        Vec3 a = from.add(o);
        Vec3 b = from.subtract(o);
        Vec3 c = to.subtract(o);
        Vec3 d = to.add(o);
        quad(pose, buffer, a, b, c, d, argb);
        quad(pose, buffer, d, c, b, a, argb);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int argb) {
        vertex(buffer, pose, p0, 0.0F, 0.0F, argb);
        vertex(buffer, pose, p1, 1.0F, 0.0F, argb);
        vertex(buffer, pose, p2, 1.0F, 1.0F, argb);
        vertex(buffer, pose, p3, 0.0F, 1.0F, argb);
    }

    // ------------------------------------------------------------ 金色傀儡

    private static void submitRobot(PoseStack poseStack, SubmitNodeCollector collector,
                                    GenshinNpcRenderState state, float lift) {
        float grow = smoothstep(state.robotWeight);
        int light = state.lightCoords;
        int gold = 0xFFFFFFFF;
        int goldDark = 0xFF9A9488;
        int joint = 0xFF5E5A54;

        poseStack.pushPose();
        // 傀儡站在地面上，不跟着桑多涅浮空；位置紧贴她身后（本地 -Z）。
        poseStack.translate(0.0F, -lift - (1.0F - grow) * 0.55F, 0.0F);
        poseStack.rotateDegrees(Axis.YP, -state.bodyRot);
        poseStack.scale(grow, grow, grow);

        collector.submitCustomGeometry(poseStack, GOLD_TYPE, (pose, buffer) -> {
            // 双腿：细长的小腿 + 明显的膝关节，站姿比之前更挺拔。
            box(pose, buffer, -0.27F, 0.09F, -1.04F, -0.13F, 0.58F, -0.84F, goldDark, light);
            box(pose, buffer, 0.13F, 0.09F, -1.04F, 0.27F, 0.58F, -0.84F, goldDark, light);
            box(pose, buffer, -0.29F, 0.56F, -1.06F, -0.11F, 0.68F, -0.82F, joint, light);
            box(pose, buffer, 0.11F, 0.56F, -1.06F, 0.29F, 0.68F, -0.82F, joint, light);
            box(pose, buffer, -0.28F, 0.66F, -1.02F, -0.12F, 1.06F, -0.86F, gold, light);
            box(pose, buffer, 0.12F, 0.66F, -1.02F, 0.28F, 1.06F, -0.86F, gold, light);
            // 脚掌
            box(pose, buffer, -0.31F, 0.0F, -1.08F, -0.09F, 0.10F, -0.74F, gold, light);
            box(pose, buffer, 0.09F, 0.0F, -1.08F, 0.31F, 0.10F, -0.74F, gold, light);
            // 髋部与收窄的腰
            box(pose, buffer, -0.25F, 1.04F, -1.04F, 0.25F, 1.16F, -0.84F, joint, light);
            box(pose, buffer, -0.16F, 1.14F, -1.00F, 0.16F, 1.28F, -0.88F, gold, light);
            // 胸甲：上宽下窄，背后加一块散热背板
            box(pose, buffer, -0.31F, 1.26F, -1.06F, 0.31F, 1.72F, -0.82F, gold, light);
            box(pose, buffer, -0.22F, 1.36F, -1.10F, 0.22F, 1.66F, -1.04F, goldDark, light);
            box(pose, buffer, -0.10F, 1.72F, -1.00F, 0.10F, 1.79F, -0.88F, joint, light);
            // 肩甲与上臂
            box(pose, buffer, -0.45F, 1.54F, -1.06F, -0.29F, 1.74F, -0.80F, gold, light);
            box(pose, buffer, 0.29F, 1.54F, -1.06F, 0.45F, 1.74F, -0.80F, gold, light);
            box(pose, buffer, -0.42F, 1.26F, -1.02F, -0.30F, 1.58F, -0.86F, goldDark, light);
            box(pose, buffer, 0.30F, 1.26F, -1.02F, 0.42F, 1.58F, -0.86F, goldDark, light);
            // 前伸的小臂：把手掌送到全息屏两侧
            box(pose, buffer, -0.46F, 1.30F, -0.92F, -0.34F, 1.44F, -0.50F, gold, light);
            box(pose, buffer, 0.34F, 1.30F, -0.92F, 0.46F, 1.44F, -0.50F, gold, light);
            box(pose, buffer, -0.52F, 1.24F, -0.60F, -0.32F, 1.50F, -0.44F, joint, light);
            box(pose, buffer, 0.32F, 1.24F, -0.60F, 0.52F, 1.50F, -0.44F, joint, light);
            // 头部：略窄的机械头 + 顶冠
            box(pose, buffer, -0.15F, 1.80F, -1.00F, 0.15F, 2.04F, -0.84F, gold, light);
            box(pose, buffer, -0.17F, 2.00F, -1.02F, 0.17F, 2.07F, -0.82F, goldDark, light);
            box(pose, buffer, -0.05F, 2.07F, -0.98F, 0.05F, 2.20F, -0.86F, gold, light);
            // 全息屏金属边框
            box(pose, buffer, -0.62F, 1.86F, -0.50F, 0.62F, 1.93F, -0.41F, gold, light);
            box(pose, buffer, -0.62F, 1.08F, -0.50F, 0.62F, 1.15F, -0.41F, gold, light);
            box(pose, buffer, -0.62F, 1.15F, -0.50F, -0.55F, 1.86F, -0.41F, gold, light);
            box(pose, buffer, 0.55F, 1.15F, -0.50F, 0.62F, 1.86F, -0.41F, gold, light);
        });

        // 面甲眼缝、胸口核心与背板指示灯：满亮度青色，像真的在运转。
        int glow = (Math.round(Math.min(1.0F, state.robotWeight) * 235.0F) << 24) | 0x00A8F0FF;
        collector.submitCustomGeometry(poseStack, GLOW_TYPE, (pose, buffer) -> {
            box(pose, buffer, -0.11F, 1.86F, -0.855F, 0.11F, 1.91F, -0.845F, glow, FULL_BRIGHT);
            box(pose, buffer, -0.08F, 1.44F, -1.115F, 0.08F, 1.58F, -1.105F, glow, FULL_BRIGHT);
            box(pose, buffer, -0.17F, 1.40F, -1.115F, -0.12F, 1.62F, -1.105F, glow, FULL_BRIGHT);
            box(pose, buffer, 0.12F, 1.40F, -1.115F, 0.17F, 1.62F, -1.105F, glow, FULL_BRIGHT);
        });

        // 半透明机械全息屏：一块有厚度的薄板，双面都能看见，贴在傀儡双手之间。
        float alpha = 0.88F * state.robotWeight;
        int panel = (Math.round(alpha * 255.0F) << 24) | 0x00FFFFFF;
        collector.submitCustomGeometry(poseStack, GLOW_TYPE, (pose, buffer) -> {
            box(pose, buffer, -0.545F, 1.16F, -0.472F, 0.545F, 1.855F, -0.452F, panel, FULL_BRIGHT);
        });

        poseStack.popPose();
    }

    private static float smoothstep(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    // ---------------------------------------------------------------- 几何

    /** 轴对齐立方体的六个面，UV 使用整张贴图，每块装甲自然带一圈面板缝。 */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            int argb, int light) {
        float u0 = 0.02F;
        float u1 = 0.98F;
        // +Z
        face(pose, buffer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                u0, u1, argb, light, 0.0F, 0.0F, 1.0F);
        // -Z
        face(pose, buffer, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
                u0, u1, argb, light, 0.0F, 0.0F, -1.0F);
        // +X
        face(pose, buffer, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                u0, u1, argb, light, 1.0F, 0.0F, 0.0F);
        // -X
        face(pose, buffer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                u0, u1, argb, light, -1.0F, 0.0F, 0.0F);
        // +Y
        face(pose, buffer, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0,
                u0, u1, argb, light, 0.0F, 1.0F, 0.0F);
        // -Y
        face(pose, buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                u0, u1, argb, light, 0.0F, -1.0F, 0.0F);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer buffer,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float u1, int argb, int light,
                             float nx, float ny, float nz) {
        vertex(buffer, pose, new Vec3(ax, ay, az), u0, 1.0F, argb, light, nx, ny, nz);
        vertex(buffer, pose, new Vec3(bx, by, bz), u1, 1.0F, argb, light, nx, ny, nz);
        vertex(buffer, pose, new Vec3(cx, cy, cz), u1, 0.0F, argb, light, nx, ny, nz);
        vertex(buffer, pose, new Vec3(dx, dy, dz), u0, 0.0F, argb, light, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 pos,
                               float u, float v, int argb) {
        vertex(buffer, pose, pos, u, v, argb, FULL_BRIGHT, 0.0F, 0.0F, 1.0F);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 pos,
                               float u, float v, int argb, int light, float nx, float ny, float nz) {
        buffer.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
