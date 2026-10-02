#!/usr/bin/env python3
"""Fingerprints the page fonts the app fetches, so it can check them.

    python3 tools/font_hashes.py [cache folder]

Downloads the 604 print fonts and the 604 tajweed fonts of the Madinah
mushaf once and writes app/src/main/assets/quran/fonts.sha256: one line
per font, "print 1 <sha256>". The app refuses a downloaded font whose
SHA-256 differs, whatever server it came from.
"""
import concurrent.futures, hashlib, os, sys, time, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "quran", "fonts.sha256")
CACHE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "quran-cache")
KINDS = {
    "print": "https://static.qurancdn.com/fonts/quran/hafs/v2/ttf/p{}.ttf",
    "tajweed": "https://verses.quran.foundation/fonts/quran/hafs/v4/colrv1/ttf/p{}.ttf",
}


def fetch(kind, page):
    path = os.path.join(CACHE, "fonts", kind, f"p{page}.ttf")
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        for attempt in range(5):
            try:
                req = urllib.request.Request(KINDS[kind].format(page), headers={"User-Agent": "mushaf-data-build"})
                with urllib.request.urlopen(req, timeout=60) as r:
                    data = r.read()
                break
            except Exception:
                time.sleep(2 + attempt * 3)
        else:
            sys.exit(f"failed: {kind} {page}")
        open(path + ".part", "wb").write(data)
        os.replace(path + ".part", path)
    return kind, page, hashlib.sha256(open(path, "rb").read()).hexdigest()


def main():
    jobs = [(k, p) for k in KINDS for p in range(1, 605)]
    with concurrent.futures.ThreadPoolExecutor(4) as pool:
        rows = list(pool.map(lambda j: fetch(*j), jobs))
    open(OUT, "w").write("".join(f"{k} {p} {h}\n" for k, p, h in rows))
    print(f"{len(rows)} fonts fingerprinted")


if __name__ == "__main__":
    main()
