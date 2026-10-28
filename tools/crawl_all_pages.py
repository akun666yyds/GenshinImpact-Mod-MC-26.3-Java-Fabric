import re, time, urllib.request, pathlib, sys
H={"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36","Accept-Language":"en-US,en;q=0.9"}
OUT=pathlib.Path("blueprints_src/tmp/pages"); OUT.mkdir(parents=True,exist_ok=True)
def get(url):
    for i in range(5):
        try:
            return urllib.request.urlopen(urllib.request.Request(url,headers=H),timeout=60).read().decode("utf-8","replace")
        except Exception as e:
            print("retry",url,e,flush=True); time.sleep(5)
    return None
start=int(sys.argv[1]); end=int(sys.argv[2])
for page in range(start,end+1):
    f=OUT/f"page_{page:03d}.html"
    if f.exists() and f.stat().st_size>5000: continue
    url="https://mc-schematics.com/browse" if page==1 else f"https://mc-schematics.com/browse/page/{page}"
    text=get(url)
    if text:
        f.write_text(text,encoding="utf-8")
        print(page,"ok",len(text),flush=True)
    time.sleep(1.2)
print("done",flush=True)
