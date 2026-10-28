"""Download the second batch of community blueprints from mc-schematics.com.

Candidates were picked from the cached listing pages in blueprints_src/tmp/pages.
Not shipped with the mod.
"""

import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "blueprints_src"
SRC.mkdir(exist_ok=True)

FILES = {
    "luxury_waterfront_villa.schem":
        "https://files.mc-schematics.com/b/e6e5a9e226/luxury-waterfront-villa.schem",
    "modern_villa_design.litematic":
        "https://files.mc-schematics.com/b/303d6569f3/modern-villa-design.litematic",
    "modern_sandstone_villa.schem":
        "https://files.mc-schematics.com/b/c71012756a/modern-sandstone-villa.schem",
    "castle_cornet_medieval_fortress.schem":
        "https://files.mc-schematics.com/b/42073492c2/castle-cornet-medieval-fortress.schem",
    "fantasy_medieval_castle_build.schem":
        "https://files.mc-schematics.com/b/aa66df58e1/fantasy-medieval-castle-build.schem",
    "japanese_pagoda_house.schem":
        "https://files.mc-schematics.com/b/f73bfbd747/japanese-pagoda-house.schem",
    "golden_sun_observatory.schematic":
        "https://files.mc-schematics.com/b/6e44d57457/golden-sun-observatory.schematic",
    "absolute_bastion_sky_tower.schem":
        "https://files.mc-schematics.com/b/e74187fb43/absolute-bastion-sky-tower.schem",
    "cinderella_castle.litematic":
        "https://files.mc-schematics.com/b/5fb4575d58/cinderella-castle.litematic",
    "blocky_space_needle_tower.schematic":
        "https://files.mc-schematics.com/b/1febedf775/blocky-space-needle-tower.schematic",
    "modern_skyscraper.schematic":
        "https://files.mc-schematics.com/b/61c2341622/modern-skyscraper.schematic",
    "functional_medieval_lighthouse.litematic":
        "https://files.mc-schematics.com/b/19f6f5b26f/functional-medieval-lighthouse-structure.litematic",
}


def main() -> None:
    for name, url in FILES.items():
        target = SRC / name
        if target.exists() and target.stat().st_size > 1000:
            print(f"cached {name} ({target.stat().st_size} bytes)", flush=True)
            continue
        for attempt in range(3):
            try:
                request = urllib.request.Request(url, headers={
                    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                  "(KHTML, like Gecko) Chrome/126.0 Safari/537.36",
                    "Accept-Language": "en-US,en;q=0.9",
                })
                with urllib.request.urlopen(request, timeout=120) as response:
                    body = response.read()
                target.write_bytes(body)
                print(f"saved {name} ({len(body)} bytes)", flush=True)
                break
            except Exception as error:  # noqa: BLE001 - diagnostic tool
                print(f"retry {name} attempt {attempt + 1}: {error}", flush=True)
                time.sleep(12)
        time.sleep(5)


if __name__ == "__main__":
    main()
