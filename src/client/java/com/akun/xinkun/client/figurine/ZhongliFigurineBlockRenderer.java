package com.akun.xinkun.client.figurine;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.ZhongliFigurineBlockEntity;
import java.util.Map;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * 钟离手办渲染器。
 *
 * <p>模型由随包附带的 MMD(PMX) 静态网格转换而来，按原材质使用的贴图分成
 * 脸、头发、衣物、脸颊共 4 个分区。</p>
 */
public class ZhongliFigurineBlockRenderer extends FigurineBlockRenderer<ZhongliFigurineBlockEntity> {
    private static final Identifier MODEL = XinkunGenshinMod.id("figurine/zhongli_figurine.glb");
    private static final Map<String, Identifier> TEXTURES = Map.of(
            "zhongli_face", texture("face"),
            "zhongli_hair", texture("hair"),
            "zhongli_cloth", texture("cloth"),
            "zhongli_blush", texture("blush"));

    public ZhongliFigurineBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Identifier texture(String name) {
        return XinkunGenshinMod.id("textures/entity/figurine/zhongli/" + name + ".png");
    }

    @Override
    protected Identifier modelLocation() {
        return MODEL;
    }

    @Override
    protected Map<String, Identifier> textures() {
        return TEXTURES;
    }

    /** 钟离是成年男性中最高的一档：比默认手办略高一成。 */
    @Override
    protected float targetHeight() {
        return 0.79F;
    }
}
