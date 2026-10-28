"""Convert downloaded community schematics into vanilla structure .nbt files.

Sources (mc-schematics.com, free community library):
  luxury_villa    Modern 3-Story House (21x23)        SuperMario128
  medieval_castle Small Medieval Castle               MckPL_Discord
  modern_tower    Modern Office Tower                 (site author)
  liyue           Asian Survival Home                 (site author)
  french          19th Century Palace                 (site author)

The site distributes Sponge .schem v2 and Litematica files.  This script
normalises both formats into Minecraft 26.3 structure NBT (DataVersion 5023)
so the mod ships real, self-contained blueprints that can also be opened with
a vanilla structure block.
"""

import gzip
import json
import math
import re
import sys
from pathlib import Path

import nbtlib
from nbtlib.tag import Compound, Int, List, String

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "blueprints_src"
OUT = ROOT / "src/main/resources/data/xinkun_genshin/structure"
DATA_VERSION = 5023

SOURCES = {
    "luxury_villa": {
        "file": "modern_3_story_house.litematic",
        "build": "Modern 3-Story House (21x23)",
        "author": "SuperMario128",
        "url": "https://mc-schematics.com/s/modern-3-story-house-21x23-cad9541338",
        "license": "Free community download (mc-schematics.com)",
    },
    "medieval_castle": {
        "file": "small_medieval_castle.litematic",
        "build": "Small Medieval Castle",
        "author": "MckPL_Discord",
        "url": "https://mc-schematics.com/s/small-medieval-castle-structure-a067e7f736",
        "license": "Free community download (mc-schematics.com)",
    },
    "modern_tower": {
        "file": "modern_office_tower.schem",
        "build": "Modern Office Tower",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/modern-office-tower-9ab255b010",
        "license": "Free community download (mc-schematics.com)",
    },
    "liyue": {
        "file": "asian_survival_home.schem",
        "build": "Asian Survival Home",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/asian-survival-home-8085267af9",
        "license": "Free community download (mc-schematics.com)",
    },
    "french": {
        "file": "nineteenth_century_palace.schem",
        "build": "19th Century Palace",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/19th-century-palace-83ce072584",
        "license": "Free community download (mc-schematics.com)",
    },
    "summer_festival": {
        "file": "asian_mansion_market.litematic",
        "build": "Asian Mansion And Market",
        "author": "LaDandyDude",
        "url": "https://mc-schematics.com/s/asian-mansion-and-market-3358e7e54f",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 通用：现代别墅 ----
    "villa_garden": {
        "file": "modern_villa_with_extensive_garden.schem",
        "build": "Modern Villa With Extensive Garden",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/modern-villa-with-extensive-garden-3fdf44e317",
        "license": "Free community download (mc-schematics.com)",
    },
    "villa_dawn": {
        "file": "dawn_villa_house.schem",
        "build": "Dawn Villa House",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/dawn-villa-house-5458ae98dc",
        "license": "Free community download (mc-schematics.com)",
    },
    "villa_contemporary": {
        "file": "contemporary_luxury_mansion.schem",
        "build": "Contemporary Luxury Mansion",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/contemporary-luxury-mansion-e656cc2cdb",
        "license": "Free community download (mc-schematics.com)",
    },
    "villa_suburban": {
        "file": "contemporary_suburban_residence.schem",
        "build": "Contemporary Suburban Family Residence",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/contemporary-suburban-family-residence-b98e573dba",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 通用：城市高楼 ----
    "city_tower_glass": {
        "file": "modern_skyscraper_design.schem",
        "build": "Modern Skyscraper Design",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/modern-skyscraper-design-4efabe172b",
        "license": "Free community download (mc-schematics.com)",
    },
    "city_tower_rocket": {
        "file": "skyscraper_rocket_farm.litematic",
        "build": "Skyscraper Rocket Farm",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/skyscraper-rocket-farm-f177b6cc74",
        "license": "Free community download (mc-schematics.com)",
    },
    "city_tower_giant": {
        "file": "giant_city_skyscraper.litematic",
        "build": "Giant City Skyscraper with Trees",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/giant-city-skyscraper-with-trees-c903aa8161",
        "license": "Free community download (mc-schematics.com)",
    },
    "city_tower_complex": {
        "file": "modern_city_skyscrapers.litematic",
        "build": "Modern City Skyscrapers",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/modern-city-skyscrapers-162a4fef41",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 通用：中世纪建筑 ----
    "medieval_house": {
        "file": "cozy_medieval_house.litematic",
        "build": "Cozy Medieval Village House",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/cozy-medieval-village-house-schematic-for-minecraft-8420009ef0",
        "license": "Free community download (mc-schematics.com)",
    },
    "medieval_watchtower": {
        "file": "gothic_watchtower.schem",
        "build": "Compact Gothic Medieval Watchtower",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/compact-gothic-medieval-watchtower-with-interior-f560510db8",
        "license": "Free community download (mc-schematics.com)",
    },
    "medieval_manor": {
        "file": "fantasy_medieval_manor.litematic",
        "build": "Detailed Fantasy Medieval Manor",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/detailed-fantasy-medieval-manor-build-3a3750b883",
        "license": "Free community download (mc-schematics.com)",
    },
    "medieval_lighthouse": {
        "file": "medieval_lighthouse_dwelling.schem",
        "build": "Medieval Lighthouse Dwelling",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/medieval-lighthouse-dwelling-62e6d94e52",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 通用：其他 ----
    "desert_temple": {
        "file": "ancient_desert_temple.litematic",
        "build": "Ancient Desert Temple",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/ancient-desert-temple-structure-bb4d738746",
        "license": "Free community download (mc-schematics.com)",
    },
    "coastal_lighthouse": {
        "file": "coastal_lighthouse_residence.litematic",
        "build": "Coastal Lighthouse Residence",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/coastal-lighthouse-residence-daae3fdbb0",
        "license": "Free community download (mc-schematics.com)",
    },
    "garden_gazebo": {
        "file": "japanese_garden_gazebo.litematic",
        "build": "Japanese Garden Gazebo",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/japanese-garden-gazebo-a940fc225b",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 芙宁娜 ----
    "furina_opera": {
        "file": "baroque_city_castle.schem",
        "build": "Baroque Tropical City Castle",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/baroque-tropical-city-castle-003708144a",
        "license": "Free community download (mc-schematics.com)",
    },
    "furina_cathedral": {
        "file": "french_stone_cathedral.schem",
        "build": "French Stone Cathedral",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/french-stone-cathedral-2b862067b1",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 胡桃 ----
    "hutao_mansion": {
        "file": "grand_chinese_mansion.litematic",
        "build": "Grand Chinese Mansion",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/grand-chinese-mansion-400fccf984",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 宵宫 ----
    "yoimiya_mansion": {
        "file": "japanese_style_mansion.litematic",
        "build": "Japanese Style Mansion",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/japanese-style-mansion-972541b973",
        "license": "Free community download (mc-schematics.com)",
    },
    "yoimiya_shrine": {
        "file": "cherry_japanese_temple.litematic",
        "build": "Cherry Japanese Temple",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/cherry-japanese-temple-structure-de2efde788",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 哥伦比娅 ----
    "columbina_teapot": {
        "file": "overgrown_moon_teapot.litematic",
        "build": "Overgrown Moon Teapot",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/overgrown-moon-teapot-structure-fb5c5cd62a",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 第二批：现代别墅 ----
    "villa_waterfront": {
        "file": "luxury_waterfront_villa.schem",
        "build": "Luxury Waterfront Villa",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/luxury-waterfront-villa-e6e5a9e226",
        "license": "Free community download (mc-schematics.com)",
    },
    "villa_modern": {
        "file": "modern_villa_design.litematic",
        "build": "Modern Villa Design",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/modern-villa-design-303d6569f3",
        "license": "Free community download (mc-schematics.com)",
    },
    "villa_sandstone": {
        "file": "modern_sandstone_villa.schem",
        "build": "Modern Sandstone Villa",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/modern-sandstone-villa-c71012756a",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 第二批：城堡与高塔 ----
    "castle_fantasy": {
        "file": "fantasy_medieval_castle_build.schem",
        "build": "Fantasy Medieval Castle",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/fantasy-medieval-castle-build-aa66df58e1",
        "license": "Free community download (mc-schematics.com)",
    },
    "fortress_cornet": {
        "file": "castle_cornet_medieval_fortress.schem",
        "build": "Castle Cornet Medieval Fortress",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/castle-cornet-medieval-fortress-42073492c2",
        "license": "Free community download (mc-schematics.com)",
    },
    "lighthouse_grand": {
        "file": "functional_medieval_lighthouse.litematic",
        "build": "Functional Medieval Lighthouse",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/functional-medieval-lighthouse-structure-19f6f5b26f",
        "license": "Free community download (mc-schematics.com)",
    },
    "tower_bastion": {
        "file": "absolute_bastion_sky_tower.schem",
        "build": "Absolute Bastion Sky Tower",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/absolute-bastion-sky-tower-e74187fb43",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 第二批：角色特色 ----
    "yoimiya_pagoda": {
        "file": "japanese_pagoda_house.schem",
        "build": "Japanese Pagoda House",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/japanese-pagoda-house-f73bfbd747",
        "license": "Free community download (mc-schematics.com)",
    },
    # ---- 现代都市：全部为 mc-schematics.com 社区真实作品 ----
    "elegant_office_tower": {
        "file": "modern/elegant-modern-office-tower.schem",
        "build": "Elegant Modern Office Tower",
        "author": "igocraft",
        "url": "https://mc-schematics.com/s/elegant-modern-office-tower-898eca63f7",
        "license": "Free community download (mc-schematics.com)",
    },
    "city_shops": {
        "file": "modern/city-shops-building.schem",
        "build": "City Shops Building",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/city-shops-building-95dcd88cc8",
        "license": "Free community download (mc-schematics.com)",
    },
    "city_plaza": {
        "file": "modern/city-palace-minecraft-build.schem",
        "build": "City Palace Minecraft Build",
        "author": "wovenhopper",
        "url": "https://mc-schematics.com/s/city-palace-minecraft-build-b31b448e13",
        "license": "Free community download (mc-schematics.com)",
    },
    "skyline_apartment": {
        "file": "modern/modern-city-apartment-building.schem",
        "build": "Modern City Apartment Building",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/modern-city-apartment-building-8c4479e9cd",
        "license": "Free community download (mc-schematics.com)",
    },
    "downtown_block": {
        "file": "modern/modern-urban-minecraft-complex.schem",
        "build": "Modern Urban Minecraft Complex",
        "author": "dangerousdana",
        "url": "https://mc-schematics.com/s/modern-urban-minecraft-complex-63e3b27248",
        "license": "Free community download (mc-schematics.com)",
    },
    "modern_housing": {
        "file": "modern/urban-modern-houses-pack.schem",
        "build": "Urban Modern Houses Pack",
        "author": "copperwillow37",
        "url": "https://mc-schematics.com/s/urban-modern-houses-pack-b0f710e155",
        "license": "Free community download (mc-schematics.com)",
    },
    "townhouses": {
        "file": "modern/urban-houses-minecraft-pack.schem",
        "build": "Urban Houses Minecraft Pack",
        "author": "rugged_bramble",
        "url": "https://mc-schematics.com/s/urban-houses-minecraft-pack-9cafe7ff69",
        "license": "Free community download (mc-schematics.com)",
    },
    "glass_palace": {
        "file": "modern/modern-urban-palace-house.schem",
        "build": "Modern Urban Palace House",
        "author": "frostzephyr94",
        "url": "https://mc-schematics.com/s/modern-urban-palace-house-d8ced95f78",
        "license": "Free community download (mc-schematics.com)",
    },
    "parking_garage": {
        "file": "modern/parking-structure.schem",
        "build": "Parking Structure",
        "author": "Raekon",
        "url": "https://mc-schematics.com/s/parking-structure-3c68a5ba63",
        "license": "Free community download (mc-schematics.com)",
    },
    "ring_house": {
        "file": "modern/modern-futuristic-house-design.schem",
        "build": "Modern Futuristic House Design",
        "author": "glitchscraps",
        "url": "https://mc-schematics.com/s/modern-futuristic-house-design-8f3a739f30",
        "license": "Free community download (mc-schematics.com)",
    },
    "signal_tower": {
        "file": "modern/modern-futuristic-sci-fi-building.schem",
        "build": "Modern Futuristic Sci Fi Building",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/modern-futuristic-sci-fi-building-3de106dbf6",
        "license": "Free community download (mc-schematics.com)",
    },
    "city_library": {
        "file": "modern/library-observatory.litematic",
        "build": "Library Observatory",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/library-observatory-cac0c673a0",
        "license": "Free community download (mc-schematics.com)",
    },
    "hill_library": {
        "file": "modern/mountain-library-structure.litematic",
        "build": "Mountain Library Structure",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/mountain-library-structure-1d319595a4",
        "license": "Free community download (mc-schematics.com)",
    },
    "cloud_tower": {
        "file": "modern/huge-city-house.schem",
        "build": "Huge City House",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/huge-city-house-5abd4a21ba",
        "license": "Free community download (mc-schematics.com)",
    },
    "city_corner": {
        "file": "modern/part-of-the-city-minecraft-build.schem",
        "build": "Part of the City Minecraft Build",
        "author": "mc-schematics.com community",
        "url": "https://mc-schematics.com/s/part-of-the-city-minecraft-build-277b2368d2",
        "license": "Free community download (mc-schematics.com)",
    },
}

RENAMES = {
    "minecraft:grass": "minecraft:short_grass",
    "minecraft:grass_path": "minecraft:dirt_path",
    "minecraft:sign": "minecraft:oak_sign",
    "minecraft:wall_sign": "minecraft:oak_wall_sign",
    "minecraft:flowing_water": "minecraft:water",
    "minecraft:flowing_lava": "minecraft:lava",
    # 26.3 split the chain block into per-material variants.
    "minecraft:chain": "minecraft:iron_chain",
    # 26.3 没有这三种树根方块；它们是作者用来点缀树桩的小装饰，
    # 直接去掉，避免整套蓝图都因一个未知状态被记一条警告。
    "minecraft:cherry_roots": "minecraft:air",
    "minecraft:birch_roots": "minecraft:air",
    "minecraft:spruce_roots": "minecraft:air",
}

AIR = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air"}

# Natural ground and vegetation that some community exports include around the
# building.  The mod ships buildings only, so these are stripped before the
# structure is baked.  Structural blocks (stone bricks, cobblestone, sandstone
# walls, farmland, water) are deliberately kept.
GROUND = {
    "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
    "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
    "minecraft:mud", "minecraft:muddy_mangrove_roots", "minecraft:clay",
    "minecraft:gravel", "minecraft:sand", "minecraft:red_sand",
    "minecraft:stone", "minecraft:deepslate", "minecraft:granite",
    "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
    "minecraft:calcite", "minecraft:dripstone_block",
    "minecraft:moss_carpet",
    "minecraft:snow", "minecraft:snow_block", "minecraft:powder_snow",
    "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice",
}
# 砂岩和苔藓块几乎只会出现在建筑本体里（沙漠神庙的墙体、遗迹的包边），
# 不是可以安全抹掉的表土。把它们排除在清理范围外，避免整面墙消失。
VEGETATION = {
    "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern",
    "minecraft:large_fern", "minecraft:dead_bush",
    "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
    "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
    "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
    "minecraft:oxeye_daisy", "minecraft:cornflower",
    "minecraft:lily_of_the_valley", "minecraft:torchflower",
    "minecraft:pitcher_plant", "minecraft:sunflower", "minecraft:lilac",
    "minecraft:rose_bush", "minecraft:peony", "minecraft:sugar_cane",
    "minecraft:brown_mushroom", "minecraft:red_mushroom",
    "minecraft:bamboo", "minecraft:sweet_berry_bush",
}
# Per-structure exceptions: french uses plain stone as part of the chateau's
# chimneys and wall trim, so only true soil/vegetation is removed there.
KEEP_NATURAL = {
    "french": {"minecraft:stone"},
}


def is_ore(name):
    return name.endswith("_ore") or name == "minecraft:ancient_debris"


# 依附类装饰：它们离开支撑方块就会掉落。地形清理会去掉草方块、泥土和
# 石头，但若这些自然方块正是某块地毯、红石线或墙挂告示牌的支点，就
# 必须保留，否则建筑盖好后会少一层细节。
SUPPORT_ABOVE = VEGETATION | {
    "minecraft:redstone_wire", "minecraft:repeater", "minecraft:comparator",
    "minecraft:lever", "minecraft:stone_button", "minecraft:oak_button",
    "minecraft:spruce_button", "minecraft:birch_button", "minecraft:jungle_button",
    "minecraft:acacia_button", "minecraft:dark_oak_button", "minecraft:mangrove_button",
    "minecraft:cherry_button", "minecraft:bamboo_button", "minecraft:crimson_button",
    "minecraft:warped_button", "minecraft:polished_blackstone_button",
    "minecraft:oak_pressure_plate", "minecraft:stone_pressure_plate",
    "minecraft:light_weighted_pressure_plate", "minecraft:heavy_weighted_pressure_plate",
    "minecraft:torch", "minecraft:soul_torch", "minecraft:lantern", "minecraft:soul_lantern",
    "minecraft:redstone_torch", "minecraft:rail", "minecraft:powered_rail",
    "minecraft:detector_rail", "minecraft:activator_rail", "minecraft:flower_pot",
    "minecraft:pink_petals", "minecraft:bush", "minecraft:firefly_bush",
    "minecraft:sweet_berry_bush", "minecraft:cactus", "minecraft:bamboo",
}
SUPPORT_SIDE = {
    "minecraft:torch", "minecraft:soul_torch", "minecraft:redstone_torch",
    "minecraft:wall_torch", "minecraft:soul_wall_torch", "minecraft:redstone_wall_torch",
    "minecraft:lever", "minecraft:ladder", "minecraft:flower_pot",
}


# 需要土壤支撑的地面植物：清理后若脚下的土被挖掉，它们就悬空了。
PLANT_SOIL_BLOCKS = {
    "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
    "minecraft:rooted_dirt", "minecraft:podzol", "minecraft:mycelium",
    "minecraft:moss_block", "minecraft:mud", "minecraft:farmland",
}
PLANT_SOIL = {
    "minecraft:short_grass", "minecraft:fern", "minecraft:dandelion",
    "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium",
    "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip",
    "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:oxeye_daisy",
    "minecraft:cornflower", "minecraft:lily_of_the_valley",
    "minecraft:torchflower", "minecraft:oak_sapling", "minecraft:spruce_sapling",
    "minecraft:birch_sapling", "minecraft:jungle_sapling",
    "minecraft:acacia_sapling", "minecraft:dark_oak_sapling",
    "minecraft:cherry_sapling", "minecraft:mangrove_propagule",
    "minecraft:sweet_berry_bush", "minecraft:bush", "minecraft:firefly_bush",
}
PLANT_DOUBLE = {
    "minecraft:tall_grass", "minecraft:large_fern", "minecraft:sunflower",
    "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony",
    "minecraft:pitcher_plant",
}
# 只要脚下有方块就能存活的轻装饰。
PLANT_ANY = {
    "minecraft:pink_petals", "minecraft:wildflowers", "minecraft:leaf_litter",
    "minecraft:dead_bush", "minecraft:brown_mushroom", "minecraft:red_mushroom",
    "minecraft:bamboo", "minecraft:sugar_cane", "minecraft:cactus",
    "minecraft:small_dripleaf", "minecraft:pale_moss_carpet",
}
# 必须种在耕地上；地形清理掉耕地后它们会整片悬空。
PLANT_CROPS = {
    "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes",
    "minecraft:beetroots",
}


def needs_support_above(name):
    return name.endswith("_carpet") or name in SUPPORT_ABOVE


def needs_support_side(name):
    return (name.endswith("_wall_sign") or name.endswith("_button")
            or name.endswith("_banner") or name in SUPPORT_SIDE)


def strip_terrain(blocks, style_id):
    keep = KEEP_NATURAL.get(style_id, set())
    out = {}
    removed = {}
    for pos, state in blocks.items():
        name, _ = split_state(state)
        natural = name in GROUND or name in VEGETATION or is_ore(name)
        if natural and name not in keep:
            removed[pos] = state
        else:
            out[pos] = state
    # 少数自然方块并不属于地形底座，而是作者自己的地板垫层：例如楼梯或
    # 石柱正下方的一块泥土。它们被保留构件四面围住，挖掉会在室内留下
    # 看不见的洞。这样的方块放回蓝图，确保建筑逐块完整。
    restored = 0
    for pos, state in list(removed.items()):
        x, y, z = pos
        neighbours = sum(
            1 for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0))
            if (x + d[0], y + d[1], z + d[2]) in out
        )
        above = (x, y + 1, z)
        side_names = [
            split_state(out[other])[0]
            for other in ((x + 1, y, z), (x - 1, y, z), (x, y, z + 1), (x, y, z - 1))
            if other in out
        ]
        above_name = split_state(out[above])[0] if above in out else ""
        needed = (above_name and needs_support_above(above_name)) or any(
            needs_support_side(name) for name in side_names)
        if neighbours >= 4 or needed:
            out[pos] = state
            del removed[pos]
            restored += 1
    # 第二遍只认“紧贴建筑构件”的直接相邻：石材（石、安山岩、深板岩等）
    # 在作者手里经常就是立面饰带、台阶包边和地基，但按材质无法和山体区分。
    # 只要它有一面对着非自然构件，就说明它是建筑的一部分，原位放回；
    # 不做连锁扩散，否则整座山都会顺着墙脚一路保留下来。
    def is_natural(state):
        name, _ = split_state(state)
        return name in GROUND or name in VEGETATION or is_ore(name)

    architecture = {pos for pos, state in out.items() if not is_natural(state)}
    dirs = ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0))
    adjacent = 0
    for pos, state in list(removed.items()):
        x, y, z = pos
        if any((x + dx, y + dy, z + dz) in architecture for dx, dy, dz in dirs):
            out[pos] = state
            del removed[pos]
            adjacent += 1
    return out, len(removed)


def drop_unsupported_plants(blocks):
    """Remove ground plants the source left hanging in the air.

    A community export sometimes keeps a tuft of grass or a flower whose soil
    block was edited away; vanilla physics deletes it the moment a neighbour
    updates, so leaving it in the blueprint only produces a random hole where
    the builder happened to update it. Dropping it up front keeps the shipped
    structure identical to what the game will actually keep.
    """
    out = dict(blocks)
    for pos, state in list(out.items()):
        name, props = split_state(state)
        if (name not in PLANT_SOIL and name not in PLANT_DOUBLE
                and name not in PLANT_ANY and name not in PLANT_CROPS):
            continue
        above = (pos[0], pos[1] + 1, pos[2])
        below = out.get((pos[0], pos[1] - 1, pos[2]))
        below_name, below_props = split_state(below) if below else ("", {})
        if name in PLANT_DOUBLE:
            # Both halves must be present and the lower half must stand on soil.
            # The terrain restore pass can bring back only one half, so check
            # the partner instead of assuming it exists.
            if props.get("half") == "upper":
                partner = out.get(below or pos)
                ok = below_name == name and below_props.get("half") == "lower"
                if ok:
                    continue
            else:
                partner = out.get(above)
                ok = below_name in PLANT_SOIL_BLOCKS and partner is not None
                if ok:
                    continue
            for other in (pos, above, below):
                if other is not None and other in out and split_state(out[other])[0] == name:
                    out.pop(other, None)
            continue
        if name in PLANT_CROPS:
            ok = below_name == "minecraft:farmland"
        elif name in PLANT_ANY:
            ok = below is not None
        else:
            ok = below_name in PLANT_SOIL_BLOCKS
        if ok:
            continue
        for other in list(out):
            if other[0] != pos[0] or other[2] != pos[2]:
                continue
            if other[1] not in (pos[1], pos[1] + 1):
                continue
            if split_state(out[other])[0] == name:
                del out[other]
    return out


def read_varints(data):
    out = []
    i = 0
    n = len(data)
    while i < n:
        value = 0
        shift = 0
        while True:
            b = data[i]
            i += 1
            value |= (b & 0x7F) << shift
            if not (b & 0x80):
                break
            shift += 7
        out.append(value)
    return out


def state_string(name, properties):
    if not properties:
        return name
    body = ",".join(f"{k}={properties[k]}" for k in sorted(properties))
    return f"{name}[{body}]"


def litematic_palette_entry(entry):
    name = str(entry["Name"])
    props = {}
    if "Properties" in entry:
        props = {str(k): str(v) for k, v in entry["Properties"].items()}
    return state_string(name, props)


def load_litematic(path):
    nbt = nbtlib.load(path)
    blocks = {}
    for region in nbt["Regions"].values():
        size = region["Size"]
        pos = region["Position"]
        sx, sy, sz = int(size["x"]), int(size["y"]), int(size["z"])
        px, py, pz = int(pos["x"]), int(pos["y"]), int(pos["z"])
        ax, ay, az = abs(sx), abs(sy), abs(sz)
        palette = [litematic_palette_entry(e) for e in region["BlockStatePalette"]]
        bits = max(2, math.ceil(math.log2(max(2, len(palette)))))
        longs = [int(v) & 0xFFFFFFFFFFFFFFFF for v in region["BlockStates"]]
        mask = (1 << bits) - 1
        for index in range(ax * ay * az):
            bit = index * bits
            slot = bit >> 6
            offset = bit & 63
            value = (longs[slot] >> offset) & mask
            if offset + bits > 64 and slot + 1 < len(longs):
                value |= (longs[slot + 1] << (64 - offset)) & mask
            state = palette[value]
            if state in AIR:
                continue
            x = index % ax
            z = (index // ax) % az
            y = index // (ax * az)
            # 负的 Size 只表示 Position 是区域的“远端”角落，方块索引依然从
            # 最小角落向上/向右递增。按索引直接往负方向减会把整栋楼上下或
            # 前后翻过来（玉兔镇的地毯全飘在空中就是这么来的）。
            wx = px + (x if sx >= 0 else x - ax + 1)
            wy = py + (y if sy >= 0 else y - ay + 1)
            wz = pz + (z if sz >= 0 else z - az + 1)
            blocks[(wx, wy, wz)] = state
    return blocks


def load_schem(path):
    schematic = nbtlib.load(path)
    # 少数文件是 Sponge v1 的包装格式，真正的内容在 Schematic 子标签里。
    if "Schematic" in schematic:
        schematic = schematic["Schematic"]
    width = int(schematic["Width"])
    height = int(schematic["Height"])
    length = int(schematic["Length"])
    if "Palette" in schematic:
        palette = {int(v): str(k) for k, v in schematic["Palette"].items()}
        raw = schematic["BlockData"]
    else:
        # Sponge v3 的 Blocks 子标签：Palette 为 状态->编号，Data 为 varint。
        blocks_tag = schematic["Blocks"]
        palette = {int(v): str(k) for k, v in blocks_tag["Palette"].items()}
        palette = {k: v for k, v in palette.items() if v != "minecraft:__reserved__"}
        raw = blocks_tag["Data"]
    data = read_varints(bytes(int(b) & 0xFF for b in raw))
    if len(data) != width * height * length:
        raise ValueError(f"unexpected block data length in {path}")
    blocks = {}
    for index, palette_index in enumerate(data):
        state = palette.get(palette_index, "minecraft:air")
        if state in AIR:
            continue
        x = index % width
        z = (index // width) % length
        y = index // (width * length)
        blocks[(x, y, z)] = state
    return blocks


def split_state(state):
    match = re.fullmatch(r"([a-z0-9_:./-]+)(?:\[(.*)\])?", state)
    if not match:
        raise ValueError(f"bad state {state}")
    name = RENAMES.get(match.group(1), match.group(1))
    props = {}
    if match.group(2):
        for pair in match.group(2).split(","):
            key, _, value = pair.partition("=")
            props[key] = value
    return name, props


def fix_state(name, props):
    """Repair states that community exports still carry in the old format."""
    # 26.x 的炼药锅不再有 level 属性；带 level 的旧状态的整条调色板会
    # 解析失败，连带丢掉该状态下所有方块。
    if name == "minecraft:cauldron":
        props = {}
    # 平滑砂岩是全方块，旧导出里残留的 axis 会让整批方块作废。
    if name == "minecraft:smooth_sandstone":
        props.pop("axis", None)
    # 某些导出把台阶属性写在木板 id 上；按真实方块换回台阶。
    if name == "minecraft:dark_oak_planks" and "type" in props:
        name = "minecraft:dark_oak_slab"
        props.pop("waterlogged", None)
    # 26.3 的书架不再有朝向/供能属性（材质仍由方块 id 区分），
    # 旧属性会让整条调色板解析失败；保留方块本体即可。
    if name.startswith("minecraft:") and name.endswith("_shelf"):
        props = {}
    # 旧导出把楼梯/台阶属性写在凿制砂岩上，按属性还原成真实方块。
    if name == "minecraft:chiseled_sandstone" and "half" in props:
        name = "minecraft:sandstone_stairs"
        props = {k: props[k] for k in ("facing", "half", "shape", "waterlogged") if k in props}
    elif name == "minecraft:chiseled_sandstone" and "type" in props:
        name = "minecraft:sandstone_slab"
        props = {"type": props["type"], "waterlogged": props.get("waterlogged", "false")}
    # 睡莲在 26.3 没有 waterlogged 属性。
    if name == "minecraft:lily_pad":
        props = {}
    return name, props


def normalise(blocks):
    """Trim empty margins, force still water, drop unrenderable leftovers."""
    xs = [p[0] for p in blocks]
    ys = [p[1] for p in blocks]
    zs = [p[2] for p in blocks]
    min_x, min_y, min_z = min(xs), min(ys), min(zs)
    max_x, max_y, max_z = max(xs), max(ys), max(zs)
    out = {}
    for (x, y, z), state in blocks.items():
        name, props = fix_state(*split_state(state))
        if name in AIR:
            continue
        # 水流保持作者导出时的原始 level。把每一格流动水改成水源会让整条
        # 瀑布变成无限水源，落到底板后横向漫流，冲掉墙面上的花盆；falling
        # 水（level=8）只向下落，不会向侧面扩散，正是作者建好时的样子。
        out[(x - min_x, y - min_y, z - min_z)] = state_string(name, props)
    size = (max_x - min_x + 1, max_y - min_y + 1, max_z - min_z + 1)
    return size, out


def write_structure(path, size, blocks):
    palette = sorted(set(blocks.values()))
    index = {state: i for i, state in enumerate(palette)}
    palette_tag = List[Compound]()
    for state in palette:
        name, props = split_state(state)
        entry = Compound({"id": String(name)})
        if props:
            entry["properties"] = Compound({k: String(v) for k, v in props.items()})
        palette_tag.append(entry)
    block_tags = List[Compound]()
    for (x, y, z), state in sorted(blocks.items(), key=lambda item: (item[0][1], item[0][0], item[0][2])):
        block_tags.append(Compound({
            "pos": List[Int]([Int(x), Int(y), Int(z)]),
            "state": Int(index[state]),
        }))
    root = Compound({
        "DataVersion": Int(DATA_VERSION),
        "size": List[Int]([Int(v) for v in size]),
        "palette": palette_tag,
        "blocks": block_tags,
        "entities": List[Compound](),
    })
    with gzip.open(path, "wb") as handle:
        nbtlib.File(root).write(handle)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    credits = []
    for style_id, meta in SOURCES.items():
        source = SRC / meta["file"]
        if not source.exists():
            sys.exit(f"missing source file {source}")
        if source.suffix == ".litematic":
            blocks = load_litematic(source)
        else:
            blocks = load_schem(source)
        blocks, stripped = strip_terrain(blocks, style_id)
        blocks = drop_unsupported_plants(blocks)
        size, blocks = normalise(blocks)
        target = OUT / f"{style_id}.nbt"
        write_structure(target, size, blocks)
        credits.append({
            "id": style_id,
            "file": target.name,
            "build": meta["build"],
            "author": meta["author"],
            "source": meta["url"],
            "license": meta["license"],
            "size": list(size),
            "blocks": len(blocks),
            "stripped_terrain": stripped,
        })
        print(f"{style_id:16s} {size[0]:3d}x{size[1]:3d}x{size[2]:3d}  {len(blocks):6d} blocks  "
              f"stripped {stripped:5d}  {target.stat().st_size / 1024:7.1f} KiB")
    (OUT / "credits.json").write_text(
        json.dumps({"format": "minecraft:structure", "data_version": DATA_VERSION,
                    "blueprints": credits}, ensure_ascii=False, indent=2),
        encoding="utf-8")


if __name__ == "__main__":
    main()
