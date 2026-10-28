package com.akun.xinkun.client;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.entity.GenshinNpcEntity;
import com.akun.xinkun.entity.NahidaEntity;
import com.akun.xinkun.entity.SandroneEntity;
import com.akun.xinkun.entity.YoimiyaEntity;
import com.akun.xinkun.entity.ZhongliEntity;
import com.akun.xinkun.client.rig.GenshinRigLayer;
import com.akun.xinkun.client.rig.RigModel;
import com.akun.xinkun.client.fx.SandroneCastFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 直接复用原版玩家骨骼与皮肤图层：基础模型 + 帽檐（发型/头饰）、外套、衣袖、裤装四层
 * 0.25 像素外扩的立体层全部开启，皮肤文件里的 3D 发型、首饰和服饰凸起会完整呈现。
 * 骨骼按角色皮肤类型选用 Alex（slim）或 Steve（wide）版本。
 *
 * 注意：渲染状态必须使用 HumanoidRenderState 而非 AvatarRenderState。
 * 26.x 的实体渲染调度器会把任何 AvatarRenderState 转交给原版玩家渲染器，
 * 那样本渲染器提交的模型和所有自定义图层都不会执行。
 */
public class GenshinNpcRenderer extends MobRenderer<GenshinNpcEntity, GenshinNpcRenderState, PlayerNpcModel> {
    private final GenshinCharacter character;
    private final Identifier openTexture;
    private final Identifier blinkTexture;
    /** 角色骨架网格；非空时隐藏玩家皮肤模型，改由 {@link GenshinRigLayer} 绘制。 */
    private final RigModel rig;
    /** 跨帧保留的招式/握持平滑量，按实体区分，同屏多个同名角色也不会串味。 */
    private final Map<GenshinNpcEntity, Smooth> smoothing = new WeakHashMap<>();

    public GenshinNpcRenderer(EntityRendererProvider.Context context, GenshinCharacter character) {
        this(context, character, false);
    }

    public GenshinNpcRenderer(EntityRendererProvider.Context context, GenshinCharacter character, boolean rigged) {
        super(context, createModel(context, character, rigged), 0.5F);
        this.character = character;
        this.rig = rigged ? RigModel.of(character) : null;
        this.openTexture = XinkunGenshinMod.id("textures/entity/" + character.id() + ".png");
        this.blinkTexture = XinkunGenshinMod.id("textures/entity/blink/" + character.id() + ".png");
        if (this.rig != null) {
            // 骨架模型自带完整衣物与头发，盔甲、帽子和鞘翅的原版立体层不再适用；
            // 手持物品仍然使用原版图层，由骨架肩关节定位。
            this.addLayer(new ItemInHandLayer<>(this));
            this.addLayer(new GenshinRigLayer(this, this.rig));
        } else {
            this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
            this.addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
            this.addLayer(new ItemInHandLayer<>(this));
            this.addLayer(new NpcArmorLayer(this, context, character.modelType() == PlayerModelType.SLIM));
        }
    }

    private static PlayerNpcModel createModel(EntityRendererProvider.Context context, GenshinCharacter character,
                                              boolean rigged) {
        boolean slim = character.modelType() == PlayerModelType.SLIM;
        RigModel rig = rigged ? RigModel.of(character) : null;
        return new PlayerNpcModel(
                context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim, rig, character);
    }

    @Override
    protected RenderType getRenderType(GenshinNpcRenderState state, boolean bodyVisible,
                                       boolean forceTransparent, boolean appearGlowing) {
        if (this.rig != null) {
            // 返回 null 只跳过玩家皮肤模型的提交，setupAnim 与图层仍会执行。
            return null;
        }
        return super.getRenderType(state, bodyVisible, forceTransparent, appearGlowing);
    }

    @Override
    public GenshinNpcRenderState createRenderState() {
        return new GenshinNpcRenderState();
    }

