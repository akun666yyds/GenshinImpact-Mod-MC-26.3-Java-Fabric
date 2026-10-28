package com.akun.xinkun;

import com.akun.xinkun.building.BuildingType;
import com.akun.xinkun.event.ModEvents;
import com.akun.xinkun.registry.ModBlockEntities;
import com.akun.xinkun.registry.ModBlocks;
import com.akun.xinkun.registry.ModEffects;
import com.akun.xinkun.registry.ModEntities;
import com.akun.xinkun.registry.ModItems;
import com.akun.xinkun.registry.ModParticles;
import com.akun.xinkun.registry.ModSounds;
import com.akun.xinkun.registry.ModTabs;
import com.akun.xinkun.voice.ModVoices;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XinkunGenshinMod implements ModInitializer {
    public static final String MOD_ID = "xinkun_genshin";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModSounds.initialize();
        ModEffects.initialize();
        ModParticles.initialize();
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModEntities.initialize();
        ModItems.initialize();
        ModTabs.initialize();
        ModVoices.initialize();
        ModEvents.initialize();
        LOGGER.info("新坤的原神角色已载入：芙宁娜、胡桃、宵宫、哥伦比娅、钟离、纳西妲，以及 {} 件建筑道具",
                BuildingType.values().length);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
