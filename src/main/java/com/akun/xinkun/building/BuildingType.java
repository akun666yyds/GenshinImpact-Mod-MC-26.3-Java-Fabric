package com.akun.xinkun.building;

import com.akun.xinkun.GenshinCharacter;

/**
 * 全部可放置建筑道具的目录。每一项都对应 {@code data/xinkun_genshin/structure/}
 * 下的一份完整结构文件；玩家拿到的建筑道具只是这份蓝图的入口，放置时按原样
 * 逐层落成，不再依赖任何角色的建造行为。
 */
public enum BuildingType {
    // ---- 通用：现代别墅 ----
    LUXURY_VILLA("luxury_villa", "luxury_villa", Category.VILLA, null),
    VILLA_GARDEN("villa_garden", "villa_garden", Category.VILLA, null),
    VILLA_DAWN("villa_dawn", "villa_dawn", Category.VILLA, null),
    VILLA_CONTEMPORARY("villa_contemporary", "villa_contemporary", Category.VILLA, null),
    VILLA_SUBURBAN("villa_suburban", "villa_suburban", Category.VILLA, null),
    VILLA_WATERFRONT("villa_waterfront", "villa_waterfront", Category.VILLA, null),
    VILLA_MODERN("villa_modern", "villa_modern", Category.VILLA, null),
    VILLA_SANDSTONE("villa_sandstone", "villa_sandstone", Category.VILLA, null),

    // ---- 通用：城市高楼 ----
    MODERN_TOWER("modern_tower", "modern_tower", Category.TOWER, null),
    CITY_TOWER_GLASS("city_tower_glass", "city_tower_glass", Category.TOWER, null),
    CITY_TOWER_ROCKET("city_tower_rocket", "city_tower_rocket", Category.TOWER, null),
    CITY_TOWER_GIANT("city_tower_giant", "city_tower_giant", Category.TOWER, null),
    CITY_TOWER_COMPLEX("city_tower_complex", "city_tower_complex", Category.TOWER, null),
    TOWER_BASTION("tower_bastion", "tower_bastion", Category.TOWER, null),

    // ---- 通用：中世纪建筑 ----
    MEDIEVAL_CASTLE("medieval_castle", "medieval_castle", Category.MEDIEVAL, null),
    MEDIEVAL_HOUSE("medieval_house", "medieval_house", Category.MEDIEVAL, null),
    MEDIEVAL_WATCHTOWER("medieval_watchtower", "medieval_watchtower", Category.MEDIEVAL, null),
    MEDIEVAL_MANOR("medieval_manor", "medieval_manor", Category.MEDIEVAL, null),
    MEDIEVAL_LIGHTHOUSE("medieval_lighthouse", "medieval_lighthouse", Category.MEDIEVAL, null),
    CASTLE_FANTASY("castle_fantasy", "castle_fantasy", Category.MEDIEVAL, null),
    FORTRESS_CORNET("fortress_cornet", "fortress_cornet", Category.MEDIEVAL, null),
    LIGHTHOUSE_GRAND("lighthouse_grand", "lighthouse_grand", Category.MEDIEVAL, null),

    // ---- 通用：其他 ----
    DESERT_TEMPLE("desert_temple", "desert_temple", Category.OTHER, null),
    COASTAL_LIGHTHOUSE("coastal_lighthouse", "coastal_lighthouse", Category.OTHER, null),
    GARDEN_GAZEBO("garden_gazebo", "garden_gazebo", Category.OTHER, null),

    // ---- 通用：现代都市（mc-schematics.com 社区真实蓝图） ----
    ELEGANT_OFFICE_TOWER("elegant_office_tower", "elegant_office_tower", Category.CITY, null),
    CITY_SHOPS("city_shops", "city_shops", Category.CITY, null),
    CITY_PLAZA("city_plaza", "city_plaza", Category.CITY, null),
    SKYLINE_APARTMENT("skyline_apartment", "skyline_apartment", Category.CITY, null),
    DOWNTOWN_BLOCK("downtown_block", "downtown_block", Category.CITY, null),
    MODERN_HOUSING("modern_housing", "modern_housing", Category.CITY, null),
    TOWNHOUSES("townhouses", "townhouses", Category.CITY, null),
    GLASS_PALACE("glass_palace", "glass_palace", Category.CITY, null),
    PARKING_GARAGE("parking_garage", "parking_garage", Category.CITY, null),
    RING_HOUSE("ring_house", "ring_house", Category.CITY, null),
    SIGNAL_TOWER("signal_tower", "signal_tower", Category.CITY, null),
    CITY_LIBRARY("city_library", "city_library", Category.CITY, null),
    HILL_LIBRARY("hill_library", "hill_library", Category.CITY, null),
    CLOUD_TOWER("cloud_tower", "cloud_tower", Category.CITY, null),
    CITY_CORNER("city_corner", "city_corner", Category.CITY, null),

