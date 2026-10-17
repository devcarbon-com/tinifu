#!/bin/bash
# One-shot setup: fetch the needle engine + whistle model into
# ~/.cache/cactus-needle and build the tray shim. Idempotent — safe to rerun.
# --no-shim skips the tray-shim build (CI: the shim is committed).
set -euo pipefail

NO_SHIM=0
[ "${1:-}" = "--no-shim" ] && NO_SHIM=1

CACHE="$HOME/.cache/cactus-needle"
LIB_DIR="$CACHE/v3/3.3.1"
MODEL_DIR="$CACHE/whistle/2.0.0"
REPO="Cactus-Compute/needle3"
WHEEL="python/cactus_needle-3.3.1-py3-none-manylinux2014_x86_64.whl"
ARCH=$(uname -m)
case "$ARCH" in
  x86_64) WHEEL="python/cactus_needle-3.3.1-py3-none-manylinux2014_x86_64.whl" ;;
  aarch64) WHEEL="python/cactus_needle-3.3.1-py3-none-manylinux2014_aarch64.whl" ;;
  *) echo "unsupported arch: $ARCH" >&2; exit 1 ;;
esac
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

mkdir -p "$LIB_DIR" "$MODEL_DIR" "$REPO_ROOT/vendor"

# --- libneedle.so (extracted from the manylinux wheel) ---
if [ -f "$LIB_DIR/libneedle.so" ] && [ -f "$REPO_ROOT/vendor/libneedle.so" ]; then
  echo "libneedle.so already present"
else
  echo "Downloading needle engine ..."
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT
  curl -fL -o "$TMP/needle.whl" "https://huggingface.co/$REPO/resolve/main/$WHEEL"
  unzip -o -j "$TMP/needle.whl" "needle/libneedle3.so" -o "$LIB_DIR" >/dev/null 2>&1 || \
    unzip -o -j "$TMP/needle.whl" "needle/libneedle3.so" -d "$LIB_DIR" >/dev/null
  mv "$LIB_DIR/libneedle3.so" "$LIB_DIR/libneedle.so"
  cp "$LIB_DIR/libneedle.so" "$REPO_ROOT/vendor/libneedle.so"
  echo "  -> $LIB_DIR/libneedle.so + vendor/"
fi

# --- whistle.cact model ---
if [ -f "$MODEL_DIR/whistle.cact" ] && [ -f "$REPO_ROOT/vendor/whistle.cact" ]; then
  echo "whistle.cact already present"
else
  echo "Downloading whistle model (~16 MB) ..."
  curl -fL -o "$MODEL_DIR/whistle.cact" \
    "https://huggingface.co/Cactus-Compute/whistle/resolve/main/whistle.cact"
  cp "$MODEL_DIR/whistle.cact" "$REPO_ROOT/vendor/whistle.cact"
  echo "  -> $MODEL_DIR/whistle.cact + vendor/"
fi

# --- tray shim (needs libdbus headers) ---
if [ "$NO_SHIM" = 1 ]; then
  echo "skipping tray shim (--no-shim)"
elif [ -f "$REPO_ROOT/shim/libtinifu_sni.so" ]; then
  echo "tray shim already built"
elif pkg-config --exists dbus-1 2>/dev/null; then
  echo "Building tray shim ..."
  gcc -shared -fPIC -O2 $(pkg-config --cflags dbus-1) "$REPO_ROOT/shim/sni.c" \
    -o "$REPO_ROOT/shim/libtinifu_sni.so" $(pkg-config --libs dbus-1)
  echo "  -> shim/libtinifu_sni.so"
else
  echo "libdbus dev headers not found — tray icon disabled (install dbus, e.g. 'sudo pacman -S dbus', then rerun)"
fi

# --- sanity: jolt present? ---
if command -v jolt >/dev/null; then
  echo "jolt: $(jolt --version 2>/dev/null || echo ok)"
else
  echo "WARNING: jolt not on PATH — install it from https://github.com/jolt-lang"
fi

echo "Done. Next:"
echo "  jolt -A:time -m tinifu.transcribe clip.wav   # transcribe a WAV"
echo "  jolt -A:time -m tinifu.daemon                # start the voice daemon"
