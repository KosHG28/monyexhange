#!/usr/bin/env python3
"""20 real editor/receipt/settings cycles; changed screens only, no guide or camera."""
from pathlib import Path
import re,subprocess,time,xml.etree.ElementTree as ET
from ui_tree import read_tree

OUT=Path('screens');PKG='ru.kurspocheku';ACT=PKG+'/app.dirhamrub.MainActivity'

def adb(*args,timeout=45):return subprocess.check_output(['adb',*args],text=True,timeout=timeout)
def tree():
    return read_tree(adb, '/sdcard/long-session.xml')

def find(predicate,scroll=False):
    for attempt in range(10 if scroll else 5):
        root=tree()
        for node in root.iter('node'):
            if predicate(node):
                b=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
                if len(b)==4 and b[2]>b[0] and b[3]>b[1] and (not scroll or 280<b[1]<b[3]<2050):return node
        if scroll:adb('shell','input','swipe','540','1600','540','1250','260')
        else:time.sleep(.3)
    (OUT/'long-session-error.xml').write_text(ET.tostring(root,encoding='unicode'))
    raise AssertionError('Long-session UI not found')
def tap(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds','')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def capture(name):
    with (OUT/(name+'.png')).open('wb') as f:subprocess.run(['adb','exec-out','screencap','-p'],stdout=f,check=True)
def main():find(lambda n:n.get('content-desc')=='9')
def checkpoint(round):
    # Keep the Activity alive but background it, then release renderer caches BEFORE GC.
    # Foreground HWUI is allowed to cache a recent image; that alone is not an app leak.
    adb('shell','input','keyevent','3')
    tree() # Wait for the launcher; Activity remains alive in the background.
    adb('shell','am','broadcast','-n',PKG+'/app.dirhamrub.diag.MemoryAuditReceiver')
    adb('shell','am','send-trim-memory',PKG,'BACKGROUND')
    # Activity's callback precedes HWUI trim completion; a launcher snapshot waits for UI idle.
    tree()
    adb('shell','am','dumpheap','-g',PKG,'/data/local/tmp/kpc-session.hprof',timeout=60)
    prior=adb('logcat','-d','-s','KpcAudit:I','*:S').count('aliveReceipts=')
    adb('shell','am','broadcast','-n',PKG+'/app.dirhamrub.diag.MemoryAuditReceiver')
    for _ in range(40):
        log=adb('logcat','-d','-s','KpcAudit:I','*:S')
        values=re.findall(r'aliveReceipts=(\d+) javaBytes=(\d+) nativeBytes=(\d+)',log)
        if len(values)>prior:break
        time.sleep(.25)
    else:raise AssertionError('No fresh memory checkpoint: '+log)
    adb('shell','rm','-f','/data/local/tmp/kpc-session.hprof')
    alive,java,native=map(int,values[-1])
    report.append((round,alive,java,native))
    (OUT/'long-session-memory.txt').write_text('\n'.join(map(str,report))+'\nRaw trim/GC observations:\n'+log)
    assert alive==0,f'Receipt bitmap retained after closed screens and GC: {report[-1]}'
    (OUT/f'long-session-meminfo-{round}.txt').write_text(adb('shell','dumpsys','meminfo',PKG))
    adb('shell','am','start','-W','-n',ACT)
    main()

report=[]
try:
    adb('shell','am','force-stop',PKG)
    adb('logcat','-b','main','-c')
    adb('shell','am','start','-W','-n',ACT,'--ez','demo','true','--ez','receiptDemo','true','--es','spend','EUR','--es','amount','789','--es','card','demo-cny')
    main()
    adb('shell','dumpsys','gfxinfo',PKG,'reset')
    for _ in range(3):
        tap(find(lambda n:'Карта в юанях' in n.get('content-desc','') and (n.get('selected')=='true' or n.get('checked')=='true')))
        time.sleep(.1)
        adb('shell','input','keyevent','4')
        main()
    print('Rapid editor open/back cycles: 3 passed',flush=True)

    for round in range(1,21):
        if round in (1,11):
            adb('shell','cmd','uimode','night','no' if round==1 else 'yes')
            main()
        tap(find(lambda n:'Карта в юанях' in n.get('content-desc','') and (n.get('selected')=='true' or n.get('checked')=='true')))
        find(lambda n:n.get('text')=='Сохранить')
        theme='light' if round<=10 else 'dark'
        if round in (1,11):capture('editor-audit-'+theme)
        tap(find(lambda n:n.get('content-desc')=='Скриншот банка для этого чека',scroll=True))
        find(lambda n:n.get('content-desc')=='Закрыть скриншот')
        find(lambda n:n.get('content-desc')=='Скриншот банка для этого чека' and len(re.findall(r'\d+',n.get('bounds','')))==4 and int(re.findall(r'\d+',n.get('bounds',''))[3])-int(re.findall(r'\d+',n.get('bounds',''))[1])>500)
        if round in (1,11):capture('receipt-audit-'+theme)
        adb('shell','input','keyevent','4')
        find(lambda n:n.get('text')=='Сохранить')
        adb('shell','input','keyevent','4')
        main()
        tap(find(lambda n:n.get('content-desc')=='Настройки'))
        find(lambda n:n.get('text')=='Настройки')
        adb('shell','input','keyevent','4')
        main()
        if round in (5,10,15,20):checkpoint(round)
        print('Editor, receipt, settings cycle',round,flush=True)
    warm=report[-2];last=report[-1]
    assert last[2] <= warm[2]+8*1024*1024,report
    assert last[3] <= warm[3]+8*1024*1024,report
    (OUT/'long-session-frames.txt').write_text(adb('shell','dumpsys','gfxinfo',PKG,'framestats'))
except Exception:
    capture('audit-error')
    raise
finally:adb('shell','am','force-stop',PKG)
