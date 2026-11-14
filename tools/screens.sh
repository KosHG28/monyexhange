#!/usr/bin/env bash
# Changed screen only: card editor payment-system labels. Country information and camera excluded.
set -euo pipefail
mkdir -p screens
exec > >(tee screens/scenario.log) 2>&1
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell setprop debug.hwui.renderer skiagl
adb shell setprop debug.hwui.use_buffer_age false
adb shell setprop debug.hwui.swap_with_damage false
adb logcat -b crash -c
sleep 5
python3 tools/ui-network-chips.py
python3 - <<'PY'
from pathlib import Path
import struct,zlib
files=list(Path('screens').glob('editor-*.png'))
assert len(files)==10,len(files)
for path in files:
    data=path.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n',path
    pos=8;finished=False
    while pos<len(data):
        n,=struct.unpack_from('>I',data,pos);kind=data[pos+4:pos+8];payload=data[pos+8:pos+8+n]
        crc,=struct.unpack_from('>I',data,pos+8+n)
        assert zlib.crc32(kind+payload)&0xffffffff==crc,path
        pos+=n+12
        if kind==b'IEND':finished=True;break
    assert finished,path
print('10 complete editor screenshots, both themes and font scales 1.0/1.3/1.5.')
PY
adb logcat -d -b crash > screens/crash.txt
if rg -q "Process: ru.kurspocheku," screens/crash.txt; then cat screens/crash.txt; exit 1; fi
