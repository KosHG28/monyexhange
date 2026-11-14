#!/usr/bin/env bash
# Снимки только изменённых экранов текущего пакета: главный, последний шаг знакомства и редактор карты (общая обёртка окна).
# Информация о стране исключена по просьбе владельца.
set -euo pipefail
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=ru.kurspocheku
ACT=app.dirhamrub.MainActivity
OUT=screens
mkdir -p "$OUT"
exec > >(tee "$OUT/scenario.log") 2>&1
trap 'printf "Ошибка на строке %s: %s\n" "$LINENO" "$BASH_COMMAND" >&2' ERR
adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.CAMERA
# Для SwiftShader используем полную перерисовку кадров: частичные обновления давали обрезанные слои в PNG.
adb shell setprop debug.hwui.renderer skiagl
adb shell setprop debug.hwui.use_buffer_age false
adb shell setprop debug.hwui.swap_with_damage false
# 720/280 = 1080/420: та же ширина в dp при меньшей нагрузке программного GPU.
adb shell wm size 720x1600
adb shell wm density 280
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
shot 35-irr-max-dark --es spend IRR --es amount 999999999999.99
adb shell cmd uimode night no
shot 07-main-light
shot 08-main-long-light --es amount 812492.31
shot 36-irr-max-light --es spend IRR --es amount 999999999999.99

adb shell wm density 300
shot 15-main-narrow --es amount 812492.31
adb shell wm density 280
adb shell wm size 720x1280
shot 17-main-short --es amount 812492.31
adb shell wm size 720x1600
adb shell am force-stop "$PKG"
adb shell settings put system font_scale 1.3
sleep 2
WAIT=10 shot 18-main-font130 --es amount 812492.31
adb shell uiautomator dump /sdcard/font130.xml > /dev/null
adb exec-out cat /sdcard/font130.xml > "$OUT/font130-ui.xml"
adb shell am force-stop "$PKG"
adb shell wm density 300
sleep 2
shot 37-irr-narrow-font130 --es spend IRR --es amount 999999999999.99
adb shell wm density 280
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
assert len(files) == 26, f'Ожидалось 26 снимков, получено {len(files)}: {[p.name for p in files]}'
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
print('Проверены 26 целых PNG изменённых экранов; содержимое требует визуального просмотра.')
PY

adb logcat -d -b crash > "$OUT/crash.txt"
if grep -A1 'FATAL EXCEPTION' "$OUT/crash.txt" | grep -q "Process: $PKG,"; then
  cat "$OUT/crash.txt"
  exit 1
fi
