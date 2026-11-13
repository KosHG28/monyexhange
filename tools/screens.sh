#!/usr/bin/env bash
# Снимки только изменённых экранов текущего пакета: главный и последний шаг знакомства.
# Информация о стране исключена по просьбе владельца.
set -euo pipefail
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=ru.kurspocheku
ACT=app.dirhamrub.MainActivity
OUT=screens
mkdir -p "$OUT"
adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.CAMERA
adb logcat -b crash -c
adb shell settings put global hide_error_dialogs 1
sleep 15

restore_display() {
  adb shell wm size reset || true
  adb shell wm density reset || true
  adb shell settings put system font_scale 1.0 || true
}
trap restore_display EXIT

shot() {
  local name=$1
  shift
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/$ACT" --ez demo true "$@" > /dev/null
  sleep "${WAIT:-6}"
  adb exec-out screencap -p > "$OUT/$name.png"
  test -s "$OUT/$name.png"
  echo "снимок: $name"
}

adb shell cmd uimode night yes
shot 01-main
shot 02-main-long --es amount 812492.31
adb shell cmd uimode night no
shot 07-main-light
shot 08-main-long-light --es amount 812492.31

adb shell wm density 450
shot 15-main-narrow --es amount 812492.31
adb shell wm density reset
adb shell wm size 1080x1920
shot 17-main-short --es amount 812492.31
adb shell wm size reset
adb shell settings put system font_scale 1.3
shot 18-main-font130 --es amount 812492.31
adb shell settings put system font_scale 1.0
adb shell cmd uimode night yes
shot 23-onboarding-setup --es screen onboarding --ei step 2

# Настоящие нажатия: третий шаг до/во время/после перехода, сохранение первой карты и выбор строки.
python3 tools/ui-transitions.py

# Проверяем PNG средствами стандартной библиотеки: обрезанный файл не должен считаться снимком.
python3 - <<'PY'
from pathlib import Path
import struct, zlib
files = sorted(Path('screens').glob('*.png'))
assert len(files) == 18, f'Ожидалось 18 снимков, получено {len(files)}'
for path in files:
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    pos, finished = 8, False
    while pos < len(data):
        n, = struct.unpack_from('>I', data, pos)
        kind = data[pos + 4:pos + 8]
        payload = data[pos + 8:pos + 8 + n]
        crc, = struct.unpack_from('>I', data, pos + 8 + n)
        assert zlib.crc32(kind + payload) & 0xffffffff == crc, path
        pos += 12 + n
        if kind == b'IEND':
            finished = True
            break
    assert finished, path
print('Проверены 18 целых PNG изменённых экранов; содержимое требует визуального просмотра.')
PY

adb logcat -d -b crash > "$OUT/crash.txt"
if grep -A1 'FATAL EXCEPTION' "$OUT/crash.txt" | grep -q "Process: $PKG,"; then
  cat "$OUT/crash.txt"
  exit 1
fi
