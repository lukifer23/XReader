#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE_DIR="$ROOT_DIR/.native-cache"
TARGET_DIR="$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"
QNN_VERSION="${QNN_RUNTIME_VERSION:-2.47.0}"
QNN_AAR="$CACHE_DIR/qnn-runtime-$QNN_VERSION.aar"
QNN_URL="https://repo1.maven.org/maven2/com/qualcomm/qti/qnn-runtime/$QNN_VERSION/qnn-runtime-$QNN_VERSION.aar"

mkdir -p "$CACHE_DIR" "$TARGET_DIR"

if [[ ! -f "$QNN_AAR" ]]; then
  curl -L --fail -o "$QNN_AAR" "$QNN_URL"
fi

TMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

unzip -q "$QNN_AAR" -d "$TMP_DIR"

QNN_LIB_DIR="$TMP_DIR/jni/arm64-v8a"
if [[ ! -d "$QNN_LIB_DIR" ]]; then
  echo "QNN runtime AAR does not contain arm64-v8a native libraries: $QNN_AAR" >&2
  exit 3
fi

for lib in \
  libQnnGpu.so \
  libQnnHtp.so \
  libQnnHtpPrepare.so \
  libQnnSystem.so; do
  install -m 0644 "$QNN_LIB_DIR/$lib" "$TARGET_DIR/$lib"
done

stub_count=0
skel_count=0
shopt -s nullglob
for lib in "$QNN_LIB_DIR"/libQnnHtpV*Stub.so; do
  install -m 0644 "$lib" "$TARGET_DIR/$(basename "$lib")"
  stub_count=$((stub_count + 1))
done
for lib in "$QNN_LIB_DIR"/libQnnHtpV*Skel.so; do
  install -m 0644 "$lib" "$TARGET_DIR/$(basename "$lib")"
  skel_count=$((skel_count + 1))
done
shopt -u nullglob

if [[ "$stub_count" -eq 0 || "$skel_count" -eq 0 ]]; then
  echo "No QNN HTP Stub/Skel libraries were staged from $QNN_AAR." >&2
  exit 4
fi

echo "Staged public Qualcomm QNN runtime $QNN_VERSION into $TARGET_DIR"
