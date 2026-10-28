package com.akun.xinkun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 蓝色海马水灵：卷尾、背鳍、胸鳍与金色头冠，全部由立方体拼出立体轮廓。
 */
public class SeahorseSpiritModel extends WaterSpiritModel {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart dorsalFin;
    private final ModelPart rightFin;
    private final ModelPart leftFin;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public SeahorseSpiritModel(ModelPart root) {
        super(root.getChild("root"));
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.neck = this.body.getChild("neck");
        this.head = this.neck.getChild("head");
        this.tail = this.body.getChild("tail");
        this.tailTip = this.tail.getChild("tail_tip");
        this.dorsalFin = this.body.getChild("dorsal_fin");
        this.rightFin = this.body.getChild("right_fin");
        this.leftFin = this.body.getChild("left_fin");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition base = root.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, ROOT_Y, 0.0F));

        PartDefinition body = base.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 10).addBox(-2.0F, -3.5F, -2.0F, 4.0F, 6.0F, 4.0F)
                        .texOffs(18, 10).addBox(-1.5F, 2.2F, -1.5F, 3.0F, 2.8F, 3.0F)
                        .texOffs(21, 49).addBox(-1.55F, -2.8F, -2.25F, 3.1F, 4.6F, 0.5F)
                        .texOffs(31, 10).addBox(-0.35F, -3.0F, 1.9F, 0.7F, 5.0F, 1.0F),
                PartPose.offset(0.0F, 18.0F, 0.0F));

        PartDefinition neck = body.addOrReplaceChild("neck",
                CubeListBuilder.create()
                        .texOffs(36, 16).addBox(-1.25F, -2.2F, -1.25F, 2.5F, 2.4F, 2.5F),
                PartPose.offsetAndRotation(0.0F, -3.2F, -0.35F, -0.18F, 0.0F, 0.0F));

        PartDefinition head = neck.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 22).addBox(-1.9F, -2.9F, -2.7F, 3.8F, 3.0F, 3.8F)
                        .texOffs(16, 22).addBox(-0.95F, -1.5F, -5.1F, 1.9F, 1.8F, 2.5F)
                        .texOffs(49, 1).addBox(-0.45F, -5.0F, -1.0F, 0.9F, 2.4F, 1.1F)
                        .texOffs(49, 1).addBox(-1.5F, -4.0F, -0.6F, 0.7F, 1.4F, 0.8F)
                        .texOffs(49, 1).addBox(0.8F, -4.0F, -0.6F, 0.7F, 1.4F, 0.8F)
                        .texOffs(1, 1).addBox(-2.3F, -2.45F, -2.2F, 0.6F, 1.3F, 1.3F)
                        .texOffs(1, 1).addBox(1.7F, -2.45F, -2.2F, 0.6F, 1.3F, 1.3F)
                        .texOffs(17, 1).addBox(-2.45F, -2.2F, -1.95F, 0.4F, 0.9F, 0.9F)
                        .texOffs(17, 1).addBox(2.05F, -2.2F, -1.95F, 0.4F, 0.9F, 0.9F)
                        .texOffs(33, 1).addBox(-2.5F, -2.1F, -1.75F, 0.25F, 0.35F, 0.35F)
                        .texOffs(33, 1).addBox(2.25F, -2.1F, -1.75F, 0.25F, 0.35F, 0.35F),
                PartPose.offsetAndRotation(0.0F, -2.0F, -0.45F, 0.1F, 0.0F, 0.0F));

        head.addOrReplaceChild("cheek_fin_right",
                CubeListBuilder.create().texOffs(1, 49).addBox(-1.1F, -1.6F, -0.4F, 1.2F, 1.6F, 0.35F),
                PartPose.offsetAndRotation(-1.8F, 0.1F, -1.2F, 0.0F, 0.35F, 0.35F));
        head.addOrReplaceChild("cheek_fin_left",
                CubeListBuilder.create().texOffs(1, 49).addBox(-0.1F, -1.6F, -0.4F, 1.2F, 1.6F, 0.35F),
                PartPose.offsetAndRotation(1.8F, 0.1F, -1.2F, 0.0F, -0.35F, -0.35F));

        PartDefinition tail = body.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(40, 10).addBox(-1.05F, 0.0F, -1.05F, 2.1F, 2.5F, 2.1F),
                PartPose.offsetAndRotation(0.0F, 4.7F, 0.35F, 0.4F, 0.0F, 0.0F));
        PartDefinition tailTip = tail.addOrReplaceChild("tail_tip",
                CubeListBuilder.create()
                        .texOffs(49, 10).addBox(-0.8F, 0.0F, -0.8F, 1.6F, 2.1F, 1.6F)
                        .texOffs(1, 49).addBox(-0.2F, 1.9F, -1.9F, 0.4F, 1.8F, 2.7F),
                PartPose.offsetAndRotation(0.0F, 2.5F, 0.0F, -0.9F, 0.0F, 0.0F));

        body.addOrReplaceChild("dorsal_fin",
                CubeListBuilder.create().texOffs(1, 49).addBox(-0.25F, -2.6F, 0.0F, 0.5F, 4.8F, 2.0F),
                PartPose.offsetAndRotation(0.0F, -1.6F, 2.0F, 0.2F, 0.0F, 0.0F));
        body.addOrReplaceChild("right_fin",
                CubeListBuilder.create().texOffs(1, 49).addBox(-0.3F, -1.2F, -1.1F, 0.3F, 2.4F, 2.2F),
                PartPose.offsetAndRotation(-1.95F, -2.4F, -0.2F, 0.0F, 0.0F, -0.3F));
        body.addOrReplaceChild("left_fin",
                CubeListBuilder.create().texOffs(1, 49).addBox(0.0F, -1.2F, -1.1F, 0.3F, 2.4F, 2.2F),
                PartPose.offsetAndRotation(1.95F, -2.4F, -0.2F, 0.0F, 0.0F, 0.3F));

        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(48, 16).addBox(-0.45F, 0.0F, -0.45F, 0.9F, 2.0F, 0.9F)
                        .texOffs(48, 22).addBox(-0.55F, 1.9F, -0.85F, 1.1F, 0.85F, 1.5F)
                        .texOffs(49, 1).addBox(-0.5F, 1.55F, -0.5F, 1.0F, 0.3F, 1.0F),
                PartPose.offsetAndRotation(-1.95F, -2.2F, -1.0F, -0.25F, 0.0F, -0.12F));
        body.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(48, 16).addBox(-0.45F, 0.0F, -0.45F, 0.9F, 2.0F, 0.9F)
                        .texOffs(48, 22).addBox(-0.55F, 1.9F, -0.85F, 1.1F, 0.85F, 1.5F)
                        .texOffs(49, 1).addBox(-0.5F, 1.55F, -0.5F, 1.0F, 0.3F, 1.0F),
                PartPose.offsetAndRotation(1.95F, -2.2F, -1.0F, -0.25F, 0.0F, 0.12F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WaterSpiritRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        this.root.y = ROOT_Y + wave(age, 0.11F, 0.45F);
        this.root.zRot = wave(age, 0.07F, 0.04F);
        applyLook(state, this.head, 0.85F, 0.55F);
        this.head.zRot = wave(age, 0.06F, 0.06F);
        this.neck.xRot = -0.18F + wave(age, 0.09F, 0.05F);

        this.tail.xRot = 0.4F + wave(age + 4.0F, 0.15F, 0.16F);
        this.tail.yRot = wave(age, 0.12F, 0.28F);
        this.tailTip.yRot = wave(age - 6.0F, 0.12F, 0.35F);
        this.dorsalFin.zRot = wave(age, 0.18F, 0.18F);

        float flap = wave(age, 0.22F, 0.45F);
        this.rightFin.zRot = -0.3F - flap;
        this.leftFin.zRot = 0.3F + flap;

        float armSwing = wave(age, 0.2F, 0.08F);
        float basePitch = state.charging ? -1.2F : -0.25F;
        this.rightArm.xRot = basePitch + armSwing;
        this.leftArm.xRot = basePitch - armSwing;
        this.rightArm.zRot = state.charging ? -0.35F : -0.12F;
        this.leftArm.zRot = state.charging ? 0.35F : 0.12F;
        this.rightArm.yRot = state.charging ? -0.2F : 0.0F;
        this.leftArm.yRot = state.charging ? 0.2F : 0.0F;
    }

    @Override
    public void translateToHand(WaterSpiritRenderState state, HumanoidArm arm, PoseStack poseStack) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart activeArm = right ? this.rightArm : this.leftArm;
        this.root.translateAndRotate(poseStack);
        this.body.translateAndRotate(poseStack);
        activeArm.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 2.25F / 16.0F, -0.35F / 16.0F);
        poseStack.scale(0.55F, 0.55F, 0.55F);
        poseStack.translate(right ? -0.06F : 0.06F, -0.16F, 0.1F);
    }
}
