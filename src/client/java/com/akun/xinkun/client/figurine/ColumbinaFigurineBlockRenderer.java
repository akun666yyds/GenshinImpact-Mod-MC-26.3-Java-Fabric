package com.akun.xinkun.client.figurine;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.ColumbinaFigurineBlockEntity;
import java.util.Map;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * 「少女的手办」渲染器：哥伦比娅模型与贴图。
 */
public class ColumbinaFigurineBlockRenderer
        extends FigurineBlockRenderer<ColumbinaFigurineBlockEntity> {
    private static final Identifier MODEL =
            XinkunGenshinMod.id("figurine/columbina_figurine.glb");
    private static final Map<String, Identifier> TEXTURES = Map.of(
            "columbina_face", texture("face"),
            "columbina_eyewhite", texture("face"),
            "columbina_eye", texture("eye"),
            "columbina_hair", texture("hair"),
            "columbina_cloth", texture("cloth"),
            "columbina_skirt", texture("skirt"),
            "columbina_shoes", texture("shoes"));

    public ColumbinaFigurineBlockRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static Identifier texture(String name) {
        return XinkunGenshinMod.id("textures/entity/columbina/" + name + ".png");
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
