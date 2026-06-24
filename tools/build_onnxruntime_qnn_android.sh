#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${XREADER_NATIVE_WORK_DIR:-"$ROOT_DIR/.native-build"}"
ORT_VERSION="${XREADER_ONNXRUNTIME_VERSION:-v1.24.3}"
ORT_DIR="${XREADER_ONNXRUNTIME_DIR:-"$WORK_DIR/onnxruntime"}"
BUILD_DIR="${XREADER_ONNXRUNTIME_BUILD_DIR:-"$WORK_DIR/onnxruntime-qnn-android"}"
PACKAGE_DIR="${XREADER_ONNXRUNTIME_QNN_ROOT:-"$WORK_DIR/onnxruntime-qnn-android-package"}"
QAIRT_COMPAT_PATCH="$ROOT_DIR/tools/patches/onnxruntime-qnn-qairt-2_40-bfloat16-compat.patch"
QNN_NNAPI_STATIC_PATCH="$ROOT_DIR/tools/patches/onnxruntime-qnn-nnapi-static-nodeattrhelper.patch"
QNN_HTP_V79_PATCH="$ROOT_DIR/tools/patches/onnxruntime-qnn-htp-v79-arch.patch"
QNN_HTP_SIGNED_PD_PATCH="$ROOT_DIR/tools/patches/onnxruntime-qnn-htp-signed-pd-option.patch"

clean_conflict_copies() {
  local target_dir="$1"
  if [[ -d "$target_dir" ]]; then
    find "$target_dir" -name '* [0-9].*' -print -delete
  fi
}

if [[ -z "${QNN_SDK_ROOT:-}" ]]; then
  echo "QNN_SDK_ROOT is not set. Install Qualcomm AI Runtime/QNN SDK and export QNN_SDK_ROOT." >&2
  exit 2
fi

if [[ ! -d "$QNN_SDK_ROOT" ]]; then
  echo "QNN_SDK_ROOT does not exist: $QNN_SDK_ROOT" >&2
  exit 2
fi

ANDROID_NDK_PATH="${ANDROID_NDK:-${ANDROID_NDK_HOME:-}}"
if [[ -z "$ANDROID_NDK_PATH" || ! -d "$ANDROID_NDK_PATH" ]]; then
  echo "ANDROID_NDK or ANDROID_NDK_HOME must point to an installed Android NDK." >&2
  exit 2
fi

ANDROID_SDK_PATH="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$ANDROID_SDK_PATH" || ! -d "$ANDROID_SDK_PATH" ]]; then
  candidate="$(cd "$ANDROID_NDK_PATH/../.." && pwd)"
  if [[ -d "$candidate/platforms" ]]; then
    ANDROID_SDK_PATH="$candidate"
  fi
fi
if [[ -z "$ANDROID_SDK_PATH" || ! -d "$ANDROID_SDK_PATH" ]]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK." >&2
  exit 2
fi

mkdir -p "$WORK_DIR"
if [[ ! -d "$ORT_DIR/.git" ]]; then
  git clone --recursive https://github.com/microsoft/onnxruntime.git "$ORT_DIR"
fi

clean_conflict_copies "$ORT_DIR"
clean_conflict_copies "$BUILD_DIR"

cd "$ORT_DIR"
if [[ "${XREADER_ONNXRUNTIME_SKIP_FETCH:-0}" != "1" ]]; then
  git fetch --tags --quiet
fi
git checkout --quiet "$ORT_VERSION"
git submodule update --init --recursive
clean_conflict_copies "$ORT_DIR"

if [[ -f "$QAIRT_COMPAT_PATCH" ]]; then
  if git apply --check "$QAIRT_COMPAT_PATCH" >/dev/null 2>&1; then
    git apply "$QAIRT_COMPAT_PATCH"
  elif ! git apply --reverse --check "$QAIRT_COMPAT_PATCH" >/dev/null 2>&1; then
    echo "ONNX Runtime QAIRT compatibility patch does not apply cleanly: $QAIRT_COMPAT_PATCH" >&2
    exit 3
  fi
fi

if [[ -f "$QNN_NNAPI_STATIC_PATCH" ]]; then
  if git apply --check "$QNN_NNAPI_STATIC_PATCH" >/dev/null 2>&1; then
    git apply "$QNN_NNAPI_STATIC_PATCH"
  elif ! git apply --reverse --check "$QNN_NNAPI_STATIC_PATCH" >/dev/null 2>&1; then
    echo "ONNX Runtime QNN+NNAPI static provider patch does not apply cleanly: $QNN_NNAPI_STATIC_PATCH" >&2
    exit 3
  fi
