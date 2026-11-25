"""Verify saved material, editor transfer removal and the affected screens."""
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
    root = read_tree(adb, "/sdcard/kpc-polish-159.xml")
    return [node for node in root.iter("node") if node.get("package") == PACKAGE]


def scroll_down(current):
    for node in current:
        if node.get("class") != "android.widget.ScrollView":
            continue
        left, top, right, bottom = bounds(node)
        if bottom - top > 240:
            x = str((left + right) // 2)
            adb("shell", "input", "swipe", x, str(bottom - 45), x, str(bottom - 305), "580")
            return


def find(label, *, prefix=False, scroll=False):
    for _ in range(7):
        current = nodes()
        for node in current:
            if any(value.startswith(label) if prefix else value == label for value in labels(node)):
                left, top, right, bottom = bounds(node)
                if left < right and top < bottom:
                    return node
        if scroll:
            scroll_down(current)
    raise AssertionError(f"{label!r} missing. Visible labels: {[pair for node in current if any(pair := labels(node))]}")


def tap(label, *, scroll=False):
    left, top, right, bottom = bounds(find(label, scroll=scroll))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    time.sleep(0.75)


def capture(name):
    with (Path("screens") / f"{name}-159.png").open("wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=output, timeout=30)


adb("shell", "cmd", "uimode", "night", "no")
start("pro", "--ez", "receiptDemo", "true")
find("Стиль карт")
capture("pro")
tap("Стиль карт")
capture("materials")
find("Оформление карты")
tap("Графит", scroll=True)
tap("Карбон", scroll=True)
find("Фактура")
capture("materials")
tap("Применить")
find("Стиль карт")
tap("Стиль карт")
material = find("Карбон", scroll=True)
assert "true" in (material.get("checked"), material.get("selected")), material.attrib
print("The selected card material stayed selected after applying and reopening.", flush=True)

adb("shell", "cmd", "uimode", "night", "yes")
start("main", seed=False)
find("О стране", prefix=True)
find("Итог:", prefix=True)
capture("calculator")

start("editor", "--es", "card", "demo-cny", "--es", "spend", "JPY")
find("Удалить текущее пополнение", scroll=True)
find("CNY")
capture("editor")
tap("Удалить текущее пополнение")
find("Отправил")
tap("Удалить текущее пополнение")
find("+ Добавить пополнение")
tap("Сохранить")
find("Итог:", prefix=True)
start("editor", "--es", "card", "demo-cny", seed=False)
find("+ Добавить пополнение", scroll=True)
print("Removing the latest transfer exposed the previous one. Removing both stayed saved after restart.", flush=True)

start("settings")
auto = "Курсы для старых чеков загружаются автоматически"
find(auto, scroll=True)
assert find("На даты чеков").get("clickable") != "true"
capture("settings")
