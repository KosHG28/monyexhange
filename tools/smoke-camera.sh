#!/usr/bin/env bash
# Дымовой тест камеры на эмуляторе. Два сценария, как у пользователя:
# 1) первый запуск «Ценника» без разрешения камеры (экран с объяснением);
# 2) разрешение выдано — камера и распознавание работают 40 с.
# Падение (FATAL EXCEPTION или пропавший процесс) валит тест; стек и logcat — в логе шага.
set -u
APK=app/build/outputs/apk/release/app-release.apk
PKG=app.dirhamrub

fail_if_crashed() {
  if adb logcat -d | grep -q 'FATAL EXCEPTION'; then
    echo "=== ПАДЕНИЕ: $1 ==="
    adb logcat -d | grep -A 80 'FATAL EXCEPTION'
    exit 1
  fi
  if [ -z "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then
    echo "=== Процесс пропал: $1 ==="
    adb logcat -d -v brief '*:W' | tail -n 200
    exit 1
  fi
  echo "OK: $1"
}

adb install -r "$APK"

adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" --es screen camera
sleep 8
fail_if_crashed "экран камеры без разрешения"

adb shell am force-stop "$PKG"
adb shell pm grant "$PKG" android.permission.CAMERA
adb logcat -c
adb shell am start -W -n "$PKG/.MainActivity" --es screen camera
sleep 40
echo "=== logcat: предупреждения и ошибки ==="
adb logcat -d -v brief '*:W' | grep -v chatty | tail -n 150
fail_if_crashed "камера с разрешением, 40 с"
