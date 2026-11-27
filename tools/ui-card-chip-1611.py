"""Review monochrome card chips and screen-scoped sensor use on Android."""
import re
import subprocess
import time
from pathlib import Path

from ui_tree import read_tree

PACKAGE = "ru.kurspocheku"


def adb(*args):
    if args[:3] == ("shell", "uiautomator", "dump"):
        args = ("shell", "timeout", "12", *args[1:])
    return subprocess.check_output(["adb", *args], text=True, timeout=35)


def start(screen, *extras, seed=True):
    adb("shell", "am", "force-stop", PACKAGE)
    demo = ["--ez", "demo", "true"] if seed else []
    print(adb(
        "shell", "am", "start", "-W", "-n", f"{PACKAGE}/app.dirhamrub.MainActivity",
        "--es", "screen", screen, *demo, *extras,
    ), flush=True)


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))


def labels(node):
    return tuple(re.sub(r"\s+", " ", node.get(key, "")) for key in ("text", "content-desc"))


def nodes():
    root = read_tree(adb, "/sdcard/kpc-chip-1611.xml")
    return [node for node in root.iter("node") if node.get("package") == PACKAGE]


def scroll_down(current):
    for node in current:
        if node.get("class") != "android.widget.ScrollView":
            continue
        left, top, right, bottom = bounds(node)
        if bottom - top > 240:
            x = str((left + right) // 2)
            end = top + (bottom - top) // 3
            adb("shell", "input", "swipe", x, str(bottom - 45), x, str(end), "580")
            return


def find(label, *, prefix=False, scroll=False, selected=False):
    for _ in range(7):
        current = nodes()
        for node in current:
            if selected and "true" not in (node.get("checked"), node.get("selected")):
                continue
            # Compose can expose the text as a child of the selectable control.
            names = (value for child in node.iter("node") for value in labels(child)) if selected else labels(node)
            if any(value.startswith(label) if prefix else value == label for value in names):
                left, top, right, bottom = bounds(node)
                if left < right and top < bottom:
                    return node
        if scroll:
            scroll_down(current)
    raise AssertionError(f"{label!r} missing. Visible labels: {[pair for node in current if any(pair := labels(node))]}")


def tap(label, *, scroll=False, prefix=False):
    left, top, right, bottom = bounds(find(label, scroll=scroll, prefix=prefix))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(0.75)


def capture(path):
    data = subprocess.check_output(["adb", "exec-out", "screencap", "-p"], timeout=20)
    assert data.startswith(b"\x89PNG\r\n\x1a\n"), f"Invalid screencap: {path}"
    path.write_bytes(data)


def center(node):
    left, top, right, bottom = bounds(node)
    return str((left + right) // 2), str((top + bottom) // 2)


ACTIVITY = f"{PACKAGE}/app.dirhamrub.MainActivity"


def expect_sensors(expected, screen):
    for _ in range(10):
        dump = adb("shell", "dumpsys", "sensorservice")
        active = re.search(r"(?ms)^ *\d+ active connections[^\n]*\n(.*?)^ *\d+ direct connections", dump)
        assert active, "Android did not report active sensor connections"
        count = len(re.findall(rf"(?m)^\s*{re.escape(PACKAGE)}\s+\|\s*WakeLockRefCount", active[1]))
        if count == expected:
            print(f"{screen}: {count} active sensor connections", flush=True)
            return
        time.sleep(0.25)
    raise AssertionError(f"{screen}: expected {expected} active app sensor connections, got {count}")


def record_sheet(action):
    name = "reduced-motion-sheet-1611.mp4"
    remote = f"/sdcard/{name}"
    adb("shell", "rm", "-f", remote)
    recorder = subprocess.Popen(
        ["adb", "shell", "screenrecord", "--size", "540x1200", "--bit-rate", "2000000", "--time-limit", "4", remote],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
    )
    try:
        time.sleep(0.8)
        x, y = center(action)
        adb("shell", "input", "tap", x, y)
        time.sleep(1.5)
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
    finally:
        _, errors = recorder.communicate(timeout=13)
        assert recorder.returncode == 0, errors.decode(errors="replace")
    adb("pull", remote, str(out / name))
    assert (out / name).stat().st_size > 1000, "Reduced-motion transition recording is empty"


out = Path("screens")
adb("shell", "settings", "put", "global", "animator_duration_scale", "1")
adb("shell", "cmd", "uimode", "night", "yes")
start("main")
find("Итог:", prefix=True)
find("Карта в тенге", prefix=True)
expect_sensors(0, "Calculator")
capture(out / "mini-chip-1611.png")

tap("Настройки")
find("Карты и наличные")
expect_sensors(0, "Settings")
tap("Карты и наличные")
find("Карта в тенге")
expect_sensors(1, "Wallet")
capture(out / "wallet-chip-1611.png")

adb("shell", "input", "keyevent", "KEYCODE_HOME")
expect_sensors(0, "Background")
adb("shell", "am", "start", "-W", "--activity-single-top", "-n", ACTIVITY)
find("Карта в тенге")
expect_sensors(1, "Resumed wallet")

tap("Карта в тенге")
find("Карта")
expect_sensors(1, "Card editor replaces wallet listener")
tap("Все валюты", prefix=True, scroll=True)
find("Валюта, код или страна")
expect_sensors(0, "Currency picker over card editor")
adb("shell", "input", "keyevent", "KEYCODE_BACK")
find("Карта")
expect_sensors(1, "Card editor after closing currency picker")
adb("shell", "input", "keyevent", "KEYCODE_BACK")
find("Карты и наличные")
expect_sensors(1, "Wallet after closing card editor")
adb("shell", "input", "keyevent", "KEYCODE_BACK")
find("Обновлять курсы", scroll=True)
expect_sensors(0, "Settings after closing wallet")

adb("shell", "cmd", "uimode", "night", "no")
start("card-style", "--ez", "receiptDemo", "true")
find("Оформление карты")
tap("Стекло", scroll=True)
find("Стекло", selected=True)
expect_sensors(1, "Card appearance")
capture(out / "glass-chip-1611.png")

start("settings", seed=False)
tap("Без анимаций", scroll=True)
find("Без анимаций", selected=True)
start("money", seed=False)
find("Карты и наличные")
expect_sensors(0, "Wallet with animations disabled")
start("editor", "--es", "card", "demo-kzt", seed=False)
find("Карта")
expect_sensors(0, "Editor with animations disabled")

# Narrow width opens the Material rounding sheet. A long system scale makes
# a leaked Compose animation visible even in the emulator recording.
adb("shell", "wm", "density", "320")
adb("shell", "settings", "put", "global", "animator_duration_scale", "5")
start("settings", seed=False)
rounding = find("Округление итога", scroll=True)
record_sheet(rounding)
tap("Округление итога")
find("3 706,06 ₽")
expect_sensors(0, "Material sheet with animations disabled")
print("Screen changes, Activity pause/resume and reduced-motion sensor release verified.", flush=True)
