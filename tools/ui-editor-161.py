"""Review the editor transition, wallet, calculator and rate sources on Android."""
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
    root = read_tree(adb, "/sdcard/kpc-editor-161.xml")
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


def record_kind_transition(card, cash):
    name = "editor-switch-161.mp4"
    remote = f"/sdcard/{name}"
    adb("shell", "rm", "-f", remote)
    process = subprocess.Popen(
        ["adb", "shell", "screenrecord", "--size", "540x1200", "--bit-rate", "2000000", "--time-limit", "5", remote],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
    )
    try:
        time.sleep(0.8)
        adb("shell", "input", "tap", *center(cash))
        time.sleep(1.2)
        adb("shell", "input", "tap", *center(card))
    finally:
        _, errors = process.communicate(timeout=13)
        assert process.returncode == 0, errors.decode(errors="replace")
    adb("pull", remote, str(out / name))
    assert (out / name).stat().st_size > 1000, "Preview transition recording is empty"


def no_text(label):
    assert all(label not in labels(node) for node in nodes()), label


out = Path("screens")
adb("shell", "settings", "put", "global", "animator_duration_scale", "1")
adb("shell", "cmd", "uimode", "night", "yes")
start("main")
find("Наличные AED", prefix=True)
find("Итог:", prefix=True)
add = find("Добавить карту или наличные")
assert not add.get("text"), "A filled wallet must use an icon instead of the large add row"
rate = find("Курс ЦБ:", prefix=True)
assert bounds(rate)[1] < int(center(add)[1]) < bounds(rate)[3], "The compact add action belongs in the rate row"
capture(out / "calculator-161.png")

tap("Добавить карту или наличные")
find("Новая карта")
card = find("✓ Карта")
cash = find("Наличные")
record_kind_transition(card, cash)
find("Новая карта")
tap("Наличные")
find("Новые наличные")
find("✓ Наличные", selected=True)
assert abs(bounds(find("Карта"))[1] - bounds(card)[1]) <= 1, "Switching carrier changed the height of the preview"
tap("Банкомат", scroll=True)
find("✓ Банкомат", selected=True)
find("Сняли", scroll=True)
find("Списали с карты")
no_text("Снятие в банкомате")
capture(out / "cash-atm-161.png")
print("Both new-carrier titles, fixed preview geometry and the ATM form verified.", flush=True)

start("editor", "--es", "card", "demo-cny", seed=False)
find("Карта")
find("UnionPay РФ")
find("КУРС КАРТЫ", scroll=True)
no_text("Валюта расчёта UnionPay")
start("editor", "--es", "card", "demo-aed-cash", seed=False)
find("Наличные")
no_text("Новые наличные")

start("money", seed=False)
find("Карта в тенге")
find("Наличные AED")
find("Наличные THB")
capture(out / "wallet-161.png")

adb("shell", "cmd", "uimode", "night", "no")
start("settings", seed=False)
sources = ("ЦБ РФ", "UnionPay", "Неделю назад", "На даты чеков")
positions = [bounds(find(label, scroll=True))[1] for label in sources]
assert positions == sorted(set(positions)), f"Incorrect source order: {positions}"
find("Чек")
find("Карты UnionPay и валюты без ЦБ")
no_text("Загружено:")
capture(out / "rate-sources-161.png")
tap("Без анимаций", scroll=True)
find("Без анимаций", selected=True)
start("editor", "--es", "card", "new", seed=False)
tap("Наличные")
find("Новые наличные")
print("Rate source order, enabled UnionPay and carrier switching in reduced-motion mode verified.", flush=True)

start("main", "--ez", "nocards", "true")
assert find("Добавить карту или наличные").get("text"), "An empty wallet needs the full add action"
print("The empty wallet keeps the onboarding add button.", flush=True)
