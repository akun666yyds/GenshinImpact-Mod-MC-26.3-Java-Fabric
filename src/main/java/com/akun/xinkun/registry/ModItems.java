package com.akun.xinkun.registry;

import com.akun.xinkun.XinkunGenshinMod;
import com.akun.xinkun.building.BlueprintItem;
import com.akun.xinkun.building.BuildingType;
import com.akun.xinkun.item.FigurineBlockItem;
import com.akun.xinkun.item.MooncakeItem;
import com.akun.xinkun.item.VerdantCodexItem;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

public final class ModItems {
    public static final Item FURINA_SPAWN_EGG = spawnEgg("furina_spawn_egg", ModEntities.FURINA);
    public static final Item HU_TAO_SPAWN_EGG = spawnEgg("hu_tao_spawn_egg", ModEntities.HU_TAO);
    public static final Item YOIMIYA_SPAWN_EGG = spawnEgg("yoimiya_spawn_egg", ModEntities.YOIMIYA);
    public static final Item COLUMBINA_SPAWN_EGG = spawnEgg("columbina_spawn_egg", ModEntities.COLUMBINA);
    public static final Item ZHONGLI_SPAWN_EGG = spawnEgg("zhongli_spawn_egg", ModEntities.ZHONGLI);
    public static final Item NAHIDA_SPAWN_EGG = spawnEgg("nahida_spawn_egg", ModEntities.NAHIDA);
    public static final Item SANDRONE_SPAWN_EGG = spawnEgg("sandrone_spawn_egg", ModEntities.SANDRONE);
    /** 「少女」的月饼：回满生命，并给予十五分钟的全套增益。 */
    public static final Item MOONCAKE = mooncake();
    /** 仅作为成就图标使用的派蒙像素头像，不进入创造模式物品栏。 */
    public static final Item PAIMON_PORTRAIT = simpleItem("paimon_portrait");

    // ---- 角色专属道具 ----
    /** 纳西妲的绿色书卷：翻页横扫身边六格内的敌人。 */
    public static final Item VERDANT_CODEX = verdantCodex();
    /** 「少女的手办」：摆放哥伦比娅 3D 模型的收藏摆件，左键敲掉可回收。 */
    public static final Item FIGURINE_COLUMBINA =
            figurine("figurine_columbina", ModBlocks.FIGURINE_COLUMBINA);
    /** 芙宁娜的手办：同一套摆放规则，换成芙宁娜模型。 */
    public static final Item FIGURINE_FURINA =
            figurine("figurine_furina", ModBlocks.FIGURINE_FURINA);

    public static final Item FIGURINE_HUTAO =
            figurine("figurine_hutao", ModBlocks.FIGURINE_HUTAO);

    public static final Item FIGURINE_YOIMIYA =
            figurine("figurine_yoimiya", ModBlocks.FIGURINE_YOIMIYA);

    public static final Item FIGURINE_ZHONGLI =
            figurine("figurine_zhongli", ModBlocks.FIGURINE_ZHONGLI);

    public static final Item FIGURINE_NAHIDA =
            figurine("figurine_nahida", ModBlocks.FIGURINE_NAHIDA);

    public static final Item FIGURINE_SANDRONE =
            figurine("figurine_sandrone", ModBlocks.FIGURINE_SANDRONE);

    /** 59 件建筑道具，按目录顺序注册。 */
    public static final Map<BuildingType, Item> BUILDINGS = registerBuildings();

    private ModItems() {
    }

    public static void initialize() {
    }

    private static Map<BuildingType, Item> registerBuildings() {
        Map<BuildingType, Item> map = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            Identifier id = XinkunGenshinMod.id(type.id());
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            Item item = Registry.register(BuiltInRegistries.ITEM, key,
                    new BlueprintItem(new Item.Properties()
                            .setId(key)
                            .stacksTo(16)
                            .rarity(type.isGeneric() ? Rarity.UNCOMMON : Rarity.RARE), type));
            map.put(type, item);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Item simpleItem(String name) {
        Identifier id = XinkunGenshinMod.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    private static Item mooncake() {
        Identifier id = XinkunGenshinMod.id("mooncake");
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new MooncakeItem(new Item.Properties()
                        .setId(key)
                        .rarity(Rarity.EPIC)
                        .food(MooncakeItem.foodProperties(), MooncakeItem.consumable())));
    }

    private static Item spawnEgg(String name, EntityType<? extends Mob> type) {
        Identifier id = XinkunGenshinMod.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new SpawnEggItem(new Item.Properties().setId(key).spawnEgg(type)));
    }

    private static Item figurine(String name, Block block) {
        Identifier id = XinkunGenshinMod.id(name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new FigurineBlockItem(block, new Item.Properties()
                        .setId(key)
                        .stacksTo(16)
                        .rarity(Rarity.RARE), "item.xinkun_genshin." + name + ".desc"));
    }

    private static Item verdantCodex() {        Identifier id = XinkunGenshinMod.id("verdant_codex");
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new VerdantCodexItem(new Item.Properties()
                        .setId(key)
                        .stacksTo(1)
                        .rarity(Rarity.EPIC)
                        .fireResistant()
                        .enchantable(5)));
    }

}
