#!/usr/bin/env python3
"""PRO only: activation, settings, launcher actions, two themes and a compact font-130 layout."""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
from ui_tree import read_tree

OUT=Path('screens');PKG='ru.kurspocheku';ACT=PKG+'/app.dirhamrub.MainActivity'
report=[]
def adb(*args):return subprocess.check_output(['adb',*args],text=True,timeout=45)
def tree():return read_tree(adb,'/sdcard/pro-ui.xml')
def size():return tuple(map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size'))[-1]))
def swipe(down):
    w,h=size()
    adb('shell','input','swipe',str(w//2),str(int(h*(.78 if down else .3))),str(w//2),str(int(h*(.3 if down else .78))),'300')
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
def capture(name):
    # Never capture the launcher/system widget dialog or any other application screen.
    find(text('Курс по чеку')) if '-top-' in name or '-wallpaper-' in name else find(text('Pro активна') if 'locked' not in name else text('Активировать Pro'))
    with (OUT/(name+'.png')).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True,timeout=30)

try:
    for theme,night in [('light','no'),('dark','yes')]:
        adb('shell','am','force-stop',PKG)
        adb('shell','settings','put','system','font_scale','1.0')
        adb('shell','wm','size','reset');size_cached=size()
        adb('shell','cmd','uimode','night',night)
        adb('shell','am','start','-W','-n',ACT,'--ez','demo','true','--es','screen','pro')
        find(text('Курс по чеку'))
        capture('pro-locked-top-'+theme)
        edge(True);capture('pro-locked-bottom-'+theme)
        field=find(lambda n:n.get('class')=='android.widget.EditText',scroll=True)
        tap(field);adb('shell','input','text','KIMCHI')
        tap(find(lambda n:'Активировать Pro' in n.get('text','') and n.get('enabled')=='true',scroll=True))
        find(text('Pro активна'),scroll=True)
        edge(False)
        for label in ['Золотой','Классический','Тёмный']:
            tap(find(lambda n:n.get('content-desc')==label,scroll=True))
            assert chosen(find(lambda n:n.get('content-desc')==label))
        edge(False);capture('pro-active-top-'+theme)
        edge(True);capture('pro-active-bottom-'+theme)
        edge(False)
        switch=find(lambda n:n.get('content-desc')=='Цвета из обоев',scroll=True)
        assert switch.get('enabled')=='true' and not chosen(switch)
        tap(switch);assert chosen(find(lambda n:n.get('content-desc')=='Цвета из обоев'))
        edge(False);capture('pro-wallpaper-'+theme)
        tap(find(lambda n:n.get('content-desc')=='Цвета из обоев',scroll=True))
        assert not chosen(find(lambda n:n.get('content-desc')=='Цвета из обоев'))
        edge(False)
        tap(find(text('Быстрые действия')))
        find(text('Зажмите значок приложения'))
        adb('shell','input','keyevent','4');find(text('Курс по чеку'))
        tap(find(text('Виджет на рабочем столе')))
        for _ in range(8):
            root=tree()
            native=any(n.get('package','').startswith('com.android.launcher') for n in root.iter('node'))
            fallback=any('Зажмите свободное место' in n.get('text','') for n in root.iter('node'))
            if native or fallback:break
        assert native or fallback,'Neither widget pin confirmation nor launcher help opened'
        report.append(theme+': KIMCHI activation, 3 icon selections, wallpaper on/off, shortcuts help; widget '+('system confirmation' if native else 'manual help'))
        adb('shell','input','keyevent','4');find(text('Курс по чеку'))
        # Compact display and larger system text: still only PRO is photographed.
        adb('shell','settings','put','system','font_scale','1.3')
        adb('shell','wm','size','960x2140');size_cached=size()
        find(text('Курс по чеку'));edge(False);capture('pro-large-top-'+theme)
        edge(True);capture('pro-large-bottom-'+theme)
        # Reopen ordinary saved state; activation, icon and switch must persist.
        adb('shell','am','force-stop',PKG)
        adb('shell','settings','put','system','font_scale','1.0')
        adb('shell','wm','size','reset');size_cached=size()
        adb('shell','am','start','-W','-n',ACT,'--es','screen','pro')
        find(lambda n:n.get('content-desc')=='Тёмный' and chosen(n),scroll=True)
        assert not chosen(find(lambda n:n.get('content-desc')=='Цвета из обоев'))
        find(text('Pro активна'),scroll=True)
        report.append(theme+': compact display/font 130 and persisted PRO/style settings passed')
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
