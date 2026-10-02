#!/usr/bin/env python3
"""Builds the Quran data bundled in the app, from the public Quran.com API.

    python3 tools/quran_data.py [cache folder]

Fetches every page of the Madinah mushaf (604 pages, 15 lines) with its
words, the English translation (Saheeh International) and the surah
introductions, keeps the answers in the cache folder so a second run asks
nothing, and writes app/src/main/assets/quran/:

  meta.json          surahs, juz, hizb quarters, sajdah ayat, the first ayah
                     printed on each page
  pages/NNN.txt      one word per line: line, s:a:w, kind (w word, e ayah end),
                     QPC Hafs text, V2 glyph, simple text, meaning, transliteration
  ayat.txt           s:a, page, juz, hizb quarter, simple text (search, quizzes)
  en.txt             s:a, Saheeh International (footnote marks removed)
  info/N.txt         the surah's introduction, plain paragraphs
  mutashabihat.txt   similar ayat: an ayah (or a run of ayat, s:a+n) and
                     those that resemble it, from Quran Revision Companion
                     (github.com/Waqar144/quran_memorization_helper, MIT,
                     Copyright (c) 2023 Waqar Ahmed)
"""
import html, json, os, re, sys, time, urllib.request

API = "https://api.quran.com/api/v4"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "quran")
CACHE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "quran-cache")
FIELDS = "text_qpc_hafs,code_v2,line_number,page_number,location,text_imlaei_simple"


def get(url, path):
    path = os.path.join(CACHE, path)
    if os.path.exists(path):
        return json.load(open(path, encoding="utf8"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "mushaf-data-build"})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = r.read()
            break
        except Exception:
            time.sleep(2 + attempt * 3)
    else:
        sys.exit("failed: " + url)
    open(path, "wb").write(data)
    time.sleep(0.1)
    return json.loads(data)


def clean(text):
    # Translation footnote marks <sup foot_note=..>1</sup> and any tag left.
    text = re.sub(r"<sup[^>]*>.*?</sup>", "", text or "")
    text = re.sub(r"<[^>]+>", "", text)
    return html.unescape(text).replace("\t", " ").replace("\n", " ").strip()


def field(text):
    return (text or "").replace("\t", " ").replace("\n", " ").strip()


def paragraphs(markup):
    # Headings become their own line marked with #, paragraphs plain lines.
    out = []
    for tag, body in re.findall(r"<(h\d|p)[^>]*>(.*?)</\1>", markup or "", flags=re.S):
        body = clean(body)
        if body:
            out.append(("# " if tag.startswith("h") else "") + body)
    return "\n".join(out)


