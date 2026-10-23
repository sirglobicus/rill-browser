#!/usr/bin/env bash
# Bundles the default filter lists. The app can also re-download them itself (Settings > Update filter lists).
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=app/src/main/assets/filters
mkdir -p "$OUT"
curl -sfL -o "$OUT/easylist.txt"    https://easylist.to/easylist/easylist.txt
curl -sfL -o "$OUT/easyprivacy.txt" https://easylist.to/easylist/easyprivacy.txt
curl -sfL -o "$OUT/peterlowe.txt"   "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext"
wc -c "$OUT"/*.txt