    @Override
    public void extractRenderState(GenshinNpcEntity entity, GenshinNpcRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
        state.id = entity.getId();
        state.leftArmPose = armPose(entity, HumanoidArm.LEFT);
        state.rightArmPose = armPose(entity, HumanoidArm.RIGHT);
        if (entity instanceof YoimiyaEntity yoimiya) {
            state.fireworkPose = yoimiya.getFireworkPose();
        }
        if (entity instanceof ZhongliEntity zhongli) {
            state.zhongliPose = zhongli.getActionPose();
        }
        if (entity instanceof NahidaEntity nahida) {
            state.nahidaBlooming = nahida.isBlooming();
        }
        if (entity instanceof SandroneEntity sandrone) {
            state.sandroneCasting = sandrone.isCastingFrost();
            SandroneCastFx.extract(state, sandrone);
        }
        state.speaking = entity.isTalking();
        state.blink = this.blinkAmount(state.id, partialTicks);
        this.updateWeights(entity, state);
    }

    /**
     * 26.3 每帧都会新建一个渲染状态，放在状态里的平滑量每帧都会被清零，
     * 永远只能走到目标的第一步。因此平滑值按实体缓存在渲染器里：
     * 招式权重约 0.12 秒铺开，握持约 0.15 秒收指，收招/收武器时同样缓慢回落。
     */
    private void updateWeights(GenshinNpcEntity entity, GenshinNpcRenderState state) {
        Smooth smooth = this.smoothing.computeIfAbsent(entity, key -> new Smooth());
        float delta = smooth.age < 0.0F ? 0.05F : state.ageInTicks - smooth.age;
        if (!(delta > 0.0F) || delta > 1.0F) {
            delta = 0.05F;
        }
        smooth.age = state.ageInTicks;
        float poseAlpha = 1.0F - (float)Math.exp(-delta / 0.12F);
        approach(smooth.poses, 0, state.fireworkPose == 1 ? 1.0F : 0.0F, poseAlpha);
        for (int pose = 1; pose <= 4; pose++) {
            approach(smooth.poses, pose, state.zhongliPose == pose ? 1.0F : 0.0F, poseAlpha);
        }
        approach(smooth.poses, 5, state.nahidaBlooming ? 1.0F : 0.0F, poseAlpha);
        System.arraycopy(smooth.poses, 0, state.poseWeights, 0, smooth.poses.length);
        float gripAlpha = 1.0F - (float)Math.exp(-delta / 0.15F);
        smooth.gripLeft = approachValue(smooth.gripLeft,
                state.leftHandItemStack.isEmpty() ? 0.0F : 1.0F, gripAlpha);
        smooth.gripRight = approachValue(smooth.gripRight,
                state.rightHandItemStack.isEmpty() ? 0.0F : 1.0F, gripAlpha);
        state.gripLeft = smooth.gripLeft;
        state.gripRight = smooth.gripRight;
    }

    private static void approach(float[] weights, int index, float target, float alpha) {
        float value = weights[index] + (target - weights[index]) * alpha;
        weights[index] = Math.abs(value - target) < 0.002F ? target : value;
    }

    private static float approachValue(float value, float target, float alpha) {
        float next = value + (target - value) * alpha;
        return Math.abs(next - target) < 0.002F ? target : next;
    }

    /** 每个实体一份、跨帧保留的平滑量。 */
    private static final class Smooth {
        float age = -1.0F;
        final float[] poses = new float[6];
        float gripLeft;
        float gripRight;
    }

    /**
     * 钟离比现有女角色高出一成。整体缩放放在提交前，模型、盔甲、
     * 手持长矛与名牌会一起放大，脚下仍然踩在同一格地面上。
     */
    @Override
    public void submit(GenshinNpcRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        float scale = this.character.modelScale();
        boolean sandrone = this.character == GenshinCharacter.SANDRONE;
        // 桑多涅施法时整体离地：脚底悬空约三分之一格，并带一点极慢的呼吸起伏。
        // 离地高度跟随平滑后的施法权重，收招时会缓慢落回地面而不是瞬间掉下去。
        float lift = sandrone
                ? state.castWeight * (0.32F + Mth.sin(state.ageInTicks * 0.08F) * 0.035F)
                : 0.0F;
        if (!sandrone && scale == 1.0F) {
            super.submit(state, poseStack, collector, camera);
            return;
        }
        poseStack.pushPose();
        if (lift != 0.0F) {
            poseStack.translate(0.0F, lift, 0.0F);
        }
        poseStack.scale(scale, scale, scale);
        super.submit(state, poseStack, collector, camera);
        if (sandrone) {
            // 傀儡与连续光束都画在角色本体之后：傀儡在身后，光束从掌心射向真实落点。
            SandroneCastFx.submit(poseStack, collector, state, lift);
        }
        poseStack.popPose();
    }

