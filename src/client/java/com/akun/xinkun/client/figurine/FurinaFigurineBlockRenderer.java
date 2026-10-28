package com.akun.xinkun.client.figurine;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.FurinaFigurineBlockEntity;
import java.util.Map;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * 芙宁娜手办渲染器。
 *
 * <p>模型由随包附带的 PMX 静态网格转换而来，按原材质使用的贴图分成
 * 脸、头发/身体、后发、衣物四个分区。</p>
 */
public class FurinaFigurineBlockRenderer extends FigurineBlockRenderer<FurinaFigurineBlockEntity> {
    private static final Identifier MODEL = XinkunGenshinMod.id("figurine/furina_figurine.glb");
    private static final Map<String, Identifier> TEXTURES = Map.of(
            "furina_face", texture("face"),
            "furina_hair", texture("hair"),
            "furina_hair2", texture("hair2"),
            "furina_cloth", texture("cloth"));

    public FurinaFigurineBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Identifier texture(String name) {
        return XinkunGenshinMod.id("textures/entity/furina/" + name + ".png");
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
