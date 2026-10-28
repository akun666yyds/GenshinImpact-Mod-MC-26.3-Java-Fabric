package com.akun.xinkun.client;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.entity.WaterSpiritEntity;
import com.akun.xinkun.entity.WaterSpiritKind;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public class WaterSpiritRenderer
        extends MobRenderer<WaterSpiritEntity, WaterSpiritRenderState, WaterSpiritModel> {
    private final Identifier texture;

    public WaterSpiritRenderer(EntityRendererProvider.Context context, WaterSpiritKind kind) {
        super(context, createModel(context, kind), 0.28F);
        this.texture = XinkunGenshinMod.id("textures/entity/water_spirit/" + kind.id() + ".png");
        this.addLayer(new ItemInHandLayer<>(this));
    }

    private static WaterSpiritModel createModel(EntityRendererProvider.Context context, WaterSpiritKind kind) {
        return switch (kind) {
            case SEAHORSE -> new SeahorseSpiritModel(context.bakeLayer(WaterSpiritModelLayers.SEAHORSE));
            case CRAB -> new CrabSpiritModel(context.bakeLayer(WaterSpiritModelLayers.CRAB));
            case OCTOPUS -> new OctopusSpiritModel(context.bakeLayer(WaterSpiritModelLayers.OCTOPUS));
        };
    }

    @Override
    public WaterSpiritRenderState createRenderState() {
        return new WaterSpiritRenderState();
    }

    @Override
    public void extractRenderState(WaterSpiritEntity entity, WaterSpiritRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, partialTicks);
        state.charging = entity.isCharging();
    }

    @Override
    public Identifier getTextureLocation(WaterSpiritRenderState state) {
        return this.texture;
    }

    @Override
    protected int getBlockLightLevel(WaterSpiritEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    protected void scale(WaterSpiritRenderState state, PoseStack poseStack) {
        poseStack.scale(0.85F, 0.85F, 0.85F);
    }
}
