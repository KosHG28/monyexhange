#!/usr/bin/env python3
"""Real keypad input; assert static groups are skipped. Decode receipts without opening camera."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT=Path('screens')
PKG='ru.kurspocheku'

def adb(*args):
    return subprocess.check_output(['adb',*args],text=True,timeout=30)

def tree():
    adb('shell','uiautomator','dump','/sdcard/recomposition.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/recomposition.xml'))

def logs():
    return adb('logcat','-d','-b','main','-s','KpcCompose:I','KpcImages:I','*:S')

def counts(text):
    return {name:int(count) for name,count in re.findall(r'\b(Header|CbrLine|Keypad|PayIcon|PriceField)=(\d+)',text)}

adb('shell','am','force-stop',PKG)
adb('logcat','-b','main','-c')
adb('shell','am','start','-W','-n',PKG+'/app.dirhamrub.MainActivity',
    '--ez','demo','true','--ez','traceRecompositions','true','--ez','checkImageMemory','true',
    '--es','amount','123','--es','spend','EUR','--es','card','demo-cny')
for _ in range(30):
    text=logs()
    if 'KpcImages' in text and 'PASS limits' in text:break
    if 'FAIL decoder' in text:raise AssertionError(text)
    time.sleep(1)
else:raise AssertionError('Native image decoder check did not complete: '+text)
time.sleep(1)
root=tree()
coords={}
for node in root.iter('node'):
    desc=node.get('content-desc','')
    if desc in ['Очистить','7','8','9','Удалить последнюю цифру']:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds','')))
        coords[desc]=((x1+x2)//2,(y1+y2)//2)
assert {'Очистить','7','8','9'} <= coords.keys(),coords
before=counts(logs())
assert set(before)=={'Header','CbrLine','Keypad','PayIcon','PriceField'},before
# Clear between short amounts to keep the existing amount-sizing algorithm and layout unchanged.
for _ in range(3):
    for key in ['Очистить','7','8','9']:
        x,y=coords[key]
        adb('shell','input','tap',str(x),str(y))
time.sleep(1)
after_text=logs()
after=counts(after_text)
(OUT/'recomposition-check.txt').write_text('before='+str(before)+'\nafter='+str(after)+'\n'+after_text)
assert all(after[k]==before[k] for k in ['Header','CbrLine','Keypad','PayIcon']), f'Static groups recomposed during amount input: {before} -> {after}'
assert after['PriceField']>before['PriceField'], 'Dynamic input field did not recompose'
assert any(n.get('content-desc','').startswith('Цена на ценнике: 789 EUR') for n in tree().iter('node'))
print('Static groups skipped all 12 key events:',before,after,flush=True)
print('Native receipt image limits/recycling passed; no camera opened.',flush=True)
adb('shell','am','force-stop',PKG)
