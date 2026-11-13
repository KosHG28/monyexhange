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


def motion(enabled, scale=1):
    for setting in ['animator_duration_scale', 'transition_animation_scale', 'window_animation_scale']:
        adb('shell', 'settings', 'put', 'global', setting, str(scale) if enabled else '0')


def launch(onboarding=False, demo=True, screen=None, amount=None, spend=None):
    adb('shell', 'am', 'force-stop', PKG)
    args = ['shell', 'am', 'start', '-W', '-n', ACT]
    if demo:
        args += ['--ez', 'demo', 'true']
    if onboarding:
        args += ['--es', 'screen', 'onboarding', '--ei', 'step', '2', '--ez', 'nocards', 'true']
    if screen:
        args += ['--es', 'screen', screen]
    if amount is not None:
        args += ['--es', 'amount', amount]
    if spend:
        args += ['--es', 'spend', spend]
    adb(*args)
    time.sleep(3)


def card_node(name):
    return find(lambda n: name in n.get('content-desc', ''))


def assert_home():
    row = card_node('Карта RUB')
    assert row.get('selected') == 'true' or row.get('checked') == 'true', row.attrib
    assert not any('Настройте под поездку' in n.get('text', '') for n in tree().iter('node'))


def finish_with_video(button, middle, after, theme):
    name = f'welcome-{theme}.mp4'
    recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--size', '540x1200', '--bit-rate', '2000000',
                                 '--time-limit', '8', f'/sdcard/{name}'],
                                stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        time.sleep(0.5)
        tap(button)
        time.sleep(0.20)
        capture(middle)
        time.sleep(1)
        assert_home()
        capture(after)
    finally:
        _, errors = recorder.communicate(timeout=10)
        assert recorder.returncode == 0, errors.decode(errors='replace')
        adb('pull', f'/sdcard/{name}', str(OUT / name))
        assert (OUT / name).stat().st_size > 1000, 'Запись перехода пуста'


def search_hit_targets():
    density = int(re.findall(r'\d+', adb('shell', 'wm', 'density'))[-1]) / 160
    # Каждая попытка — без фокуса после нового запуска: лупа, верхний/нижний отступ и правый край.
    for point in ['icon', 'top', 'bottom', 'right']:
        launch(screen='currencies')
        field = find(lambda n: n.get('class') == 'android.widget.EditText' and n.get('package') == PKG)
        assert field.get('focused') == 'false', field.attrib
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', field.attrib['bounds']))
        assert y2-y1 >= 48*density-1, field.attrib
        positions = {
            'icon': (x1+int(24*density), (y1+y2)//2),
            'top': ((x1+x2)//2, y1+int(3*density)),
            'bottom': ((x1+x2)//2, y2-int(3*density)),
            'right': (x2-int(4*density), (y1+y2)//2),
        }
        x, y = positions[point]
        adb('shell', 'input', 'tap', str(x), str(y))
        find(lambda n: n.get('class') == 'android.widget.EditText' and n.get('package') == PKG and n.get('focused') == 'true')
        if point == 'icon':
            capture('40-search-icon-focus')
        if point == 'right':
            adb('shell', 'input', 'text', 'JPY')
            find(lambda n: n.get('class') == 'android.widget.EditText' and n.get('package') == PKG and n.get('text') == 'JPY')
            capture('41-search-query')
            tap(find(lambda n: n.get('content-desc') == 'Очистить поиск'))
            find(lambda n: n.get('class') == 'android.widget.EditText' and n.get('package') == PKG and 'JPY' not in n.get('text', ''))


def twelve_digits():
    launch(amount='0', spend='IRR')
    nine = find(lambda n: n.get('content-desc') == '9')
    for _ in range(13):
        tap(nine)
    field = find(lambda n: '999999999999' in re.sub(r'\s+', '', n.get('content-desc', '')) and 'IRR' in n.get('content-desc', ''))
    assert '9999999999999' not in re.sub(r'\s+', '', field.get('content-desc', '')), '13-я цифра принята'
    capture('42-irr-keypad-limit')
    time.sleep(1)
    launch(demo=False)
    find(lambda n: '999999999999' in re.sub(r'\s+', '', n.get('content-desc', '')) and 'IRR' in n.get('content-desc', ''))


try:
    # Замедляем только CI-запись: на слабом эмуляторе нужны промежуточные кадры, в приложении скорость обычная.
    motion(True, scale=3)
    for theme, before, middle, after in [
        ('yes', '25-onboarding-before-dark', '26-welcome-mid-dark', '27-welcome-after-dark'),
        ('no', '28-onboarding-before-light', '29-welcome-mid-light', '30-welcome-after-light'),
    ]:
        adb('shell', 'cmd', 'uimode', 'night', theme)
        launch(onboarding=True)
        button = find(lambda n: n.get('text') == 'Продолжить без чека')
        capture(before)
        finish_with_video(button, middle, after, 'dark' if theme == 'yes' else 'light')
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
    motion(False)
    search_hit_targets()
    twelve_digits()
    print('Поиск проверен по четырём краям, 12 цифр введены клавиатурой и сохранены. Переход проверен в двух темах, без анимаций и после перезапуска; выбор карты подтверждён.')
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
