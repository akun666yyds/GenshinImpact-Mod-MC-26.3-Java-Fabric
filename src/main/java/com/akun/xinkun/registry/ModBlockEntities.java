package com.akun.xinkun.registry;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.block.ColumbinaFigurineBlockEntity;
import com.akun.xinkun.block.FurinaFigurineBlockEntity;
import com.akun.xinkun.block.HutaoFigurineBlockEntity;
import com.akun.xinkun.block.NahidaFigurineBlockEntity;
import com.akun.xinkun.block.SandroneFigurineBlockEntity;
import com.akun.xinkun.block.YoimiyaFigurineBlockEntity;
import com.akun.xinkun.block.ZhongliFigurineBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<ColumbinaFigurineBlockEntity> FIGURINE_COLUMBINA =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    XinkunGenshinMod.id("figurine_columbina"),
                    new BlockEntityType<>(ColumbinaFigurineBlockEntity::new,
                            Set.of(ModBlocks.FIGURINE_COLUMBINA)));

    public static final BlockEntityType<FurinaFigurineBlockEntity> FURINA_FIGURINE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    XinkunGenshinMod.id("figurine_furina"),
                    new BlockEntityType<>(FurinaFigurineBlockEntity::new,
                            Set.of(ModBlocks.FIGURINE_FURINA)));

    public static final BlockEntityType<HutaoFigurineBlockEntity> FIGURINE_HUTAO =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    XinkunGenshinMod.id("figurine_hutao"),
                    new BlockEntityType<>(HutaoFigurineBlockEntity::new,
                            Set.of(ModBlocks.FIGURINE_HUTAO)));

    public static final BlockEntityType<YoimiyaFigurineBlockEntity> FIGURINE_YOIMIYA =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    XinkunGenshinMod.id("figurine_yoimiya"),
                    new BlockEntityType<>(YoimiyaFigurineBlockEntity::new,
                            Set.of(ModBlocks.FIGURINE_YOIMIYA)));

    public static final BlockEntityType<ZhongliFigurineBlockEntity> FIGURINE_ZHONGLI =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    XinkunGenshinMod.id("figurine_zhongli"),
                    new BlockEntityType<>(ZhongliFigurineBlockEntity::new,
                            Set.of(ModBlocks.FIGURINE_ZHONGLI)));

    public static final BlockEntityType<NahidaFigurineBlockEntity> FIGURINE_NAHIDA =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    XinkunGenshinMod.id("figurine_nahida"),
                    new BlockEntityType<>(NahidaFigurineBlockEntity::new,
                            Set.of(ModBlocks.FIGURINE_NAHIDA)));

    public static final BlockEntityType<SandroneFigurineBlockEntity> FIGURINE_SANDRONE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    XinkunGenshinMod.id("figurine_sandrone"),
                    new BlockEntityType<>(SandroneFigurineBlockEntity::new,
                            Set.of(ModBlocks.FIGURINE_SANDRONE)));

    private ModBlockEntities() {
    }

    public static void initialize() {
    }
}
