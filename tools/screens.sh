#!/usr/bin/env bash
# Скриншоты экранов на эмуляторе для проверки интерфейса до релиза (отладочная сборка с демо-данными).
# Снимки — в screens/*.png; workflow выкладывает их в пререлиз «screens».
set -u
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=app.dirhamrub
OUT=screens
mkdir -p "$OUT"

adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.CAMERA
# Эмулятор в CI медленный: системные окна «… isn't responding» закрывали снимки — прячем их
# и даём системе успокоиться после загрузки.
adb shell settings put global hide_error_dialogs 1
sleep 30

# shot <имя> [extra...]: свежий запуск с демо-данными и снимок экрана.
shot() {
  local name=$1
  shift
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" --ez demo true "$@" > /dev/null
  sleep "${WAIT:-7}"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
  sleep 1
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "снимок: $name"
}

adb shell cmd uimode night yes
adb shell settings put system font_scale 1.0
WAIT=15 shot 01-main
shot 02-cheat --es screen cheat
shot 03-currencies --es screen currencies
WAIT=12 shot 04-editor --es screen editor
WAIT=10 shot 05-camera --es screen camera

adb shell cmd uimode night no
shot 06-main-light
WAIT=12 shot 07-editor-light --es screen editor

adb shell cmd uimode night yes
adb shell settings put system font_scale 1.3
shot 08-main-font130
adb shell settings put system font_scale 1.0

# Короткий телефон (≈ 730 dp по высоте).
adb shell wm size 1080x1920
shot 09-main-short
adb shell wm size reset

# Первый запуск: данные стёрты — знакомство.
adb shell pm clear "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
sleep 10
adb exec-out screencap -p > "$OUT/10-onboarding.png"
echo "снимок: 10-onboarding"

ls -la "$OUT"
# Падения только нашего процесса: системные приложения эмулятора тоже иногда падают.
if adb logcat -d -b crash | grep -A1 'FATAL EXCEPTION' | grep -q "Process: $PKG,"; then
  echo "=== Падение при съёмке экранов ==="
  adb logcat -d -b crash -v raw
  exit 1
fi
