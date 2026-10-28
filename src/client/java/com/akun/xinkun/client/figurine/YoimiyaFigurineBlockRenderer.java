package com.akun.xinkun.client.figurine;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.YoimiyaFigurineBlockEntity;
import java.util.Map;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * 宵宫手办渲染器。
 *
 * <p>模型由随包附带的 MMD(PMX) 静态网格转换而来，按原材质使用的贴图分成
 * 脸、头发、衣物、皮肤共 4 个分区。</p>
 */
public class YoimiyaFigurineBlockRenderer extends FigurineBlockRenderer<YoimiyaFigurineBlockEntity> {
    private static final Identifier MODEL = XinkunGenshinMod.id("figurine/yoimiya_figurine.glb");
    private static final Map<String, Identifier> TEXTURES = Map.of(
            "yoimiya_face", texture("face"),
            "yoimiya_hair", texture("hair"),
            "yoimiya_cloth", texture("cloth"),
            "yoimiya_skin", texture("skin"));

    public YoimiyaFigurineBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Identifier texture(String name) {
        return XinkunGenshinMod.id("textures/entity/figurine/yoimiya/" + name + ".png");
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
