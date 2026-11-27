"""Verify that exchange and ATM drafts stay independent after saving cash."""
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
    root = read_tree(adb, "/sdcard/kpc-cash-funding-1612.xml")
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


def field(label, expected=None):
    for _ in range(6):
        current = nodes()
        for node in current:
            if node.get("class") != "android.widget.EditText":
                continue
            if not any(label in labels(child) for child in node.iter("node")):
                continue
            left, top, right, bottom = bounds(node)
            if right <= left or bottom <= top:
                continue
            value = re.sub(r"\s+", "", node.get("text", ""))
            if expected is None:
                return node
            assert value == expected, f"{label}: expected {expected!r}, got {value!r}"
            return node
        scroll_down(current)
    raise AssertionError(f"Missing editable field {label!r}")


def enter(label, digits):
    x, y = center(field(label, ""))
    adb("shell", "input", "tap", x, y)
    adb("shell", "input", "text", digits)
    if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
    field(label, digits)


def exchange_saved():
    find("✓ Обменник", selected=True, scroll=True)
    field("Отдал", "100")
    field("Получил", "15000")
    find("USD ▾")


def atm_empty():
    find("✓ Банкомат", selected=True, scroll=True)
    field("Сняли", "")
    field("Списали с карты", "")
    assert not any(value.startswith("✓ По этому чеку") for node in nodes() for value in labels(node))


def open_saved_cash(label):
    start("money", seed=False)
    tap(label)
    find("Наличные")


def record_preview_tabs():
    cash_switch = find("✓ Обменник", selected=True)
    atm_switch = find("Банкомат")
    remote = "/sdcard/cash-funding-1612.mp4"
    adb("shell", "rm", "-f", remote)
    recorder = subprocess.Popen(
        ["adb", "shell", "screenrecord", "--size", "540x1200", "--bit-rate", "2000000", "--time-limit", "4", remote],
        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
    )
    try:
        time.sleep(0.8)
        adb("shell", "input", "tap", *center(atm_switch))
        time.sleep(1.1)
        adb("shell", "input", "tap", *center(cash_switch))
    finally:
        _, errors = recorder.communicate(timeout=13)
        assert recorder.returncode == 0, errors.decode(errors="replace")
    adb("pull", remote, str(out / "cash-funding-1612.mp4"))


out = Path("screens")
adb("shell", "settings", "put", "global", "animator_duration_scale", "1")
adb("shell", "cmd", "uimode", "night", "yes")
start("money", "--es", "spend", "JPY")
tap("Добавить наличные")
find("Новые наличные")
find("Валюта наличных")
tap("RUB ▾", scroll=True)
tap("🇺🇸 USD ·", prefix=True)
find("USD ▾")
enter("Отдал", "100")
enter("Получил", "15000")

# Re-tapping the current source used to replace the selected USD with the home currency.
tap("✓ Обменник", scroll=True)
exchange_saved()
tap("Банкомат")
atm_empty()
enter("Сняли", "12000")
enter("Списали с карты", "8000")
tap("Обменник", scroll=True)
exchange_saved()
tap("Банкомат")
field("Сняли", "12000")
field("Списали с карты", "8000")
tap("Обменник", scroll=True)
tap("Сохранить")
find("Наличные JPY")
open_saved_cash("Наличные JPY")
exchange_saved()

with TemporaryDirectory() as temp:
    exchange_png = Path(temp) / "exchange.png"
    atm_png = Path(temp) / "atm.png"
    capture(exchange_png)
    record_preview_tabs()
    exchange_saved()
    tap("Банкомат")
    atm_empty()
    capture(atm_png)
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", str(exchange_png), "-i", str(atm_png),
         "-filter_complex", "hstack=inputs=2", "-frames:v", "1", "-threads", "1", "-update", "1",
         str(out / "cash-funding-1612.png")],
        check=True, timeout=25,
    )

# Saving after viewing the empty tab must keep the exchange and its original source.
tap("Сохранить")
find("Наличные JPY")
open_saved_cash("Наличные JPY")
exchange_saved()
print("USD 100 → JPY 15000 survived source switching, saving and two restarts.", flush=True)

# Existing ATM receipts must keep the inverse separation, including after saving the empty exchange tab.
open_saved_cash("Наличные THB")
find("✓ Банкомат", selected=True, scroll=True)
field("Сняли", "10000")
field("Списали с карты", "2210")
tap("Обменник")
field("Отдал", "")
field("Получил", "")
tap("Сохранить")
find("Наличные THB")
open_saved_cash("Наличные THB")
find("✓ Банкомат", selected=True, scroll=True)
field("Сняли", "10000")
field("Списали с карты", "2210")
print("Saved ATM withdrawal stayed separate and survived saving from the empty exchange tab.", flush=True)
