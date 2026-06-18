#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHERPA_LIB_DIR="${1:-}"
TARGET_DIR="$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"
ONNXRUNTIME_LIB_DIR="${ONNXRUNTIME_LIB_DIR:-$SHERPA_LIB_DIR}"

if [[ -z "$SHERPA_LIB_DIR" || ! -d "$SHERPA_LIB_DIR" ]]; then
  echo "Usage: tools/stage_qnn_android_runtime.sh /path/to/sherpa-onnx/build-android-arm64-v8a/lib" >&2
  echo "Set ONNXRUNTIME_LIB_DIR when libonnxruntime.so comes from a separate QNN-enabled ONNX Runtime AAR." >&2
  exit 2
fi

if [[ ! -d "$ONNXRUNTIME_LIB_DIR" ]]; then
  echo "ONNXRUNTIME_LIB_DIR is missing: $ONNXRUNTIME_LIB_DIR" >&2
  exit 2
fi

if [[ -z "${QNN_SDK_ROOT:-}" ]]; then
  echo "QNN_SDK_ROOT is not set. Use tools/stage_public_qnn_runtime.sh for the public Maven runtime, or install Qualcomm AI Runtime/QNN SDK and export QNN_SDK_ROOT." >&2
  exit 2
fi

QNN_LIB_DIR="$QNN_SDK_ROOT/lib/aarch64-android"
if [[ ! -d "$QNN_LIB_DIR" ]]; then
  echo "QNN Android library directory is missing: $QNN_LIB_DIR" >&2
  exit 2
fi

mkdir -p "$TARGET_DIR"

install -m 0644 "$SHERPA_LIB_DIR/libsherpa-onnx-jni.so" "$TARGET_DIR/libsherpa-onnx-jni.so"
install -m 0644 "$ONNXRUNTIME_LIB_DIR/libonnxruntime.so" "$TARGET_DIR/libonnxruntime.so"

for lib in libQnnGpu.so libQnnHtp.so libQnnHtpPrepare.so libQnnSystem.so; do
  install -m 0644 "$QNN_LIB_DIR/$lib" "$TARGET_DIR/$lib"
done

stub_count=0
skel_count=0
shopt -s nullglob
for lib in "$QNN_LIB_DIR"/libQnnHtpV*Stub.so; do
  install -m 0644 "$lib" "$TARGET_DIR/$(basename "$lib")"
  stub_count=$((stub_count + 1))
done
for lib in "$QNN_LIB_DIR"/libQnnHtpV*Skel.so "$QNN_SDK_ROOT"/lib/hexagon-v*/unsigned/libQnnHtpV*Skel.so; do
  install -m 0644 "$lib" "$TARGET_DIR/$(basename "$lib")"
  skel_count=$((skel_count + 1))
done
shopt -u nullglob

if [[ "$stub_count" -eq 0 || "$skel_count" -eq 0 ]]; then
  echo "No QNN HTP Stub/Skel libraries were staged from $QNN_LIB_DIR." >&2
  exit 3
fi

echo "Staged QNN Sherpa runtime into $TARGET_DIR"
