#!/usr/bin/env bash
set -euo pipefail

PACKAGE="${PACKAGE:-com.xreader.app}"
ACTIVITY="${ACTIVITY:-com.xreader.app/.MainActivity}"
ITERATIONS="${ITERATIONS:-5}"
OUT_DIR="${OUT_DIR:-build/perf/$(date +%Y%m%d-%H%M%S)}"
READER_TAP_X="${READER_TAP_X:-}"
READER_TAP_Y="${READER_TAP_Y:-}"
SERIAL="${ANDROID_SERIAL:-}"
DISPLAY_ID="${DISPLAY_ID:-}"

usage() {
  cat <<USAGE
Usage: $0 [--serial SERIAL] [--iterations N] [--out DIR] [--reader-tap X Y] [--display-id ID]

Environment:
  PACKAGE       Android package name. Default: com.xreader.app
  ACTIVITY      Launch activity. Default: com.xreader.app/.MainActivity
  ANDROID_SERIAL adb serial when more than one device is attached.
  DISPLAY_ID    Physical display id for screenshots on foldables/multi-display devices.

The script writes adb startup timings, gfxinfo, framestats, UI dumps, and
screenshots into OUT_DIR. Reader-open capture runs only when --reader-tap is
provided, because the tap coordinate depends on the current test library.
USAGE
}

epoch_ms() {
  if command -v python3 >/dev/null; then
    python3 -c 'import time; print(int(time.time() * 1000))'
  else
    perl -MTime::HiRes=time -e 'printf "%.0f\n", time() * 1000'
  fi
}

capture_screenshot() {
  local remote_path="$1"
  if [[ -n "$DISPLAY_ID" ]]; then
    "${ADB[@]}" shell screencap -d "$DISPLAY_ID" -p "$remote_path" >/dev/null || true
  else
    "${ADB[@]}" shell screencap -p "$remote_path" >/dev/null || true
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial)
      SERIAL="${2:?missing serial}"
      shift 2
      ;;
    --iterations)
      ITERATIONS="${2:?missing iteration count}"
      shift 2
      ;;
    --out)
      OUT_DIR="${2:?missing output directory}"
      shift 2
      ;;
    --reader-tap)
      READER_TAP_X="${2:?missing reader tap x}"
      READER_TAP_Y="${3:?missing reader tap y}"
      shift 3
      ;;
    --display-id)
      DISPLAY_ID="${2:?missing display id}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v adb >/dev/null; then
  echo "adb is required" >&2
  exit 1
fi

if [[ -z "$SERIAL" ]]; then
  devices_raw="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
  device_count="$(printf "%s\n" "$devices_raw" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ "$device_count" -eq 1 ]]; then
    SERIAL="$(printf "%s\n" "$devices_raw" | sed -n '1p')"
  elif [[ "$device_count" -eq 0 ]]; then
    echo "No adb device is connected" >&2
    exit 1
  else
    echo "Multiple adb devices found; pass --serial" >&2
    adb devices -l >&2
    exit 1
  fi
fi

ADB=(adb -s "$SERIAL")
mkdir -p "$OUT_DIR"

{
  echo "timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "serial=$SERIAL"
  echo "package=$PACKAGE"
  echo "activity=$ACTIVITY"
  echo "iterations=$ITERATIONS"
  if [[ -n "$DISPLAY_ID" ]]; then
    echo "display_id=$DISPLAY_ID"
  fi
  "${ADB[@]}" shell getprop ro.product.model | tr -d '\r' | sed 's/^/device_model=/'
  "${ADB[@]}" shell getprop ro.build.version.release | tr -d '\r' | sed 's/^/android_release=/'
  "${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r' | sed 's/^/android_sdk=/'
} > "$OUT_DIR/context.txt"

