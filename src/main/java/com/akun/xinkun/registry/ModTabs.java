package com.akun.xinkun.registry;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.building.BuildingType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModTabs {
    public static final ResourceKey<CreativeModeTab> GENSHIN = key("genshin");
    public static final ResourceKey<CreativeModeTab> ITEMS = key("items");
    public static final ResourceKey<CreativeModeTab> BUILDINGS_GENERIC = key("buildings_generic");
    public static final ResourceKey<CreativeModeTab> BUILDINGS_CHARACTER = key("buildings_character");

    private ModTabs() {
    }

    private static ResourceKey<CreativeModeTab> key(String name) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, XinkunGenshinMod.id(name));
    }

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, GENSHIN, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.xinkun_genshin.genshin"))
                .icon(() -> new ItemStack(ModItems.FURINA_SPAWN_EGG))
                .displayItems((parameters, output) -> {
                    output.accept(ModItems.FURINA_SPAWN_EGG);
                    output.accept(ModItems.HU_TAO_SPAWN_EGG);
                    output.accept(ModItems.YOIMIYA_SPAWN_EGG);
                    output.accept(ModItems.COLUMBINA_SPAWN_EGG);
                    output.accept(ModItems.ZHONGLI_SPAWN_EGG);
                    output.accept(ModItems.NAHIDA_SPAWN_EGG);
                    output.accept(ModItems.SANDRONE_SPAWN_EGG);
                })
                .build());

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ITEMS, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.xinkun_genshin.items"))
                .icon(() -> new ItemStack(ModItems.MOONCAKE))
                .displayItems((parameters, output) -> {
                    output.accept(ModItems.MOONCAKE);
                    output.accept(ModItems.VERDANT_CODEX);
                    output.accept(ModItems.FIGURINE_COLUMBINA);
                    output.accept(ModItems.FIGURINE_FURINA);
                    output.accept(ModItems.FIGURINE_HUTAO);
                    output.accept(ModItems.FIGURINE_YOIMIYA);
                    output.accept(ModItems.FIGURINE_ZHONGLI);
                    output.accept(ModItems.FIGURINE_NAHIDA);
                    output.accept(ModItems.FIGURINE_SANDRONE);
                })
                .build());

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BUILDINGS_GENERIC, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.xinkun_genshin.buildings_generic"))
                .icon(() -> new ItemStack(ModItems.BUILDINGS.get(BuildingType.LUXURY_VILLA)))
                .displayItems((parameters, output) -> {
                    for (BuildingType type : BuildingType.values()) {
                        if (type.isGeneric()) {
                            output.accept(ModItems.BUILDINGS.get(type));
                        }
                    }
                })
                .build());

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BUILDINGS_CHARACTER, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.xinkun_genshin.buildings_character"))
                .icon(() -> new ItemStack(ModItems.BUILDINGS.get(BuildingType.FURINA_PALACE)))
                .displayItems((parameters, output) -> {
                    for (BuildingType type : BuildingType.values()) {
                        if (!type.isGeneric()) {
                            output.accept(ModItems.BUILDINGS.get(type));
                        }
                    }
                })
                .build());
    }
}
