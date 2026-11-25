#!/usr/bin/env bash
# Review the card editor for the NFC template-name fix.
set -euo pipefail
mkdir -p screens
exec > >(tee screens/scenario.log) 2>&1
diagnose() {
    if [ "$?" -ne 0 ]; then
        timeout 12s adb logcat -d -b crash > screens/crash.txt 2>&1 || true
        timeout 12s adb shell dumpsys activity activities > screens/activity-state.txt 2>&1 || true
    fi
}
trap diagnose EXIT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell setprop debug.hwui.renderer skiagl
adb shell setprop debug.hwui.use_buffer_age false
adb shell setprop debug.hwui.swap_with_damage false
adb logcat -b crash -c
timeout 180s adb logcat -b crash -b main -b system -v brief AndroidRuntime:E libc:F DEBUG:F OpenGLRenderer:E '*:S' > screens/app-log.txt 2>&1 &
android_log_pid=$!
adb shell cmd uimode night yes
adb shell am start -W -n ru.kurspocheku/app.dirhamrub.MainActivity --ez demo true --ez nocards true --es screen editor --es card new --es spend CNY
python3 - <<'PY'
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, 'tools')
from ui_tree import read_tree

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=35)

for _ in range(6):
    root = read_tree(adb, '/sdcard/kpc-editor-1571.xml')
    nodes = [node for node in root.iter('node') if node.get('package') == 'ru.kurspocheku']
    if (any(node.get('text') == 'Новая карта' for node in nodes)
            and any(node.get('content-desc') == 'Карта CNY' or node.get('text') == 'Карта CNY' for node in nodes)):
        break
else:
    raise AssertionError('The CNY template did not appear in the card editor')

with Path('screens/editor-1571.png').open('wb') as output:
    subprocess.run(['adb', 'exec-out', 'screencap', '-p'], check=True, stdout=output, timeout=30)
PY
kill "$android_log_pid" 2>/dev/null || true
python3 - <<'PY'
from pathlib import Path
import struct, zlib
for name in ('editor-1571.png',):
    path = Path('screens') / name
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    pos = 8
    while pos + 12 <= len(data):
        n, = struct.unpack_from('>I', data, pos)
        kind = data[pos+4:pos+8]
        payload = data[pos+8:pos+8+n]
        crc, = struct.unpack_from('>I', data, pos+8+n)
        assert zlib.crc32(kind + payload) & 0xffffffff == crc, path
        pos += n + 12
        if kind == b'IEND':
            break
    else:
        raise AssertionError(f'Incomplete PNG: {path}')
PY
adb logcat -d -b crash > screens/crash.txt
if rg -q "Process: ru.kurspocheku," screens/crash.txt; then cat screens/crash.txt; exit 1; fi
