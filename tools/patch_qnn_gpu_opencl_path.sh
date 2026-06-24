#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIB_PATH="${1:-"$ROOT_DIR/app/src/main/jniLibs/arm64-v8a/libQnnGpu.so"}"
NEW_PATH="/dev/fd/198"
OLD_PATHS=(
  "/proc/self/fd/198"
  "/system/vendor/lib64/libOpenCL.so"
  "/system_ext/lib64/libOpenCL_system.so"
  "/system/lib64/libOpenCL_system.so"
  "/system/lib64/libOpenCL.so"
  "/vendor/lib64/libOpenCL-pixel.so"
  "/vendor/lib64/libOpenCL.so"
  "/libOpenCL_Adreno.so"
  "/libOpenCL.so"
)

if [[ ! -f "$LIB_PATH" ]]; then
  echo "QNN GPU library is missing: $LIB_PATH" >&2
  exit 2
fi

STRINGS_FILE="$(mktemp "${TMPDIR:-/tmp}/xreader-qnn-gpu-strings.XXXXXX")"
trap 'rm -f "$STRINGS_FILE"' EXIT
strings "$LIB_PATH" >"$STRINGS_FILE"

patched=0
for OLD_PATH in "${OLD_PATHS[@]}"; do
  if (( ${#NEW_PATH} > ${#OLD_PATH} )); then
    echo "Replacement OpenCL path is longer than QNN GPU built-in path: $OLD_PATH" >&2
    exit 2
  fi

  if ! grep -qF "$OLD_PATH" "$STRINGS_FILE"; then
    continue
  fi

  OLD_PATH="$OLD_PATH" NEW_PATH="$NEW_PATH" perl -0pi -e '
  BEGIN {
    $old = $ENV{"OLD_PATH"};
    $new = $ENV{"NEW_PATH"};
    $new = $new . ("\0" x (length($old) - length($new)));
  }
  $count += s/\Q$old\E/$new/g;
  END {
    exit($count > 0 ? 0 : 4);
  }
' "$LIB_PATH"
  patched=1
  strings "$LIB_PATH" >"$STRINGS_FILE"
  echo "Patched QNN GPU OpenCL path in $LIB_PATH: $OLD_PATH -> $NEW_PATH"
done

if [[ "$patched" -eq 0 ]]; then
  if grep -qF "$NEW_PATH" "$STRINGS_FILE"; then
    echo "QNN GPU OpenCL paths are already patched: $LIB_PATH"
    exit 0
  fi
  echo "Expected QNN GPU OpenCL paths were not found in $LIB_PATH." >&2
  exit 3
fi

strings "$LIB_PATH" >"$STRINGS_FILE"
if ! grep -qF "$NEW_PATH" "$STRINGS_FILE"; then
  echo "Patched QNN GPU OpenCL path was not found in $LIB_PATH: $NEW_PATH" >&2
  exit 5
fi
