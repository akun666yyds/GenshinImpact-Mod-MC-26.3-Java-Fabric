package com.akun.xinkun.client;

import com.akun.xinkun.XinkunGenshinMod;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

public final class WaterSpiritModelLayers {
    public static final ModelLayerLocation SEAHORSE = create("water_spirit_seahorse");
    public static final ModelLayerLocation CRAB = create("water_spirit_crab");
    public static final ModelLayerLocation OCTOPUS = create("water_spirit_octopus");

    private WaterSpiritModelLayers() {
    }

    public static void register() {
        ModelLayerRegistry.registerModelLayer(SEAHORSE, SeahorseSpiritModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(CRAB, CrabSpiritModel::createBodyLayer);
        ModelLayerRegistry.registerModelLayer(OCTOPUS, OctopusSpiritModel::createBodyLayer);
    }

    private static ModelLayerLocation create(String name) {
        return new ModelLayerLocation(XinkunGenshinMod.id(name), "main");
    }
}
