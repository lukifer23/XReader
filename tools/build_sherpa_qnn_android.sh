#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${XREADER_NATIVE_WORK_DIR:-"$ROOT_DIR/.native-build"}"
SHERPA_DIR="${SHERPA_ONNX_DIR:-"$WORK_DIR/sherpa-onnx"}"
SHERPA_PATCH="$ROOT_DIR/tools/patches/sherpa-onnx-qnn-session-provider.patch"

if [[ -z "${QNN_SDK_ROOT:-}" ]]; then
  echo "QNN_SDK_ROOT is not set. Install Qualcomm AI Runtime/QNN SDK and export QNN_SDK_ROOT." >&2
  exit 2
fi

if [[ ! -d "$QNN_SDK_ROOT/lib/aarch64-android" ]]; then
  echo "QNN_SDK_ROOT does not contain lib/aarch64-android: $QNN_SDK_ROOT" >&2
  exit 2
fi

ANDROID_NDK_PATH="${ANDROID_NDK:-${ANDROID_NDK_HOME:-}}"
if [[ -z "$ANDROID_NDK_PATH" || ! -d "$ANDROID_NDK_PATH" ]]; then
  echo "ANDROID_NDK or ANDROID_NDK_HOME must point to an installed Android NDK." >&2
  exit 2
fi

mkdir -p "$WORK_DIR"
if [[ ! -d "$SHERPA_DIR/.git" ]]; then
  git clone https://github.com/k2-fsa/sherpa-onnx "$SHERPA_DIR"
fi

cd "$SHERPA_DIR"
if [[ "${SHERPA_SKIP_FETCH:-0}" != "1" ]]; then
  git fetch --tags --quiet
fi

if [[ -f "$SHERPA_PATCH" ]] && ! git apply --check "$SHERPA_PATCH" >/dev/null 2>&1; then
  if ! git apply --reverse --check "$SHERPA_PATCH" >/dev/null 2>&1; then
    echo "Sherpa QNN provider patch does not apply cleanly. Inspect $SHERPA_PATCH against $SHERPA_DIR." >&2
    exit 3
  fi
fi

if [[ -f "$SHERPA_PATCH" ]] && git apply --check "$SHERPA_PATCH" >/dev/null 2>&1; then
  git apply "$SHERPA_PATCH"
fi

export QNN_SDK_ROOT
export ANDROID_NDK="$ANDROID_NDK_PATH"
export SHERPA_ONNX_ENABLE_QNN=ON
export SHERPA_ONNX_ENABLE_BINARY="${SHERPA_ONNX_ENABLE_BINARY:-OFF}"

./build-android-arm64-v8a.sh

OUT_DIR="$SHERPA_DIR/build-android-arm64-v8a/lib"
for lib in libsherpa-onnx-jni.so; do
  if [[ ! -f "$OUT_DIR/$lib" ]]; then
    echo "Expected Sherpa QNN build output is missing: $OUT_DIR/$lib" >&2
    exit 4
  fi
done

echo "$OUT_DIR"