    // ---- 芙宁娜 ----
    FURINA_PALACE("furina_palace", "french", Category.CHARACTER, GenshinCharacter.FURINA),
    FURINA_OPERA("furina_opera", "furina_opera", Category.CHARACTER, GenshinCharacter.FURINA),
    FURINA_CATHEDRAL("furina_cathedral", "furina_cathedral", Category.CHARACTER, GenshinCharacter.FURINA),

    // ---- 胡桃 ----
    HUTAO_TEAHOUSE("hutao_teahouse", "liyue", Category.CHARACTER, GenshinCharacter.HU_TAO),
    HUTAO_PAVILION("hutao_pavilion", "liyue_pavilion", Category.CHARACTER, GenshinCharacter.HU_TAO),
    HUTAO_MANSION("hutao_mansion", "hutao_mansion", Category.CHARACTER, GenshinCharacter.HU_TAO),

    // ---- 宵宫 ----
    YOIMIYA_MARKET("yoimiya_market", "summer_festival", Category.CHARACTER, GenshinCharacter.YOIMIYA),
    YOIMIYA_MANSION("yoimiya_mansion", "yoimiya_mansion", Category.CHARACTER, GenshinCharacter.YOIMIYA),
    YOIMIYA_SHRINE("yoimiya_shrine", "yoimiya_shrine", Category.CHARACTER, GenshinCharacter.YOIMIYA),
    YOIMIYA_PAGODA("yoimiya_pagoda", "yoimiya_pagoda", Category.CHARACTER, GenshinCharacter.YOIMIYA),

    // ---- 哥伦比娅 ----
    COLUMBINA_BASE("columbina_base", "moon_base", Category.CHARACTER, GenshinCharacter.COLUMBINA),
    COLUMBINA_STATION("columbina_station", "space_station", Category.CHARACTER, GenshinCharacter.COLUMBINA),
    COLUMBINA_TEAPOT("columbina_teapot", "columbina_teapot", Category.CHARACTER, GenshinCharacter.COLUMBINA),

    // ---- 钟离 ----
    ZHONGLI_COURTYARD("zhongli_courtyard", "liyue_courtyard", Category.CHARACTER, GenshinCharacter.ZHONGLI),
    ZHONGLI_PAGODA("zhongli_pagoda", "liyue_pagoda", Category.CHARACTER, GenshinCharacter.ZHONGLI),
    ZHONGLI_MANOR("zhongli_manor", "liyue_manor", Category.CHARACTER, GenshinCharacter.ZHONGLI),

    // ---- 纳西妲 ----
    NAHIDA_SUMERU("nahida_sumeru", "sumeru_residence", Category.CHARACTER, GenshinCharacter.NAHIDA),
    NAHIDA_SANCTUARY("nahida_sanctuary", "sumeru_sanctuary", Category.CHARACTER, GenshinCharacter.NAHIDA),
    NAHIDA_AKADEMIYA("nahida_akademiya", "sumeru_akademiya", Category.CHARACTER, GenshinCharacter.NAHIDA);

    public enum Category {
        VILLA,
        TOWER,
        MEDIEVAL,
        OTHER,
        CITY,
        CHARACTER
    }

    private final String id;
    private final String blueprint;
    private final Category category;
    private final GenshinCharacter owner;

    BuildingType(String id, String blueprint, Category category, GenshinCharacter owner) {
        this.id = id;
        this.blueprint = blueprint;
        this.category = category;
        this.owner = owner;
    }

    /** 物品 ID，同时也是纹理与模型的文件名。 */
    public String id() {
        return this.id;
    }

    /** 打包在模组内的结构文件名（不含扩展名）。 */
    public String blueprint() {
        return this.blueprint;
    }

    public Category category() {
        return this.category;
    }

    public GenshinCharacter owner() {
        return this.owner;
    }

    public boolean isGeneric() {
        return this.owner == null;
    }

    public String nameKey() {
        return "item.xinkun_genshin." + this.id;
    }

    public String tooltipKey() {
        return "item.xinkun_genshin." + this.id + ".tooltip";
    }
}
