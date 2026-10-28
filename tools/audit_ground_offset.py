"""模拟 BlueprintLibrary.computeGroundOffset / BuildBlueprint.shiftDown。

对每份已发布的 .nbt 计算加载期会发生的“下沉”偏移，并逐块列出会被
丢弃的方块。用于确认蓝图在运行时没有被裁剪。
"""

from collections import Counter
from pathlib import Path

import nbtlib

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/data/xinkun_genshin/structure"


def load(style_id):
    data = nbtlib.load(OUT / f"{style_id}.nbt")
    size = [int(v) for v in data["size"]]
    palette = []
    for entry in data["palette"]:
        name = str(entry["id"])
        props = {}
        if "properties" in entry:
            props = {str(k): str(v) for k, v in entry["properties"].items()}
        body = ",".join(f"{k}={props[k]}" for k in sorted(props))
        palette.append(name + (f"[{body}]" if body else ""))
    blocks = {}
    for entry in data["blocks"]:
        pos = [int(v) for v in entry["pos"]]
        blocks[(pos[0], pos[1], pos[2])] = palette[int(entry["state"])]
    return size, blocks


def main():
    for path in sorted(OUT.glob("*.nbt")):
        style = path.stem
        size, blocks = load(style)
        w, h, d = size
        area = w * d
        threshold = max(1, -(-area * 25 // 100))
        per_y = Counter(y for (_, y, _) in blocks)
        layers = []
        ground = 0
        found = False
        for y in range(h):
            layers.append(per_y.get(y, 0))
            if not found and per_y.get(y, 0) >= threshold:
                ground = y
                found = True
        doors = [(p, s) for p, s in blocks.items()
                 if s.startswith("minecraft:") and "door" in s.split("[")[0][10:]
                 and "half=lower" in s]
        door_block = 0
        for door, _ in doors:
            if door[1] - ground < 0:
                door_block += 1
        # Java: ground offset only seats the structure; no block is removed.
        print(f"\n=== {style}  size={w}x{h}x{d} area={area} threshold={threshold} ===")
        print("下层实心数 Y0..Y7:", layers[:8])
        print(f"computeGroundOffset={ground}  doors={len(doors)}  "
              f"门低于地面={door_block}")
        buried = {p: s for p, s in blocks.items() if p[1] < ground}
        if buried:
            mats = Counter(s.split("[")[0] for s in buried.values())
            print(f"埋入地下的原始构件 {len(buried)} 块（不丢弃、不放置）: "
                  f"{mats.most_common(12)}")
            sample = sorted(buried.items(), key=lambda kv: kv[0])[:8]
            for p, s in sample:
                print("   ", p, s)
        else:
            print("无地下构件；地面层直接坐落在 terrain")


if __name__ == "__main__":
    main()