    private static HumanoidModel.ArmPose armPose(GenshinNpcEntity entity, HumanoidArm arm) {
        // 和玩家一样：拉弓时双臂呈瞄准姿势，而不是把弓平端在手里。
        // 生物的“使用物品”标记在客户端会被原版立刻清掉，所以读服务端同步的瞄准标记。
        if (entity instanceof YoimiyaEntity yoimiya
                && yoimiya.isAiming()
                && yoimiya.getMainHandItem().is(net.minecraft.world.item.Items.BOW)) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        return entity.getItemHeldByArm(arm).isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
    }

    /**
     * 完全照搬原版玩家游泳时的身体倾角：入水后整个人趴平成水平姿态，
     * 头朝游动方向，身体随镜头俯仰微调，并像玩家一样做 -1 格的视觉下沉补偿。
     * MobRenderer 默认不会为 Pose.SWIMMING 旋转身体，不补这一段就会直立着“游”。
     */
    @Override
    protected void setupRotations(GenshinNpcRenderState state, PoseStack poseStack, float bob, float yBodyRot) {
        if (state.swimAmount > 0.0F) {
            super.setupRotations(state, poseStack, bob, yBodyRot);
            float target = state.isInWater ? -90.0F - state.xRot : -90.0F;
            poseStack.rotateDegrees(Axis.XP, Mth.lerp(state.swimAmount, 0.0F, target));
            if (state.isVisuallySwimming) {
                poseStack.translate(0.0F, -1.0F, 0.3F);
            }
            return;
        }
        super.setupRotations(state, poseStack, bob, yBodyRot);
    }

    @Override
    public Identifier getTextureLocation(GenshinNpcRenderState state) {
        return this.isBlinking(state) ? this.blinkTexture : this.openTexture;
    }

    private boolean isBlinking(GenshinNpcRenderState state) {
        return state.blink > 0.35F;
    }

    /**
     * 0~1 的平滑眨眼进度。每个实体 id 与角色编号给出不同周期，偶尔连眨两下。
     * 皮肤渲染看整张眨眼贴图，骨架渲染按同一进度压扁眼睛网格。
     */
    private float blinkAmount(int id, float partialTicks) {
        if (Minecraft.getInstance().level == null) {
            return 0.0F;
        }
        float ticks = Minecraft.getInstance().level.getGameTime() + partialTicks;
        int seed = Math.floorMod(id * 37 + this.character.ordinal() * 101, 211);
        int cycle = 88 + Math.floorMod(seed, 53);
        float phase = Math.floorMod((long)ticks + seed, cycle);
        float amount = blinkCurve(phase);
        if (Math.floorMod(seed, 5) == 0 && cycle > 100) {
            amount = Math.max(amount, blinkCurve(Math.floorMod((long)phase - 8L, cycle)));
        }
        return amount;
    }

    private static float blinkCurve(float phase) {
        // 4 tick 闭合再张开：正弦半波，帧间不会跳变。
        if (phase < 0.0F || phase >= 4.0F) {
            return 0.0F;
        }
        return Mth.sin(phase * 0.25F * (float)Math.PI);
    }

    private static final class NpcArmorLayer extends RenderLayer<GenshinNpcRenderState, PlayerNpcModel> {
        private final HumanoidArmorLayer<GenshinNpcRenderState, PlayerNpcModel, PlayerNpcModel> armor;

        private NpcArmorLayer(RenderLayerParent<GenshinNpcRenderState, PlayerNpcModel> parent,
                              EntityRendererProvider.Context context, boolean slim) {
            super(parent);
            ArmorModelSet<ModelLayerLocation> layers =
                    slim ? ModelLayers.PLAYER_SLIM_ARMOR : ModelLayers.PLAYER_ARMOR;
            this.armor = new HumanoidArmorLayer<>(parent,
                    ArmorModelSet.bake(layers, context.getModelSet(), part -> new PlayerNpcModel(part, slim, null, null)),
                    context.getEquipmentRenderer());
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                           int lightCoords, GenshinNpcRenderState state, float yRot, float xRot) {
            this.armor.submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot);
        }
    }
}
