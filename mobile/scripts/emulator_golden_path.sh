#!/usr/bin/env bash
# Phase 4.1 Android Emulator golden path helper.
# Requires: KVM, Android SDK, running Spring Boot, seeded ERPNext data.
set -euo pipefail

API_PORT="${SPRING_PORT:-8080}"
API_BASE="http://10.0.2.2:${API_PORT}"
SCREEN_DIR="${SCREEN_DIR:-$(cd "$(dirname "$0")/../artifacts/screenshots" && pwd)}"
APK="${APK:-$(cd "$(dirname "$0")/.." && pwd)/build/app/outputs/flutter-apk/app-debug.apk}"

mkdir -p "$SCREEN_DIR"

if ! command -v adb >/dev/null; then
  echo "adb not found; set ANDROID_HOME and install platform-tools" >&2
  exit 1
fi

device=$(adb devices | awk '/emulator-/{print $1; exit}')
if [[ -z "${device:-}" ]]; then
  echo "No emulator detected. Start one first, e.g.:" >&2
  echo "  sg kvm -c 'emulator -avd <name> -gpu swiftshader_indirect'" >&2
  exit 1
fi

boot=$(adb -s "$device" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
if [[ "$boot" != "1" ]]; then
  echo "Emulator $device not booted (boot_completed=$boot)" >&2
  exit 1
fi

shot() {
  local name="$1"
  adb -s "$device" exec-out screencap -p > "$SCREEN_DIR/${name}.png"
  echo "screenshot: $SCREEN_DIR/${name}.png"
}

if [[ -f "$APK" ]]; then
  adb -s "$device" install -r "$APK"
fi

adb -s "$device" reverse tcp:"$API_PORT" tcp:"$API_PORT" 2>/dev/null || true

echo "Launch app with API_BASE_URL=$API_BASE"
echo "Manual steps (see mobile/README.md 真实黄金路径):"
echo "  1. Login"
echo "  2. New order -> 韩兆亮 -> APPLE-80 20箱 + BANANA-FEN 30件 -> save draft"
echo "  3. Edit apple to 30箱 -> submit"
echo "  4. Payment 1000 confirmed -> partial; top-up remaining -> paid"
echo "  5. Inventory, customers, logout"
echo ""
echo "After each screen, run: shot <name>"
echo "Suggested names: 01-login 02-home 03-orders 04-order-edit 05-customer-selector"
echo "  06-product-selector 07-item-editor 08-order-detail 09-payment 10-customers"
echo "  11-inventory 12-more"
