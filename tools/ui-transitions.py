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


def assert_home(amount=None):
    row = card_node('Карта RUB')
    assert row.get('selected') == 'true' or row.get('checked') == 'true', row.attrib
    assert not any('Настройте под поездку' in n.get('text', '') for n in tree().iter('node'))
    if amount:
        find(lambda n: amount in re.sub(r'\s+', '', n.get('content-desc', '')) and 'AED' in n.get('content-desc', ''))


def video_frame(video, name, last=False):
    seek = ['-sseof', '-0.2'] if last else ['-ss', '0.9']
    subprocess.run(['ffmpeg', '-v', 'error', '-y', *seek, '-i', str(OUT / video),
                    '-frames:v', '1', str(OUT / f'{name}.png')], check=True, timeout=30)


def finish_with_video(button, middle, after, theme, amount=None):
    name = f'welcome-{theme}.mp4'
    recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--size', '540x1200', '--bit-rate', '2000000',
                                 '--time-limit', '8', f'/sdcard/{name}'],
                                stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        time.sleep(0.5)
        tap(button)
        time.sleep(1)
        assert_home(amount)
    finally:
        _, errors = recorder.communicate(timeout=15)
        assert recorder.returncode == 0, errors.decode(errors='replace')
        adb('pull', f'/sdcard/{name}', str(OUT / name))
        assert (OUT / name).stat().st_size > 1000, 'Запись перехода пуста'
    # Одновременный screencap и screenrecord на программном GPU портил композицию слоёв.
    video_frame(name, middle)
    video_frame(name, after, last=True)
    assert_home(amount)


try:
    # Замедляем только CI-запись: на слабом эмуляторе нужны промежуточные кадры, в приложении скорость обычная.
    motion(True, scale=3)
    for theme, before, middle, after in [
        ('yes', '25-onboarding-before-dark', '26-welcome-mid-dark', '27-welcome-after-dark'),
        ('no', '28-onboarding-before-light', '29-welcome-mid-light', '30-welcome-after-light'),
    ]:
        adb('shell', 'cmd', 'uimode', 'night', theme)
        launch(onboarding=True)
        price = find(lambda n: n.get('class') == 'android.widget.EditText' and n.get('content-desc') == 'Цена')
        tap(price)
        adb('shell', 'input', 'text', '1234.56')
        adb('shell', 'input', 'keyevent', '4')
        time.sleep(1)
        button = find(lambda n: n.get('text') == 'Продолжить без чека')
        capture(before)
        finish_with_video(button, middle, after, 'dark' if theme == 'yes' else 'light', amount='1234,56')
        time.sleep(1)
        launch(demo=False)
        assert_home('1234,56')

    motion(False)
    launch(onboarding=True)
    tap(find(lambda n: n.get('text') == 'Продолжить без чека'))
    time.sleep(0.3)
    assert_home()
    capture('31-welcome-reduced')

    motion(True)
    for theme, prefix in [('no', '32-card-light'), ('yes', '35-card-dark')]:
        adb('shell', 'cmd', 'uimode', 'night', theme)
        launch()
        keypad_before = find(lambda n: n.get('content-desc') == '9').get('bounds')
        capture(prefix + '-before')
        tap(card_node('Карта в юанях'))
        time.sleep(0.08)
        capture(prefix + '-mid')
        time.sleep(1)
        chosen = card_node('Карта в юанях')
        assert chosen.get('selected') == 'true' or chosen.get('checked') == 'true', chosen.attrib
        assert find(lambda n: n.get('content-desc') == '9').get('bounds') == keypad_before, 'Numpad moved after selection'
        capture(prefix + '-after')
    print('Общие элементы записаны в обеих темах; цена перенесена и сохранена после перезапуска. Проверены reduced motion, выбор карты в двух темах и неподвижность numpad.')

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
