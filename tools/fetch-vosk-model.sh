#!/usr/bin/env bash
# Puts the Vosk English model where the app build expects it: app/src/main/assets/model-en-us
#
#     tools/fetch-vosk-model.sh                 # downloads it (about 41 MB)
#     tools/fetch-vosk-model.sh path/to/vosk-model-small-en-us-0.15.zip   # or uses a zip you already have
#
# The model is Apache 2.0, from https://alphacephei.com/vosk/models . It is not kept in git (it is 71 MB unpacked);
# run this once after cloning, before building. The checksum below is of the exact file this keyboard was built with.
set -euo pipefail

URL="https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
SHA256="30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/model-en-us"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

zip="${1:-}"
if [ -z "$zip" ]; then
    zip="$work/model.zip"
    echo "Downloading $URL"
    curl -fSL -o "$zip" "$URL"
fi

echo "$SHA256  $zip" | sha256sum -c - >/dev/null || { echo "Checksum does not match; not using this file." >&2; exit 1; }

unzip -q "$zip" -d "$work/unpacked"
rm -rf "$DEST"
mkdir -p "$(dirname "$DEST")"
mv "$work/unpacked/vosk-model-small-en-us-0.15" "$DEST"
echo "Model ready in $DEST"
