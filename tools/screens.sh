#!/usr/bin/env bash
# Check card materials, transfer removal and the other changed screens.
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
# Preserve phone layout while keeping the emulator framebuffer small.
adb shell wm size 720x1600
adb shell wm density 288
adb logcat -b crash -c
timeout 180s adb logcat -b crash -b main -b system -v brief AndroidRuntime:E libc:F DEBUG:F OpenGLRenderer:E '*:S' > screens/app-log.txt 2>&1 &
android_log_pid=$!
python3 tools/ui-polish-159.py
kill "$android_log_pid" 2>/dev/null || true
python3 - <<'PY'
from pathlib import Path
import struct, zlib
for name in ('calculator-159.png', 'editor-159.png', 'settings-159.png', 'pro-159.png', 'materials-159.png'):
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
