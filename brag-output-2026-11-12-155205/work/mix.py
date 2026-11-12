"""Звук ролика: трек Sascha Ende (CC BY 4.0) + звуки Kenney (CC0), всё на сетке 120 BPM от первого кадра.

python3 mix.py out.wav
"""
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

SR = 48000
DUR = 20.0
HERE = Path(__file__).parent / "audio"
TRACK = "happy-beats-business-moves-vol-1-by-ende-dot-app.mp3"
TRACK_START = 16.0218          # сильная доля трека (cues: strong_beat)
TEMPO_FIX = 120.0 / 120.19     # трек 120,19 BPM → ровно 120: доля = 0,5 с, как склейки ролика


def load(name, extra=()):
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", *extra, "-i", str(HERE / name), "-ac", "2", "-ar", str(SR), "-f", "f32le", "-"],
        check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype="<f4").reshape(-1, 2).copy()


n = int(SR * DUR)
music = load(TRACK, ["-ss", str(TRACK_START), "-t", str(DUR / TEMPO_FIX + 0.5)])
music = load(TRACK, ["-ss", str(TRACK_START), "-t", str(DUR + 1)]) if music.size == 0 else music
# Подгонка темпа и обрезка
tempo = subprocess.run(
    ["ffmpeg", "-v", "error", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-", "-af", f"atempo={TEMPO_FIX:.6f}",
     "-f", "f32le", "-"], input=music.tobytes(), check=True, capture_output=True).stdout
music = np.frombuffer(tempo, dtype="<f4").reshape(-1, 2)[:n].copy()
t = np.arange(len(music)) / SR
music *= np.clip(t / 0.03, 0, 1)[:, None]                      # без щелчка в начале
music *= np.clip((DUR - t) / 1.6, 0, 1)[:, None] ** 1.5         # затухание под финал

sfx = np.zeros((n, 2), dtype=np.float32)


def add(at, name, db, pan=0.0):
    s = load(name) * (10 ** (db / 20))
    s[:, 0] *= np.sqrt(0.5 * (1 - pan)) * np.sqrt(2)
    s[:, 1] *= np.sqrt(0.5 * (1 + pan)) * np.sqrt(2)
    i = int(round(at * SR))
    m = min(len(s), n - i)
    if m > 0:
        sfx[i:i + m] += s[:m]


# Страны: первая — капля, смены — мягкий переключатель
add(0.0, "drop_002.ogg", -12)
for at, pan in ((1.0, -0.2), (2.0, 0.2), (3.0, -0.1), (4.25, 0.1)):
    add(at, "switch_004.ogg", -13, pan)
add(4.0, "impactSoft_heavy_001.ogg", -11)                    # титр
for cut in (5.5, 9.0, 11.0, 15.0):
    add(cut, "impactSoft_medium_001.ogg", -17)               # склейки — едва слышно
# Камера: уголки, суммы проступают в трёх ценниках, «было», карточка
add(5.875, "select_008.ogg", -15)
for at, f, pan in ((6.25, "drop_001.ogg", -0.35), (6.5, "drop_002.ogg", 0.35), (6.75, "drop_003.ogg", 0.0)):
    add(at, f, -9, pan)
add(6.875, "drop_001.ogg", -17, 0.1)
add(7.25, "switch_002.ogg", -16, -0.2)
# Вставка: тап, скриншот влетает, «Читаем…», курс точный
add(9.25, "click_003.ogg", -6)
add(9.375, "rollover2.ogg", -12, 0.3)
add(10.0, "select_008.ogg", -15)
add(10.25, "bong_001.ogg", -6)
# Пересчёт: клавиши 1, 6, 7, касание карты
for at, f, pan in ((11.625, "keypress-003.wav", -0.2), (11.875, "keypress-007.wav", 0.0), (12.125, "keypress-012.wav", 0.2)):
    add(at, f, -6, pan)
add(13.75, "click_003.ogg", -7, 0.25)
add(13.75, "drop_002.ogg", -12, 0.25)
# Справочник: пять строк по одной
for i, at in enumerate((15.375, 15.75, 16.125, 16.5, 16.875)):
    add(at, ("drop_001.ogg", "drop_002.ogg", "drop_003.ogg")[i % 3], -12, (-0.3, -0.1, 0.1, 0.3, 0.0)[i])
# Финал
add(18.0, "impactSoft_heavy_002.ogg", -9)
add(18.25, "bong_001.ogg", -12)

# Сведение: музыка под эффекты чуть приседает (−3 дБ на 0,25 с после каждого эффекта)
env = np.abs(sfx).max(axis=1)
duck = np.ones(n, dtype=np.float32)
hits = np.flatnonzero(env > 0.02)
for i in hits[::480]:
    duck[i:i + SR // 4] = np.minimum(duck[i:i + SR // 4], 10 ** (-3 / 20))
kernel = np.ones(SR // 50) / (SR // 50)
duck = np.convolve(duck, kernel, mode="same").astype(np.float32)
mix = music * duck[:, None] * 10 ** (-2 / 20) + sfx
peak = np.abs(mix).max()
mix = np.tanh(mix / peak * 1.2) / np.tanh(1.2) * 0.89

out = sys.argv[1] if len(sys.argv) > 1 else "music.wav"
with wave.open(out, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((mix * 32767).astype("<i2").tobytes())
print("peak", float(np.abs(mix).max()), "len", len(mix) / SR)
