#!/usr/bin/env python3
"""Signs app/src/main/assets/sources.json, the list of servers the app
fetches from, which it reads again from the repository each week: the app
takes a newer list only when sources.json.sig, beside it, is its
signature by this key (ECDSA P-256, SHA-256). The private key never leaves
the computer it was made on; the app carries the public one (Sources.kt).

    python3 tools/sign_sources.py [key.pem]

Default key: ~/.config/mushaf/sources-signing-key.pem. Run it after every
change of sources.json, before the commit; a unit test fails otherwise.
"""
import base64, os, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LIST = os.path.join(ROOT, "app", "src", "main", "assets", "sources.json")
KEY = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser("~/.config/mushaf/sources-signing-key.pem")

der = subprocess.run(["openssl", "dgst", "-sha256", "-sign", KEY, LIST], check=True, capture_output=True).stdout
open(LIST + ".sig", "w").write(base64.b64encode(der).decode() + "\n")
print("signed", os.path.relpath(LIST, ROOT))
