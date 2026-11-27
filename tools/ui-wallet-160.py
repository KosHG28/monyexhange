"""Review the wallet, payment previews, calculator and rate controls on Android."""
import re
import subprocess
import time
from pathlib import Path
from tempfile import TemporaryDirectory

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
    root = read_tree(adb, "/sdcard/kpc-wallet-160.xml")
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


def check_wallet_order():
    first, second = sorted(("Карта в тенге", "Наличные THB"), key=lambda label: bounds(find(label))[1])
    for _ in range(2):
        source, target = find(second), find(first)
        if bounds(source)[1] < bounds(target)[1]:
            break
        adb("shell", "input", "draganddrop", *center(source), *center(target), "720")
        time.sleep(0.5)
    assert bounds(find(second))[1] < bounds(find(first))[1], "The card did not move in the wallet"
    start("money", seed=False)
    assert bounds(find(second))[1] < bounds(find(first))[1], "Wallet order was not saved"
    print("Reordered a card and cash in another currency. The order survived restart.", flush=True)


out = Path("screens")
adb("shell", "cmd", "uimode", "night", "no")
start("money")
find("Карта в юанях")
find("Наличные AED")
find("Наличные THB")
find("Добавить карту")
find("Добавить наличные")
capture(out / "wallet-160.png")
check_wallet_order()

with TemporaryDirectory() as temp:
    frames = []
    for card, title, preview in (
        ("demo-kzt", "Карта", "Карта в тенге"),
        ("demo-aed-cash", "Наличные", "Наличные AED"),
    ):
        start("editor", "--es", "card", card, seed=False)
        find(title)
        find(preview, prefix=True)
        frame = Path(temp) / f"{card}.png"
        capture(frame)
        frames.extend(["-i", str(frame)])
        if card == "demo-aed-cash":
            find("Получил", scroll=True)
            assert sum("Обмен" in labels(node) for node in nodes()) <= 1, "Cash exchange heading is duplicated"
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", *frames, "-filter_complex", "hstack=inputs=2",
         "-frames:v", "1", "-threads", "1", "-update", "1", str(out / "payment-previews-160.png")],
        check=True,
        timeout=30,
    )

start("money", seed=False)
tap("Добавить наличные")
find("Наличные")
find("Валюта наличных")
start("money", seed=False)
tap("Добавить карту")
find("Новая карта")
find("Валюта карты")
print("Both wallet tiles open the matching editor.", flush=True)

adb("shell", "cmd", "uimode", "night", "yes")
start("main", seed=False)
find("Наличные AED", prefix=True)
find("Итог:", prefix=True)
capture(out / "calculator-notes-160.png")

start("settings", seed=False)
find("Карты и наличные")
refresh = find("Обновить", scroll=True)
wifi = find("Только по Wi-Fi")
assert bounds(refresh)[1] >= bounds(wifi)[3], "Refresh must follow the Wi-Fi row"
capture(out / "settings-rates-160.png")

adb("shell", "settings", "put", "global", "animator_duration_scale", "1")
tap("Без анимаций", scroll=True)
find("Без анимаций", selected=True)
start("settings", seed=False)
find("Без анимаций", scroll=True, selected=True)
print("Refresh follows the Wi-Fi row. The selected motion mode survived restart.", flush=True)
