#!/usr/bin/env bash
# Дымовой тест камеры на эмуляторе. Два сценария, как у пользователя:
# 1) первый запуск «Ценника» без разрешения камеры (экран с объяснением);
# 2) разрешение выдано — камера и распознавание работают 40 с.
# Падение (FATAL EXCEPTION или пропавший процесс) валит тест; стек и logcat — в логе шага.
set -u
APK=app/build/outputs/apk/release/app-release.apk
PKG=app.dirhamrub

MAPPING=app/build/outputs/mapping/release/mapping.txt

# Расшифровка стека release-сборки (R8) по mapping.txt.
retrace_crash() {
  echo "=== Расшифровка (retrace) ==="
  RETRACE="$ANDROID_HOME/cmdline-tools/latest/bin/retrace"
  if [ -x "$RETRACE" ]; then "$RETRACE" "$MAPPING" "$1" || true; fi
  echo "=== Классы из стека по mapping ==="
  for c in $(grep -oE '(at |field .)[a-z0-9]+\.[a-zA-Z0-9]+' "$1" | sed -E 's/^(at |field .)//; s/\..*//' | sort -u); do
    grep -E " -> $c:$" "$MAPPING" || true
  done
  grep -oE "'[a-z0-9]+ [a-z0-9]+\.[a-z0-9]+'" "$1" | tr -d "'" | while read -r t f; do
    owner="${f%%.*}"; echo "поле $f типа $t:"; grep -E " -> ($owner|$t):$" "$MAPPING" || true
  done
}

fail_if_crashed() {
  # Только падения нашего процесса: системные приложения эмулятора (Gmail и т. п.) тоже иногда падают.
  if adb logcat -d -b crash | grep -A1 'FATAL EXCEPTION' | grep -q "Process: $PKG,"; then
    echo "=== ПАДЕНИЕ: $1 ==="
    adb logcat -d -b crash -v raw > crash.txt
    cat crash.txt
    retrace_crash crash.txt
    exit 1
  fi
  if adb logcat -d -s 'DirhamCamera:E' | grep -q 'E DirhamCamera\|E/DirhamCamera'; then
    echo "=== ОШИБКА КАМЕРЫ: $1 ==="
    adb logcat -d -s 'DirhamCamera:E' > camera-error.txt
    cat camera-error.txt
    retrace_crash camera-error.txt
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
