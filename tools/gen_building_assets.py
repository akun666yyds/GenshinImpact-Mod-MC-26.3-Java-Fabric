"""Bake item icons and item assets for the building catalog.

Icons are isometric renders of the bundled structure NBTs, so every building
gets a picture of itself instead of a generic placeholder. Run once after the
structures are converted; the produced PNG/JSON files are shipped in the mod.
"""

import json
import math
import re
from pathlib import Path

import nbtlib
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
STRUCT = ROOT / "src/main/resources/data/xinkun_genshin/structure"
ASSETS = ROOT / "src/main/resources/assets/xinkun_genshin"
TEX = ASSETS / "textures/item"
MODELS = ASSETS / "models/item"
ITEMS = ASSETS / "items"
LANG = ASSETS / "lang"

NAMES = {
    "luxury_villa": ("现代三层别墅", "Modern Three-Story Villa",
                     "玻璃与白墙交错的三层住宅，带开放式露台。"),
    "villa_garden": ("庭园现代别墅", "Garden Modern Villa",
                     "环绕草坪与步道的低层别墅，适合安静地看日落。"),
    "villa_dawn": ("晨曦别墅", "Dawn Villa",
                   "清晨光线最好的坡顶别墅，木格栅与白墙相间。"),
    "villa_contemporary": ("当代豪宅", "Contemporary Mansion",
                           "线条利落的当代住宅，落地窗把庭院收进客厅。"),
    "villa_suburban": ("城郊住宅", "Suburban Residence",
                       "小巧的城郊独栋，木平台与浅色外墙。"),
    "modern_tower": ("现代办公塔", "Modern Office Tower",
                     "玻璃幕墙的城市办公楼，顶层有观景平台。"),
    "city_tower_glass": ("玻璃幕墙高塔", "Glass Curtain Tower",
                         "通体玻璃的摩天楼，夜里像一根发光的水晶。"),
    "city_tower_rocket": ("高空农场塔", "Sky Farm Tower",
                          "把农田搬上高空的试验塔，层层绿意。"),
    "city_tower_giant": ("巨树环绕大厦", "Tree-Clad Skyscraper",
                         "被巨树环抱的城市大厦，玻璃与枝叶交错。"),
    "city_tower_complex": ("城市高楼群", "City Skyline",
                           "一组高低错落的城市建筑，适合拼出一整片天际线。"),
    "medieval_castle": ("中世纪城堡", "Medieval Castle",
                        "石墙、塔楼与吊桥俱全的小型城堡。"),
    "medieval_house": ("中世纪村舍", "Medieval Village House",
                       "木梁与石基的村舍，烟囱里还留着柴火味。"),
    "medieval_watchtower": ("哥特瞭望塔", "Gothic Watchtower",
                            "窄高的哥特式瞭望塔，塔顶能望见整片原野。"),
    "medieval_manor": ("奇幻中世纪庄园", "Fantasy Medieval Manor",
                       "尖顶与拱窗组成的中世纪庄园。"),
    "medieval_lighthouse": ("中世纪灯塔居所", "Medieval Lighthouse Dwelling",
                            "海边石塔改成的居所，顶层仍亮着灯。"),
    "desert_temple": ("沙漠古神庙", "Ancient Desert Temple",
                      "半埋在黄沙里的古神庙，石柱上刻着风化的纹路。"),
    "coastal_lighthouse": ("海岸灯塔", "Coastal Lighthouse",
                           "立在礁石上的白色灯塔，为夜航的船指路。"),
    "garden_gazebo": ("日式庭园凉亭", "Japanese Garden Gazebo",
                      "池畔的木亭与石灯笼，适合听雨。"),
    "furina_palace": ("十九世纪水色宫殿", "19th-Century Water Palace",
                      "芙宁娜偏爱的石雕宫殿，长廊尽头正对喷泉。"),
    "furina_opera": ("巴洛克歌剧院", "Baroque Opera House",
                     "金饰与穹顶堆叠的歌剧院，谢幕时灯光会亮到很晚。"),
    "furina_cathedral": ("法兰西石造大教堂", "French Stone Cathedral",
                         "彩窗与飞扶壁撑起的大教堂，钟声能传过整片湖面。"),
    "hutao_teahouse": ("璃月古宅", "Liyue Teahouse",
                       "往生堂常去的璃月古宅，檐角挂着风铃。"),
    "hutao_pavilion": ("璃月水榭", "Liyue Waterside Pavilion",
                       "临水的木榭，胡桃喜欢在这里晒太阳。"),
    "hutao_mansion": ("璃月大宅", "Grand Liyue Mansion",
                      "深宅大院里层层叠叠的飞檐，夜里点起一排红灯笼。"),
    "yoimiya_market": ("夏日祭长街", "Summer Festival Street",
                       "宵宫亲手布置的祭典长街，两侧挂满灯笼与摊位。"),
    "yoimiya_mansion": ("和风大宅", "Japanese Style Mansion",
                        "木廊与障子门围成的和风宅院。"),
    "yoimiya_shrine": ("樱色神社", "Cherry Blossom Shrine",
                       "樱花下的神社，石阶尽头是宵宫放烟花的地方。"),
    "columbina_base": ("月面基地", "Lunar Base",
                       "哥伦比娅的月面据点，穹顶下永远亮着苍白的灯。"),
    "columbina_station": ("环月空间站", "Lunar Orbital Station",
                          "悬浮在月影里的空间站，舷窗外是整颗星球。"),
    "columbina_teapot": ("月华茶壶庭园", "Moonlit Teapot Garden",
                         "藤蔓缠绕的巨型茶壶，壶口飘出月光。"),
    "zhongli_courtyard": ("璃月庭院", "Liyue Courtyard",
                          "青石铺地的璃月庭院，石桌旁常备一壶茶。"),
    "zhongli_pagoda": ("璃月宝塔", "Liyue Pagoda",
                       "七层飞檐的璃月宝塔，风过时铜铃齐响。"),
    "zhongli_manor": ("璃月山庄", "Liyue Manor",
                      "依山而建的璃月山庄，回廊一直延伸到云里。"),
    "nahida_sumeru": ("须弥雨林居所", "Sumeru Rainforest Residence",
                      "雨林里的小居所，铜绿屋顶上爬满枝叶。"),
    "nahida_sanctuary": ("净善宫", "Sanctuary of Surasthana",
                         "须弥的净善宫，圆形屋顶托起金色的日轮。"),
    "nahida_akademiya": ("教令院", "Akademiya",
                         "教令院的主殿与高塔，学者们的灯火昼夜不熄。"),
    "villa_waterfront": ("水岸豪宅", "Waterfront Mansion",
                         "临水的大宅，白色石英与玻璃幕墙映着水面。"),
    "villa_modern": ("现代简约别墅", "Modern Minimal Villa",
                     "石英白墙配深色玻璃，屋顶平台收进整片天空。"),
    "villa_sandstone": ("砂岩度假别墅", "Sandstone Villa",
                        "砂岩与柚木搭成的度假宅，院子里留着一池水。"),
    "castle_fantasy": ("奇幻城堡", "Fantasy Castle",
                       "深色石墙托起铜绿尖顶，墙头爬满藤叶。"),
    "fortress_cornet": ("科内特要塞", "Castle Cornet Fortress",
                        "石墙与炮位围成的要塞，旗帜挂在风里。"),
    "lighthouse_grand": ("高塔灯塔", "Grand Lighthouse",
                         "石砌塔身配樱桃木梯，塔顶灯室彻夜亮着。"),
    "tower_bastion": ("天穹堡垒塔", "Sky Bastion Tower",
                      "黑石与红砖垒起的高塔，塔尖直入云层。"),
    "yoimiya_pagoda": ("稻妻町屋", "Inazuma Townhouse",
                       "竹木町屋，屋檐下挂着纸灯，屋里铺着榻榻米。"),
    "elegant_office_tower": ("云端商务塔", "Cloudline Office Tower",
                             "通体玻璃与竖向线条的商务塔，顶层悬着一圈观景廊。"),
    "city_shops": ("城市商业街", "City Shopping Street",
                   "沿街店面与屋顶平台层层叠起，是城市最热闹的一角。"),
    "city_plaza": ("环城商业广场", "City Plaza Mall",
                   "围合式商业广场，外立面是一整排落地橱窗。"),
    "skyline_apartment": ("云台公寓", "Skyline Apartments",
                          "错落的塔楼与裙房拼成的公寓群，屋顶留着花园。"),
    "downtown_block": ("都汇商住区", "Downtown Mixed-Use Block",
                       "低层商铺托起高层住宅，街角还有一间咖啡馆。"),
    "modern_housing": ("现代住宅街区", "Modern Housing Block",
                       "成排的现代住宅，阳台与遮阳板拼出整齐的立面。"),
    "townhouses": ("城市联排住宅", "Urban Townhouses",
                   "紧凑的联排住宅，每户都有独立的入口与小院。"),
    "glass_palace": ("彩玻公寓", "Glass Palette Apartments",
                     "彩色玻璃拼接的公寓塔，夜里像一块发光的拼图。"),
    "parking_garage": ("立体停车楼", "Parking Garage",
                       "带太阳能顶棚的多层停车楼，坡道一路盘到屋顶。"),
    "ring_house": ("环形未来宅", "Ring Futurist House",
                   "环形玻璃围绕中庭的未来住宅，天光直接落进客厅。"),
    "signal_tower": ("未来信号塔", "Futurist Signal Tower",
                     "深色金属与青色灯带组成的信号塔，塔顶立着天线。"),
    "city_library": ("城市图书馆", "City Library",
                     "红砖与穹顶的城市图书馆，阅览室围着中庭排开。"),
    "hill_library": ("山麓图书馆", "Hillside Library",
                     "贴着山坡展开的图书馆，屋顶草坡与树影连成一体。"),
    "cloud_tower": ("云端摩天塔", "Cloudline Skyscraper",
                    "二百五十六格高的摩天大楼，层层退台一直伸进云里。"),
    "city_corner": ("城市一角", "City Corner",
                    "河道、步道与几栋小楼拼成的街区一角，适合嵌进城市。"),
}

