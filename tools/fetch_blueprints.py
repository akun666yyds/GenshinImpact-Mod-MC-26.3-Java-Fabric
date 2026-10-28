"""Download the expanded building catalog from mc-schematics.com.

Temporary working tool for the building-catalog expansion; not shipped.
Requests are spaced out because the site is behind Cloudflare.
"""

import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "blueprints_src"
SRC.mkdir(exist_ok=True)

FILES = {
    # modern villas
    "modern_villa_with_extensive_garden.schem":
        "https://files.mc-schematics.com/b/3fdf44e317/modern-villa-with-extensive-garden.schem",
    "dawn_villa_house.schem":
        "https://files.mc-schematics.com/b/5458ae98dc/dawn-villa-house.schem",
    "contemporary_luxury_mansion.schem":
        "https://files.mc-schematics.com/b/e656cc2cdb/contemporary-luxury-mansion-architectural-build.schem",
    "contemporary_suburban_residence.schem":
        "https://files.mc-schematics.com/b/b98e573dba/contemporary-suburban-family-residence.schem",
    # city towers
    "modern_skyscraper_design.schem":
        "https://files.mc-schematics.com/b/4efabe172b/modern-skyscraper-design.schem",
    "skyscraper_rocket_farm.litematic":
        "https://files.mc-schematics.com/b/f177b6cc74/skyscraper-rocket-farm.litematic",
    "giant_city_skyscraper.litematic":
        "https://files.mc-schematics.com/b/c903aa8161/giant-city-skyscraper-with-trees.litematic",
    "modern_city_skyscrapers.litematic":
        "https://files.mc-schematics.com/b/162a4fef41/modern-city-skyscrapers.litematic",
    # medieval
    "cozy_medieval_house.litematic":
        "https://files.mc-schematics.com/b/8420009ef0/cozy-medieval-village-house-schematic-for-minecraft.litematic",
    "gothic_watchtower.schem":
        "https://files.mc-schematics.com/b/f560510db8/compact-gothic-medieval-watchtower-with-interior.schem",
    "fantasy_medieval_manor.litematic":
        "https://files.mc-schematics.com/b/3a3750b883/detailed-fantasy-medieval-manor-build.litematic",
    "medieval_lighthouse_dwelling.schem":
        "https://files.mc-schematics.com/b/62e6d94e52/medieval-lighthouse-dwelling.schem",
    # other generic
    "ancient_desert_temple.litematic":
        "https://files.mc-schematics.com/b/bb4d738746/ancient-desert-temple-structure.litematic",
    "coastal_lighthouse_residence.litematic":
        "https://files.mc-schematics.com/b/daae3fdbb0/coastal-lighthouse-residence.litematic",
    "japanese_garden_gazebo.litematic":
        "https://files.mc-schematics.com/b/a940fc225b/japanese-garden-gazebo.litematic",
    # Furina
    "baroque_city_castle.schem":
        "https://files.mc-schematics.com/b/003708144a/baroque-tropical-city-castle.schem",
    "french_stone_cathedral.schem":
        "https://files.mc-schematics.com/b/2b862067b1/french-stone-cathedral.schem",
    # Hu Tao
    "grand_chinese_mansion.litematic":
        "https://files.mc-schematics.com/b/400fccf984/grand-chinese-mansion.litematic",
    # Yoimiya
    "japanese_style_mansion.litematic":
        "https://files.mc-schematics.com/b/972541b973/japanese-style-mansion.litematic",
    "cherry_japanese_temple.litematic":
        "https://files.mc-schematics.com/b/de2efde788/cherry-japanese-temple-structure.litematic",
    # Columbina
    "overgrown_moon_teapot.litematic":
        "https://files.mc-schematics.com/b/fb5c5cd62a/overgrown-moon-teapot-structure.litematic",
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
