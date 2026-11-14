#!/usr/bin/env python3
"""Only the changed card editor: Android 11, normal/narrow width and enlarged font."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = 'ru.kurspocheku'
OUT = Path('screens')
LABELS = {'UnionPay РФ', 'UnionPay зарубежная', 'Visa/MC зарубежная', 'МИР'}

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=30)

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/network-chips.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/network-chips.xml'))

def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds', ''))))

OUT.mkdir(exist_ok=True)
report=[]
try:
    for theme in ['no', 'yes']:
        adb('shell', 'cmd', 'uimode', 'night', theme)
        for case, density, font, card in [
            ('normal', 420, '1.0', 'demo-kzt'),
            ('realme-width', 480, '1.0', 'demo-kzt'),
            ('realme-font130', 480, '1.3', 'demo-kzt'),
            ('realme-font150', 480, '1.5', 'demo-kzt'),
            ('realme-unselected', 480, '1.3', 'new'),
        ]:
            adb('shell', 'am', 'force-stop', PKG)
            adb('shell', 'wm', 'size', '1080x2408')
            adb('shell', 'wm', 'density', str(density))
            adb('shell', 'settings', 'put', 'system', 'font_scale', font)
            adb('shell', 'am', 'start', '-W', '-n', PKG+'/app.dirhamrub.MainActivity',
                '--ez', 'demo', 'true', '--es', 'screen', 'editor', '--es', 'card', card)
            time.sleep(5)
            nodes={}
            root=ET.Element("unavailable")
            for attempt in range(7):
                try:
                    root=tree()
                    nodes={n.get('text'):n for n in root.iter('node') if n.get('text') in LABELS}
                except (subprocess.CalledProcessError, ET.ParseError):
                    time.sleep(1)
                    continue
                print(case, attempt, {label:bounds(n) for label,n in nodes.items()}, flush=True)
                # The Text line is 20sp, not the 44dp clickable parent; scale with font size.
                if set(nodes)==LABELS and all(len(bounds(n))==4 and bounds(n)[3]-bounds(n)[1]>=18*float(font)*density/160 for n in nodes.values()):
                    break
                adb('shell', 'input', 'swipe', '540', '1700', '540', '1000', '450')
                time.sleep(1)
            if set(nodes)!=LABELS:
                (OUT/'network-chips-ui.xml').write_text(ET.tostring(root,encoding='unicode'))
                with (OUT/'editor-error.png').open('wb') as f:
                    subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True,timeout=30)
                raise AssertionError(f'Missing payment labels: {LABELS-set(nodes)}')
            name=f'editor-{case}-'+('dark' if theme=='yes' else 'light')
            with (OUT/(name+'.png')).open('wb') as f:
                subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True,timeout=30)
            (OUT/(name+'.xml')).write_text(ET.tostring(root,encoding='unicode'))
            report.append(name+' '+str({label:bounds(n) for label,n in nodes.items()}))
            print('Screenshot:',name)
    assert len(list(OUT.glob('editor-*.png')))==10
    (OUT/'network-chips.txt').write_text('\n'.join(report))
finally:
    adb('shell','am','force-stop',PKG)
    adb('shell','wm','size','reset')
    adb('shell','wm','density','reset')
    adb('shell','settings','put','system','font_scale','1.0')