fi

if [[ -f "$QNN_HTP_V79_PATCH" ]]; then
  if git apply --check "$QNN_HTP_V79_PATCH" >/dev/null 2>&1; then
    git apply "$QNN_HTP_V79_PATCH"
  elif ! git apply --reverse --check "$QNN_HTP_V79_PATCH" >/dev/null 2>&1; then
    echo "ONNX Runtime QNN HTP v79 architecture patch does not apply cleanly: $QNN_HTP_V79_PATCH" >&2
    exit 3
  fi
fi

if [[ -f "$QNN_HTP_SIGNED_PD_PATCH" ]]; then
  if git apply --check "$QNN_HTP_SIGNED_PD_PATCH" >/dev/null 2>&1; then
    git apply "$QNN_HTP_SIGNED_PD_PATCH"
  elif ! git apply --reverse --check "$QNN_HTP_SIGNED_PD_PATCH" >/dev/null 2>&1; then
    echo "ONNX Runtime QNN HTP signed process-domain patch does not apply cleanly: $QNN_HTP_SIGNED_PD_PATCH" >&2
    exit 3
  fi
fi

clean_conflict_copies "$ORT_DIR"
clean_conflict_copies "$BUILD_DIR"

python3 tools/ci_build/build.py \
  --config Release \
  --android \
  --android_abi arm64-v8a \
  --android_api "${XREADER_ONNXRUNTIME_ANDROID_API:-27}" \
  --android_ndk_path "$ANDROID_NDK_PATH" \
  --android_sdk_path "$ANDROID_SDK_PATH" \
  --build_shared_lib \
  --cmake_generator Ninja \
  --parallel \
  --skip_tests \
  --targets onnxruntime \
  --use_qnn static_lib \
  --qnn_home "$QNN_SDK_ROOT" \
  --use_nnapi \
  --nnapi_min_api "${XREADER_ONNXRUNTIME_NNAPI_MIN_API:-27}" \
  --build_dir "$BUILD_DIR"

LIB="$(find "$BUILD_DIR" -path '*/Release/libonnxruntime.so' -type f | head -n 1)"
if [[ -z "$LIB" || ! -f "$LIB" ]]; then
  echo "QNN-enabled libonnxruntime.so was not produced under $BUILD_DIR." >&2
  exit 3
fi

rm -rf "$PACKAGE_DIR"
mkdir -p "$PACKAGE_DIR/jni/arm64-v8a" "$PACKAGE_DIR/headers"
cp "$LIB" "$PACKAGE_DIR/jni/arm64-v8a/libonnxruntime.so"
cp include/onnxruntime/core/session/*.h "$PACKAGE_DIR/headers/"
cp include/onnxruntime/core/providers/qnn/qnn_provider_factory.h "$PACKAGE_DIR/headers/" 2>/dev/null || true
cp include/onnxruntime/core/providers/nnapi/nnapi_provider_factory.h "$PACKAGE_DIR/headers/" 2>/dev/null || true

ORT_NM_FILE="$(mktemp "${TMPDIR:-/tmp}/xreader-ort-nm.XXXXXX")"
ORT_STRINGS_FILE="$(mktemp "${TMPDIR:-/tmp}/xreader-ort-strings.XXXXXX")"
nm -D "$PACKAGE_DIR/jni/arm64-v8a/libonnxruntime.so" >"$ORT_NM_FILE"
strings "$PACKAGE_DIR/jni/arm64-v8a/libonnxruntime.so" >"$ORT_STRINGS_FILE"

if ! grep -q "OrtSessionOptionsAppendExecutionProvider_Nnapi" "$ORT_NM_FILE"; then
  rm -f "$ORT_NM_FILE" "$ORT_STRINGS_FILE"
  echo "Built ONNX Runtime does not export OrtSessionOptionsAppendExecutionProvider_Nnapi." >&2
  exit 4
fi

if ! grep -Eq "QnnHtp|QNN EP|qnnexecution" "$ORT_STRINGS_FILE"; then
  rm -f "$ORT_NM_FILE" "$ORT_STRINGS_FILE"
  echo "Built ONNX Runtime does not contain QNN provider implementation metadata." >&2
  exit 4
fi
rm -f "$ORT_NM_FILE" "$ORT_STRINGS_FILE"

echo "$PACKAGE_DIR"
