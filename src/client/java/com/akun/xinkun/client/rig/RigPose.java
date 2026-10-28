package com.akun.xinkun.client.rig;

import com.akun.xinkun.client.GenshinNpcRenderState;
import com.akun.xinkun.client.PlayerNpcModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 把原版人形模型当前帧的骨骼角度换算成角色骨架的世界矩阵。
 *
 * <p>原版玩家模型的头、身、四肢都挂在根骨骼下，彼此不继承旋转；角色的 MMD 骨架
 * 则是 hips → spine → chest → 头/手臂的链。为了让动作看起来仍与玩家一致：
 * 胸骨承担 {@code body} 的旋转，头和手臂减去这部分旋转，得到与原版相同的世界角度，
 * 同时仍会随胸腔平移（潜行时整体下蹲）。</p>
 *
 * <p>欧拉角换算：原版在模型空间里使用 ZYX 顺序（y 轴向下）；角色骨架是 y 轴向上，
 * 等价于 x、z 旋转取反，y 保持不变。</p>
 */
public final class RigPose {
    private static final float ARM_DEFAULT_X = 5.0F;
    private static final float ARM_DEFAULT_Y = 2.0F;
    private static final float LEG_DEFAULT_X = 1.9F;
    private static final float LEG_DEFAULT_Y = 12.0F;
    /** 步态频率缩放：0.85 让步子稍慢一点，仍与原版同一条余弦曲线。 */
    private static final float WALK_FREQ_SCALE = 0.85F;
    /** 腿部摆幅缩放：原版 1.4 rad 的幅度收敛到 65%，步子更小更稳。 */
    private static final float LEG_SWING_SCALE = 0.65F;
    /** 原版走路公式里腿部的幅度系数。 */
    private static final float LEG_WALK_AMPLITUDE = 1.4F;

    private final RigModel rig;
    private final Matrix4f[] world;
    private final Matrix4f[] locals;
    /** 说话时平滑过渡的口型开合度，0 闭嘴、1 张到最大。 */
    private float mouthOpen;

    public RigPose(RigModel rig) {
        this.rig = rig;
        this.world = new Matrix4f[rig.boneNames.length];
        this.locals = new Matrix4f[rig.boneNames.length];
        for (int i = 0; i < this.world.length; i++) {
            this.world[i] = new Matrix4f();
            this.locals[i] = new Matrix4f();
        }
        // 网格由模型作者按手办的 A 姿势摆好并蒙皮，肩点与袖根共享权重。
        // 若在运行时再把上臂强行转到竖直，混合权重区域会被剪成斜向破面，
        // 因此静止姿态完全交给原始骨架，不做任何额外的手臂修正。
    }

