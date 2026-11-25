"""Verify payment ordering, live cash preview and the custom hue slider."""
import re
import subprocess
import time
from pathlib import Path

from ui_tree import read_tree

PACKAGE = "ru.kurspocheku"
ACTIVITY = f"{PACKAGE}/app.dirhamrub.MainActivity"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=35)


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))


def tree():
    return read_tree(adb, "/sdcard/kpc-wallet-155.xml")


def find(predicate):
    for _ in range(6):
        root = tree()
        for node in root.iter("node"):
            if node.get("package") == PACKAGE and predicate(node):
                left, top, right, bottom = bounds(node)
                if left < right and top < bottom:
                    return node
    for node in root.iter("node"):
        if node.get("package") == PACKAGE and (node.get("text") or node.get("content-desc")):
            print(node.attrib)
    raise AssertionError("Expected control missing")


def text(label):
    return lambda node: node.get("text") == label or node.get("content-desc") == label


def labeled(node, android_class, label):
    return node.get("class") == android_class and any(text(label)(child) for child in node.iter("node"))


def center(node, x=0.5):
    left, top, right, bottom = bounds(node)
    return str(round(left + (right - left) * x)), str((top + bottom) // 2)


def tap(node, x=0.5):
    adb("shell", "input", "tap", *center(node, x))


def start(screen, *extras, demo=True):
    adb("shell", "am", "force-stop", PACKAGE)
    seed = ["--ez", "demo", "true"] if demo else []
    print(adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--es", "screen", screen, *seed, *extras), flush=True)


def capture(name):
    with (Path("screens") / name).open("wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=output, timeout=30)


def payment(label):
    return find(lambda node: node.get("content-desc", "").startswith(label))


adb("shell", "cmd", "uimode", "night", "yes")
adb("shell", "settings", "put", "global", "animator_duration_scale", "1")
start("main", "--es", "spend", "VND")
names = ["Карта в юанях", "Карта в тенге"]
first, last = sorted(names, key=lambda label: bounds(payment(label))[1])
adb("shell", "input", "draganddrop", *center(payment(last)), *center(payment(first)), "600")
assert bounds(payment(last))[1] < bounds(payment(first))[1], "Dragged card did not move up"
start("main", demo=False)
assert bounds(payment(last))[1] < bounds(payment(first))[1], "Manual order did not survive restart"
print("Long press and drag reordered two cards. Order survived a restart.", flush=True)

start("editor", "--es", "card", "new", "--es", "spend", "VND")
find(text("Новая карта"))
tap(find(text("Наличные")))
find(text("Новые наличные"))
find(text("Наличные VND"))
start("editor", "--es", "card", "demo-aed-cash", "--es", "spend", "VND")
find(text("Наличные"))
assert not any(text("Карта")(node) for node in tree().iter("node")), "Existing record still offers a kind switch"
received = find(lambda node: labeled(node, "android.widget.EditText", "Получил"))
tap(received)
adb("shell", "input", "keyevent", "KEYCODE_MOVE_END", *(["KEYCODE_DEL"] * 10))
adb("shell", "input", "text", "1000")
find(lambda node: node.get("class") == "android.widget.EditText" and re.sub(r"\s+", "", node.get("text", "")) == "1000")
if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
left, top, right, bottom = bounds(find(lambda node: node.get("class") == "android.widget.ScrollView"))
x = str((left + right) // 2)
adb("shell", "input", "swipe", x, str(top + (bottom - top) // 5), x, str(bottom - 10), "320")
find(text("Наличные"))
capture("wallet-155-cash.png")
print("New cash name follows its kind. Existing cash has a fixed kind and the receipt was edited before saving.", flush=True)

start("pro", "--ez", "receiptDemo", "true")
tap(find(text("Стиль карт")))
find(text("Оформление карты"))
apply_button = lambda node: node.get("content-desc") == "Применить"
tap(find(apply_button))
time.sleep(1.1)
find(apply_button)
tap(find(text("Свой цвет")))
tap(find(apply_button))
time.sleep(1.1)
slider = find(text("Оттенок"))
hex_field = lambda node: node.get("class") == "android.widget.EditText" and node.get("text", "").startswith("#")
original_hex = find(hex_field).get("text")
adb("shell", "input", "swipe", *center(slider, 0.49), *center(slider, 0.96), "480")
find(lambda node: hex_field(node) and node.get("text") != original_hex)
apply = find(apply_button)
capture("wallet-155-hue.png")
tap(apply)
find(text("Курс по чеку"))
print("Changing hue enabled Apply. Two screenshots of changed screens.")

amoled = lambda node: node.get("checkable") == "true" and any(text("AMOLED")(child) for child in node.iter("node"))
tap(find(amoled))
find(lambda node: amoled(node) and (node.get("checked") == "true" or node.get("selected") == "true"))
start("main", demo=False)
tap(find(text("Запятая")))
tap(find(text("5")))
row = payment("Карта в юанях")
with subprocess.Popen(["adb", "shell", "input", "swipe", *center(row), *center(row), "2400"]) as hold:
    time.sleep(1.0)
    capture("polish-156-grab.png")
    assert hold.wait(timeout=12) == 0

start("settings", demo=False)
find(lambda node: text("Обновить сейчас")(node) or text("Обновляем…")(node))
capture("polish-156-settings.png")
