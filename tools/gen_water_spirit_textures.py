"""Generate 64x64 water spirit textures (seahorse / crab / octopus).

The palette strips at fixed UV coordinates are sampled by the eye, gold, pink,
pale and mint boxes in the custom models; everything else is a flowing water
gradient with caustics and bubbles.
"""

import math
import os
import random

from PIL import Image

SIZE = 64
OUT = os.path.join("src", "main", "resources", "assets", "xinkun_genshin", "textures", "entity", "water_spirit")

VARIANTS = {
    "seahorse": {
        "top": (10, 52, 96),
        "mid": (34, 154, 190),
        "bottom": (126, 236, 242),
        "caustic": (176, 250, 252),
        "seed": 17,
    },
    "crab": {
        "top": (12, 40, 96),
        "mid": (40, 118, 214),
        "bottom": (118, 214, 250),
        "caustic": (168, 232, 255),
        "seed": 41,
    },
    "octopus": {
        "top": (26, 34, 94),
        "mid": (74, 96, 216),
        "bottom": (150, 206, 250),
        "caustic": (190, 226, 255),
        "seed": 73,
    },
}

# UV palette strips. Every special box in the models fits inside one of these.
PATCHES = {
    "white": (0, 0, 16, 8),
    "black": (16, 0, 32, 8),
    "cyan": (32, 0, 48, 8),
    "gold": (48, 0, 64, 8),
    "pink": (0, 48, 20, 64),
    "pale": (20, 48, 48, 64),
    "mint": (48, 48, 64, 64),
}

PATCH_COLORS = {
    "white": ((246, 252, 255), (206, 240, 250)),
    "black": ((10, 18, 38), (24, 42, 74)),
    "cyan": ((104, 246, 255), (52, 196, 236)),
    "gold": ((255, 214, 112), (226, 158, 52)),
    "pink": ((250, 168, 196), (232, 120, 160)),
    "pale": ((214, 246, 252), (168, 224, 242)),
    "mint": ((176, 250, 232), (108, 216, 206)),
}


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def build_base(cfg):
    img = Image.new("RGB", (SIZE, SIZE))
    px = img.load()
    top, mid, bottom, caustic = cfg["top"], cfg["mid"], cfg["bottom"], cfg["caustic"]
    for y in range(SIZE):
        for x in range(SIZE):
            t = (x * 0.35 + y) / (SIZE - 1) / 1.35
            if t < 0.55:
                color = mix(top, mid, t / 0.55)
            else:
                color = mix(mid, bottom, (t - 0.55) / 0.45)
            wave = (math.sin(x * 0.2 + y * 0.12 + 1.7) * math.sin(x * 0.09 - y * 0.21) * 0.5 + 0.5)
            wave2 = (math.sin(x * 0.11 - y * 0.17 + 0.4) * math.sin(x * 0.05 + y * 0.07 + 2.1) * 0.5 + 0.5)
            if wave > 0.74:
                color = mix(color, caustic, (wave - 0.74) * 1.5)
            if wave2 > 0.88:
                color = mix(color, (255, 255, 255), (wave2 - 0.88) * 1.1)
            shade = ((x * 7 + y * 11) % 7) - 3
            color = tuple(max(0, min(255, c + shade)) for c in color)
            px[x, y] = color
    return img


def paint_patch(img, rect, colors, rng):
    x0, y0, x1, y1 = rect
    px = img.load()
    a, b = colors
    for y in range(y0, y1):
        t = (y - y0) / max(1, (y1 - y0 - 1))
        row = mix(a, b, t * 0.8)
        for x in range(x0, x1):
            jitter = rng.randint(-4, 4)
            px[x, y] = tuple(max(0, min(255, c + jitter)) for c in row)


def add_bubbles(img, rng, color):
    px = img.load()
    for _ in range(26):
        cx = rng.randint(2, SIZE - 3)
        cy = rng.randint(9, 62)
        radius = rng.choice([1, 1, 1, 2, 2, 3])
        if 48 <= cy and (cx < 48 or cy < 48):
            continue
        if cy < 8:
            continue
        for dy in range(-radius, radius + 1):
            for dx in range(-radius, radius + 1):
                dist = math.hypot(dx, dy)
                if dist > radius + 0.2:
                    continue
                x, y = cx + dx, cy + dy
                if not (0 <= x < SIZE and 8 <= y < 48):
                    continue
                if dist > radius - 0.9:
                    px[x, y] = color
                elif rng.random() > 0.35:
                    base = px[x, y]
                    px[x, y] = mix(base, (255, 255, 255), 0.18)


def add_sparkles(img, rng):
    px = img.load()
    for _ in range(80):
        x = rng.randint(49, 62)
        y = rng.randint(1, 6)
        c = px[x, y]
        px[x, y] = mix(c, (255, 255, 235), 0.75)


def make(name, cfg):
    rng = random.Random(cfg["seed"])
    img = build_base(cfg)
    add_bubbles(img, rng, mix(cfg["caustic"], (255, 255, 255), 0.45))
    for patch_name, rect in PATCHES.items():
        paint_patch(img, rect, PATCH_COLORS[patch_name], rng)
    add_sparkles(img, rng)
    out = os.path.join(OUT, name + ".png")
    img.convert("RGBA").save(out)
    print("wrote", out)


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, cfg in VARIANTS.items():
        make(name, cfg)
