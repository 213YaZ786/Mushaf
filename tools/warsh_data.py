#!/usr/bin/env python3
"""Builds the Warsh mushaf bundled in the app, and checks it before writing it.

    python3 tools/warsh_data.py [cache folder]

Source: the King Fahd Complex's Warsh Uthmanic package (UthmanicWarsh-v-3.0,
2026) as laid out word by word, with each word's page and line, in
quran-ws/quran-text (CC BY 4.0; the text and the font keep the Complex's
own terms). Pinned to one commit; the font's SHA-256 is checked.

Checked, and nothing is written unless both pass, against the Complex's
earlier Warsh release (warshData_v10, 2021, as kept in ibnhazm/KFGQPC):
  - the text: every ayah, letter by letter, once both are reduced to their
    letters (the two releases encode a few signs differently: the alef
    variants of Arabic Extended-B, the seat of a hamza, a tatweel; never
    a word);
  - the layout: every ayah's page, first line and last line, once the
    surah titles and basmalas the 2026 file does not count are put back.

Writes app/src/main/assets/warsh/ in the same shapes as assets/quran/:
pages/NNN.txt, ayat.txt, meta.json, and hafs.txt, which gives for each
Warsh ayah the Hafs ayat its words are in (for the meanings, the tafsir
and the word by word, all numbered as Hafs), and res/font/uthmanic_warsh.ttf.
"""
import csv, gzip, hashlib, io, json, os, shutil, sys, unicodedata, urllib.request
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "warsh")
FONT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "font", "uthmanic_warsh.ttf")
HAFS = os.path.join(ROOT, "app", "src", "main", "assets", "quran")
CACHE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "warsh-cache")

TEXT = "https://raw.githubusercontent.com/quran-ws/quran-text/{commit}/data/{path}"
TEXT_COMMIT = "87d7691a0179dbb3cadbc2276581f0fdbbe476b1"  # 2026-09-13
V10 = "https://raw.githubusercontent.com/ibnhazm/KFGQPC/25f2f8886d6ad0b47190f252d9d7fb1e296f3231/warsh/data/warshData_v10.json"
FONT_SHA = "b644f1c6665baa95c9c0ab40b01a7f3e89edc42a0f50d3b244318a843bec81a9"


def get(url, name):
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        os.makedirs(CACHE, exist_ok=True)
        req = urllib.request.Request(url, headers={"User-Agent": "mushaf-data-build"})
        with urllib.request.urlopen(req, timeout=120) as r:
            open(path, "wb").write(r.read())
    return path


def check_order(page, rows):
    """Every row in reading order: ayah after ayah, word after word, each number right after its ayah."""
    last = None
    for row in rows:
        f = row.split("\t")
        if f[2] in ("t", "b"):
            continue
        place = tuple(int(x) for x in f[1].split(":"))
        if last is not None and place <= last:
            sys.exit(f"page {page}: {f[1]} comes after {':'.join(map(str, last))}")
        last = place


def letters(text):
    """The letters only, the forms both releases share."""
    out = []
    for c in text:
        o = ord(c)
        if o == 0x06E8:
            # The small nun the 2021 release writes as a sign, 2026 as a letter (12:110 فَنُنجِي).
            out.append("ن")
            continue
        if unicodedata.category(c) not in ("Lo", "Lm"):
            continue
        if 0x0870 <= o <= 0x0886 or c in "ٱآأإٲٳ":
            c = "ا"
        elif c in "ىیئےۦ\u08C9":
            c = "ي"
        elif c == "ؤ":
            c = "و"
        elif c == "ڢ":
            c = "ف"
        elif c in "ڧٯ":
            c = "ق"
        elif c in "ءـۥۑ" or o == 0x0887:
            # A hamza, its seat (ۑ in النبيۑن), a tatweel, a small waw of silah, a round dot: signs, not letters.
            continue
        out.append(c)
    return "".join(out)


def plain(text):
    """For search and recitation: each word's letters as typed on a keyboard, words kept apart."""
    return " ".join(w for w in (letters(t) for t in text.split()) if w)


