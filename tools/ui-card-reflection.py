"""Check the actual reflection pixels in a single screenshot of card appearance."""
import re
from statistics import median
from struct import unpack_from
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


def start(screen, *extras):
    adb("shell", "am", "force-stop", PACKAGE)
    print(adb("shell", "am", "start", "-W", "-n", ACTIVITY, "--es", "screen", screen, "--ez", "demo", "true", *extras), flush=True)


def bordeaux(node):
    return node.get("checkable") == "true" and any(text("Бордо")(child) for child in node.iter("node"))


def reflection_gain(screenshot):
    width, height = unpack_from(">II", screenshot.read_bytes(), 16)
    frame = subprocess.check_output(
        ["ffmpeg", "-v", "error", "-i", str(screenshot), "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], timeout=25,
    )

    def pixel(x, y):
        index = (width * y + x) * 3
        return frame[index:index + 3]

    def is_bordeaux(x, y):
        red, green, blue = pixel(x, y)
        return red > 45 and red - max(green, blue) > 28

    # The card is the red surface above the palette. At mid-height there is no lettering.
    rows = [y for y in range(height * 14 // 100, height * 39 // 100) if is_bordeaux(width // 2, y)]
    assert len(rows) > height // 10, "Bordeaux preview not found"
    middle = (rows[0] + rows[-1]) // 2
    columns = [x for x in range(width) if is_bordeaux(x, middle)]
    left, right = columns[0], columns[-1]

    def green_at(fraction):
        x = round(left + (right - left) * fraction)
        return median(pixel(x + dx, middle + dy)[1] for dx in range(-4, 5) for dy in range(-4, 5))

    # The base gradient alone has an approximately linear green channel across this row.
    return green_at(0.5) - (green_at(0.14) + green_at(0.86)) / 2


def main():
    adb("shell", "cmd", "uimode", "night", "yes")
    adb("shell", "settings", "put", "global", "animator_duration_scale", "1")
    start("pro", "--ez", "receiptDemo", "true")
    find(text("Курс по чеку"))
    tap(find(text("Стиль карт")))
    find(text("Оформление карты"))
    tap(find(bordeaux))
    find(lambda node: bordeaux(node) and (node.get("checked") == "true" or node.get("selected") == "true"))
    screenshot = Path("screens") / "card-reflection-1542.png"
    with screenshot.open("wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, stdout=output, timeout=30)
    gain = reflection_gain(screenshot)
    assert gain >= 14, f"Highlight disappeared on bordeaux: green-channel gain {gain:.1f}/255"
    print(f"Bordeaux preview has a visible reflection: green-channel gain {gain:.1f}/255. One screenshot.")


if __name__ == "__main__":
    main()
