"""Check changed payment and calculator controls on the release emulator."""
import re
import subprocess
from pathlib import Path

from ui_tree import read_tree

PACKAGE = "ru.kurspocheku"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, timeout=35)


def start(screen, *extras):
    adb("shell", "am", "force-stop", PACKAGE)
    print(adb(
        "shell", "am", "start", "-W", "-n", f"{PACKAGE}/app.dirhamrub.MainActivity",
        "--ez", "demo", "true", "--es", "screen", screen, *extras,
    ), flush=True)


def nodes():
    root = read_tree(adb, "/sdcard/kpc-polish-158.xml")
    return [node for node in root.iter("node") if node.get("package") == PACKAGE]


def labels(node):
    return tuple(re.sub(r"\s+", " ", node.get(key, "")) for key in ("text", "content-desc"))


def find(label, prefix=False):
    for _ in range(5):
        current = nodes()
        for node in current:
            if any(value.startswith(label) if prefix else value == label for value in labels(node)):
                return node
    raise AssertionError(f"{label!r} missing. Visible labels: {[pair for node in current if any(pair := labels(node))]}")


def tap(label):
    left, top, right, bottom = map(int, re.findall(r"\d+", find(label).get("bounds", "")))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def capture(name):
    with (Path("screens") / f"{name}-158.png").open("wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=output, timeout=30)


adb("shell", "cmd", "uimode", "night", "yes")
start("main", "--es", "spend", "VND", "--ez", "noamount", "true")
tap("5")
tap("Допишите два нуля")
find("Цена на ценнике: 500 VND")
tap("Допишите три нуля")
find("Цена на ценнике: 500 000 VND")
find("Итог:", prefix=True)
capture("calculator")
print("Both zero shortcuts append exact numbers of digits.", flush=True)

start("money", "--es", "spend", "AED")
find("Карты и наличные")
find("пополнение", prefix=True)
find("Наличные AED")
find("Чтобы изменить порядок", prefix=True)
assert not any("уточнить" in " ".join(labels(node)).lower() for node in nodes())
capture("wallet")
print("Wallet shows the date, cash thumbnail and ordering tip.", flush=True)

start("editor", "--es", "card", "demo-aed-cash", "--es", "spend", "AED")
find("Наличные")
find("Наличные AED", prefix=True)
capture("cash")

adb("shell", "cmd", "uimode", "night", "no")
start("pro", "--ez", "receiptDemo", "true")
active = find("Pro активна")
assert active.get("clickable") != "true", active.attrib
capture("pro")
print("Active PRO status has no click action.", flush=True)
