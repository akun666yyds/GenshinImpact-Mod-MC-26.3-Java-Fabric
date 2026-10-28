package com.akun.xinkun.client.figurine;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.HutaoFigurineBlockEntity;
import java.util.Map;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * 胡桃手办渲染器。
 *
 * <p>模型由随包附带的 MMD(PMX) 静态网格转换而来，按原材质使用的贴图分成
 * 脸、头发、衣物共 3 个分区。</p>
 */
public class HutaoFigurineBlockRenderer extends FigurineBlockRenderer<HutaoFigurineBlockEntity> {
    private static final Identifier MODEL = XinkunGenshinMod.id("figurine/hutao_figurine.glb");
    private static final Map<String, Identifier> TEXTURES = Map.of(
            "hutao_face", texture("face"),
            "hutao_hair", texture("hair"),
            "hutao_cloth", texture("cloth"));

    public HutaoFigurineBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Identifier texture(String name) {
        return XinkunGenshinMod.id("textures/entity/figurine/hutao/" + name + ".png");
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
