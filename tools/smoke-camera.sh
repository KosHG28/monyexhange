#!/usr/bin/env bash
# Дымовой тест камеры на эмуляторе: ставит release-APK, открывает экран «Ценник»
# (как ярлык на иконке), ждёт работы распознавания и проверяет, что процесс жив.
# В лог выводятся предупреждения и ошибки logcat, а при падении — стек.
set -u
APK=app/build/outputs/apk/release/app-release.apk
PKG=app.dirhamrub

adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.CAMERA
adb logcat -c

adb shell am start -n "$PKG/.MainActivity" --es screen camera
sleep 40

echo "=== logcat: предупреждения и ошибки ==="
adb logcat -d -v brief '*:W' | grep -v chatty | tail -n 250

if adb logcat -d | grep -q 'FATAL EXCEPTION'; then
  echo "=== ПАДЕНИЕ ==="
  adb logcat -d | grep -A 80 'FATAL EXCEPTION'
  exit 1
fi
PID=$(adb shell pidof "$PKG" | tr -d '\r')
if [ -z "$PID" ]; then
  echo "Процесс не найден — приложение закрылось"
  adb logcat -d | grep -iE "$PKG|DEBUG|libc|crash" | tail -n 120
  exit 1
fi
echo "OK: камера работает 40 с, процесс жив (pid $PID)"
