#!/usr/bin/env python3
"""Checks the bundled Quran against a second source before any release.

    python3 tools/verify_text.py [cache folder]

The words bundled in app/src/main/assets/quran come from Quran.com (the King
Fahd Complex's Hafs text). Each ayah is compared, letter by letter, with
the Uthmani text of the Tanzil Project (tanzil.net, verified by hand
against the Madinah mushaf), after both are reduced to their letters: no
vowels, no Quranic signs, one form of each letter. Tanzil prints the
basmala at the head of each surah's first ayah, which is taken off.

Exits with an error, and lists the ayat, on any difference: missing
ayat, extra ayat or a single letter that differs. Nothing is published
while this fails.
"""
import glob, os, re, sys, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "quran")
CACHE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "quran-cache")
TANZIL = "https://tanzil.net/pub/download/index.php?marks=true&sajdah=true&rub=false&tatweel=true&quranType=uthmani&outType=txt-2&agree=true"

MARKS = set(range(0x064B, 0x0660)) | {0x0670, 0x0640} | set(range(0x06D6, 0x06EE)) | set(range(0x0610, 0x061B)) | set(range(0x08D3, 0x0900))
FORMS = {"آ": "ا", "أ": "ا", "إ": "ا", "ٱ": "ا", "ى": "ي",
         "ی": "ي", "ة": "ه", "ؤ": "و", "ئ": "ي", "ء": ""}
BASMALA = "بسماللهالرحمنالرحيم"


def letters(text):
    return "".join(FORMS.get(c, c) for c in text if ord(c) not in MARKS and not c.isspace())


def tanzil():
    path = os.path.join(CACHE, "tanzil-uthmani.txt")
    if not os.path.exists(path):
        os.makedirs(CACHE, exist_ok=True)
        req = urllib.request.Request(TANZIL, headers={"User-Agent": "mushaf-data-check"})
        with urllib.request.urlopen(req, timeout=60) as r:
            open(path, "wb").write(r.read())
    out = {}
    for line in open(path, encoding="utf8"):
        parts = line.rstrip("\n").split("|")
        if len(parts) == 3 and parts[0].isdigit():
            out[f"{parts[0]}:{parts[1]}"] = parts[2]
    return out


def bundled():
    out = {}
    for f in sorted(glob.glob(os.path.join(ASSETS, "pages", "*.txt"))):
        for line in open(f, encoding="utf8"):
            p = line.rstrip("\n").split("\t")
            if len(p) < 4 or p[2] != "w":
                continue
            s, a, w = p[1].split(":")
            out.setdefault(f"{s}:{a}", {})[int(w)] = p[3]
    return {k: " ".join(v[i] for i in sorted(v)) for k, v in out.items()}


def main():
    ref, ours = tanzil(), bundled()
    problems = []
    if len(ref) != 6236:
        problems.append(f"reference has {len(ref)} ayat")
    for key in ref.keys() - ours.keys():
        problems.append(f"{key}: missing from the app")
    for key in ours.keys() - ref.keys():
        problems.append(f"{key}: not an ayah")
    for key in ref.keys() & ours.keys():
        theirs = letters(ref[key])
        surah, ayah = key.split(":")
        if ayah == "1" and surah not in ("1", "9") and theirs.startswith(BASMALA):
            theirs = theirs[len(BASMALA):]
        mine = letters(ours[key])
        if mine != theirs:
            problems.append(f"{key}: letters differ\n  app    {mine}\n  tanzil {theirs}")
    if problems:
        print("\n".join(sorted(problems)))
        sys.exit(f"{len(problems)} problem(s): the Quran text must not ship like this")
    print(f"6236 ayat checked against Tanzil: every letter matches")


if __name__ == "__main__":
    main()
