#!/usr/bin/env python3
"""Checks that every word of the bundled Hafs text has its own recording on
audio.qurancdn.com, named by its place (surah_ayah_word): for each ayah the
recording of its last word is there, and no recording follows it. The API's
own audio_url is not used: for an ayah with pause signs it names files that
do not exist (2:2, word 6 is given as 008, while 006 to 007 are the files).
Checked by ear too (whisper.cpp with the Quran model on a sample, 2026-10-03):
2:2 words 4 to 7, 2:255:50, 12:8:14, 36:52 words 6 to 8 say what is printed.

Where an ayah's last recordings are missing (12:8:15, 79:1:2 in 2026), the
words without one are written to assets/quran/word_audio_missing.txt and the
app offers no sound for them. A recording past the last word fails the check.

    python3 tools/check_word_audio.py
"""
import glob, os, sys, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = "https://audio.qurancdn.com/wbw/"


def status(stem):
    req = urllib.request.Request(BASE + stem + ".mp3", method="HEAD", headers={"User-Agent": "mushaf-data-build"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status
        except urllib.error.HTTPError as e:
            if e.code in (403, 404):
                return e.code
        except Exception:
            pass
    return 0


words = {}
for f in glob.glob(os.path.join(ROOT, "app/src/main/assets/quran/pages/*.txt")):
    for row in open(f, encoding="utf8"):
        x = row.split("\t")
        if len(x) > 2 and x[2] == "w":
            s, a, w = map(int, x[1].split(":"))
            words[(s, a)] = max(words.get((s, a), 0), w)

checks = []
for (s, a), last in words.items():
    checks.append(("%03d_%03d_%03d" % (s, a, last), 200))
    checks.append(("%03d_%03d_%03d" % (s, a, last + 1), 404))
with ThreadPoolExecutor(16) as pool:
    got = list(pool.map(lambda c: status(c[0]), checks))
if 0 in got:
    sys.exit("the server did not answer every check")
extra = [c[0] for c, g in zip(checks, got) if c[1] == 404 and g == 200]
if extra:
    sys.exit(f"recordings past an ayah's last word: {extra[:20]}")
missing = []
for c, g in zip(checks, got):
    if c[1] == 200 and g != 200:
        s, a, last = (int(x) for x in c[0].split("_"))
        n = last - 1
        while n > 0 and status("%03d_%03d_%03d" % (s, a, n)) != 200:
            n -= 1
        missing += [f"{s}:{a}:{w}" for w in range(n + 1, last + 1)]
open(os.path.join(ROOT, "app/src/main/assets/quran/word_audio_missing.txt"), "w").write("".join(m + "\n" for m in missing))
print(f"{len(words)} ayat checked: {len(missing)} words without a recording: {' '.join(missing)}")
