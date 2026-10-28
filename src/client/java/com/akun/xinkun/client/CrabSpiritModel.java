package com.akun.xinkun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 蓝色螃蟹水灵：宽甲壳、金色眼柄、双螯与划水步足。
 */
public class CrabSpiritModel extends WaterSpiritModel {
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightStalk;
    private final ModelPart leftStalk;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightForearm;
    private final ModelPart leftForearm;
    private final ModelPart rightPincerTop;
    private final ModelPart rightPincerBottom;
    private final ModelPart leftPincerTop;
    private final ModelPart leftPincerBottom;
    private final ModelPart[] rightLegs = new ModelPart[3];
    private final ModelPart[] leftLegs = new ModelPart[3];
    private final ModelPart[] rightLegTips = new ModelPart[3];
    private final ModelPart[] leftLegTips = new ModelPart[3];

    public CrabSpiritModel(ModelPart root) {
        super(root.getChild("root"));
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.head = this.body.getChild("head");
        this.rightStalk = this.head.getChild("right_stalk");
        this.leftStalk = this.head.getChild("left_stalk");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        this.rightForearm = this.rightArm.getChild("forearm");
        this.leftForearm = this.leftArm.getChild("forearm");
        this.rightPincerTop = this.rightForearm.getChild("pincer_top");
        this.rightPincerBottom = this.rightForearm.getChild("pincer_bottom");
        this.leftPincerTop = this.leftForearm.getChild("pincer_top");
        this.leftPincerBottom = this.leftForearm.getChild("pincer_bottom");
        for (int i = 0; i < 3; i++) {
            this.rightLegs[i] = this.body.getChild("right_leg_" + i);
            this.leftLegs[i] = this.body.getChild("left_leg_" + i);
            this.rightLegTips[i] = this.rightLegs[i].getChild("tip");
            this.leftLegTips[i] = this.leftLegs[i].getChild("tip");
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition base = root.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, ROOT_Y, 0.0F));

