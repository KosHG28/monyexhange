"""Check the currency heading and card palette. Capture only the card-style screen."""
import re
import subprocess
from pathlib import Path

from ui_tree import read_tree

PACKAGE = "ru.kurspocheku"
ACTIVITY = f"{PACKAGE}/app.dirhamrub.MainActivity"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=35)


def tree():
    return read_tree(adb, "/sdcard/kpc-reflection.xml")


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds", ""))))


def find(predicate):
    for _ in range(7):
        root = tree()
        for node in root.iter("node"):
            if node.get("package") == PACKAGE and predicate(node):
                left, top, right, bottom = bounds(node)
                if left < right and top < bottom:
                    return node
    for node in root.iter("node"):
        if node.get("package") == PACKAGE and (node.get("text") or node.get("content-desc")):
            print(node.get("text"), node.get("content-desc"), node.get("bounds"))
    raise AssertionError("Expected control missing")


def text(label):
    return lambda node: node.get("text") == label or node.get("content-desc") == label


def tap(node):
    left, top, right, bottom = bounds(node)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def swipe(start, end):
    left, top, right, bottom = bounds(find(lambda node: node.get("class") == "android.widget.ScrollView"))
    x = str((left + right) // 2)
    adb("shell", "input", "swipe", x, str(int(top + start * (bottom - top))),
        x, str(int(top + end * (bottom - top))), "330")


def start(screen, *extras):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--es", "screen", screen, "--ez", "demo", "true", *extras)


adb("shell", "cmd", "uimode", "night", "yes")
adb("shell", "settings", "put", "global", "animator_duration_scale", "1")
start("guide", "--es", "spend", "KRW")
expanded_top = bounds(find(text("Вона")))[1]
swipe(.78, .22)
find(lambda node: text("Вона")(node) and bounds(node)[1] < expanded_top)
swipe(.12, .92)
find(lambda node: text("Вона")(node) and bounds(node)[1] >= expanded_top)
print("Currency title collapses into the top bar and expands again.")

start("pro", "--ez", "receiptDemo", "true")
find(text("Курс по чеку"))
tap(find(text("Стиль карт")))
find(text("Оформление карты"))


def bordeaux(node):
    return node.get("checkable") == "true" and any(text("Бордо")(child) for child in node.iter("node"))


tap(find(bordeaux))
find(lambda node: bordeaux(node) and (node.get("checked") == "true" or node.get("selected") == "true"))
with (Path("screens") / "card-reflection-1541.png").open("wb") as output:
    subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=output, timeout=30)
print("Bordeaux selected in card appearance. One screenshot.")
