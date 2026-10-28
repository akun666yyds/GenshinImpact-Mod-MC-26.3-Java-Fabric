package com.akun.xinkun.client.figurine;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.AbstractFigurineBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 角色手办方块实体的公共渲染器：把随模组内置的 GLB 网格按材质分区画出来。
 *
 * <p>模型是 glTF 的 Y 轴向上结构，正面朝 +Z；这里按方块的 16 档旋转
 * （每档 22.5°）绕 Y 轴旋转，让手办可以朝向任意方向。模型统一缩放到
 * 0.72 格高，底座正好落在方块顶面。具体模型与贴图由子类提供。</p>
 */
public abstract class FigurineBlockRenderer<T extends BlockEntity>
        implements BlockEntityRenderer<T, FigurineBlockRenderer.RenderState> {
    private static final Logger LOGGER = LoggerFactory.getLogger("xinkun_genshin/figurine");
    /** 纯白贴图，供只有顶点颜色的底座、圆环等材质使用。 */
    private static final Identifier WHITE = XinkunGenshinMod.id("textures/entity/figurine/white.png");
    /** 默认高度：略低于一格，像摆在方块上的桌面手办。角色可按设定覆盖。 */
    private static final float DEFAULT_TARGET_HEIGHT = 0.72F;
    /** 底座底面再抬高一点，避免与方块顶面叠在一起闪烁。 */
    private static final float GROUND_CLEARANCE = 0.002F;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** 16 档旋转中每一档的角度。 */
    public static final float DEGREES_PER_ROTATION = 22.5F;

    private final Map<String, RenderType> renderTypes = new HashMap<>();
    private Map<String, Identifier> textureMap;
    private GlbMesh mesh;
    private boolean loadFailed;
    private float scale = DEFAULT_TARGET_HEIGHT;
    private float lift;

    /** 手办 GLB 在资源包里的位置。 */
    protected abstract Identifier modelLocation();

    /** 材质名到贴图的映射；未命中的材质使用纯白贴图与顶点颜色。 */
    protected abstract Map<String, Identifier> textures();

    /**
     * 手办总高度（含底座）。默认 0.72 格；纳西妲与钟离按原神设定身高
     * 分别调低/调高，摆在一起时能看到与游戏内一致的身材比例。
     */
    protected float targetHeight() {
        return DEFAULT_TARGET_HEIGHT;
    }

    protected FigurineBlockRenderer() {
    }

    @Override
    public RenderState createRenderState() {
        return new RenderState();
    }

    @Override
    public void extractRenderState(T blockEntity, RenderState state, float partialTick,
                                   Vec3 cameraPos,
                                   ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
        state.rotation = blockEntity.getBlockState().getValue(AbstractFigurineBlock.ROTATION);
    }

    @Override
    public void submit(RenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        GlbMesh model = this.model();
        if (model == null) {
            return;
        }
        poseStack.pushPose();
        // 方块坐标原点是方块最小角，先移到方块中心。
        poseStack.translate(0.5F, 0.0F, 0.5F);
        // 模型正面朝 +Z，档位 0 即为朝南；正角度旋转会把 +Z 转向 -X，所以取负。
        poseStack.rotate(Axis.YP.rotationDegrees(-state.rotation * DEGREES_PER_ROTATION));
        poseStack.translate(0.0F, this.lift, 0.0F);
        poseStack.scale(this.scale, this.scale, this.scale);
        for (GlbMesh.Part part : model.parts()) {
            RenderType type = this.typeFor(part.material());
            int light = part.emissive() ? FULL_BRIGHT : state.lightCoords;
            collector.submitCustomGeometry(poseStack, type,
                    (pose, buffer) -> emit(pose, buffer, part, light));
        }
        poseStack.popPose();
    }

    private RenderType typeFor(String material) {
        Identifier texture = this.textureMap().getOrDefault(material, WHITE);
        return this.renderTypes.computeIfAbsent(texture.toString(),
                key -> RenderTypes.entityCutout(texture));
    }

    private Map<String, Identifier> textureMap() {
        if (this.textureMap == null) {
            this.textureMap = this.textures();
        }
        return this.textureMap;
    }

    private GlbMesh model() {
        if (this.mesh == null && !this.loadFailed) {
            Identifier location = this.modelLocation();
            try {
                this.mesh = GlbMesh.load(location);
                float minY = this.mesh.minY();
                float height = this.mesh.maxY() - minY;
                float target = this.targetHeight();
                this.scale = height > 0.001F ? target / height : target;
                this.lift = -minY * this.scale + GROUND_CLEARANCE;
                LOGGER.info("手办模型已载入：{}，{} 个材质分区，高 {} 格",
                        location, this.mesh.parts().size(),
                        String.format("%.3f", height * this.scale));
            } catch (RuntimeException exception) {
                this.loadFailed = true;
                LOGGER.error("手办模型载入失败，将不再尝试：{}", location, exception);
            }
        }
        return this.mesh;
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, GlbMesh.Part part,
                             int light) {
        float[] positions = part.positions();
        float[] normals = part.normals();
        float[] uvs = part.uvs();
        int[] indices = part.indices();
        int tint = part.tint();
        // 26.3 的实体渲染管线是 QUADS 拓扑，顶点按四个一组切成四边形；
        // 直接写三角形会让相邻三角形被拼成四边形，模型出现撕裂纹。
        // 这里把每个三角形写成 (a, b, c, c) 的退化四边形，第二个三角形面积为零，
        // 实际结果与三角形完全一致。
        for (int i = 0; i + 2 < indices.length; i += 3) {
            int a = indices[i];
            int b = indices[i + 1];
            int c = indices[i + 2];
            vertex(pose, buffer, positions, normals, uvs, a, tint, light);
            vertex(pose, buffer, positions, normals, uvs, b, tint, light);
            vertex(pose, buffer, positions, normals, uvs, c, tint, light);
            vertex(pose, buffer, positions, normals, uvs, c, tint, light);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float[] positions,
                               float[] normals, float[] uvs, int index, int tint, int light) {
        buffer.addVertex(pose,
                        positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2])
                .setColor(tint)
                .setUv(uvs[index * 2], uvs[index * 2 + 1])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normals[index * 3], normals[index * 3 + 1],
                        normals[index * 3 + 2]);
    }

    /** 方块实体渲染状态：只需要记录 16 档朝向。 */
    public static class RenderState extends BlockEntityRenderState {
        public int rotation;
    }
}