        PartDefinition body = base.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 10).addBox(-4.2F, -3.2F, -3.8F, 8.4F, 3.6F, 7.6F)
                        .texOffs(34, 10).addBox(-3.2F, -4.6F, -2.8F, 6.4F, 1.6F, 5.6F)
                        .texOffs(21, 49).addBox(-3.4F, -0.1F, -3.0F, 6.8F, 0.9F, 6.0F)
                        .texOffs(49, 1).addBox(-0.5F, -5.5F, -2.6F, 1.0F, 1.2F, 1.0F)
                        .texOffs(1, 49).addBox(-1.8F, 0.2F, 3.4F, 3.6F, 0.6F, 1.8F),
                PartPose.offset(0.0F, 18.5F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 24).addBox(-2.8F, -1.2F, -0.6F, 5.6F, 2.4F, 0.8F)
                        .texOffs(1, 49).addBox(-0.9F, -0.9F, -0.9F, 1.8F, 0.6F, 0.5F)
                        .texOffs(49, 1).addBox(-1.35F, -1.5F, -1.05F, 0.8F, 1.2F, 0.6F)
                        .texOffs(49, 1).addBox(0.55F, -1.5F, -1.05F, 0.8F, 1.2F, 0.6F),
                PartPose.offset(0.0F, -1.8F, -3.8F));

        PartDefinition rightStalk = head.addOrReplaceChild("right_stalk",
                CubeListBuilder.create()
                        .texOffs(30, 24).addBox(-0.35F, -2.6F, -0.35F, 0.7F, 2.6F, 0.7F)
                        .texOffs(1, 1).addBox(-0.75F, -3.9F, -0.75F, 1.5F, 1.5F, 1.5F)
                        .texOffs(17, 1).addBox(-0.55F, -3.5F, -1.05F, 1.1F, 1.1F, 0.5F)
                        .texOffs(33, 1).addBox(-0.35F, -3.35F, -1.2F, 0.5F, 0.5F, 0.3F)
                        .texOffs(49, 1).addBox(-0.5F, -1.4F, -0.5F, 1.0F, 0.4F, 1.0F),
                PartPose.offsetAndRotation(-1.7F, -2.2F, -0.2F, 0.0F, 0.0F, 0.25F));
        rightStalk.addOrReplaceChild("dummy", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition leftStalk = head.addOrReplaceChild("left_stalk",
                CubeListBuilder.create()
                        .texOffs(30, 24).addBox(-0.35F, -2.6F, -0.35F, 0.7F, 2.6F, 0.7F)
                        .texOffs(1, 1).addBox(-0.75F, -3.9F, -0.75F, 1.5F, 1.5F, 1.5F)
                        .texOffs(17, 1).addBox(-0.55F, -3.5F, -1.05F, 1.1F, 1.1F, 0.5F)
                        .texOffs(33, 1).addBox(-0.15F, -3.35F, -1.2F, 0.5F, 0.5F, 0.3F)
                        .texOffs(49, 1).addBox(-0.5F, -1.4F, -0.5F, 1.0F, 0.4F, 1.0F),
                PartPose.offsetAndRotation(1.7F, -2.2F, -0.2F, 0.0F, 0.0F, -0.25F));
        leftStalk.addOrReplaceChild("dummy", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition rightArm = body.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(13, 24).addBox(-0.9F, -0.6F, -0.7F, 1.8F, 1.2F, 1.4F)
                        .texOffs(49, 1).addBox(-0.65F, -0.7F, -0.75F, 0.5F, 1.4F, 1.5F),
                PartPose.offsetAndRotation(-4.0F, -1.2F, -2.0F, 0.0F, 0.0F, 0.25F));
        PartDefinition rightForearm = rightArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(20, 24).addBox(-1.8F, -0.55F, -0.6F, 1.9F, 1.1F, 1.2F),
                PartPose.offsetAndRotation(-1.7F, 0.0F, 0.0F, 0.0F, -0.2F, 0.0F));
        rightForearm.addOrReplaceChild("pincer_top",
                CubeListBuilder.create().texOffs(49, 1).addBox(-1.4F, -0.55F, -0.65F, 1.5F, 0.8F, 1.3F),
                PartPose.offsetAndRotation(-1.8F, -0.15F, 0.0F, 0.0F, 0.0F, -0.12F));
        rightForearm.addOrReplaceChild("pincer_bottom",
                CubeListBuilder.create().texOffs(49, 1).addBox(-1.4F, -0.25F, -0.65F, 1.5F, 0.8F, 1.3F),
                PartPose.offsetAndRotation(-1.8F, 0.15F, 0.0F, 0.0F, 0.0F, 0.12F));

        PartDefinition leftArm = body.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(13, 24).addBox(-0.9F, -0.6F, -0.7F, 1.8F, 1.2F, 1.4F)
                        .texOffs(49, 1).addBox(0.35F, -0.7F, -0.75F, 0.5F, 1.4F, 1.5F),
                PartPose.offsetAndRotation(4.0F, -1.2F, -2.0F, 0.0F, 0.0F, -0.25F));
        PartDefinition leftForearm = leftArm.addOrReplaceChild("forearm",
                CubeListBuilder.create().texOffs(20, 24).addBox(-0.1F, -0.55F, -0.6F, 1.9F, 1.1F, 1.2F),
                PartPose.offsetAndRotation(1.7F, 0.0F, 0.0F, 0.0F, 0.2F, 0.0F));
        leftForearm.addOrReplaceChild("pincer_top",
                CubeListBuilder.create().texOffs(49, 1).addBox(-0.1F, -0.55F, -0.65F, 1.5F, 0.8F, 1.3F),
                PartPose.offsetAndRotation(1.8F, -0.15F, 0.0F, 0.0F, 0.0F, 0.12F));
        leftForearm.addOrReplaceChild("pincer_bottom",
                CubeListBuilder.create().texOffs(49, 1).addBox(-0.1F, -0.25F, -0.65F, 1.5F, 0.8F, 1.3F),
                PartPose.offsetAndRotation(1.8F, 0.15F, 0.0F, 0.0F, 0.0F, -0.12F));

        for (int i = 0; i < 3; i++) {
            float z = -2.2F + i * 2.2F;
            PartDefinition rightLeg = body.addOrReplaceChild("right_leg_" + i,
                    CubeListBuilder.create().texOffs(0, 30).addBox(-2.6F, -0.35F, -0.35F, 2.6F, 0.7F, 0.7F),
                    PartPose.offsetAndRotation(-3.9F, -0.3F, z, 0.0F, 0.0F, 0.5F));
            rightLeg.addOrReplaceChild("tip",
                    CubeListBuilder.create().texOffs(6, 30).addBox(-2.0F, -0.3F, -0.3F, 2.0F, 0.6F, 0.6F),
                    PartPose.offsetAndRotation(-2.6F, 0.0F, 0.0F, 0.0F, 0.0F, -0.6F));
            PartDefinition leftLeg = body.addOrReplaceChild("left_leg_" + i,
                    CubeListBuilder.create().texOffs(0, 30).addBox(0.0F, -0.35F, -0.35F, 2.6F, 0.7F, 0.7F),
                    PartPose.offsetAndRotation(3.9F, -0.3F, z, 0.0F, 0.0F, -0.5F));
            leftLeg.addOrReplaceChild("tip",
                    CubeListBuilder.create().texOffs(6, 30).addBox(0.0F, -0.3F, -0.3F, 2.0F, 0.6F, 0.6F),
                    PartPose.offsetAndRotation(2.6F, 0.0F, 0.0F, 0.0F, 0.0F, 0.6F));
        }

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WaterSpiritRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        this.root.y = ROOT_Y + wave(age, 0.1F, 0.35F);
        this.root.zRot = wave(age, 0.05F, 0.035F);
        this.body.zRot = wave(age, 0.08F, 0.04F);
        applyLook(state, this.head, 0.75F, 0.45F);

        this.rightStalk.zRot = 0.25F + wave(age, 0.09F, 0.12F);
        this.leftStalk.zRot = -0.25F - wave(age + 5.0F, 0.09F, 0.12F);
        this.rightStalk.xRot = wave(age + 2.0F, 0.07F, 0.06F);
        this.leftStalk.xRot = wave(age + 7.0F, 0.07F, 0.06F);

        float scissor = state.charging ? 0.38F : 0.08F + wave(age, 0.3F, 0.12F);
        this.rightArm.zRot = 0.25F + wave(age, 0.16F, 0.08F);
        this.leftArm.zRot = -0.25F - wave(age + 3.0F, 0.16F, 0.08F);
        this.rightArm.xRot = wave(age + 1.0F, 0.12F, 0.06F);
        this.leftArm.xRot = wave(age + 4.0F, 0.12F, 0.06F);
        this.rightForearm.yRot = -0.2F + wave(age, 0.18F, 0.08F);
        this.leftForearm.yRot = 0.2F - wave(age + 2.0F, 0.18F, 0.08F);
        this.rightPincerTop.zRot = -0.12F - scissor;
        this.rightPincerBottom.zRot = 0.12F + scissor;
        this.leftPincerTop.zRot = 0.12F + scissor;
        this.leftPincerBottom.zRot = -0.12F - scissor;

        for (int i = 0; i < 3; i++) {
            float phase = age + i * 2.4F;
            this.rightLegs[i].zRot = 0.5F + wave(phase, 0.18F, 0.13F);
            this.leftLegs[i].zRot = -0.5F - wave(phase + 1.2F, 0.18F, 0.13F);
            this.rightLegs[i].yRot = wave(phase, 0.15F, 0.16F);
            this.leftLegs[i].yRot = -wave(phase + 0.8F, 0.15F, 0.16F);
            this.rightLegTips[i].zRot = -0.6F - wave(phase + 0.5F, 0.2F, 0.12F);
            this.leftLegTips[i].zRot = 0.6F + wave(phase + 1.7F, 0.2F, 0.12F);
        }
    }

    @Override
    public void translateToHand(WaterSpiritRenderState state, HumanoidArm arm, PoseStack poseStack) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart activeArm = right ? this.rightArm : this.leftArm;
        this.root.translateAndRotate(poseStack);
        this.body.translateAndRotate(poseStack);
        activeArm.translateAndRotate(poseStack);
        poseStack.translate(right ? -2.2F / 16.0F : 2.2F / 16.0F, -0.2F / 16.0F, -0.65F / 16.0F);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.translate(right ? -0.08F : 0.08F, -0.12F, 0.1F);
    }
}
