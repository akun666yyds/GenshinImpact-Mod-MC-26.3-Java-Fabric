"""按类别核对 strip_terrain：哪些是地形底座，哪些是作者摆放的景致。"""

import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "blueprints_src"
sys.path.insert(0, str(ROOT / "tools"))
import convert_blueprints as cb  # noqa: E402


def main():
    for style_id, meta in cb.SOURCES.items():
        src = cb.load_source_path(SRC / meta["file"]) if hasattr(cb, "load_source_path") else (
            cb.load_litematic(SRC / meta["file"]) if meta["file"].endswith(".litematic")
            else cb.load_schem(SRC / meta["file"]))
        kept, removed_count = cb.strip_terrain(src, style_id)
        kept_set = set(kept)
        removed = {p: s for p, s in src.items() if p not in kept}
        cats = {"ground": [], "veg": [], "ore": []}
        for pos, state in removed.items():
            name, _ = cb.split_state(state)
            if name in cb.GROUND and name not in cb.KEEP_NATURAL.get(style_id, set()):
                cats["ground"].append(pos)
            elif name in cb.VEGETATION:
                cats["veg"].append(pos)
            elif cb.is_ore(name):
                cats["ore"].append(pos)

        print(f"\n=== {style_id}  源 {len(src)}  保留 {len(kept)}  移除 {removed_count} ===")
        for cat, positions in cats.items():
            if not positions:
                continue
            ys = Counter(p[1] for p in positions)
            print(f"{cat}: {len(positions)} 块  Y {min(ys)}..{max(ys)}  "
                  f"Y分布 {sorted(ys.items())[:8]}")
            mats = Counter(cb.split_state(removed[p])[0] for p in positions)
            print("   材质:", mats.most_common(8))
            if cat == "veg":
                # 支撑方块在剥离后是否还在：不在则保留植物会悬空。
                unsupported = 0
                for p in positions:
                    below = (p[0], p[1] - 1, p[2])
                    if below not in kept_set:
                        unsupported += 1
                print(f"   支撑被同时移除的植物: {unsupported}/{len(positions)}")

        # 剥离后最低的保留层：真正的建筑地面。
        ys = sorted({p[1] for p in kept})
        print(f"保留方块 Y {ys[0]}..{ys[-1]}；最低层 {sum(1 for p in kept if p[1] == ys[0])} 块")


if __name__ == "__main__":
    main()
