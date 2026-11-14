#!/usr/bin/env python3
"""Only the calculator: cold startup, immediate input and restored session in both themes."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = Path('screens')
PKG = 'ru.kurspocheku'
ACT = PKG+'/app.dirhamrub.MainActivity'
report = []

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=30)

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/startup-ui.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/startup-ui.xml'))

def wait_for(predicate):
    for _ in range(8):
        root = tree()
        for node in root.iter('node'):
            if predicate(node): return node
        time.sleep(0.5)
    (OUT/'startup-error-ui.xml').write_text(ET.tostring(root,encoding='unicode'))
    raise AssertionError('Expected saved calculator state did not appear')

def tap(node):
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds','')))
    assert x2>x1 and y2>y1
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

def screenshot(name):
    with (OUT/(name+'.png')).open('wb') as f:
        subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True,timeout=30)

def assert_session(amount):
    wait_for(lambda n:n.get('content-desc')=='9')
    wait_for(lambda n:'Карта в юанях' in n.get('content-desc','') and (n.get('selected')=='true' or n.get('checked')=='true'))
    wait_for(lambda n: n.get('content-desc','').startswith('Цена на ценнике:') and amount in re.sub(r'\s+','',n.get('content-desc','')) and 'EUR' in n.get('content-desc',''))

OUT.mkdir(exist_ok=True)
try:
    for theme in ['no','yes']:
        name='light' if theme=='no' else 'dark'
        adb('shell','cmd','uimode','night',theme)
        adb('shell','am','force-stop',PKG)
        adb('shell','am','start','-W','-n',ACT,'--ez','demo','true','--es','spend','EUR','--es','amount','1234.56','--es','card','demo-cny')
        wait_for(lambda n:n.get('content-desc')=='9')
        adb('shell','am','force-stop',PKG)
        # Actual startup uses saved data, with no demo extras.
        result=adb('shell','am','start','-W','-n',ACT)
        report.append('cold-'+name+'\n'+result)
        assert_session('1234,56')
        screenshot('main-cold-'+name)
        tap(wait_for(lambda n:n.get('content-desc')=='Очистить'))
        for digit in ['7','8','9']:
            tap(wait_for(lambda n:n.get('content-desc')==digit))
        assert_session('789')
        screenshot('main-input-'+name)
        time.sleep(1)
        adb('shell','am','force-stop',PKG)
        result=adb('shell','am','start','-W','-n',ACT)
        report.append('restored-'+name+'\n'+result)
        assert_session('789')
        screenshot('main-restored-'+name)
    assert len(list(OUT.glob('main-*.png')))==6
    (OUT/'startup-check.txt').write_text('\n'.join(report))
except Exception:
    screenshot('main-error')
    raise
finally:
    adb('shell','am','force-stop',PKG)
