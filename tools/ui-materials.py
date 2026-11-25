"""Review the four card materials and the card preview and settings on Android."""
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
    root = read_tree(adb, "/sdcard/kpc-materials.xml")
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


out = Path("screens")
adb("shell", "cmd", "uimode", "night", "no")
start("card-style", "--ez", "receiptDemo", "true")
find("Оформление карты")
tap("Изумруд", scroll=True)
with TemporaryDirectory() as temp:
    frames = []
    for index, label in enumerate(("Сатин", "Металл", "Карбон", "Стекло")):
        tap(label, scroll=True)
        find(label, selected=True)
        frame = Path(temp) / f"{index}.png"
        capture(frame)
        frames.extend(["-i", str(frame)])
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", *frames, "-filter_complex", "hstack=inputs=4",
         "-frames:v", "1", "-threads", "1", "-update", "1", str(out / "materials-1592.png")],
        check=True,
        timeout=30,
    )

tap("Карбон")
find("Карбон", selected=True)
tap("Применить")
find("Итог:", prefix=True)
adb("shell", "cmd", "uimode", "night", "yes")
start("main", seed=False)
card = find("Карта в тенге", prefix=True)
if card.get("selected") != "true":
    tap("Карта в тенге", prefix=True)
find("Карта в тенге", prefix=True, selected=True)
find("Итог:", prefix=True)
capture(out / "material-calculator-1592.png")

start("card-style", seed=False)
find("Карбон", scroll=True, selected=True)
find("Изумруд", selected=True)
print("The material and color stay selected after restarting the app.", flush=True)


start("settings", seed=False)
find("Настройки")
assert not any(
    label in ("Обновить сейчас", "Обновляем…") and bounds(node)[1] < bounds(node)[3]
    for node in nodes() for label in labels(node)
), "The refresh button must be off-screen at the top of Settings."
refresh = find("Обновить сейчас", scroll=True)
footer = find("Сеть — только для курсов", prefix=True)
assert bounds(refresh)[1] >= bounds(footer)[3], "The refresh button must appear below the network note."
capture(out / "settings-1592.png")
print("The refresh button scrolls into view below the network note.", flush=True)
