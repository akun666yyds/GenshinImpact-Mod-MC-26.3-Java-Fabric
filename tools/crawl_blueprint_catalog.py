"""Crawl mc-schematics browse pages and collect build candidates.

The site exposes 21 builds per browse page (164 pages at the time of writing).
We only cache the listing HTML and the extracted metadata, and download the
actual schematic files later through ``tools/fetch_selected_blueprints.py``.
"""

from __future__ import annotations

import html
import json
import re
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "blueprints_src" / "tmp" / "browse_pages"
OUT.mkdir(parents=True, exist_ok=True)
INDEX = ROOT / "blueprints_src" / "tmp" / "browse_index.json"
SITE = "https://mc-schematics.com/browse"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/126.0 Safari/537.36",
    "Accept-Language": "en-US,en;q=0.9",
}


def fetch(url: str, timeout: int = 60) -> str:
    request = urllib.request.Request(url, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read().decode("utf-8", "replace")


def parse(page: str) -> list[dict[str, object]]:
    results = []
    for href, inner in re.findall(r'href="(/s/[^"]+)"[^>]*>(.*?)</a>', page, re.S):
        text = re.sub(r"<[^>]+>", " ", inner)
        text = html.unescape(re.sub(r"\s+", " ", text)).strip()
        if not text:
            continue
        blocks = re.search(r"([\d,]+)\s+blocks", text)
        results.append({
            "slug": href[len("/s/"):],
            "text": text,
            "title": re.sub(r"^#\d+\s*", "", text).split(" by ")[0].strip(),
            "blocks": int(blocks.group(1).replace(",", "")) if blocks else 0,
        })
    return results


def main() -> None:
    start = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    end = int(sys.argv[2]) if len(sys.argv) > 2 else start + 24
    index: dict[str, dict[str, object]] = {}
    if INDEX.exists():
        index = {item["slug"]: item for item in json.loads(INDEX.read_text("utf-8"))}
    for page in range(start, end + 1):
        target = OUT / f"page_{page:03d}.html"
        if target.exists() and target.stat().st_size > 5000:
            text = target.read_text("utf-8", "replace")
        else:
            url = SITE if page == 1 else f"{SITE}/page/{page}"
            for attempt in range(3):
                try:
                    text = fetch(url)
                    break
                except Exception as error:  # noqa: BLE001 - network retry
                    print(f"retry page {page} attempt {attempt + 1}: {error}", flush=True)
                    time.sleep(6)
            else:
                continue
            target.write_text(text, encoding="utf-8")
            time.sleep(1.5)
        for item in parse(text):
            index[item["slug"]] = item
        print(f"page {page}: {len(index)} builds indexed", flush=True)
    INDEX.write_text(json.dumps(list(index.values()), ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"wrote {len(index)} entries to {INDEX}", flush=True)


if __name__ == "__main__":
    main()