printf "iteration\tthis_ms\ttotal_ms\twait_ms\n" > "$OUT_DIR/startup.tsv"
for ((i = 1; i <= ITERATIONS; i++)); do
  "${ADB[@]}" shell am force-stop "$PACKAGE" >/dev/null
  sleep 0.5
  output="$("${ADB[@]}" shell am start -W -n "$ACTIVITY" | tr -d '\r')"
  printf "%s\n" "$output" > "$OUT_DIR/startup-$i.txt"
  this_ms="$(printf "%s\n" "$output" | awk -F: '/ThisTime/ { gsub(/ /, "", $2); print $2 }')"
  total_ms="$(printf "%s\n" "$output" | awk -F: '/TotalTime/ { gsub(/ /, "", $2); print $2 }')"
  wait_ms="$(printf "%s\n" "$output" | awk -F: '/WaitTime/ { gsub(/ /, "", $2); print $2 }')"
  printf "%d\t%s\t%s\t%s\n" "$i" "${this_ms:-}" "${total_ms:-}" "${wait_ms:-}" >> "$OUT_DIR/startup.tsv"
done

"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null || true
"${ADB[@]}" shell am force-stop "$PACKAGE" >/dev/null
sleep 0.5
"${ADB[@]}" shell am start -W -n "$ACTIVITY" > "$OUT_DIR/startup-gfx-start.txt"
sleep 3
"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/startup-gfxinfo.txt" || true
"${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" framestats > "$OUT_DIR/startup-framestats.txt" || true
"${ADB[@]}" shell uiautomator dump /sdcard/xreader-perf-startup.xml >/dev/null || true
"${ADB[@]}" pull /sdcard/xreader-perf-startup.xml "$OUT_DIR/startup-ui.xml" >/dev/null 2>&1 || true
capture_screenshot /sdcard/xreader-perf-startup.png
"${ADB[@]}" pull /sdcard/xreader-perf-startup.png "$OUT_DIR/startup.png" >/dev/null 2>&1 || true

if [[ -n "$READER_TAP_X" && -n "$READER_TAP_Y" ]]; then
  "${ADB[@]}" shell am force-stop "$PACKAGE" >/dev/null
  sleep 0.5
  "${ADB[@]}" shell am start -W -n "$ACTIVITY" > "$OUT_DIR/reader-open-start.txt"
  sleep 2
  "${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" reset >/dev/null || true
  start_epoch_ms="$(epoch_ms)"
  "${ADB[@]}" shell input tap "$READER_TAP_X" "$READER_TAP_Y"
  sleep 5
  end_epoch_ms="$(epoch_ms)"
  {
    echo "reader_tap_x=$READER_TAP_X"
    echo "reader_tap_y=$READER_TAP_Y"
    echo "capture_window_ms=$((end_epoch_ms - start_epoch_ms))"
  } > "$OUT_DIR/reader-open-context.txt"
  "${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" > "$OUT_DIR/reader-open-gfxinfo.txt" || true
  "${ADB[@]}" shell dumpsys gfxinfo "$PACKAGE" framestats > "$OUT_DIR/reader-open-framestats.txt" || true
  "${ADB[@]}" shell uiautomator dump /sdcard/xreader-perf-reader.xml >/dev/null || true
  "${ADB[@]}" pull /sdcard/xreader-perf-reader.xml "$OUT_DIR/reader-open-ui.xml" >/dev/null 2>&1 || true
  capture_screenshot /sdcard/xreader-perf-reader.png
  "${ADB[@]}" pull /sdcard/xreader-perf-reader.png "$OUT_DIR/reader-open.png" >/dev/null 2>&1 || true
fi

TOTALS_FILE="$OUT_DIR/startup-total-ms.txt"
tail -n +2 "$OUT_DIR/startup.tsv" | awk '$3 != "" { print $3 }' | sort -n > "$TOTALS_FILE"
count="$(wc -l < "$TOTALS_FILE" | tr -d ' ')"
if [[ "$count" -eq 0 ]]; then
  echo "No startup timings captured" > "$OUT_DIR/summary.txt"
else
  avg="$(awk '{ sum += $1 } END { printf "%.1f", sum / NR }' "$TOTALS_FILE")"
  median_index="$(((count + 1) / 2))"
  p90_index="$(((count * 90 + 99) / 100))"
  median="$(sed -n "${median_index}p" "$TOTALS_FILE")"
  p90="$(sed -n "${p90_index}p" "$TOTALS_FILE")"
  {
    echo "startup_total_ms_count=$count"
    echo "startup_total_ms_avg=$avg"
    echo "startup_total_ms_median=$median"
    echo "startup_total_ms_p90=$p90"
  } > "$OUT_DIR/summary.txt"
fi

echo "Wrote performance artifacts to $OUT_DIR"
cat "$OUT_DIR/summary.txt"
