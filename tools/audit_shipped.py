"""检查已发布蓝图本身：是否残留自然地形、是否含会被 sanitize 抹掉的方块。"""

from collections import Counter
from pathlib import Path

import nbtlib

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/data/xinkun_genshin/structure"

NATURAL = {
    "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
    "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
    "minecraft:mud", "minecraft:gravel", "minecraft:sand", "minecraft:red_sand",
    "minecraft:sandstone", "minecraft:red_sandstone", "minecraft:deepslate",
    "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
    "minecraft:tuff", "minecraft:calcite", "minecraft:dripstone_block",
    "minecraft:moss_block", "minecraft:moss_carpet", "minecraft:snow",
    "minecraft:snow_block", "minecraft:powder_snow", "minecraft:ice",
    "minecraft:packed_ice", "minecraft:blue_ice", "minecraft:short_grass",
    "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
    "minecraft:bamboo", "minecraft:sugar_cane", "minecraft:sweet_berry_bush",
}
SANITIZED = {
    "minecraft:command_block", "minecraft:chain_command_block",
    "minecraft:repeating_command_block", "minecraft:structure_block",
    "minecraft:jigsaw", "minecraft:barrier", "minecraft:light",
    "minecraft:structure_void", "minecraft:bedrock", "minecraft:spawner",
    "minecraft:tnt", "minecraft:fire", "minecraft:soul_fire", "minecraft:lava",
    "minecraft:nether_portal", "minecraft:end_portal", "minecraft:end_gateway",
}


def main():
    for path in sorted(OUT.glob("*.nbt")):
        data = nbtlib.load(path)
        size = [int(v) for v in data["size"]]
        palette = [str(e["id"]) for e in data["palette"]]
        used = Counter()
        ys = Counter()
        positions = {}
        for entry in data["blocks"]:
            pos = tuple(int(v) for v in entry["pos"])
            name = palette[int(entry["state"])]
            used[name] += 1
            ys[pos[1]] += 1
            positions[pos] = name
        natural = {k: v for k, v in used.items() if k in NATURAL}
        embedded = {}
        exposed = {}
        for pos, name in positions.items():
            if name not in NATURAL:
                continue
            neighbours = sum(
                1 for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0))
                if (pos[0] + d[0], pos[1] + d[1], pos[2] + d[2]) in positions
                and positions[(pos[0] + d[0], pos[1] + d[1], pos[2] + d[2])] not in NATURAL
            )
            target = embedded if neighbours >= 4 else exposed
            target[name] = target.get(name, 0) + 1
        sanit = {k: v for k, v in used.items() if k in SANITIZED}
        ores = {k: v for k, v in used.items() if k.endswith("_ore")}
        unsupported = [p for p in palette if p.startswith("minecraft:") is False]
        print(f"\n=== {path.stem} size={size} 总数={sum(used.values())} 材质种类={len(used)} ===")
        print("自然方块残留（被构件包围=作者垫层）:", embedded or "无")
        print("自然方块残留（外露=疑似地形）:", exposed or "无")
        print("矿石残留:", ores or "无")
        print("会被 sanitize 抹掉:", sanit or "无")
        print("非 minecraft 命名空间材质:", unsupported or "无")
        low = [(y, ys[y]) for y in sorted(ys) if y <= 3]
        print("Y0..Y3 方块数:", low)


if __name__ == "__main__":
    main()