# 常见建材的近似颜色；未收录的方块按名称关键字推断。
COLORS = {
    "stone": (132, 132, 136), "stone_bricks": (128, 126, 122),
    "cobblestone": (118, 118, 118), "mossy_cobblestone": (104, 120, 96),
    "mossy_stone_bricks": (110, 130, 100), "deepslate": (74, 74, 78),
    "deepslate_bricks": (88, 88, 92), "deepslate_tiles": (80, 80, 84),
    "blackstone": (62, 56, 60), "basalt": (78, 78, 80),
    "sandstone": (222, 208, 160), "smooth_sandstone": (228, 214, 168),
    "red_sandstone": (200, 130, 70), "dirt": (122, 88, 60),
    "grass_block": (104, 160, 74), "podzol": (110, 82, 48),
    "mud": (92, 72, 60), "gravel": (140, 134, 130),
    "clay": (160, 166, 176), "terracotta": (170, 110, 82),
    "bricks": (152, 84, 70), "quartz_block": (232, 226, 216),
    "smooth_quartz": (236, 230, 220), "calcite": (222, 222, 214),
    "prismarine": (96, 160, 150), "dark_prismarine": (60, 110, 100),
    "copper_block": (188, 112, 78), "oxidized_copper": (90, 160, 140),
    "oxidized_cut_copper": (86, 156, 136),
    "oxidized_cut_copper_stairs": (80, 148, 130),
    "oxidized_cut_copper_slab": (86, 156, 136),
    "weathered_cut_copper": (110, 150, 120),
    "gold_block": (232, 196, 72), "iron_block": (210, 210, 214),
    "oak_planks": (168, 132, 82), "spruce_planks": (112, 80, 48),
    "birch_planks": (206, 184, 132), "jungle_planks": (160, 116, 80),
    "acacia_planks": (178, 100, 58), "dark_oak_planks": (86, 62, 36),
    "cherry_planks": (226, 170, 162), "bamboo_planks": (198, 174, 98),
    "crimson_planks": (112, 54, 70), "warped_planks": (60, 112, 110),
    "mangrove_planks": (120, 60, 50), "pale_oak_planks": (224, 220, 212),
    "oak_log": (108, 82, 50), "spruce_log": (78, 56, 34),
    "birch_log": (214, 210, 198), "dark_oak_log": (64, 46, 28),
    "cherry_log": (160, 120, 110), "jungle_log": (96, 76, 44),
    "oak_leaves": (66, 128, 54), "spruce_leaves": (52, 100, 56),
    "birch_leaves": (94, 142, 74), "cherry_leaves": (232, 160, 180),
    "azalea_leaves": (86, 130, 58), "mangrove_leaves": (70, 120, 50),
    "glass": (176, 214, 226), "glass_pane": (176, 214, 226),
    "white_stained_glass": (226, 232, 236), "gray_stained_glass": (96, 104, 108),
    "black_stained_glass": (38, 38, 42), "blue_stained_glass": (58, 92, 170),
    "cyan_stained_glass": (56, 140, 150), "light_blue_stained_glass": (110, 170, 220),
    "white_concrete": (224, 228, 230), "light_gray_concrete": (160, 162, 164),
    "gray_concrete": (84, 88, 92), "black_concrete": (30, 32, 36),
    "red_concrete": (150, 48, 44), "blue_concrete": (48, 68, 150),
    "cyan_concrete": (32, 120, 136), "yellow_concrete": (220, 176, 60),
    "green_concrete": (72, 110, 60), "brown_concrete": (110, 74, 50),
    "white_wool": (228, 230, 230), "light_gray_wool": (160, 160, 158),
    "gray_wool": (90, 92, 96), "black_wool": (36, 36, 40),
    "red_wool": (164, 54, 50), "blue_wool": (52, 70, 150),
    "yellow_wool": (226, 190, 70), "green_wool": (70, 112, 58),
    "water": (60, 110, 200), "lava": (214, 110, 32),
    "snow_block": (240, 244, 248), "ice": (176, 210, 236),
    "tuff": (110, 110, 100), "andesite": (136, 136, 138),
    "diorite": (206, 206, 204), "granite": (154, 106, 88),
    "smooth_stone": (158, 158, 158), "polished_andesite": (132, 134, 134),
    "polished_diorite": (192, 192, 190), "polished_granite": (154, 110, 92),
    "polished_deepslate": (70, 70, 74), "polished_blackstone": (58, 52, 56),
    "polished_tuff": (120, 120, 110), "bamboo_block": (186, 170, 82),
    "lantern": (240, 190, 100), "torch": (250, 200, 110),
    "glowstone": (236, 206, 130), "sea_lantern": (200, 226, 214),
    "redstone_lamp": (200, 140, 80), "shroomlight": (226, 150, 80),
    "hay_block": (196, 172, 70), "melon": (110, 160, 60),
    "pumpkin": (200, 130, 40), "bookshelf": (150, 120, 80),
    "barrel": (140, 110, 70), "chest": (150, 112, 66),
    "campfire": (190, 130, 70), "bell": (222, 186, 84),
    "amethyst_block": (160, 120, 200), "lapis_block": (50, 80, 160),
    "emerald_block": (60, 180, 100), "diamond_block": (110, 210, 210),
    "netherite_block": (70, 60, 58), "obsidian": (30, 24, 44),
    "brick_wall": (150, 84, 70), "nether_bricks": (60, 36, 40),
}

