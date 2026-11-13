#!/usr/bin/env python3
"""Проверка промокода через настоящий UI на CI-эмуляторе с debug APK."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = 'ru.kurspocheku'
ACT = f'{PKG}/app.dirhamrub.MainActivity'


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True)


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/pro-ui.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/pro-ui.xml')).iter('node')


def find(predicate):
    for _ in range(8):
        node = next((n for n in nodes() if predicate(n)), None)
        if node is not None:
            return node
        time.sleep(1)
    raise AssertionError('Ожидаемый элемент Pro не появился')


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    assert x2 > x1 and y2 > y1, 'Элемент находится вне экрана'
    adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))


def launch(demo=False):
    adb('shell', 'am', 'force-stop', PKG)
    args = ['shell', 'am', 'start', '-W', '-n', ACT, '--es', 'screen', 'pro']
    if demo:
        args += ['--ez', 'demo', 'true']
    adb(*args)
    time.sleep(3)


def activate():
    # Убираем IME: закреплённая кнопка должна остаться доступна.
    adb('shell', 'input', 'keyevent', '111')
    tap(find(lambda n: n.get('text') == 'Активировать Pro'))


launch(demo=True)
width, height = map(int, re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1])
# Поле под списком возможностей, прокручиваем только содержимое экрана.
adb('shell', 'input', 'swipe', str(width // 2), str(height * 3 // 4), str(width // 2), str(height // 3), '400')
field = lambda n: n.get('class') == 'android.widget.EditText'
tap(find(field))
adb('shell', 'input', 'text', 'WRONG')
activate()
find(lambda n: n.get('text', '').startswith('Неверный промокод.'))
assert not any('Pro активна' in n.get('text', '') for n in nodes()), 'Неверный код включил Pro'
tap(find(field))
adb('shell', 'input', 'keyevent', '123')
for _ in 'WRONG':
    adb('shell', 'input', 'keyevent', '67')
adb('shell', 'input', 'text', 'kimchi')
activate()
find(lambda n: 'Pro активна' in n.get('text', ''))
time.sleep(2)
launch()
find(lambda n: 'Pro активна' in n.get('text', ''))
assert not any(field(n) for n in nodes()), 'После перезапуска снова требуется код'
print('Pro: неверный код отклонён, общий код принят без учёта регистра, активация пережила перезапуск')
