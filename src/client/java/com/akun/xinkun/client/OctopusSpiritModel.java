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
 * 蓝色章鱼水灵：圆润的软体头罩、金色饰片、两侧耳鳍与六条会摆动的小触手。
 */
public class OctopusSpiritModel extends WaterSpiritModel {
    private static final int TENTACLES = 6;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightFin;
    private final ModelPart leftFin;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart[] tentacles = new ModelPart[TENTACLES];
    private final ModelPart[] tentacleMids = new ModelPart[TENTACLES];
    private final ModelPart[] tentacleTips = new ModelPart[TENTACLES];

    public OctopusSpiritModel(ModelPart root) {
        super(root.getChild("root"));
        this.root = root.getChild("root");
        this.body = this.root.getChild("body");
        this.head = this.body.getChild("head");
        this.rightFin = this.body.getChild("right_fin");
        this.leftFin = this.body.getChild("left_fin");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        for (int i = 0; i < TENTACLES; i++) {
            this.tentacles[i] = this.body.getChild("tentacle_" + i);
            this.tentacleMids[i] = this.tentacles[i].getChild("mid");
            this.tentacleTips[i] = this.tentacleMids[i].getChild("tip");
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition base = root.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, ROOT_Y, 0.0F));

        PartDefinition body = base.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 10).addBox(-3.4F, -4.2F, -3.4F, 6.8F, 4.6F, 6.8F)
                        .texOffs(29, 10).addBox(-2.5F, -6.0F, -2.5F, 5.0F, 2.2F, 5.0F)
                        .texOffs(25, 49).addBox(-2.6F, 0.0F, -2.6F, 5.2F, 0.9F, 5.2F)
                        .texOffs(49, 1).addBox(-1.95F, -4.5F, -3.55F, 0.8F, 0.8F, 0.4F)
                        .texOffs(49, 1).addBox(-0.4F, -4.5F, -3.55F, 0.8F, 0.8F, 0.4F)
                        .texOffs(49, 1).addBox(1.15F, -4.5F, -3.55F, 0.8F, 0.8F, 0.4F),
                PartPose.offset(0.0F, 18.5F, 0.0F));

        PartDefinition head = body.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(1, 1).addBox(-2.2F, -1.1F, -0.55F, 1.8F, 1.8F, 0.6F)
                        .texOffs(1, 1).addBox(0.4F, -1.1F, -0.55F, 1.8F, 1.8F, 0.6F)
                        .texOffs(17, 1).addBox(-1.9F, -0.8F, -0.85F, 1.2F, 1.2F, 0.4F)
                        .texOffs(17, 1).addBox(0.7F, -0.8F, -0.85F, 1.2F, 1.2F, 0.4F)
                        .texOffs(33, 1).addBox(-1.55F, -0.65F, -1.0F, 0.5F, 0.5F, 0.3F)
                        .texOffs(33, 1).addBox(1.05F, -0.65F, -1.0F, 0.5F, 0.5F, 0.3F)
                        .texOffs(1, 49).addBox(-0.5F, 0.9F, -0.8F, 1.0F, 0.5F, 0.4F)
                        .texOffs(1, 49).addBox(-2.9F, 0.3F, -0.7F, 0.8F, 0.6F, 0.4F)
                        .texOffs(1, 49).addBox(2.1F, 0.3F, -0.7F, 0.8F, 0.6F, 0.4F),
                PartPose.offset(0.0F, -2.0F, -3.2F));

        body.addOrReplaceChild("right_fin",
                CubeListBuilder.create().texOffs(1, 49).addBox(-0.4F, -2.2F, -1.2F, 0.4F, 3.0F, 2.4F),
                PartPose.offsetAndRotation(-3.4F, -3.2F, 0.2F, 0.0F, 0.0F, -0.6F));
        body.addOrReplaceChild("left_fin",
                CubeListBuilder.create().texOffs(1, 49).addBox(0.0F, -2.2F, -1.2F, 0.4F, 3.0F, 2.4F),
                PartPose.offsetAndRotation(3.4F, -3.2F, 0.2F, 0.0F, 0.0F, 0.6F));

        PartDefinition rightArm = body.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(50, 10).addBox(-0.45F, 0.0F, -0.45F, 0.9F, 2.0F, 0.9F)
                        .texOffs(50, 14).addBox(-0.55F, 1.9F, -0.8F, 1.1F, 0.9F, 1.4F)
                        .texOffs(49, 1).addBox(-0.5F, 1.55F, -0.5F, 1.0F, 0.3F, 1.0F),
                PartPose.offsetAndRotation(-2.4F, -2.2F, -2.4F, -0.25F, 0.0F, -0.35F));
        rightArm.addOrReplaceChild("dummy", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition leftArm = body.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(50, 10).addBox(-0.45F, 0.0F, -0.45F, 0.9F, 2.0F, 0.9F)
                        .texOffs(50, 14).addBox(-0.55F, 1.9F, -0.8F, 1.1F, 0.9F, 1.4F)
                        .texOffs(49, 1).addBox(-0.5F, 1.55F, -0.5F, 1.0F, 0.3F, 1.0F),
                PartPose.offsetAndRotation(2.4F, -2.2F, -2.4F, -0.25F, 0.0F, 0.35F));
        leftArm.addOrReplaceChild("dummy", CubeListBuilder.create(), PartPose.ZERO);

        float[][] spots = {
                {-2.2F, -1.8F, 0.32F, -0.1F},
                {2.2F, -1.8F, -0.32F, -0.1F},
                {-3.0F, 0.2F, 0.55F, 0.0F},
                {3.0F, 0.2F, -0.55F, 0.0F},
                {-1.7F, 2.2F, 0.25F, 0.15F},
                {1.7F, 2.2F, -0.25F, 0.15F}
        };
        for (int i = 0; i < TENTACLES; i++) {
            float x = spots[i][0];
            float z = spots[i][1];
            float zRot = spots[i][2];
            float xRot = spots[i][3];
            PartDefinition tentacle = body.addOrReplaceChild("tentacle_" + i,
                    CubeListBuilder.create().texOffs(0, 24).addBox(-0.7F, 0.0F, -0.7F, 1.4F, 2.2F, 1.4F),
                    PartPose.offsetAndRotation(x, 0.6F, z, xRot, 0.0F, zRot));
            PartDefinition mid = tentacle.addOrReplaceChild("mid",
                    CubeListBuilder.create().texOffs(16, 24).addBox(-0.55F, 0.0F, -0.55F, 1.1F, 1.9F, 1.1F),
                    PartPose.offsetAndRotation(0.0F, 2.2F, 0.0F, 0.3F, 0.0F, 0.0F));
            mid.addOrReplaceChild("tip",
                    CubeListBuilder.create()
                            .texOffs(24, 24).addBox(-0.4F, 0.0F, -0.4F, 0.8F, 1.6F, 0.8F)
                            .texOffs(49, 49).addBox(-0.3F, 1.6F, -0.3F, 0.6F, 0.9F, 0.6F),
                    PartPose.offsetAndRotation(0.0F, 1.9F, 0.0F, 0.35F, 0.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(WaterSpiritRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        this.root.y = ROOT_Y + wave(age, 0.09F, 0.4F);
        this.root.zRot = wave(age, 0.06F, 0.04F);
        this.body.xRot = wave(age, 0.08F, 0.03F);
        applyLook(state, this.head, 0.8F, 0.5F);

        this.rightFin.zRot = -0.6F - wave(age, 0.16F, 0.28F);
        this.leftFin.zRot = 0.6F + wave(age, 0.16F, 0.28F);

        for (int i = 0; i < TENTACLES; i++) {
            float phase = age + i * 1.35F;
            float side = i % 2 == 0 ? 1.0F : -1.0F;
            this.tentacles[i].yRot = side * wave(phase, 0.09F, 0.18F);
            this.tentacles[i].xRot = wave(phase, 0.13F, 0.16F) + (state.charging ? -0.18F : 0.0F);
            this.tentacleMids[i].xRot = 0.3F + wave(phase + 0.7F, 0.15F, 0.22F);
            this.tentacleTips[i].xRot = 0.35F + wave(phase + 1.4F, 0.17F, 0.25F);
            this.tentacleTips[i].yRot = side * wave(phase + 1.0F, 0.11F, 0.2F);
        }

        float armSwing = wave(age, 0.2F, 0.08F);
        float basePitch = state.charging ? -1.25F : -0.25F;
        this.rightArm.xRot = basePitch + armSwing;
        this.leftArm.xRot = basePitch - armSwing;
        this.rightArm.zRot = state.charging ? -0.5F : -0.35F;
        this.leftArm.zRot = state.charging ? 0.5F : 0.35F;
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
        poseStack.translate(0.0F, 2.25F / 16.0F, -0.3F / 16.0F);
        poseStack.scale(0.55F, 0.55F, 0.55F);
        poseStack.translate(right ? -0.06F : 0.06F, -0.16F, 0.1F);
    }
}
