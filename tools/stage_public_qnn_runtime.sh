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

if ! unzip -l "$QNN_AAR" 'jni/arm64-v8a/*.so' >/dev/null; then
  echo "QNN runtime AAR does not contain arm64-v8a native libraries: $QNN_AAR" >&2
  exit 3
fi

stage_lib() {
  local lib="$1"
  if ! unzip -p "$QNN_AAR" "jni/arm64-v8a/$lib" > "$TARGET_DIR/$lib"; then
    echo "Missing QNN runtime library in $QNN_AAR: $lib" >&2
    rm -f "$TARGET_DIR/$lib"
    exit 3
  fi
  chmod 0644 "$TARGET_DIR/$lib"
}

for lib in \
  libQnnGpu.so \
  libQnnGpuNetRunExtensions.so \
  libQnnHtp.so \
  libQnnHtpNetRunExtensions.so \
  libQnnHtpPrepare.so \
  libQnnSystem.so; do
  stage_lib "$lib"
done

stub_count=0
skel_count=0
while IFS= read -r entry; do
  lib="$(basename "$entry")"
  stage_lib "$lib"
  stub_count=$((stub_count + 1))
done < <(unzip -Z1 "$QNN_AAR" 'jni/arm64-v8a/libQnnHtpV*Stub.so' 'jni/arm64-v8a/libQnnHtpV*CalculatorStub.so')
while IFS= read -r entry; do
  lib="$(basename "$entry")"
  stage_lib "$lib"
  skel_count=$((skel_count + 1))
done < <(unzip -Z1 "$QNN_AAR" 'jni/arm64-v8a/libQnnHtpV*Skel.so')

if [[ "$stub_count" -eq 0 || "$skel_count" -eq 0 ]]; then
  echo "No QNN HTP Stub/Skel libraries were staged from $QNN_AAR." >&2
  exit 4
fi

echo "Staged public Qualcomm QNN runtime $QNN_VERSION into $TARGET_DIR"
