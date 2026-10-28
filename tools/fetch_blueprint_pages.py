"""Fetch candidate blueprint pages from mc-schematics.com with a polite delay.

Temporary working tool for the building-catalog expansion; not shipped.
"""

import re
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "blueprints_src" / "tmp" / "pages"
OUT.mkdir(parents=True, exist_ok=True)

SLUGS = [
    # modern villas
    "modern-luxury-villa-e2289547a6",
    "modern-sandstone-villa-c71012756a",
    "modern-villa-design-303d6569f3",
    "modern-villa-with-extensive-garden-3fdf44e317",
    "dawn-villa-house-5458ae98dc",
    "luxury-waterfront-villa-e6e5a9e226",
    "contemporary-luxury-mansion-e656cc2cdb",
    "contemporary-suburban-family-residence-b98e573dba",
    # skyscrapers
    "modern-skyscraper-61c2341622",
    "modern-skyscraper-design-4efabe172b",
    "modern-city-skyscrapers-162a4fef41",
    "giant-city-skyscraper-with-trees-c903aa8161",
    "blocky-space-needle-tower-1febedf775",
    "absolute-bastion-sky-tower-e74187fb43",
    # medieval
    "cozy-medieval-village-house-schematic-for-minecraft-8420009ef0",
    "detailed-fantasy-medieval-manor-build-3a3750b883",
    "compact-gothic-medieval-watchtower-with-interior-f560510db8",
    "castle-cornet-medieval-fortress-42073492c2",
    "medieval-lighthouse-dwelling-62e6d94e52",
    "fantasy-medieval-castle-build-aa66df58e1",
    "functional-medieval-lighthouse-structure-19f6f5b26f",
    # other generic
    "coastal-lighthouse-residence-daae3fdbb0",
    "ancient-desert-temple-structure-bb4d738746",
    "golden-sun-observatory-6e44d57457",
    "japanese-garden-gazebo-a940fc225b",
    # Furina
    "french-stone-cathedral-2b862067b1",
    "baroque-tropical-city-castle-003708144a",
    "cinderella-castle-5fb4575d58",
    # Hu Tao
    "grand-chinese-mansion-400fccf984",
    # Yoimiya
    "japanese-pagoda-house-f73bfbd747",
    "japanese-style-mansion-972541b973",
    "cherry-japanese-temple-structure-de2efde788",
    # Columbina
    "overgrown-moon-teapot-structure-fb5c5cd62a",
]


def main() -> None:
    only = set(sys.argv[1:])
    for index, slug in enumerate(SLUGS):
        if only and slug not in only:
            continue
        target = OUT / f"{slug}.html"
        if target.exists() and target.stat().st_size > 1000:
            print(f"cached {slug}", flush=True)
            continue
        url = f"https://mc-schematics.com/s/{slug}"
        request = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                          "(KHTML, like Gecko) Chrome/126.0 Safari/537.36",
            "Accept-Language": "en-US,en;q=0.9",
        })
        try:
            with urllib.request.urlopen(request, timeout=40) as response:
                body = response.read()
        except Exception as error:  # noqa: BLE001 - diagnostic tool
            print(f"FAIL {slug}: {error}", flush=True)
            time.sleep(8)
            continue
        target.write_bytes(body)
        text = body.decode("utf-8", "replace")
        title = re.search(r"<title>(.*?)</title>", text, re.S)
        file_url = re.search(r"https://files\.mc-schematics\.com/[^\"']+", text)
        info = re.search(r"·\s*([\d,]+) blocks\s*·\s*Minecraft ([^<\"]+?)\s*(?:Java Edition)?\.", text)
        print(
            f"{len(body):7d}  {slug}\n"
            f"         title={title.group(1).strip() if title else '?'}\n"
            f"         blocks={info.group(1) if info else '?'} version={info.group(2) if info else '?'}\n"
            f"         file={file_url.group(0) if file_url else '?'}",
            flush=True,
        )
        time.sleep(6)


if __name__ == "__main__":
    main()
