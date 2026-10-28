package com.akun.xinkun.client;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.client.figurine.ColumbinaFigurineBlockRenderer;
import com.akun.xinkun.client.figurine.FurinaFigurineBlockRenderer;
import com.akun.xinkun.client.figurine.HutaoFigurineBlockRenderer;
import com.akun.xinkun.client.figurine.NahidaFigurineBlockRenderer;
import com.akun.xinkun.client.figurine.SandroneFigurineBlockRenderer;
import com.akun.xinkun.client.figurine.YoimiyaFigurineBlockRenderer;
import com.akun.xinkun.client.figurine.ZhongliFigurineBlockRenderer;
import com.akun.xinkun.client.particle.GlowDustParticle;
import com.akun.xinkun.entity.WaterSpiritKind;
import com.akun.xinkun.registry.ModBlockEntities;
import com.akun.xinkun.registry.ModEntities;
import com.akun.xinkun.registry.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.RabbitRenderer;

public class XinkunGenshinClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ParticleProviderRegistry.getInstance().register(ModParticles.GLOW_DUST, GlowDustParticle.Provider::new);
        WaterSpiritModelLayers.register();
        EntityRendererRegistry.register(ModEntities.FURINA,
                context -> new GenshinNpcRenderer(context, GenshinCharacter.FURINA, true));
        EntityRendererRegistry.register(ModEntities.HU_TAO,
                context -> new GenshinNpcRenderer(context, GenshinCharacter.HU_TAO, true));
        EntityRendererRegistry.register(ModEntities.YOIMIYA,
                context -> new GenshinNpcRenderer(context, GenshinCharacter.YOIMIYA, true));
        EntityRendererRegistry.register(ModEntities.COLUMBINA,
                context -> new GenshinNpcRenderer(context, GenshinCharacter.COLUMBINA, true));
        EntityRendererRegistry.register(ModEntities.ZHONGLI,
                context -> new GenshinNpcRenderer(context, GenshinCharacter.ZHONGLI, true));
        EntityRendererRegistry.register(ModEntities.NAHIDA,
                context -> new GenshinNpcRenderer(context, GenshinCharacter.NAHIDA, true));
        EntityRendererRegistry.register(ModEntities.SANDRONE,
                context -> new GenshinNpcRenderer(context, GenshinCharacter.SANDRONE, true));
        EntityRendererRegistry.register(ModEntities.ZHONGLI_STONE_SPEAR, ZhongliStoneSpearRenderer::new);
        EntityRendererRegistry.register(ModEntities.ZHONGLI_METEOR, ZhongliMeteorRenderer::new);
        EntityRendererRegistry.register(ModEntities.MOON_PHANTOM, MoonPhantomRenderer::new);
        // 月兔沿用原版兔子渲染器：外形与普通白兔完全一致。
        EntityRendererRegistry.register(ModEntities.MOON_RABBIT, RabbitRenderer::new);
        EntityRendererRegistry.register(ModEntities.WATER_SPIRIT_SEAHORSE,
                context -> new WaterSpiritRenderer(context, WaterSpiritKind.SEAHORSE));
        EntityRendererRegistry.register(ModEntities.WATER_SPIRIT_CRAB,
                context -> new WaterSpiritRenderer(context, WaterSpiritKind.CRAB));
        EntityRendererRegistry.register(ModEntities.WATER_SPIRIT_OCTOPUS,
                context -> new WaterSpiritRenderer(context, WaterSpiritKind.OCTOPUS));
        EntityRendererRegistry.register(ModEntities.FIREWORK_ROCKET, FireworkRocketRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.FIGURINE_COLUMBINA,
                ColumbinaFigurineBlockRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.FURINA_FIGURINE,
                FurinaFigurineBlockRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.FIGURINE_HUTAO,
                HutaoFigurineBlockRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.FIGURINE_YOIMIYA,
                YoimiyaFigurineBlockRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.FIGURINE_ZHONGLI,
                ZhongliFigurineBlockRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.FIGURINE_NAHIDA,
                NahidaFigurineBlockRenderer::new);
        BlockEntityRendererRegistry.register(ModBlockEntities.FIGURINE_SANDRONE,
                SandroneFigurineBlockRenderer::new);
    }
}
