package com.akun.xinkun.client;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.client.rig.RigModel;
import com.akun.xinkun.client.rig.RigPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 与玩家完全相同的骨骼与皮肤图层：PLAYER / PLAYER_SLIM 模型自带帽檐、外套、
 * 衣袖和裤装四层 0.25 像素外扩的立体壳。这里始终把它们设为可见，
 * 皮肤文件里画在叠加层的发型、首饰和服饰凸起就会像普通玩家一样呈现 3D 细节。
 *
 * <p>动作方面，基础挥击仍沿用原版 {@link HumanoidModel#setupAttackAnimation}：
 * 长柄武器用原版突刺模板，单手剑与工具用原版劈砍模板。模型只在此基础上叠加
 * 各角色真实攻击方式的细节（法器的翻页、月之少女的抬掌、矿镐的双手下砸等），
 * 并且所有叠加量都以 {@code sin(πp)} 一类的包络在起手与收势归零，
 * 动作切换时不会出现一帧跳变。</p>
 */
public class PlayerNpcModel extends HumanoidModel<GenshinNpcRenderState> {
    private static final int P_HEAD_X = 0;
    private static final int P_HEAD_Y = 1;
    private static final int P_HEAD_Z = 2;
    private static final int P_LEFT_X = 3;
    private static final int P_LEFT_Y = 4;
    private static final int P_LEFT_Z = 5;
    private static final int P_RIGHT_X = 6;
    private static final int P_RIGHT_Y = 7;
    private static final int P_RIGHT_Z = 8;

    private final ModelPart leftSleeve;
    private final ModelPart rightSleeve;
    private final ModelPart leftPants;
    private final ModelPart rightPants;
    private final ModelPart jacket;
    private final boolean slim;
    private final RigModel rig;
    private final RigPose rigPose;
    /** 所属角色；护甲模型等辅助用途构造时为 null，不叠加角色专属动作。 */
    private final GenshinCharacter character;
    /** 本帧是否有烟花/钟离/纳西妲这类接管手臂的自定义姿势。 */
    private boolean customArmPose;
    /** 招式交叉淡入淡出用的基准值与加权累加值。 */
    private final float[] poseBase = new float[9];
    private final float[] poseAcc = new float[9];

    public PlayerNpcModel(ModelPart root, boolean slim, RigModel rig, GenshinCharacter character) {
        super(root, RenderTypes::entityTranslucent);
        this.slim = slim;
        this.rig = rig;
        this.character = character;
        this.rigPose = rig == null ? null : new RigPose(rig);
        this.leftSleeve = this.leftArm.getChild("left_sleeve");
        this.rightSleeve = this.rightArm.getChild("right_sleeve");
        this.leftPants = this.leftLeg.getChild("left_pants");
        this.rightPants = this.rightLeg.getChild("right_pants");
        this.jacket = this.body.getChild("jacket");
    }

    @Override
    public void setupAnim(GenshinNpcRenderState state) {
        super.setupAnim(state);
        this.hat.visible = true;
        this.jacket.visible = true;
        this.leftPants.visible = true;
        this.rightPants.visible = true;
        this.leftSleeve.visible = true;
        this.rightSleeve.visible = true;
        this.customArmPose = false;
        this.applyActionPoses(state);
        // 桑多涅浮空施法时手臂由 RigPose 接管，这里只标记，避免再叠加挥击修正。
        if (state.castWeight > 0.01F) {
            this.customArmPose = true;
        }
        if (this.rigPose != null) {
            this.rigPose.update(this, state);
        }
    }

    /** 本帧手臂是否由专属姿势完全接管；接管时不叠加步态相位修正。 */
    public boolean hasCustomArmPose() {
        return this.customArmPose;
    }

    public RigPose rigPose() {
        return this.rigPose;
    }

    /**
     * 在原版挥击模板之上叠加角色自己的动作细节。所有偏移都乘以
     * {@code lift = sin(πp)} 或 {@code stroke = sin(2πp)}，两端为零，
     * 因此起手、收势与连续挥击之间都是连续的。
     */
    @Override
    protected void setupAttackAnimation(GenshinNpcRenderState state) {
        super.setupAttackAnimation(state);
        if (this.character == null || state.swingAnimation <= 0.0F || state.currentSwing == null) {
            return;
        }
        float p = state.swingAnimation;
        float lift = Mth.sin(p * Mth.PI);
        float stroke = Mth.sin(p * Mth.PI * 2.0F);
        boolean rightHanded = state.currentSwing.hand().asArm(state.mainArm) == HumanoidArm.RIGHT;
        ModelPart arm = rightHanded ? this.rightArm : this.leftArm;
        ModelPart off = rightHanded ? this.leftArm : this.rightArm;
        float side = rightHanded ? 1.0F : -1.0F;
        ItemStack held = state.getMainHandItemStack();
        if (held.is(ItemTags.PICKAXES)) {
            this.poseMining(arm, off, side, lift, stroke);
        } else if (held.is(ItemTags.AXES)) {
            this.poseChopping(arm, off, side, lift, stroke);
        } else {
            switch (this.character) {
                case FURINA -> this.poseSwordSlash(arm, off, side, lift, stroke);
                case HU_TAO -> this.posePolearmThrust(off, side, lift, stroke);
                case ZHONGLI -> this.poseSpearThrust(off, side, lift, stroke);
                case NAHIDA -> this.posePageSweep(arm, off, side, lift, stroke);
                case COLUMBINA -> this.poseMoonCast(arm, off, side, lift, stroke);
                case SANDRONE -> this.poseFrostSwipe(arm, off, side, lift, stroke);
                case YOIMIYA -> {
                }
            }
        }
    }

    /** 芙宁娜：单手剑，原版劈砍之上加一段横跨身前的弧形收势。 */
    private void poseSwordSlash(ModelPart arm, ModelPart off, float side, float lift, float stroke) {
        arm.xRot -= 0.35F * lift;
        arm.yRot -= side * 0.80F * stroke;
        arm.zRot += side * 0.30F * lift;
        off.xRot -= 0.20F * lift;
        off.yRot += side * 0.30F * lift;
        this.body.yRot += side * 0.16F * stroke;
        this.head.yRot -= side * 0.09F * stroke;
        this.head.xRot -= 0.05F * lift;
    }

    /** 胡桃：长柄武器，原版突刺之外补上压肩与副手握杆。 */
    private void posePolearmThrust(ModelPart off, float side, float lift, float stroke) {
        this.body.xRot += 0.10F * lift;
        this.body.yRot += side * 0.10F * stroke;
        off.xRot -= 0.30F * lift;
        off.yRot += side * 0.28F * lift;
        this.head.xRot -= 0.04F * lift;
    }

    /** 钟离：长枪突刺的整个身体前压，副手扶杆保持平衡。 */
    private void poseSpearThrust(ModelPart off, float side, float lift, float stroke) {
        this.body.xRot += 0.12F * lift;
        this.body.yRot += side * 0.06F * stroke;
        off.xRot -= 0.22F * lift;
        off.yRot += side * 0.22F * lift;
        this.head.xRot -= 0.05F * lift;
    }

    /** 纳西妲：法器不挥砍，双手像翻动书页一样横掠，书本页光随之展开。 */
    private void posePageSweep(ModelPart arm, ModelPart off, float side, float lift, float stroke) {
        arm.xRot -= 1.05F * lift;
        arm.yRot -= side * 0.65F * stroke;
        arm.zRot += side * 0.22F * lift;
        off.xRot -= 0.85F * lift;
        off.yRot += side * 0.55F * stroke;
        this.body.yRot += side * 0.18F * stroke;
        this.head.xRot -= 0.06F * lift;
        this.head.yRot -= side * 0.05F * stroke;
    }

    /** 哥伦比娅：附魔书施法，单掌向上托起月光，另一手在胸前引导。 */
    private void poseMoonCast(ModelPart arm, ModelPart off, float side, float lift, float stroke) {
        arm.xRot -= 1.30F * lift;
        arm.yRot -= side * 0.25F * lift;
        arm.zRot += side * 0.18F * lift;
        off.xRot -= 0.60F * lift;
        off.yRot += side * 0.20F * lift;
        this.body.yRot += side * 0.08F * stroke;
        this.head.xRot -= 0.12F * lift;
    }

    /** 桑多涅：冰霜射线之外的近身挥击，手腕像机械爪一样横向掠过。 */
    private void poseFrostSwipe(ModelPart arm, ModelPart off, float side, float lift, float stroke) {
        arm.xRot -= 1.05F * lift;
        arm.yRot -= side * 0.50F * stroke;
        arm.zRot += side * 0.18F * lift;
        off.xRot -= 0.55F * lift;
        off.yRot += side * 0.30F * stroke;
        this.body.yRot += side * 0.10F * stroke;
        this.head.xRot -= 0.04F * lift;
    }

    /** 挖矿：双手握镐，先抬到头顶再向下砸；副手跟着主手一起发力。 */
    private void poseMining(ModelPart arm, ModelPart off, float side, float lift, float stroke) {
        arm.xRot -= 0.55F * lift + 0.30F * stroke;
        arm.zRot += side * 0.10F * lift;
        off.xRot -= 0.45F * lift;
        off.yRot += side * 0.30F * lift;
        this.body.xRot += 0.07F * lift;
        this.body.yRot += side * 0.05F * stroke;
        this.head.xRot -= 0.06F * lift;
    }

    /** 伐木：斧头从肩后斜劈到身前，躯干跟着转动，比挖矿更强调横向弧线。 */
    private void poseChopping(ModelPart arm, ModelPart off, float side, float lift, float stroke) {
        arm.xRot -= 0.72F * lift + 0.28F * stroke;
        arm.zRot += side * 0.28F * lift;
        arm.yRot -= side * 0.35F * stroke;
        off.xRot -= 0.32F * lift;
        off.yRot += side * 0.18F * lift;
        this.body.xRot += 0.05F * lift;
        this.body.yRot += side * (0.14F * stroke + 0.06F * lift);
        this.head.yRot -= side * 0.10F * stroke;
    }

    /**
     * 烟花、钟离的四个动作与纳西妲的绿化姿势按平滑权重交叉混合。
     * 权重由渲染状态逐帧逼近目标值，切换招式时手臂从当前姿态滑向新姿态，
     * 而不是直接跳过去；两个姿势同时衰减时按权重取平均。
     */
    private void applyActionPoses(GenshinNpcRenderState state) {
        float[] weights = state.poseWeights;
        float total = weights[0] + weights[1] + weights[2] + weights[3] + weights[4] + weights[5];
        if (total <= 0.002F) {
            return;
        }
        this.capturePoseBase();
        java.util.Arrays.fill(this.poseAcc, 0.0F);
        float age = state.ageInTicks;
        float sum = 0.0F;
        if (weights[0] > 0.002F) {
            float sway = Mth.sin(age * 0.15F) * 0.12F;
            sum += this.accruePose(weights[0],
                    -0.34F, 0.0F, 0.0F,
                    -2.72F - sway, 0.24F, 0.14F,
                    -2.72F + sway, -0.24F, -0.14F);
        }
        if (weights[1] > 0.002F) {
            float sway = Mth.sin(age * 0.12F) * 0.06F;
            sum += this.accruePose(weights[1],
                    -0.30F, 0.0F, 0.0F,
                    -2.86F - sway, 0.16F, 0.10F,
                    -2.86F + sway, -0.16F, -0.10F);
        }
        if (weights[2] > 0.002F) {
            float breathe = Mth.sin(age * 0.10F) * 0.03F;
            sum += this.accruePose(weights[2],
                    0.16F, 0.0F, 0.0F,
                    -1.28F + breathe, -0.34F, -0.92F,
                    -1.28F + breathe, 0.34F, 0.92F);
        }
        if (weights[3] > 0.002F) {
            float lift = Mth.sin(age * 0.16F) * 0.05F;
            sum += this.accruePose(weights[3],
                    -0.34F, 0.0F, 0.0F,
                    -0.18F + lift, 0.0F, 1.45F,
                    -0.18F + lift, 0.0F, -1.45F);
        }
        if (weights[4] > 0.002F) {
            sum += this.accruePose(weights[4],
                    0.02F, 0.0F, 0.0F,
                    0.42F, 0.0F, 0.18F,
                    -1.52F, -0.08F, 0.06F);
        }
        if (weights[5] > 0.002F) {
            float sway = Mth.sin(age * 0.11F) * 0.07F;
            sum += this.accruePose(weights[5],
                    -0.26F, 0.0F, 0.0F,
                    -2.92F - sway, 0.20F, 0.12F,
                    -2.92F + sway, -0.20F, -0.12F);
        }
        this.customArmPose = sum > 0.002F;
        if (!this.customArmPose) {
            return;
        }
        float denominator = Math.max(1.0F, sum);
        this.head.xRot = blend(0, sum, denominator);
        this.head.yRot = blend(1, sum, denominator);
        this.head.zRot = blend(2, sum, denominator);
        this.leftArm.xRot = blend(3, sum, denominator);
        this.leftArm.yRot = blend(4, sum, denominator);
        this.leftArm.zRot = blend(5, sum, denominator);
        this.rightArm.xRot = blend(6, sum, denominator);
        this.rightArm.yRot = blend(7, sum, denominator);
        this.rightArm.zRot = blend(8, sum, denominator);
    }

    private void capturePoseBase() {
        this.poseBase[0] = this.head.xRot;
        this.poseBase[1] = this.head.yRot;
        this.poseBase[2] = this.head.zRot;
        this.poseBase[3] = this.leftArm.xRot;
        this.poseBase[4] = this.leftArm.yRot;
        this.poseBase[5] = this.leftArm.zRot;
        this.poseBase[6] = this.rightArm.xRot;
        this.poseBase[7] = this.rightArm.yRot;
        this.poseBase[8] = this.rightArm.zRot;
    }

    private float accruePose(float weight, float headX, float headY, float headZ,
                             float leftX, float leftY, float leftZ,
                             float rightX, float rightY, float rightZ) {
        this.poseAcc[0] += weight * headX;
        this.poseAcc[1] += weight * headY;
        this.poseAcc[2] += weight * headZ;
        this.poseAcc[3] += weight * leftX;
        this.poseAcc[4] += weight * leftY;
        this.poseAcc[5] += weight * leftZ;
        this.poseAcc[6] += weight * rightX;
        this.poseAcc[7] += weight * rightY;
        this.poseAcc[8] += weight * rightZ;
        return weight;
    }

    private float blend(int channel, float sum, float denominator) {
        float base = this.poseBase[channel];
        return base + (this.poseAcc[channel] - base * sum) / denominator;
    }

    /**
     * 手持物品一律沿用原版人形模型的挂点与朝向，但骨架角色的手腕位置和原版
     * 玩家模型并不重合（骨架肩宽、手臂角度与 32 像素身高都不同），直接用原版
     * 挂点会让武器悬在手掌外侧。这里先按原版图层的完整变换算出挂点当前落在哪里，
     * 再把它平移到骨架掌心；旋转仍由原版图层负责，武器的朝向和挥击轨迹不变。
     */
    @Override
    public void translateToHand(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack) {
        Matrix4f outer = new Matrix4f(poseStack.last().pose());
        this.root().translateAndRotate(poseStack);
        ModelPart part = this.getArm(arm);
        if (this.slim) {
            float offset = 0.5F * (arm == HumanoidArm.RIGHT ? 1 : -1);
            part.x += offset;
            part.translateAndRotate(poseStack);
            part.x -= offset;
        } else {
            part.translateAndRotate(poseStack);
        }
        if (this.rigPose == null) {
            return;
        }
        // 原版 ItemInHandLayer 在 translateToHand 之后还会绕 X 轴 -90°、绕 Y 轴 180°，
        // 再平移 (±1, 2, -10)/16 像素。用同一组操作复算，得到真正的挂点位置。
        Matrix4f anchorMatrix = new Matrix4f(poseStack.last().pose());
        anchorMatrix.rotate(Axis.XP.rotationDegrees(-90.0F));
        anchorMatrix.rotate(Axis.YP.rotationDegrees(180.0F));
        float lateral = arm == HumanoidArm.LEFT ? -1.0F : 1.0F;
        anchorMatrix.translate(lateral / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
        Vector3f anchor = anchorMatrix.transformPosition(new Vector3f());
        Vector3f target = this.rigPose.handPointModel(arm, new Vector3f());
        target = outer.transformPosition(target);
        Vector3f delta = target.sub(anchor);
        // 平移要在手臂旋转之后、原版图层旋转之前施加，所以换算回当前手臂局部坐标。
        Matrix4f basis = new Matrix4f(poseStack.last().pose());
        basis.setTranslation(0.0F, 0.0F, 0.0F);
        basis.invert();
        Vector3f local = basis.transformDirection(delta);
        poseStack.translate(local.x, local.y, local.z);
    }
}