KEYWORD_COLORS = [
    ("glass", (176, 214, 226)), ("leaves", (66, 128, 54)),
    ("log", (108, 82, 50)), ("wood", (150, 116, 72)),
    ("planks", (168, 132, 82)), ("wool", (200, 200, 200)),
    ("concrete", (170, 170, 170)), ("terracotta", (170, 110, 82)),
    ("stone", (132, 132, 136)), ("sand", (222, 208, 160)),
    ("deepslate", (74, 74, 78)), ("brick", (150, 84, 70)),
    ("copper", (188, 112, 78)), ("gold", (232, 196, 72)),
    ("iron", (210, 210, 214)), ("water", (60, 110, 200)),
    ("snow", (240, 244, 248)), ("quartz", (232, 226, 216)),
]


def color_for(name):
    short = name.split(":", 1)[-1]
    if name in COLORS:
        return COLORS[name]
    if short in COLORS:
        return COLORS[short]
    for key, color in KEYWORD_COLORS:
        if key in short:
            return color
    hue = sum(ord(c) * (i + 3) for i, c in enumerate(short))
    base = 90 + hue % 70
    return (base, base - 6 + hue % 12, base - 14 + hue % 20)


def read_structure(path):
    root = nbtlib.load(path)
    palette = []
    for entry in root["palette"]:
        name = str(entry["id"])
        palette.append(name)
    blocks = {}
    for entry in root["blocks"]:
        pos = entry["pos"]
        x, y, z = int(pos[0]), int(pos[1]), int(pos[2])
        name = palette[int(entry["state"])]
        if name == "minecraft:air":
            continue
        blocks[(x, y, z)] = name
    return root, blocks


