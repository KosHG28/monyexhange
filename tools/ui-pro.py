#!/usr/bin/env python3
"""Changed PRO, settings and card style screens only; camera and guide are excluded."""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
from ui_tree import read_tree

OUT=Path('screens');PKG='ru.kurspocheku';ACT=PKG+'/app.dirhamrub.MainActivity'
report=[]
viewport=None
def adb(*args):return subprocess.check_output(['adb',*args],text=True,timeout=45)
def tree():
    global viewport
    root=read_tree(adb,'/sdcard/pro-ui.xml')
    for n in root.iter('node'):
        if n.get('class')=='android.widget.ScrollView' and n.get('package')==PKG:
            viewport=tuple(map(int,re.findall(r'\d+',n.get('bounds',''))))
            break
    return root
def size():return tuple(map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size'))[-1]))
def swipe(down):
    if viewport is None:tree()
    left,top,right,bottom=viewport
    x=(left+right)//2;h=bottom-top
    adb('shell','input','swipe',str(x),str(int(top+h*(.8 if down else .2))),str(x),str(int(top+h*(.2 if down else .8))),'300')
def edge(down):
    for _ in range(3):swipe(down)
    tree()
def visible(n):
    b=list(map(int,re.findall(r'\d+',n.get('bounds',''))));w,h=size_cached
    return len(b)==4 and b[2]>b[0] and b[3]>b[1] and b[1]>40 and b[3]<h-40
def find(predicate,scroll=False):
    for _ in range(8):
        root=tree()
        for n in root.iter('node'):
            if predicate(n) and visible(n):return n
        if scroll:swipe(True)
    (OUT/'pro-error-ui.xml').write_text(ET.tostring(root,encoding='unicode'))
    raise AssertionError('Expected PRO control not found')
def tap(n):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds','')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def text(value):return lambda n:value in n.get('text','')
def chosen(n):return n.get('selected')=='true' or n.get('checked')=='true'
def control(label,scroll=False):
    # Android exports the checkable control and its label as parent/child nodes.
    return find(lambda n:n.get('checkable')=='true' and any(c.get('content-desc')==label for c in n.iter('node')),scroll)
def capture(name):
    root=tree()
    assert any(n.get('package')==PKG and n.get('text') in ['Курс по чеку','Оформление карты','Настройки'] for n in root.iter('node')), 'Only changed app screens may be captured'
    with (OUT/(name+'.png')).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True,timeout=30)
def switch(label,scroll=False):
    return find(lambda n:n.get('checkable')=='true' and any(c.get('text')==label or c.get('content-desc')==label for c in n.iter('node')),scroll)
def start(screen,demo=False):
    global viewport
    adb('shell','am','force-stop',PKG);viewport=None
    args=['shell','am','start','-W','-n',ACT,'--es','screen',screen]
    if demo:args+=['--ez','demo','true']
    adb(*args)
def assert_absent(*labels):
    root=tree()
    for label in labels:assert not any(label in n.get('text','') for n in root.iter('node')),label

def style_open():
    edge(False);tap(find(text('Стиль карт'),scroll=True));find(text('Оформление карты'))

