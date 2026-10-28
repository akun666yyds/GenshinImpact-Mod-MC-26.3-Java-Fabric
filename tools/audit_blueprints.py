"""核对随模组发布的蓝图：完整性、是否自带地基、是否被裁剪或修饰。

对每份蓝图同时读取原始下载文件与发布用 .nbt，做逐方块比对：
  * 原始文件中的每个方块应在发布文件中保持相对位置不变（重命名除外）；
  * 发布文件不得出现原始文件里没有的方块；
  * 统计被移除方块的材质、Y 分布与平面覆盖率，判断是否为“地形底座”；
  * 检查发布蓝图最底层是否为整片地基板，以及被移除方块中是否有夹在建筑内部的构件。
"""

import sys
from collections import Counter, defaultdict
from pathlib import Path

import nbtlib

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "blueprints_src"
OUT = ROOT / "src/main/resources/data/xinkun_genshin/structure"

sys.path.insert(0, str(ROOT / "tools"))
import convert_blueprints as cb  # noqa: E402


def load_source(style_id, meta):
    path = SRC / meta["file"]
    if path.suffix == ".litematic":
        return cb.load_litematic(path)
    return cb.load_schem(path)


def load_shipped(style_id):
    data = nbtlib.load(OUT / f"{style_id}.nbt")
    size = [int(v) for v in data["size"]]
    palette = []
    for entry in data["palette"]:
        name = str(entry["id"])
        props = {}
        if "properties" in entry:
            props = {str(k): str(v) for k, v in entry["properties"].items()}
        palette.append(cb.state_string(name, props))
    blocks = {}
    for entry in data["blocks"]:
        pos = [int(v) for v in entry["pos"]]
        blocks[(pos[0], pos[1], pos[2])] = palette[int(entry["state"])]
    return size, blocks


def main():
    for style_id, meta in cb.SOURCES.items():
        src_blocks = load_source(style_id, meta)
        kept, removed_count = cb.strip_terrain(src_blocks, style_id)
        removed = {pos: state for pos, state in src_blocks.items() if pos not in kept}
        size, shipped = load_shipped(style_id)

        # strip 之后、normalise 之前的包围盒原点，用来对齐坐标。
        xs = [p[0] for p in kept]
        ys = [p[1] for p in kept]
        zs = [p[2] for p in kept]
        origin = (min(xs), min(ys), min(zs))

        expected = {
            (x - origin[0], y - origin[1], z - origin[2]): cb.state_string(*cb.split_state(state))
            for (x, y, z), state in kept.items()
        }
        missing = set(expected) - set(shipped)
        extra = set(shipped) - set(expected)
        changed = {p for p in set(expected) & set(shipped) if expected[p] != shipped[p]}

        print(f"\n=== {style_id}  ({meta['build']}) ===")
        print(f"源方块 {len(src_blocks)}  保留 {len(kept)}  移除 {removed_count}  "
              f"发布 {len(shipped)}  size={size}")
        print(f"比对：缺块 {len(missing)}  多块 {len(extra)}  状态不符 {len(changed)}")

        removed_by_mat = Counter(cb.split_state(s)[0] for s in removed.values())
        print("被移除材质 Top:", removed_by_mat.most_common(10))

        if removed:
            ys_rem = Counter(p[1] for p in removed)
            ys_sorted = sorted(ys_rem)
            base = ys_sorted[0]
            base_count = ys_rem[base]
            print(f"移除 Y 范围 {ys_sorted[0]}..{ys_sorted[-1]}，"
                  f"最低层 Y={base} 移除 {base_count} 块")
            # 被移除方块若四周被保留方块包围，多半是建筑构件而非地表。
            kept_set = set(kept)
            embedded = 0
            for (x, y, z) in removed:
                neighbours = sum(
                    1 for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0))
                    if (x + d[0], y + d[1], z + d[2]) in kept_set)
                if neighbours >= 4:
                    embedded += 1
            print(f"被移除且有 4+ 个保留邻居的方块（疑似构件）: {embedded}")

        bottom = Counter()
        for (x, y, z), state in shipped.items():
            if y == 0:
                bottom[cb.split_state(state)[0]] += 1
        area = size[0] * size[2]
        print(f"发布最低层 Y=0：{sum(bottom.values())}/{area} 格有方块 "
              f"({sum(bottom.values()) / area:.0%})  {bottom.most_common(6)}")

        if extra:
            print("示例多余方块:", list(extra)[:5])
        if changed:
            print("示例状态变化:", [(p, expected[p], shipped[p]) for p in list(changed)[:5]])


if __name__ == "__main__":
    main()
