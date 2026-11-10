#!/usr/bin/env bash
# 接続先が1台だけの場合に、既存履歴を残してAPKを更新インストールする。
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-${ANDROID_HOME:-$HOME/Android}/platform-tools/adb}"
APK="build/outputs/apk/coin-toss-debug.apk"
if [[ ! -f "$APK" ]]; then
  python3 scripts/build.py
fi
# Windows版adbにはLinuxのパスを渡さず、WSLの共有パスへ変換する。
case "$(readlink -f "$(command -v "$ADB")")" in
  *.exe) APK="$(wslpath -w "$PWD/$APK")" ;;
esac
"$ADB" devices -l
"$ADB" install -r "$APK"
"$ADB" shell am start -n jp.cointoss.app/.MainActivity
