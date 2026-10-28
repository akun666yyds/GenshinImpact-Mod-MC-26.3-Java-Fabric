package com.akun.xinkun.client.figurine;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.NahidaFigurineBlockEntity;
import java.util.Map;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * 纳西妲手办渲染器。
 *
 * <p>模型由随包附带的 MMD(PMX) 静态网格转换而来，按原材质使用的贴图分成
 * 脸、头发、衣物、皮肤共 4 个分区。</p>
 */
public class NahidaFigurineBlockRenderer extends FigurineBlockRenderer<NahidaFigurineBlockEntity> {
    private static final Identifier MODEL = XinkunGenshinMod.id("figurine/nahida_figurine.glb");
    private static final Map<String, Identifier> TEXTURES = Map.of(
            "nahida_face", texture("face"),
            "nahida_hair", texture("hair"),
            "nahida_cloth", texture("cloth"),
            "nahida_skin", texture("skin"));

    public NahidaFigurineBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Identifier texture(String name) {
        return XinkunGenshinMod.id("textures/entity/figurine/nahida/" + name + ".png");
    }

    @Override
    protected Identifier modelLocation() {
        return MODEL;
    }

    @Override
    protected Map<String, Identifier> textures() {
        return TEXTURES;
    }

    /** 纳西妲是孩童身材：按原神设定比例明显矮于成年角色。 */
    @Override
    protected float targetHeight() {
        return 0.54F;
    }
}