    public void update(PlayerNpcModel model, GenshinNpcRenderState state) {
        ModelPart body = model.body;
        ModelPart head = model.head;
        ModelPart leftArm = model.leftArm;
        ModelPart rightArm = model.rightArm;
        ModelPart leftLeg = model.leftLeg;
        ModelPart rightLeg = model.rightLeg;

        for (Matrix4f local : this.locals) {
            local.identity();
        }

        // 潜行时原版把身体下移 3.2、头再下移 1.0、双腿后移 4。像素单位与骨架一致，直接搬过来。
        float moving = Mth.clamp(state.walkAnimationSpeed * 4.0F, 0.0F, 1.0F);
        float idle = 1.0F - moving;
        float age = state.ageInTicks;
        // 桑多涅的浮空施法权重：起手时从站姿逐渐抬起，收招时缓慢落回地面，
        // 所有施法姿态都与这个权重混合，不再按布尔开关瞬间切换。
        float cast = Mth.clamp(state.castWeight, 0.0F, 1.0F);
        // 站立呼吸：胸腔缓慢起伏，肩臂跟着抬落。行走时淡出，交给步态。
        float breathe = Mth.sin(age * 0.09F) * idle;

        // 原版步态是一条相位为 animationPos * 0.6662 的余弦曲线。这里把相位整体放慢、
        // 腿部幅度压低，但只替换原版那一段摆动：潜行、游泳、乘骑等额外偏移原样保留。
        float phase = state.walkAnimationPos * 0.6662F;
        float slowPhase = phase * WALK_FREQ_SCALE;
        float walkAmp = state.walkAnimationSpeed / Math.max(state.speedValue, 0.001F);
        boolean plainWalk = !state.isPassenger && state.swimAmount <= 0.0F;

        float rightLegX = rightLeg.xRot;
        float leftLegX = leftLeg.xRot;
        if (plainWalk && walkAmp != 0.0F) {
            float vanillaRight = Mth.cos(phase) * LEG_WALK_AMPLITUDE * walkAmp;
            float vanillaLeft = Mth.cos(phase + Mth.PI) * LEG_WALK_AMPLITUDE * walkAmp;
            rightLegX = rightLeg.xRot - vanillaRight
                    + Mth.cos(slowPhase) * LEG_WALK_AMPLITUDE * walkAmp * LEG_SWING_SCALE;
            leftLegX = leftLeg.xRot - vanillaLeft
                    + Mth.cos(slowPhase + Mth.PI) * LEG_WALK_AMPLITUDE * walkAmp * LEG_SWING_SCALE;
        }

        // 迈步起伏：双腿张到最大（cos 最大）时躯干最低，双脚并拢时最高。
        float stepBob = -Math.abs(Mth.cos(slowPhase)) * 0.45F * moving;
        float chestY = -body.y + breathe * 0.22F + stepBob;
        float chestX = body.xRot + breathe * 0.014F;
        float headY = -head.y - chestY;
        float legZ = leftLeg.z;

        // 走路的体态连动：骨盆随步伐轻微扭转，胸腔再反向带一点，头保持稳定；
        // 配合一点前倾与上下起伏，步伐不再像两根摆杆一样机械。
        float gaitTwist = Mth.sin(slowPhase) * 0.06F * moving;
        float gaitRoll = Mth.sin(slowPhase) * 0.035F * moving;
        float gaitBounce = Mth.sin(slowPhase * 2.0F) * 0.018F * moving;
        chestX += 0.025F * moving + gaitBounce;
        float spineTwist = -gaitTwist * 0.35F;
        float chestTwist = body.yRot - gaitTwist * 0.55F;
        float headGaitCompensation = gaitTwist + spineTwist + (chestTwist - body.yRot);

        float headX = head.xRot - body.xRot;
        float headYRot = head.yRot - body.yRot;
        float headZRot = head.zRot - body.zRot;
        headYRot += Mth.sin(age * 0.031F) * 0.028F * idle;
        headYRot -= headGaitCompensation;
        headZRot -= gaitRoll;
        if (state.speaking) {
            // 说话时比平时更明显的点头与侧头，仍远小于挥手幅度。
            headX += Mth.sin(age * 0.38F) * 0.06F - 0.02F;
            headYRot += Mth.sin(age * 0.21F) * 0.045F;
        }
        if (cast > 0.001F) {
            // 浮空施法：上身略微前倾，视线压向目标，像把双手的重量推出去。
            chestX += 0.07F * cast;
            headX += 0.04F * cast;
        }

        set(this.rig.hips, 0.0F, gaitTwist, 0.0F, 0.0F, 0.0F, 0.0F);
        set(this.rig.spine, 0.0F, spineTwist, 0.0F, 0.0F, 0.0F, 0.0F);
        set(this.rig.chest, chestX, chestTwist, body.zRot + gaitRoll, 0.0F, chestY, 0.0F);
        set(this.rig.neck, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        set(this.rig.head, headX, headYRot, headZRot, 0.0F, headY, 0.0F);

        // 手臂相位跟着放慢，幅度仍取原版值（2.0 * 0.5）；挥手、拉弓、技能姿势
        // 与举火把等原版姿势由 HumanoidModel 另行接管，这里只在自然摆动时改相位。
        float leftArmX = leftArm.xRot;
        float rightArmX = rightArm.xRot;
        boolean swingArms = plainWalk && !model.hasCustomArmPose()
                && !state.isUsingItem
                && state.leftArmPose == HumanoidModel.ArmPose.EMPTY
                && state.rightArmPose == HumanoidModel.ArmPose.EMPTY;
        if (swingArms) {
            leftArmX = leftArm.xRot - Mth.cos(phase) * walkAmp
                    + Mth.cos(slowPhase) * walkAmp;
            rightArmX = rightArm.xRot - Mth.cos(phase + Mth.PI) * walkAmp
                    + Mth.cos(slowPhase + Mth.PI) * walkAmp;
        }

        if (cast > 0.001F) {
            // 双手向前推出：沿用原版拉弓时“手臂抬到接近水平”的模板角度，
            // 左右各向内收一点，掌心朝向目标。轻微抖动来自机械结构的运转。
            // 与原版姿势按施法权重混合，起手和收招都不会跳帧。
            float tremor = Mth.sin(age * 0.55F) * 0.018F;
            setArmPose(this.rig.arm[0], leftArm, 0, body, chestY,
                    Mth.lerp(cast, leftArmX, -1.40F + tremor),
                    Mth.lerp(cast, leftArm.yRot, 0.20F),
                    Mth.lerp(cast, leftArm.zRot, 0.0F));
            setArmPose(this.rig.arm[1], rightArm, 1, body, chestY,
                    Mth.lerp(cast, rightArmX, -1.40F - tremor),
                    Mth.lerp(cast, rightArm.yRot, -0.20F),
                    Mth.lerp(cast, rightArm.zRot, 0.0F));
        } else {
            setArm(this.rig.arm[0], leftArm, 0, body, chestY, leftArmX);
            setArm(this.rig.arm[1], rightArm, 1, body, chestY, rightArmX);
        }

        if (cast > 0.001F) {
            // 骑坐般的浮空姿势：大腿前抬、膝盖分开，小腿自然垂下并轻轻晃动。
            float dangle = Mth.sin(age * 0.09F) * 0.03F;
            setLegPose(this.rig.leg[0], leftLeg, 0, legZ,
                    Mth.lerp(cast, leftLegX, -1.10F + dangle),
                    Mth.lerp(cast, leftLeg.yRot, -0.32F),
                    Mth.lerp(cast, leftLeg.zRot, -0.06F));
            setLegPose(this.rig.leg[1], rightLeg, 1, legZ,
                    Mth.lerp(cast, rightLegX, -1.10F - dangle),
                    Mth.lerp(cast, rightLeg.yRot, 0.32F),
                    Mth.lerp(cast, rightLeg.zRot, 0.06F));
            setKneeBlend(this.rig.knee[0], this.rig.ankle[0], leftLegX, moving, cast);
            setKneeBlend(this.rig.knee[1], this.rig.ankle[1], rightLegX, moving, cast);
        } else {
            setLeg(this.rig.leg[0], leftLeg, 0, legZ, leftLegX);
            setLeg(this.rig.leg[1], rightLeg, 1, legZ, rightLegX);
            // 原版腿只有一整根，膝盖始终笔直；这里按步态补出后摆时的小腿回勾，
            // 踝关节反向补偿让脚掌保持接近水平，就不会出现直腿拖行。
            setKnee(this.rig.knee[0], this.rig.ankle[0], leftLegX, moving);
            setKnee(this.rig.knee[1], this.rig.ankle[1], rightLegX, moving);
        }

        this.updateMouth(state);

        for (int i = 0; i < this.rig.boneParents.length; i++) {
            int parent = this.rig.boneParents[i];
            if (parent >= 0) {
                this.world[i].set(this.world[parent]).mul(this.locals[i]);
            } else {
                this.world[i].set(this.locals[i]);
            }
        }
    }

    private void set(int bone, float xRot, float yRot, float zRot,
                     float offsetX, float offsetY, float offsetZ) {
        if (bone < 0) {
            return;
        }
        float[] pivot = this.rig.bonePivots[bone];
        this.locals[bone].identity()
                .translate(pivot[0] + offsetX, pivot[1] + offsetY, pivot[2] + offsetZ)
                .rotateZYX(-zRot, yRot, -xRot)
                .translate(-pivot[0], -pivot[1], -pivot[2]);
    }

    private void setArm(int bone, ModelPart arm, int side, ModelPart body, float chestY, float xRot) {
        float sign = side == 0 ? 1.0F : -1.0F;
        float offsetX = arm.x - sign * ARM_DEFAULT_X;
        float offsetY = -(arm.y - ARM_DEFAULT_Y) - chestY;
        float offsetZ = arm.z;
        set(bone, xRot - body.xRot, arm.yRot - body.yRot, arm.zRot - body.zRot,
                offsetX, offsetY, offsetZ);
    }

    private void setLeg(int bone, ModelPart leg, int side, float legZ, float xRot) {
        float sign = side == 0 ? 1.0F : -1.0F;
        float offsetX = leg.x - sign * LEG_DEFAULT_X;
        float offsetY = -(leg.y - LEG_DEFAULT_Y);
        float offsetZ = leg.z - legZ;
        set(bone, xRot, leg.yRot, leg.zRot, offsetX, offsetY, offsetZ);
    }

    /** 手臂的完整姿势：世界角度直接指定，内部减去胸腔已经承担的旋转。 */
    private void setArmPose(int bone, ModelPart arm, int side, ModelPart body, float chestY,
                            float xRot, float yRot, float zRot) {
        float sign = side == 0 ? 1.0F : -1.0F;
        float offsetX = arm.x - sign * ARM_DEFAULT_X;
        float offsetY = -(arm.y - ARM_DEFAULT_Y) - chestY;
        float offsetZ = arm.z;
        set(bone, xRot - body.xRot, yRot - body.yRot, zRot - body.zRot,
                offsetX, offsetY, offsetZ);
    }

    /** 腿的完整姿势：浮空骑坐时需要在前后摆动之外另外指定张开角度。 */
    private void setLegPose(int bone, ModelPart leg, int side, float legZ,
                            float xRot, float yRot, float zRot) {
        float sign = side == 0 ? 1.0F : -1.0F;
        float offsetX = leg.x - sign * LEG_DEFAULT_X;
        float offsetY = -(leg.y - LEG_DEFAULT_Y);
        float offsetZ = leg.z - legZ;
        set(bone, xRot, yRot, zRot, offsetX, offsetY, offsetZ);
    }

    /** 固定的膝弯与踝部回勾，用于浮空姿势而不是步态。 */
    private void setKneeBlend(int knee, int ankle, float legXRot, float moving, float cast) {
        float normalBend = Math.max(0.0F, legXRot) * 0.7F * moving;
        float normalAnkle = -normalBend * 0.55F;
        set(knee, Mth.lerp(cast, normalBend, 0.90F), 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        set(ankle, Mth.lerp(cast, normalAnkle, -0.42F), 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /**
     * 走路时后摆的腿补一点膝弯：原版人形模型没有膝关节，直腿摆动会显得像拖行。
     * 踝关节反向回勾一半，脚掌保持接近水平。
     */
    private void setKnee(int knee, int ankle, float legXRot, float moving) {
        float bend = Math.max(0.0F, legXRot) * 0.7F * moving;
        set(knee, bend, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        set(ankle, -bend * 0.55F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /**
     * 台词口型：两条不同周期的正弦叠加出类似音节的开合节奏，再按帧平滑，
     * 避免开合瞬间跳变。停止说话后自然闭回。
     */
    private void updateMouth(GenshinNpcRenderState state) {
        float target = 0.0F;
        if (state.speaking) {
            float syllable = 0.62F * Mth.sin(state.ageInTicks * 0.72F)
                    + 0.38F * Mth.sin(state.ageInTicks * 0.29F + 1.4F);
            target = Mth.clamp(0.18F + 0.82F * Math.max(0.0F, syllable), 0.0F, 1.0F);
        }
        this.mouthOpen += (target - this.mouthOpen) * 0.5F;
        if (this.mouthOpen < 0.01F) {
            this.mouthOpen = 0.0F;
        }
    }

    /** 当前帧的口型开合度，供网格层在蒙皮前拉伸嘴部顶点。 */
    public float mouthOpen() {
        return this.mouthOpen;
    }

    /**
     * 手腕在骨架空间中的变换，用于把手持物品挂到角色身上。
     *
     * <p>{@code world[i]} 是蒙皮矩阵：它直接把“绑定姿势的顶点”变换到当前姿势，
     * 因此它的平移分量是 {@code pivot − R·pivot}（绕关节旋转的补偿），并不是骨骼位置。
     * 骨骼真正的位置要把绑定姿势的 pivot 再乘一次这个矩阵才能得到，否则物品会落回
     * 身体中心。旋转分量本身可以直接当作手腕朝向使用。</p>
     */
    public Matrix4f hand(HumanoidArm arm) {
        int side = arm == HumanoidArm.LEFT ? 0 : 1;
        int index = this.rig.wrist[side];
        if (index < 0) {
            index = this.rig.arm[side];
        }
        if (index < 0) {
            return new Matrix4f();
        }
        float[] pivot = this.rig.bonePivots[index];
        Matrix4f skin = this.world[index];
        float x = skin.m00() * pivot[0] + skin.m10() * pivot[1] + skin.m20() * pivot[2] + skin.m30();
        float y = skin.m01() * pivot[0] + skin.m11() * pivot[1] + skin.m21() * pivot[2] + skin.m31();
        float z = skin.m02() * pivot[0] + skin.m12() * pivot[1] + skin.m22() * pivot[2] + skin.m32();
        return new Matrix4f(skin).setTranslation(x, y, z);
    }

    /**
     * 当前帧手掌中心在玩家模型坐标系中的位置（1 像素 = 1/16 格，y 向下）。
     *
     * <p>骨架的“手腕”骨骼位于手掌远端，直接拿它当握把挂点会让武器跑到指尖外侧；
     * 手掌中心在解析网格时按手腕骨权重统计得到，这里只做一次骨架变换。
     * 物品图层拿到这个点后，把原版挂点平移过来，武器柄才会真正落在手心里。</p>
     */
    public Vector3f handPointModel(HumanoidArm arm, Vector3f out) {
        int side = arm == HumanoidArm.LEFT ? 0 : 1;
        int index = this.rig.wrist[side] >= 0 ? this.rig.wrist[side] : this.rig.arm[side];
        if (index < 0) {
            return out.set(0.0F, 0.0F, 0.0F);
        }
        float[] point = this.rig.palm[side] != null
                ? this.rig.palm[side] : this.rig.bonePivots[index];
        Matrix4f skin = this.world[index];
        float x = skin.m00() * point[0] + skin.m10() * point[1] + skin.m20() * point[2] + skin.m30();
        float y = skin.m01() * point[0] + skin.m11() * point[1] + skin.m21() * point[2] + skin.m31();
        float z = skin.m02() * point[0] + skin.m12() * point[1] + skin.m22() * point[2] + skin.m32();
        // 骨架 y 向上、脚底 y = 0；玩家模型 y 向下、腰部 y = 0。
        return out.set(x / 16.0F, 1.5F - y / 16.0F, z / 16.0F);
    }

    /** 导出当前帧全部骨骼矩阵的快照，供延迟执行的几何提交使用。 */
    public float[] snapshot() {
        float[] out = new float[this.world.length * 16];
        for (int i = 0; i < this.world.length; i++) {
            this.world[i].get(out, i * 16);
        }
        return out;
    }
}