def main():
    os.makedirs(os.path.join(OUT, "pages"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "info"), exist_ok=True)
    chapters = get(f"{API}/chapters?language=en", "chapters.json")["chapters"]

    # Every word goes to the page it is printed on. The API lists an ayah
    # under one page only, sometimes not the one its words are on (5:77 is
    # listed under page 121, all its words are on page 120), so the words
    # are gathered from every answer and placed by their own page.
    verses, words = {}, {}
    for p in range(1, 605):
        data = get(f"{API}/verses/by_page/{p}?words=true&word_fields={FIELDS}&per_page=50&translations=20&fields=text_imlaei_simple", f"pages/{p}.json")
        for v in data["verses"]:
            verses.setdefault(v["verse_key"], v)
            for w in v["words"]:
                loc = w.get("location") or f"{v['verse_key']}:{w['position']}"
                words.setdefault(loc, w)

    def order(key):
        s_, a_ = key.split(":")
        return int(s_), int(a_)

    ayat, en = [], []
    juz_start, rub_start, sajdah, page_start = {}, {}, [], []
    by_page = {p: [] for p in range(1, 605)}
    for key in sorted(verses, key=order):
        v = verses[key]
        vw = sorted((w for loc, w in words.items() if loc.startswith(key + ":")), key=lambda w: w["position"])
        start = vw[0]["page_number"]
        juz_start.setdefault(v["juz_number"], (key, start))
        rub_start.setdefault(v["rub_el_hizb_number"], (key, start))
        if v.get("sajdah_number"):
            sajdah.append(key)
        ayat.append("\t".join([key, str(start), str(v["juz_number"]), str(v["rub_el_hizb_number"]), field(v["text_imlaei_simple"])]))
        tr = v.get("translations") or [{}]
        en.append(key + "\t" + clean(tr[0].get("text")))
        for w in vw:
            kind = "e" if w["char_type_name"] == "end" else "w"
            by_page[w["page_number"]].append((w["line_number"], order(key), w["position"], "\t".join([
                str(w["line_number"]), f"{key}:{w['position']}", kind,
                field(w.get("text_qpc_hafs")), field(w.get("code_v2")),
                field(w.get("text_imlaei_simple")) if kind == "w" else "",
                field((w.get("translation") or {}).get("text")) if kind == "w" else "",
                field((w.get("transliteration") or {}).get("text")) if kind == "w" else "",
            ])))
    if len(ayat) != 6236:
        sys.exit(f"{len(ayat)} ayat, expected 6236")
    for p in range(1, 605):
        rows = sorted(by_page[p], key=lambda r: (r[0], r[1], r[2]))
        if not rows:
            sys.exit(f"page {p} has no words")
        page_start.append((p, "%d:%d" % rows[0][1]))
        open(os.path.join(OUT, "pages", f"{p:03d}.txt"), "w", encoding="utf8").write("\n".join(r[3] for r in rows) + "\n")

    for c in chapters:
        info = get(f"{API}/chapters/{c['id']}/info?language=en", f"info/{c['id']}.json")["chapter_info"]
        text = paragraphs(info.get("text"))
        source = field(info.get("source"))
        open(os.path.join(OUT, "info", f"{c['id']}.txt"), "w", encoding="utf8").write(text + ("\n@ " + source if source else "") + "\n")

    meta = {
        "surahs": [{
            "n": c["id"],
            "name": c["name_simple"],
            "arabic": c["name_arabic"],
            "meaning": c["translated_name"]["name"],
            "ayat": c["verses_count"],
            "place": c["revelation_place"],
            "order": c["revelation_order"],
            "pages": c["pages"],
            "basmala": c["bismillah_pre"],
        } for c in chapters],
        "juz": [{"n": n, "key": k, "page": p} for n, (k, p) in sorted(juz_start.items())],
        "quarters": [{"n": n, "key": k, "page": p} for n, (k, p) in sorted(rub_start.items())],
        "sajdah": sajdah,
        "pageStart": [k for _, k in page_start],
    }
    # Similar ayat. The source counts ayat from 0 in the Quran's order.
    keys = [row.split("\t")[0] for row in ayat]
    def span(a):
        if isinstance(a, list):
            return keys[a[0]] + ("+%d" % (len(a) - 1) if len(a) > 1 else "")
        return keys[a]
    groups = get("https://raw.githubusercontent.com/Waqar144/quran_memorization_helper/master/assets/mutashabiha_data.json", "mutashabiha_data.json")
    lines = []
    for juz in sorted(groups, key=int):
        for m in groups[juz]:
            lines.append(span(m["src"]["ayah"]) + "\t" + ";".join(span(x["ayah"]) for x in m["muts"]))
    open(os.path.join(OUT, "mutashabihat.txt"), "w", encoding="utf8").write("\n".join(lines) + "\n")

    json.dump(meta, open(os.path.join(OUT, "meta.json"), "w", encoding="utf8"), ensure_ascii=False, separators=(",", ":"))
    open(os.path.join(OUT, "ayat.txt"), "w", encoding="utf8").write("\n".join(ayat) + "\n")
    open(os.path.join(OUT, "en.txt"), "w", encoding="utf8").write("\n".join(en) + "\n")
    print(f"{len(ayat)} ayat, {len(page_start)} pages, {len(sajdah)} sajdah, {len(juz_start)} juz, {len(rub_start)} quarters")


if __name__ == "__main__":
    main()
