#!/usr/bin/env bash
# One screenshot of card appearance.
set -euo pipefail
mkdir -p screens
exec > >(tee screens/scenario.log) 2>&1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell setprop debug.hwui.renderer skiagl
adb shell setprop debug.hwui.use_buffer_age false
adb shell setprop debug.hwui.swap_with_damage false
adb logcat -b crash -c
python3 tools/ui-card-reflection.py
python3 - <<'PY'
from pathlib import Path
import struct, zlib
for name in ('card-reflection-1541.png',):
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
