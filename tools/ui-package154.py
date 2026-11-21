"""Two screenshots of changed paths. CAMERA and country guide are not opened."""
import re
import subprocess
from pathlib import Path
from ui_tree import read_tree

PACKAGE = "ru.kurspocheku"
ACTIVITY = PACKAGE + "/app.dirhamrub.MainActivity"
OUT = Path("screens")


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=35)


def tree():
    return read_tree(adb, "/sdcard/kpc-package154.xml")


def find(predicate, retries=5):
    for _ in range(retries):
        root = tree()
        match = next((node for node in root.iter("node") if predicate(node)), None)
        if match is not None:
            return match
    for node in root.iter("node"):
        if node.get("package") == PACKAGE and (node.get("text") or node.get("content-desc")):
            print(node.get("class"), node.get("text"), node.get("content-desc"), node.get("bounds"))
    raise AssertionError("Expected control missing")


def contains(text):
    return lambda node: text in node.get("text", "") or text in node.get("content-desc", "")


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    assert x1 < x2 and y1 < y2, node.attrib
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def capture(name):
    with (OUT / name).open("wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=output, timeout=30)


def start(*extras):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--ez", "demo", "true", *extras)


adb("shell", "cmd", "uimode", "night", "yes")
start("--es", "spend", "VND", "--ez", "noamount", "true")
for digit in "125":
    tap(find(lambda node: node.get("content-desc") == digit))
tap(find(contains("Допишите три нуля")))
find(lambda node: "125000" in re.sub(r"\s+", "", node.get("content-desc", "")) and "VND" in node.get("content-desc", ""))
capture("keypad-154-vnd.png")

adb("shell", "cmd", "uimode", "night", "no")
start("--es", "screen", "editor", "--es", "card", "demo-kzt")
find(contains("Карта"))
menu = None
for _ in range(6):
    root = tree()
    menu = next((node for node in root.iter("node") if node.get("text") == "RUB ▾"), None)
    if menu is not None:
        break
    # Main content scrolls while the footer stays fixed.
    scroll = next((node for node in root.iter("node")
                   if node.get("class") == "android.widget.ScrollView" and node.get("package") == PACKAGE), None)
    assert scroll is not None, "No editor scroll viewport"
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", scroll.get("bounds")))
    x = (x1 + x2) // 2
    adb("shell", "input", "swipe", str(x), str(int(y1 + .8 * (y2 - y1))),
        str(x), str(int(y1 + .28 * (y2 - y1))), "320")
assert menu is not None, "No RUB currency control for the transfer"
tap(menu)
tap(find(contains("Все валюты ›")))
tap(find(contains("Валюта, код или страна")))
adb("shell", "input", "text", "JPY")
option = find(contains("Японская иена"))
capture("currency-154-jpy.png")
tap(option)
find(contains("JPY ▾"))
print("VND triple zero produced 125 000. Transfer currency JPY selected through the full search. Two screenshots.")
