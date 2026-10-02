#!/usr/bin/env python3
"""Builds the on-device Quran speech recogniser from Tarteel's model.

    python3 tools/convert_stt.py WHISPER_CPP_DIR OPENAI_WHISPER_DIR OUT_DIR

Takes tarteel-ai/whisper-base-ar-quran (Whisper base fine-tuned on Quran
recitation by Tarteel, Apache-2.0, word error rate 5.75 %), converts it
with whisper.cpp's converter to ggml, quantizes it to q8_0 with
whisper.cpp's tool, and writes its SHA-256 next to it. The app downloads
this one file once and checks its fingerprint (assets/stt.sha256).

WHISPER_CPP_DIR: whisper.cpp v1.9.4 sources, built (build/bin/whisper-quantize).
OPENAI_WHISPER_DIR: a checkout of github.com/openai/whisper (its mel filters).
Needs torch, transformers and huggingface_hub.
"""
import hashlib, json, os, shutil, subprocess, sys, tempfile
from huggingface_hub import snapshot_download

CPP, OPENAI, OUT = sys.argv[1:4]
MODEL = "tarteel-ai/whisper-base-ar-quran"
NAME = "ggml-quran-base-q8_0.bin"


def main():
    os.makedirs(OUT, exist_ok=True)
    src = snapshot_download(MODEL, allow_patterns=["*.json", "*.bin", "*.txt"])
    with tempfile.TemporaryDirectory() as work:
        # The converter takes the text context from max_length, a generation
        # setting (1024 here); the weights hold max_target_positions (448).
        for f in os.listdir(src):
            os.symlink(os.path.join(src, f), os.path.join(work, f))
        os.remove(os.path.join(work, "config.json"))
        config = json.load(open(os.path.join(src, "config.json")))
        config["max_length"] = config["max_target_positions"]
        json.dump(config, open(os.path.join(work, "config.json"), "w"))
        raw = os.path.join(work, "out")
        os.makedirs(raw)
        subprocess.check_call([sys.executable, os.path.join(CPP, "models", "convert-h5-to-ggml.py"), work, OPENAI, raw])
        env = dict(os.environ, LD_LIBRARY_PATH=os.path.join(CPP, "build", "bin"))
        subprocess.check_call([os.path.join(CPP, "build", "bin", "whisper-quantize"),
                               os.path.join(raw, "ggml-model.bin"), os.path.join(OUT, NAME), "q8_0"], env=env)
    digest = hashlib.sha256(open(os.path.join(OUT, NAME), "rb").read()).hexdigest()
    open(os.path.join(OUT, "SHA256SUMS"), "w").write(f"{digest}  {NAME}\n")
    print(NAME, os.path.getsize(os.path.join(OUT, NAME)) // 1024, "KB", digest)


if __name__ == "__main__":
    main()
