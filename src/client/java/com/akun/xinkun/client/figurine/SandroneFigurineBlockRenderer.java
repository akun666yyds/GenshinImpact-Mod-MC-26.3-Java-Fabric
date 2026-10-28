package com.akun.xinkun.client.figurine;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.SandroneFigurineBlockEntity;
import java.util.Map;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * 桑多涅手办渲染器。
 *
 * <p>模型由官方 FBX 静态网格转换而来，按原材质使用的贴图分成
 * 头发、外层衣物、内层衣物、脸与瞳孔共 5 个分区；脸贴使用游戏内修好的
 * 手绘眼版本，身体与裙装去掉 alpha 遮罩，保证手办全身完整可见。</p>
 */
public class SandroneFigurineBlockRenderer extends FigurineBlockRenderer<SandroneFigurineBlockEntity> {
    private static final Identifier MODEL = XinkunGenshinMod.id("figurine/sandrone_figurine.glb");
    private static final Map<String, Identifier> TEXTURES = Map.of(
            "sandrone_hair", texture("hair"),
            "sandrone_cloth", texture("cloth"),
            "sandrone_cloth2", texture("cloth2"),
            "sandrone_face", texture("face"),
            "sandrone_pupil", texture("pupil"));

    public SandroneFigurineBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Identifier texture(String name) {
        return XinkunGenshinMod.id("textures/entity/figurine/sandrone/" + name + ".png");
    }

    @Override
    protected Identifier modelLocation() {
        return MODEL;
    }

    @Override
    protected Map<String, Identifier> textures() {
        return TEXTURES;
    }
}
