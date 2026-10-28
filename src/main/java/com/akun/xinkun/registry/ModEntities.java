package com.akun.xinkun.registry;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.entity.ColumbinaEntity;
import com.akun.xinkun.entity.FurinaEntity;
import com.akun.xinkun.entity.GenshinNpcEntity;
import com.akun.xinkun.entity.HuTaoEntity;
import com.akun.xinkun.entity.MoonPhantomEntity;
import com.akun.xinkun.entity.MoonRabbitEntity;
import com.akun.xinkun.entity.NahidaEntity;
import com.akun.xinkun.entity.SandroneEntity;
import com.akun.xinkun.entity.WaterSpiritEntity;
import com.akun.xinkun.entity.WaterSpiritKind;
import com.akun.xinkun.entity.YoimiyaEntity;
import com.akun.xinkun.entity.ZhongliEntity;
import com.akun.xinkun.entity.ZhongliMeteorEntity;
import com.akun.xinkun.entity.ZhongliStoneSpearEntity;
import com.akun.xinkun.entity.projectile.FireworkRocketProjectile;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<FurinaEntity> FURINA = register("furina", FurinaEntity::new);
    public static final EntityType<HuTaoEntity> HU_TAO = register("hu_tao", HuTaoEntity::new);
    public static final EntityType<YoimiyaEntity> YOIMIYA = register("yoimiya", YoimiyaEntity::new);
    public static final EntityType<ColumbinaEntity> COLUMBINA = register("columbina", ColumbinaEntity::new);
    /** 巨型月影：没有碰撞、不可攻击的纯视觉实体，攻击由月光脉冲完成。 */
    public static final EntityType<MoonPhantomEntity> MOON_PHANTOM = registerMoonPhantom();
    /** 月兔：月之领域里唤来的白兔宠物，脱战十秒后散去。 */
    public static final EntityType<MoonRabbitEntity> MOON_RABBIT = registerMoonRabbit();
    public static final EntityType<WaterSpiritEntity> WATER_SPIRIT_SEAHORSE =
            registerSpirit(WaterSpiritKind.SEAHORSE);
    public static final EntityType<WaterSpiritEntity> WATER_SPIRIT_CRAB =
            registerSpirit(WaterSpiritKind.CRAB);
    public static final EntityType<WaterSpiritEntity> WATER_SPIRIT_OCTOPUS =
            registerSpirit(WaterSpiritKind.OCTOPUS);
    public static final EntityType<FireworkRocketProjectile> FIREWORK_ROCKET =
            registerFireworkRocket();
    public static final EntityType<ZhongliEntity> ZHONGLI = registerZhongli();
    public static final EntityType<NahidaEntity> NAHIDA = registerNahida();
    /** 「木偶」桑多涅：浮空连射冰霜射线的执行官。 */
    public static final EntityType<SandroneEntity> SANDRONE = register("sandrone", SandroneEntity::new);
    /** 从天而降的岩枪：纯视觉与伤害，落定后自行消散。 */
    public static final EntityType<ZhongliStoneSpearEntity> ZHONGLI_STONE_SPEAR = registerStoneSpear();
    /** 斜落的天星基岩。 */
    public static final EntityType<ZhongliMeteorEntity> ZHONGLI_METEOR = registerMeteor();

    private ModEntities() {
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(FURINA, FurinaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(HU_TAO, GenshinNpcEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(YOIMIYA, YoimiyaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(COLUMBINA, ColumbinaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ZHONGLI, ZhongliEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(NAHIDA, NahidaEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SANDRONE, SandroneEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WATER_SPIRIT_SEAHORSE, WaterSpiritEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WATER_SPIRIT_CRAB, WaterSpiritEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WATER_SPIRIT_OCTOPUS, WaterSpiritEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MOON_RABBIT, MoonRabbitEntity.createAttributes());
    }

    public static EntityType<WaterSpiritEntity> waterSpirit(WaterSpiritKind kind) {
        return switch (kind) {
            case SEAHORSE -> WATER_SPIRIT_SEAHORSE;
            case CRAB -> WATER_SPIRIT_CRAB;
            case OCTOPUS -> WATER_SPIRIT_OCTOPUS;
        };
    }

    private static <T extends GenshinNpcEntity> EntityType<T> register(String name, EntityType.EntityFactory<T> factory) {
        Identifier id = XinkunGenshinMod.id(name);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.of(factory, MobCategory.CREATURE)
                        .sized(0.6F, 1.8F)
                        .eyeHeight(1.62F)
                        .clientTrackingRange(10)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static <T extends GenshinNpcEntity> EntityType<T> registerSized(
            String name, EntityType.EntityFactory<T> factory, float width, float height, float eyeHeight) {
        Identifier id = XinkunGenshinMod.id(name);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.of(factory, MobCategory.CREATURE)
                        .sized(width, height)
                        .eyeHeight(eyeHeight)
                        .clientTrackingRange(10)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static EntityType<WaterSpiritEntity> registerSpirit(WaterSpiritKind kind) {
        String name = "water_spirit_" + kind.id();
        Identifier id = XinkunGenshinMod.id(name);
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.<WaterSpiritEntity>of((type, level) -> new WaterSpiritEntity(type, level, kind), MobCategory.MISC)
                        .sized(0.55F, 0.8F)
                        .eyeHeight(0.65F)
                        .clientTrackingRange(8)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static EntityType<FireworkRocketProjectile> registerFireworkRocket() {
        Identifier id = XinkunGenshinMod.id("firework_rocket");
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.<FireworkRocketProjectile>of(FireworkRocketProjectile::new, MobCategory.MISC)
                        .sized(0.3F, 0.3F)
                        .clientTrackingRange(8)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static EntityType<ZhongliEntity> registerZhongli() {
        Identifier id = XinkunGenshinMod.id("zhongli");
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.of(ZhongliEntity::new, MobCategory.CREATURE)
                        // 比现有女角色大 10%：0.6 × 1.8 各放大 1.1 倍。
                        .sized(0.66F, 1.98F)
                        .eyeHeight(1.782F)
                        .clientTrackingRange(10)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static EntityType<NahidaEntity> registerNahida() {
        Identifier id = XinkunGenshinMod.id("nahida");
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.of(NahidaEntity::new, MobCategory.CREATURE)
                        // 体型小巧：碰撞箱按 0.75 倍渲染同步缩到 0.45 × 1.35。
                        .sized(0.45F, 1.35F)
                        .eyeHeight(1.215F)
                        .clientTrackingRange(10)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static EntityType<ZhongliStoneSpearEntity> registerStoneSpear() {
        Identifier id = XinkunGenshinMod.id("zhongli_stone_spear");
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.<ZhongliStoneSpearEntity>of(ZhongliStoneSpearEntity::new, MobCategory.MISC)
                        .sized(0.5F, 5.0F)
                        .clientTrackingRange(10)
                        .updateInterval(2)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static EntityType<ZhongliMeteorEntity> registerMeteor() {
        Identifier id = XinkunGenshinMod.id("zhongli_meteor");
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.<ZhongliMeteorEntity>of(ZhongliMeteorEntity::new, MobCategory.MISC)
                        .sized(5.0F, 5.0F)
                        .clientTrackingRange(16)
                        .updateInterval(1)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static EntityType<MoonPhantomEntity> registerMoonPhantom() {        Identifier id = XinkunGenshinMod.id("moon_phantom");
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.<MoonPhantomEntity>of(MoonPhantomEntity::new, MobCategory.MISC)
                        // 月盘本身只有视觉体积；碰撞与选中都在实体里关掉了。
                        .sized(0.2F, 0.2F)
                        .clientTrackingRange(16)
                        .updateInterval(5)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static EntityType<MoonRabbitEntity> registerMoonRabbit() {
        Identifier id = XinkunGenshinMod.id("moon_rabbit");
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                EntityType.Builder.of(MoonRabbitEntity::new, MobCategory.CREATURE)
                        // 与原版兔子同尺寸，保证看起来就是一只普通白兔。
                        .sized(0.4F, 0.5F)
                        .eyeHeight(0.3F)
                        .clientTrackingRange(8)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }
}
