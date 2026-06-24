#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${XREADER_NATIVE_WORK_DIR:-"$ROOT_DIR/.native-build"}"
SHERPA_DIR="${SHERPA_ONNX_DIR:-"$WORK_DIR/sherpa-onnx"}"
SHERPA_PATCH="$ROOT_DIR/tools/patches/sherpa-onnx-qnn-session-provider.patch"
SHERPA_STRICT_NNAPI_PATCH="$ROOT_DIR/tools/patches/sherpa-onnx-strict-nnapi-provider.patch"
SHERPA_STRICT_HARDWARE_FAILURE_PATCH="$ROOT_DIR/tools/patches/sherpa-onnx-strict-hardware-provider-failures.patch"

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

if [[ -z "${SHERPA_ONNX_ONNXRUNTIME_ROOT:-}" &&
      -z "${SHERPA_ONNXRUNTIME_LIB_DIR:-}" &&
      -z "${SHERPA_ONNXRUNTIME_INCLUDE_DIR:-}" ]]; then
  if [[ "${XREADER_BUILD_ONNXRUNTIME_QNN:-0}" == "1" ]]; then
    ORT_BUILD_LOG="$WORK_DIR/onnxruntime-qnn-build.log"
    echo "Building QNN-enabled ONNX Runtime. Log: $ORT_BUILD_LOG"
      QNN_SDK_ROOT="$QNN_SDK_ROOT" \
      ANDROID_NDK="$ANDROID_NDK_PATH" \
      XREADER_NATIVE_WORK_DIR="$WORK_DIR" \
      "$ROOT_DIR/tools/build_onnxruntime_qnn_android.sh" >"$ORT_BUILD_LOG" 2>&1
    SHERPA_ONNX_ONNXRUNTIME_ROOT="$(tail -n 1 "$ORT_BUILD_LOG")"
    if [[ ! -d "$SHERPA_ONNX_ONNXRUNTIME_ROOT" ]]; then
      echo "ONNX Runtime QNN build did not produce a package. See $ORT_BUILD_LOG" >&2
      exit 5
    fi
    export SHERPA_ONNX_ONNXRUNTIME_ROOT
  else
    cat >&2 <<EOF
QNN build requires a QNN-enabled ONNX Runtime.
Set SHERPA_ONNX_ONNXRUNTIME_ROOT to a package containing headers/ and
jni/arm64-v8a/libonnxruntime.so, or rerun with XREADER_BUILD_ONNXRUNTIME_QNN=1
to build ONNX Runtime from source with --use_qnn static_lib.
EOF
    exit 5
  fi
fi

if [[ -n "${SHERPA_ONNX_ONNXRUNTIME_ROOT:-}" ]]; then
  ORT_LIB="$SHERPA_ONNX_ONNXRUNTIME_ROOT/jni/arm64-v8a/libonnxruntime.so"
elif [[ -n "${SHERPA_ONNXRUNTIME_LIB_DIR:-}" ]]; then
  ORT_LIB="$SHERPA_ONNXRUNTIME_LIB_DIR/libonnxruntime.so"
else
  ORT_LIB=""
fi
if [[ -n "$ORT_LIB" ]]; then
  if [[ ! -f "$ORT_LIB" ]]; then
    echo "QNN-enabled ONNX Runtime lib is missing: $ORT_LIB" >&2
    exit 5
  fi
  ORT_STRINGS_FILE="$(mktemp "${TMPDIR:-/tmp}/xreader-ort-strings.XXXXXX")"
  strings "$ORT_LIB" >"$ORT_STRINGS_FILE"
  if ! grep -q "QNNExecutionProvider" "$ORT_STRINGS_FILE"; then
    rm -f "$ORT_STRINGS_FILE"
    echo "ONNX Runtime lib does not contain QNNExecutionProvider: $ORT_LIB" >&2
    exit 5
  fi
  rm -f "$ORT_STRINGS_FILE"
fi

mkdir -p "$WORK_DIR"
if [[ ! -d "$SHERPA_DIR/.git" ]]; then
  git clone https://github.com/k2-fsa/sherpa-onnx "$SHERPA_DIR"
fi

cd "$SHERPA_DIR"
if [[ "${SHERPA_SKIP_FETCH:-0}" != "1" ]]; then
  git fetch --tags --quiet
fi

apply_patch_if_needed() {
  local patch_file="$1"
  local marker="$2"
  local label="$3"
  if [[ ! -f "$patch_file" ]]; then
    return
  fi
  if grep -q "$marker" sherpa-onnx/csrc/session.cc sherpa-onnx/csrc/provider.h 2>/dev/null; then
    echo "$label patch is already present."
    return
  fi
  if git apply --check "$patch_file" >/dev/null 2>&1; then
    git apply "$patch_file"
    return
  fi
  if git apply --reverse --check "$patch_file" >/dev/null 2>&1; then
    echo "$label patch is already present."
    return
  fi
  echo "$label patch does not apply cleanly. Inspect $patch_file against $SHERPA_DIR." >&2
  exit 3
}

apply_patch_if_needed "$SHERPA_PATCH" "Provider::kQNN" "Sherpa QNN provider"
apply_patch_if_needed "$SHERPA_STRICT_NNAPI_PATCH" "uint32_t nnapi_flags = NNAPI_FLAG_CPU_DISABLED" "Sherpa strict NNAPI provider"
apply_patch_if_needed "$SHERPA_STRICT_HARDWARE_FAILURE_PATCH" "Failed to enable NNAPI: \") +" "Sherpa strict hardware provider failures"

require_source_marker() {
  local marker="$1"
  local path="$2"
  local label="$3"
  if ! grep -q "$marker" "$path"; then
    echo "$label is missing from $SHERPA_DIR/$path. Re-apply the matching patch or refresh the native tree." >&2
    exit 3
  fi
}

require_source_marker "session_config_entries" "sherpa-onnx/csrc/session.cc" "Sherpa QNN session-config split"
require_source_marker "session.disable_cpu_ep_fallback" "sherpa-onnx/csrc/session.cc" "Sherpa strict CPU fallback disable"

export QNN_SDK_ROOT
export ANDROID_NDK="$ANDROID_NDK_PATH"
export SHERPA_ONNX_ENABLE_QNN=ON
export SHERPA_ONNX_ENABLE_BINARY="${SHERPA_ONNX_ENABLE_BINARY:-OFF}"
export SHERPA_ONNX_ANDROID_PLATFORM="${SHERPA_ONNX_ANDROID_PLATFORM:-android-27}"

./build-android-arm64-v8a.sh

OUT_DIR="$SHERPA_DIR/build-android-arm64-v8a/lib"
for lib in libsherpa-onnx-jni.so; do
  if [[ ! -f "$OUT_DIR/$lib" ]]; then
    echo "Expected Sherpa QNN build output is missing: $OUT_DIR/$lib" >&2
    exit 4
  fi
done

echo "$OUT_DIR"
