#!/usr/bin/env python3
"""Реальные нажатия на CI-эмуляторе: завершение знакомства и выбор карты."""
import re
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = 'ru.kurspocheku'
ACT = f'{PKG}/app.dirhamrub.MainActivity'
OUT = Path('screens')


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=30)


def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/transitions.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/transitions.xml'))


def find(predicate):
    for _ in range(6):
        node = next((n for n in tree().iter('node') if predicate(n)), None)
        if node is not None:
            return node
        time.sleep(1)
    raise AssertionError('Ожидаемый элемент интерфейса не появился')


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    assert x2 > x1 and y2 > y1
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def capture(name):
    with (OUT / f'{name}.png').open('wb') as output:
        subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=output, check=True, timeout=30)


def motion(enabled):
    for setting in ['animator_duration_scale', 'transition_animation_scale', 'window_animation_scale']:
        adb('shell', 'settings', 'put', 'global', setting, '1' if enabled else '0')


def launch(onboarding=False, demo=True):
    adb('shell', 'am', 'force-stop', PKG)
    args = ['shell', 'am', 'start', '-W', '-n', ACT]
    if demo:
        args += ['--ez', 'demo', 'true']
    if onboarding:
        args += ['--es', 'screen', 'onboarding', '--ei', 'step', '2', '--ez', 'nocards', 'true']
    adb(*args)
    time.sleep(3)


def card_node(name):
    return find(lambda n: name in n.get('content-desc', ''))


def assert_home():
    row = card_node('Карта RUB')
    assert row.get('selected') == 'true' or row.get('checked') == 'true', row.attrib
    assert not any('Настройте под поездку' in n.get('text', '') for n in tree().iter('node'))


try:
    motion(True)
    for theme, before, middle, after in [
        ('yes', '25-onboarding-before-dark', '26-welcome-mid-dark', '27-welcome-after-dark'),
        ('no', '28-onboarding-before-light', '29-welcome-mid-light', '30-welcome-after-light'),
    ]:
        adb('shell', 'cmd', 'uimode', 'night', theme)
        launch(onboarding=True)
        button = find(lambda n: n.get('text') == 'Продолжить без чека')
        capture(before)
        tap(button)
        time.sleep(0.10)
        capture(middle)
        time.sleep(1)
        assert_home()
        capture(after)
        time.sleep(1)
        launch(demo=False)
        assert_home()

    motion(False)
    launch(onboarding=True)
    tap(find(lambda n: n.get('text') == 'Продолжить без чека'))
    time.sleep(0.3)
    assert_home()
    capture('31-welcome-reduced')

    motion(True)
    launch()
    capture('32-card-before')
    tap(card_node('Карта в юанях'))
    time.sleep(0.08)
    capture('33-card-mid')
    time.sleep(1)
    chosen = card_node('Карта в юанях')
    assert chosen.get('selected') == 'true' or chosen.get('checked') == 'true', chosen.attrib
    capture('34-card-after')
    print('Переход проверен в двух темах, без анимаций и после перезапуска; выбор карты подтверждён.')
except Exception:
    (OUT / 'transition-error.txt').write_text(traceback.format_exc(), encoding='utf-8')
    try:
        capture('transition-failure')
        (OUT / 'transition-ui.xml').write_text(ET.tostring(tree(), encoding='unicode'), encoding='utf-8')
    except Exception:
        pass
    raise
finally:
    motion(False)
