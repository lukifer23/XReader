#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${XREADER_NATIVE_TARGET_DIR:-"$ROOT_DIR/app/src/main/jniLibs/arm64-v8a"}"
ADB="${ADB:-adb}"
SERIAL="${1:-${ANDROID_SERIAL:-}}"

adb_cmd=("$ADB")
if [[ -n "$SERIAL" ]]; then
  adb_cmd+=("-s" "$SERIAL")
fi

find_readelf() {
  if [[ -n "${LLVM_READELF:-}" && -x "${LLVM_READELF:-}" ]]; then
    echo "$LLVM_READELF"
    return 0
  fi
  local ndk_root="${ANDROID_NDK:-${ANDROID_NDK_HOME:-}}"
  if [[ -n "$ndk_root" && -x "$ndk_root/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf" ]]; then
    echo "$ndk_root/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-readelf"
    return 0
  fi
  if [[ -n "$ndk_root" && -d "$ndk_root" ]]; then
    local candidate
    candidate="$(find "$ndk_root" -path '*/toolchains/llvm/prebuilt/*/bin/llvm-readelf' \( -type f -o -type l \) | sort | tail -n 1)"
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  fi
  if [[ -d "$HOME/Library/Android/sdk/ndk" ]]; then
    candidate="$(find "$HOME/Library/Android/sdk/ndk" -path '*/toolchains/llvm/prebuilt/*/bin/llvm-readelf' \( -type f -o -type l \) | sort | tail -n 1)"
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  fi
  command -v llvm-readelf || command -v readelf || {
    echo "Could not find llvm-readelf/readelf. Set LLVM_READELF or ANDROID_NDK." >&2
    return 1
  }
}

READ_ELF="$(find_readelf)"
mkdir -p "$TARGET_DIR"

remote_dirs=(
  "/vendor/lib64"
  "/vendor/lib64/egl"
  "/vendor/lib64/hw"
  "/system/lib64"
  "/system/vendor/lib64"
  "/system/vendor/lib64/egl"
  "/system/vendor/lib64/hw"
  "/odm/lib64"
  "/odm/lib64/egl"
)

system_libs=(
  "ld-android.so"
  "libandroid.so"
  "libbase.so"
  "libc++.so"
  "libc.so"
  "libdl.so"
  "libEGL.so"
  "libGLESv1_CM.so"
  "libGLESv2.so"
  "libGLESv3.so"
  "liblog.so"
  "libm.so"
  "libnativewindow.so"
  "libstdc++.so"
  "libsync.so"
  "libutils.so"
  "libz.so"
)

opencl_support_libs=(
  "libbase.so"
  "libcutils.so"
  "libdl_android.so"
  "libvndksupport.so"
)

is_system_lib() {
  local lib="$1"
  local packaged_support
  for packaged_support in "${opencl_support_libs[@]}"; do
    [[ "$lib" == "$packaged_support" ]] && return 1
  done
  local known
  for known in "${system_libs[@]}"; do
    [[ "$lib" == "$known" ]] && return 0
  done
  return 1
}

remote_path_for() {
  local lib="$1"
  local dir
  for dir in "${remote_dirs[@]}"; do
    if "${adb_cmd[@]}" shell test -f "$dir/$lib" >/dev/null 2>&1; then
      echo "$dir/$lib"
      return 0
    fi
  done
  return 1
}

pull_lib() {
  local lib="$1"
  local remote
  remote="$(remote_path_for "$lib")" || {
    echo "Missing required device library: $lib" >&2
    return 1
  }
  echo "Staging $lib from $remote"
  "${adb_cmd[@]}" pull "$remote" "$TARGET_DIR/$lib" >/dev/null
  chmod 0644 "$TARGET_DIR/$lib"
}

needed_libs() {
  "$READ_ELF" -d "$1" 2>/dev/null |
    sed -n 's/.*Shared library: \[\([^]]*\)\].*/\1/p' |
    sort -u
}

pull_lib "libOpenCL.so"
pull_lib "libOpenCL_adreno.so"

for support_lib in "${opencl_support_libs[@]}"; do
  [[ -f "$TARGET_DIR/$support_lib" ]] && continue
  if remote_path_for "$support_lib" >/dev/null; then
    pull_lib "$support_lib"
  fi
done

for required in libOpenCL.so libOpenCL_adreno.so; do
  if [[ ! -f "$TARGET_DIR/$required" ]]; then
    echo "OpenCL staging failed; missing $required in $TARGET_DIR." >&2
    exit 3
  fi
done

echo "Staged device OpenCL runtime into $TARGET_DIR"