def render_icon(blocks, size=128):
    exposed = []
    for (x, y, z), name in blocks.items():
        if any((x + dx, y + dy, z + dz) not in blocks
               for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0),
                                  (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            exposed.append((x, y, z, name))
    if not exposed:
        return Image.new("RGBA", (32, 32), (0, 0, 0, 0))

    cos30 = math.cos(math.radians(30.0))
    sin30 = 0.5

    def project(px, py, pz):
        return ((px - pz) * cos30, (px + pz) * sin30 - py)

    corners = []
    for x, y, z, _ in exposed:
        for dx in (0, 1):
            for dy in (0, 1):
                for dz in (0, 1):
                    corners.append(project(x + dx, y + dy, z + dz))
    min_x = min(c[0] for c in corners)
    max_x = max(c[0] for c in corners)
    min_y = min(c[1] for c in corners)
    max_y = max(c[1] for c in corners)
    span = max(max_x - min_x, max_y - min_y, 0.001)
    scale = (size - 10) / span
    offset_x = (size - (max_x - min_x) * scale) / 2 - min_x * scale
    offset_y = (size - (max_y - min_y) * scale) / 2 - min_y * scale

    def px(point):
        return (point[0] * scale + offset_x, point[1] * scale + offset_y)

    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for x, y, z, name in sorted(exposed, key=lambda b: (b[0] + b[1] + b[2])):
        base = color_for(name)
        top = tuple(min(255, int(c * 1.16)) for c in base) + (255,)
        left = tuple(int(c * 0.86) for c in base) + (255,)
        right = tuple(int(c * 0.66) for c in base) + (255,)
        p000 = px(project(x, y, z))
        p100 = px(project(x + 1, y, z))
        p010 = px(project(x, y + 1, z))
        p110 = px(project(x + 1, y + 1, z))
        p001 = px(project(x, y, z + 1))
        p101 = px(project(x + 1, y, z + 1))
        p011 = px(project(x, y + 1, z + 1))
        p111 = px(project(x + 1, y + 1, z + 1))
        draw.polygon([p010, p110, p111, p011], fill=top)
        draw.polygon([p001, p101, p111, p011], fill=left)
        draw.polygon([p100, p101, p111, p110], fill=right)
    return image.resize((32, 32), Image.LANCZOS)


def parse_catalog():
    source = (ROOT / "src/main/java/com/akun/xinkun/building/BuildingType.java").read_text(encoding="utf-8")
    entries = re.findall(r'^\s+([A-Z0-9_]+)\("([a-z0-9_]+)",\s*"([a-z0-9_]+)",\s*Category\.([A-Z]+),\s*([^)]+)\)[;,)]',
                         source, re.M)
    return entries


def write_item_assets(item_id):
    definition = {
        "model": {
            "type": "minecraft:model",
            "model": f"xinkun_genshin:item/{item_id}",
        }
    }
    (ITEMS / f"{item_id}.json").write_text(json.dumps(definition, indent=2) + "\n", encoding="utf-8")
    model = {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": f"xinkun_genshin:item/{item_id}"},
    }
    (MODELS / f"{item_id}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")


def main():
    for folder in (TEX, MODELS, ITEMS):
        folder.mkdir(parents=True, exist_ok=True)
    zh = {}
    en = {}
    entries = parse_catalog()
    if len(entries) < 59:
        raise SystemExit(f"expected at least 59 catalog entries, parsed {len(entries)}")
    for const, item_id, blueprint, category, _owner in entries:
        if item_id not in NAMES:
            raise SystemExit(f"missing display name for {item_id}")
        zh_name, en_name, zh_tip = NAMES[item_id]
        nbt_path = STRUCT / f"{blueprint}.nbt"
        if not nbt_path.exists():
            raise SystemExit(f"missing structure for {item_id}: {nbt_path}")
        _root, blocks = read_structure(nbt_path)
        icon = render_icon(blocks)
        icon.save(TEX / f"{item_id}.png")
        write_item_assets(item_id)
        zh[f"item.xinkun_genshin.{item_id}"] = zh_name
        zh[f"item.xinkun_genshin.{item_id}.tooltip"] = zh_tip
        en[f"item.xinkun_genshin.{item_id}"] = en_name
        en[f"item.xinkun_genshin.{item_id}.tooltip"] = zh_tip
        print(f"{item_id:22s} {len(blocks):6d} blocks -> icon")

    zh["itemGroup.xinkun_genshin.items"] = "新坤的原神道具"
    zh["itemGroup.xinkun_genshin.buildings_generic"] = "新坤的原神建筑（通用）"
    zh["itemGroup.xinkun_genshin.buildings_character"] = "新坤的原神建筑（角色）"
    en["itemGroup.xinkun_genshin.items"] = "Xinkun's Genshin Items"
    en["itemGroup.xinkun_genshin.buildings_generic"] = "Xinkun's Genshin Buildings (Generic)"
    en["itemGroup.xinkun_genshin.buildings_character"] = "Xinkun's Genshin Buildings (Character)"

    (ROOT / "build/tmp/building_lang_zh.json").parent.mkdir(parents=True, exist_ok=True)
    (ROOT / "build/tmp/building_lang_zh.json").write_text(
        json.dumps(zh, ensure_ascii=False, indent=2), encoding="utf-8")
    (ROOT / "build/tmp/building_lang_en.json").write_text(
        json.dumps(en, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"assets written: {len(entries)} buildings")


if __name__ == "__main__":
    main()