try:
    for theme,night in [('light','no'),('dark','yes')]:
        adb('shell','settings','put','system','font_scale','1.0')
        adb('shell','wm','size','reset');size_cached=size()
        adb('shell','cmd','uimode','night',night)
        # Material You must work without activating PRO; removed PRO settings stay absent.
        start('settings',True);find(text('Настройки'))
        value=switch('Цвета из обоев',scroll=True)
        assert value.get('enabled')=='true' and not chosen(value)
        tap(value);assert chosen(switch('Цвета из обоев'))
        tap(switch('Цвета из обоев'));assert not chosen(switch('Цвета из обоев'))
        assert_absent('AMOLED','Значок приложения','Чек с оригиналом')
        capture('pro-settings-look-'+theme)
        start('pro');find(text('Курс по чеку'));capture('pro-locked-top-'+theme)
        assert_absent('Чек с оригиналом','Быстрые действия')
        edge(True);capture('pro-locked-bottom-'+theme)
        field=find(lambda n:n.get('class')=='android.widget.EditText',scroll=True)
        tap(field);adb('shell','input','text','KIMCHI')
        tap(find(lambda n:'Активировать Pro' in n.get('text','') and n.get('enabled')=='true',scroll=True))
        find(text('Pro активна'),scroll=True)
        edge(False)
        for label in ['Золото','Изумруд','Платина','Классика']:
            tap(control(label,scroll=True));assert chosen(control(label))
        edge(False);tap(switch('AMOLED',scroll=True));assert chosen(switch('AMOLED'))
        tap(switch('AMOLED'));assert not chosen(switch('AMOLED'))
        edge(False);capture('pro-active-top-'+theme)
        edge(True);capture('pro-active-bottom-'+theme)
        style_open()
        assert_absent('Банки')
        tap(control('Бордо',scroll=True));assert chosen(control('Бордо'))
        edge(False);capture('pro-styles-palette-'+theme)
        tap(find(text('Применить')));find(text('Курс по чеку'))
        style_open();assert chosen(control('Бордо',scroll=True))
        tap(control('Свой цвет',scroll=True))
        field=find(lambda n:n.get('class')=='android.widget.EditText',scroll=True)
        tap(field)
        adb('shell','input','keyevent','KEYCODE_MOVE_END')
        for _ in range(7):adb('shell','input','keyevent','KEYCODE_DEL')
        adb('shell','input','text','376AC4');adb('shell','input','keyevent','4')
        find(lambda n:n.get('class')=='android.widget.EditText' and '376AC4' in n.get('text',''))
        edge(False);capture('pro-styles-custom-'+theme)
        tap(find(text('Применить')));find(text('Курс по чеку'))
        # Reopen after process death, verify both appearance and PRO/icon settings persisted.
        start('pro');find(text('Курс по чеку'));assert chosen(control('Классика',scroll=True))
        style_open();assert chosen(control('Свой цвет',scroll=True));tap(control('Свой цвет'))
        find(lambda n:n.get('class')=='android.widget.EditText' and '376AC4' in n.get('text',''),scroll=True)
        adb('shell','input','keyevent','4');find(text('Курс по чеку'))
        edge(False);tap(find(text('Плитка в шторке')))
        for _ in range(8):
            root=tree()
            native=any(n.get('package')=='com.android.systemui' and n.get('class')=='android.widget.Button' for n in root.iter('node'))
            fallback=any('Откройте шторку быстрых настроек' in n.get('text','') for n in root.iter('node'))
            if native or fallback:break
        assert native or fallback,'Neither tile addition request nor manual help opened'
        adb('shell','input','keyevent','4');find(text('Курс по чеку'))
        # Do not add/click the tile or open the camera; check only its setup request.
        tap(find(text('Виджет на рабочем столе')))
        for _ in range(8):
            root=tree()
            native=any(n.get('package','').startswith('com.android.launcher') for n in root.iter('node'))
            fallback=any('Зажмите свободное место' in n.get('text','') for n in root.iter('node'))
            if native or fallback:break
        assert native or fallback,'Neither widget confirmation nor manual help opened'
        adb('shell','input','keyevent','4');find(text('Курс по чеку'))
        report.append(theme+': free Material You, KIMCHI, 4 icons, AMOLED, palette/custom color persistence; native tile/widget setup only; no camera/guide')
        adb('shell','settings','put','system','font_scale','1.3')
        adb('shell','wm','size','960x2140');size_cached=size()
        find(text('Курс по чеку'));edge(False);capture('pro-large-top-'+theme)
        edge(True);capture('pro-large-bottom-'+theme)
        style_open();capture('pro-styles-large-'+theme)
        report.append(theme+': compact display/font 130 PRO and card styles passed')
    (OUT/'pro-check.txt').write_text('\n'.join(report))
    print('\n'.join(report),flush=True)

except Exception:
    try:
        root=tree();(OUT/'pro-error-ui.xml').write_text(ET.tostring(root,encoding='unicode'))
        if any('Курс по чеку' in n.get('text','') and n.get('package')==PKG for n in root.iter('node')):
            with (OUT/'pro-error.png').open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
    finally:
        (OUT/'app-log.txt').write_text(adb('logcat','-d','-b','main','-b','crash','-v','threadtime'))
        (OUT/'activity-state.txt').write_text(adb('shell','dumpsys','activity','top'))
    raise
finally:
    adb('shell','am','force-stop',PKG)
    adb('shell','settings','put','system','font_scale','1.0')
    adb('shell','wm','size','reset')
