"""Render a bundled structure NBT as an isometric PNG for quick visual review.

Usage: python tools/render_blueprint.py <structure-id> [out.png] [scale]
"""

import sys
from pathlib import Path

import nbtlib
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
STRUCT = ROOT / "src/main/resources/data/xinkun_genshin/structure"

COLORS = {
    "minecraft:grass_block": (104, 160, 74),
    "minecraft:dirt": (122, 88, 60),
    "minecraft:sand": (222, 208, 160),
    "minecraft:smooth_sandstone": (226, 214, 168),
    "minecraft:sandstone": (220, 206, 158),
    "minecraft:chiseled_sandstone": (230, 218, 176),
    "minecraft:cut_sandstone": (222, 208, 160),
    "minecraft:dark_oak_log": (60, 44, 28),
    "minecraft:dark_oak_planks": (72, 52, 32),
    "minecraft:polished_tuff": (108, 110, 104),
    "minecraft:tuff_bricks": (96, 98, 92),
    "minecraft:mossy_stone_bricks": (104, 124, 96),
    "minecraft:stone_bricks": (126, 126, 122),
    "minecraft:chiseled_stone_bricks": (120, 120, 116),
    "minecraft:oxidized_cut_copper": (86, 156, 136),
    "minecraft:oxidized_copper": (86, 156, 136),
    "minecraft:oxidized_cut_copper_stairs": (80, 148, 130),
    "minecraft:oxidized_cut_copper_slab": (86, 156, 136),
    "minecraft:weathered_cut_copper": (110, 150, 120),
    "minecraft:gold_block": (232, 196, 72),
    "minecraft:green_stained_glass": (70, 150, 80),
    "minecraft:lime_stained_glass": (130, 200, 90),
    "minecraft:azalea_leaves": (66, 128, 54),
    "minecraft:flowering_azalea_leaves": (150, 110, 150),
    "minecraft:water": (60, 110, 200),
    "minecraft:lantern": (240, 200, 110),
    "minecraft:end_rod": (236, 236, 226),
    "minecraft:lightning_rod": (190, 120, 70),
    "minecraft:glowstone": (240, 220, 150),
}

KEYWORDS = [
    ("glass", (150, 200, 210)), ("leaves", (66, 128, 54)),
    ("log", (96, 70, 42)), ("planks", (150, 116, 72)),
    ("sand", (222, 208, 160)), ("copper", (86, 156, 136)),
    ("gold", (232, 196, 72)), ("water", (60, 110, 200)),
    ("grass", (104, 160, 74)), ("tuff", (104, 106, 100)),
    ("stone", (128, 128, 124)), ("flower", (190, 120, 160)),
    ("fern", (80, 140, 60)), ("vine", (70, 130, 50)),
]


def color_for(name):
    if name in COLORS:
        return COLORS[name]
    short = name.split(":", 1)[-1]
    for key, c in KEYWORDS:
        if key in short:
            return c
    h = sum(ord(ch) * (i + 3) for i, ch in enumerate(short))
    b = 100 + h % 60
    return (b, b - 8, b - 16)


def load(path):
    root = nbtlib.load(path)
    pal = [str(e["id"]) for e in root["palette"]]
    size = [int(v) for v in root["size"]]
    blocks = []
    for e in root["blocks"]:
        p = e["pos"]
        blocks.append(((int(p[0]), int(p[1]), int(p[2])), pal[int(e["state"])]))
    return size, blocks


def render(structure_id, out, scale=3):
    size, blocks = load(STRUCT / (structure_id + ".nbt"))
    w, hgt, v = 7, 3.5, 7.5
    pts = []
    for (x, y, z), name in blocks:
        sx = (x - z) * w
        sy = (x + z) * hgt - y * v
        pts.append((sx, sy))
    minx = min(p[0] for p in pts) - w
    miny = min(p[1] for p in pts) - v - hgt
    maxx = max(p[0] for p in pts) + w
    maxy = max(p[1] for p in pts) + v + hgt
    W = int((maxx - minx) * scale) + 20
    H = int((maxy - miny) * scale) + 20
    img = Image.new("RGB", (W, H), (24, 28, 32))
    d = ImageDraw.Draw(img)

    def tr(p):
        return ((p[0] - minx) * scale + 10, (p[1] - miny) * scale + 10)

    blocks.sort(key=lambda b: (b[0][0] + b[0][2], b[0][1]))
    for (x, y, z), name in blocks:
        sx = (x - z) * w
        sy = (x + z) * hgt - y * v
        top = color_for(name)
        left = tuple(int(c * 0.74) for c in top)
        right = tuple(int(c * 0.56) for c in top)
        top_poly = [tr((sx, sy - v - hgt)), tr((sx + w, sy - v)),
                    tr((sx, sy - v + hgt)), tr((sx - w, sy - v))]
        left_poly = [tr((sx - w, sy - v)), tr((sx, sy - v + hgt)),
                     tr((sx, sy + hgt)), tr((sx - w, sy))]
        right_poly = [tr((sx, sy - v + hgt)), tr((sx + w, sy - v)),
                      tr((sx + w, sy)), tr((sx, sy + hgt))]
        d.polygon(left_poly, fill=left)
        d.polygon(right_poly, fill=right)
        d.polygon(top_poly, fill=top)
    img.save(out)
    print(out, W, H, "blocks", len(blocks), "size", size)
    return out


if __name__ == "__main__":
    sid = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else f"{sid}.png"
    sc = float(sys.argv[3]) if len(sys.argv) > 3 else 3
    render(sid, out, sc)