def main():
    rows = list(csv.DictReader(io.TextIOWrapper(gzip.open(get(TEXT.format(commit=TEXT_COMMIT, path="mushaf/warsh.csv.gz"), "warsh.csv.gz")), encoding="utf-8-sig")))
    hafs_rows = list(csv.DictReader(io.TextIOWrapper(gzip.open(get(TEXT.format(commit=TEXT_COMMIT, path="mushaf/hafs.csv.gz"), "hafs.csv.gz")), encoding="utf-8-sig")))
    font = get(TEXT.format(commit=TEXT_COMMIT, path="fonts/UthmanicWarsh-v-3.0.ttf"), "UthmanicWarsh-v-3.0.ttf")
    if hashlib.sha256(open(font, "rb").read()).hexdigest() != FONT_SHA:
        sys.exit("the Warsh font is not the Complex's file")
    v10 = {(x["sura_no"], x["aya_no"]): x for x in json.load(open(get(V10, "warshData_v10.json"), encoding="utf-8"))}
    ayah_map = json.load(open(get(TEXT.format(commit=TEXT_COMMIT, path="ayah-map.json"), "ayah-map.json"), encoding="utf-8"))["ayahs"]

    # Each page's lines as the 2026 file numbers them, text lines only.
    pages = defaultdict(lambda: defaultdict(list))
    for r in rows:
        pages[int(r["page"])][int(r["line"])].append(r)

    # Put back the titles and basmalas: before the first word of each surah,
    # a title, and a basmala but for al-Fatihah (its basmala is a line of
    # its own in the file) and at-Tawbah (it has none).
    layout = {}
    for p in sorted(pages):
        printed = []
        for n in sorted(pages[p]):
            words = pages[p][n]
            first = words[0]
            if first["ayah"] in ("0", "1") and first["position_in_ayah"] == "1":
                s = int(first["surah"])
                if s == 1 and first["ayah"] == "0" or s != 1 and first["ayah"] == "1":
                    printed.append(("title", s))
                    if s not in (1, 9):
                        printed.append(("basmala", s))
            printed.append(("words", words))
        layout[p] = printed

    # The layout checked against v10: each ayah's page, first and last line.
    first_line, last_line = {}, {}
    for p, printed in layout.items():
        for i, (kind, words) in enumerate(printed, start=1):
            if kind != "words":
                continue
            for r in words:
                k = (int(r["surah"]), int(r["ayah"]))
                if k[1] == 0:
                    continue
                first_line.setdefault(k, (p, i))
                last_line[k] = (p, i)
    problems = []
    for k, x in v10.items():
        pg = str(x["page"]).replace("–", "-").split("-")
        if first_line.get(k) != (int(pg[0]), x["line_start"]) or last_line.get(k) != (int(pg[-1]), x["line_end"]):
            problems.append(f"{k}: layout {first_line.get(k)}-{last_line.get(k)} against v10 page {x['page']} lines {x['line_start']}-{x['line_end']}")

    # The text checked against v10, letter by letter.
    by_ayah = defaultdict(list)
    for r in rows:
        if r["ayah"] != "0":
            by_ayah[(int(r["surah"]), int(r["ayah"]))].append(r["text"])
    if len(by_ayah) != 6214:
        problems.append(f"{len(by_ayah)} ayat, the Madani count is 6214")
    for k, x in v10.items():
        if letters(" ".join(by_ayah.get(k, []))) != letters(x["aya_text"]):
            problems.append(f"{k}: letters differ from v10")
    if problems:
        print("\n".join(problems[:40]))
        sys.exit(f"{len(problems)} problem(s): the Warsh text must not ship like this")

    # Word meanings: the shared word number leads to the Hafs word, whose
    # meaning and transliteration the Hafs pages carry.
    hafs_place = {r["number"]: (r["surah"], r["ayah"], r["position_in_ayah"]) for r in hafs_rows}
    meaning = {}
    for f in os.listdir(os.path.join(HAFS, "pages")):
        for line in open(os.path.join(HAFS, "pages", f), encoding="utf-8"):
            p = line.rstrip("\n").split("\t")
            if len(p) >= 8 and p[2] == "w":
                s, a, w = p[1].split(":")
                meaning[(s, a, w)] = (p[6], p[7])

    os.makedirs(os.path.join(OUT, "pages"), exist_ok=True)
    ayat, juz_start, sajdah, page_start = [], {}, [], []
    seen = set()
    for p in sorted(layout):
        out = []
        for i, (kind, item) in enumerate(layout[p], start=1):
            if kind == "title":
                out.append(f"{i}\t{item}:0:0\tt\t\t\t\t\t")
            elif kind == "basmala":
                out.append(f"{i}\t{item}:0:0\tb\t\t\t\t\t")
            else:
                for j, r in enumerate(item):
                    s, a, w = r["surah"], r["ayah"], r["position_in_ayah"]
                    text = r["text"]
                    for m in r["marks"].split("|") if r["marks"] else []:
                        kind_, side, sign = m.split(":", 2)
                        text = f"{sign} {text}" if side == "before" else text + sign
                    mean, tr = meaning.get(hafs_place.get(r["number"], ("", "", "")), ("", ""))
                    out.append("\t".join([str(i), f"{s}:{a}:{w}", "w" if a != "0" else "z", text, "", plain(r["text"]), mean, tr]))
                    key = (int(s), int(a))
                    # The ayah's number closes it, in the mushaf's own sign, right after its last word.
                    last_of_ayah = j + 1 == len(item) or item[j + 1]["ayah"] != a or item[j + 1]["surah"] != s
                    if a != "0" and last_of_ayah and last_line.get(key) == (p, i):
                        digits = "".join("٠١٢٣٤٥٦٧٨٩"[int(c)] for c in a)
                        out.append("\t".join([str(i), f"{s}:{a}:{int(w) + 1}", "e", "۝" + digits, "", "", "", ""]))
                    if a == "0" or key in seen:
                        continue
                    seen.add(key)
                    juz_start.setdefault(int(r["juz"]), (f"{s}:{a}", p))
                    if not page_start or page_start[-1][0] != p:
                        page_start.append((p, f"{s}:{a}"))
        check_order(p, out)
        open(os.path.join(OUT, "pages", f"{p:03d}.txt"), "w", encoding="utf-8").write("\n".join(out) + "\n")

    for k in sorted(by_ayah):
        r0 = next(r for r in rows if (int(r["surah"]), int(r["ayah"])) == k)
        ayat.append(f"{k[0]}:{k[1]}\t{first_line[k][0]}\t{r0['juz']}\t0\t{plain(' '.join(by_ayah[k]))}")
        if any("sajdah" in r["marks"] for r in rows if (int(r["surah"]), int(r["ayah"])) == k):
            sajdah.append(f"{k[0]}:{k[1]}")

    # Warsh ayah -> the Hafs ayat its words are in.
    to_hafs = defaultdict(list)
    for e in ayah_map:
        wr = e.get("warsh")
        if not wr or wr["ayah"] == 0:
            continue
        last = wr.get("ayah_last", wr["ayah"])
        for a in range(wr["ayah"], last + 1):
            to_hafs[(wr["surah"], a)].append(f"{e['surah']}:{e['ayah']}")
    missing = [k for k in by_ayah if k not in to_hafs]
    if missing:
        sys.exit(f"{len(missing)} Warsh ayat without a Hafs ayah, e.g. {missing[:5]}")
    open(os.path.join(OUT, "hafs.txt"), "w", encoding="utf-8").write(
        "\n".join(f"{k[0]}:{k[1]}\t{','.join(to_hafs[k])}" for k in sorted(by_ayah)) + "\n")

    hafs_meta = json.load(open(os.path.join(HAFS, "meta.json"), encoding="utf-8"))
    counts = defaultdict(int)
    for k in by_ayah:
        counts[k[0]] += 1
    surahs = []
    for s in hafs_meta["surahs"]:
        n = s["n"]
        ps = [first_line[k][0] for k in by_ayah if k[0] == n] + [last_line[k][0] for k in by_ayah if k[0] == n]
        surahs.append(dict(s, ayat=counts[n], pages=[min(ps), max(ps)], basmala=n not in (1, 9)))
    meta = {
        "surahs": surahs,
        "juz": [{"n": n, "key": k, "page": p} for n, (k, p) in sorted(juz_start.items())],
        "quarters": [],
        "sajdah": sajdah,
        "pageStart": [k for _, k in page_start],
    }
    json.dump(meta, open(os.path.join(OUT, "meta.json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    open(os.path.join(OUT, "ayat.txt"), "w", encoding="utf-8").write("\n".join(ayat) + "\n")
    shutil.copy(font, FONT_OUT)
    print(f"Warsh: {len(by_ayah)} ayat on {len(layout)} pages, text and layout match v10, {len(sajdah)} sajdah, {len(juz_start)} juz")


if __name__ == "__main__":
    main()
