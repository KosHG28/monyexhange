"""Саундтрек brag-видео: 120 BPM, до мажор, всё на одной сетке от первого кадра.

python3 music.py out.wav
"""
import sys
import wave

import numpy as np

SR = 48000
BPM = 120
BEAT = 60 / BPM          # 0,5 с = 15 кадров
DUR = 21.0
N = int(SR * DUR)
rng = np.random.default_rng(7)


def mtof(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def tt(sec):
    return np.arange(int(sec * SR)) / SR


def fft_filter(x, lo=None, hi=None, slope=4.0):
    """Мягкий полосовой фильтр в частотной области (без scipy)."""
    n = len(x)
    size = 1 << (n - 1).bit_length()
    X = np.fft.rfft(x, size)
    f = np.fft.rfftfreq(size, 1 / SR)
    g = np.ones_like(f)
    if lo:
        g *= 1 / (1 + (lo / np.maximum(f, 1)) ** slope)
    if hi:
        g *= 1 / (1 + (f / hi) ** slope)
    return np.fft.irfft(X * g, size)[:n]


class Bus:
    def __init__(self):
        self.l = np.zeros(N)
        self.r = np.zeros(N)

    def add(self, at, sig, gain=1.0, pan=0.0):
        i = int(round(at * SR))
        if i >= N:
            return
        sig = sig[: N - i] * gain
        self.l[i:i + len(sig)] += sig * np.sqrt(0.5 * (1 - pan))
        self.r[i:i + len(sig)] += sig * np.sqrt(0.5 * (1 + pan))


# ---------- гармония ----------
# такты по 2 с: C G Am F C G Am F G C (C держится до конца)
PROG = ["C", "G", "Am", "F", "C", "G", "Am", "F", "G", "C", "C"]
CHORD = {"C": [60, 64, 67], "G": [55, 59, 62], "Am": [57, 60, 64], "F": [53, 57, 60]}
ROOT = {"C": 36, "G": 43, "Am": 45, "F": 41}


def chord_at(t):
    return PROG[min(int(t // (4 * BEAT)), len(PROG) - 1)]


# ---------- инструменты ----------
def kick():
    t = tt(0.42)
    f = 46 + 95 * np.exp(-t * 32)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7.5)
    click = fft_filter(rng.standard_normal(len(t)), lo=1500, hi=6000) * np.exp(-t * 400) * 0.25
    return body + click


def clap():
    t = tt(0.3)
    n = rng.standard_normal(len(t))
    env = np.exp(-t * 22) * (1 + 0.6 * np.exp(-((t - 0.012) ** 2) / 2e-5) + 0.5 * np.exp(-((t - 0.024) ** 2) / 2e-5))
    return fft_filter(n, lo=900, hi=5000) * env


def hat(open_=False):
    t = tt(0.18 if open_ else 0.06)
    return fft_filter(rng.standard_normal(len(t)), lo=7000) * np.exp(-t * (18 if open_ else 70))


def bass(m, length):
    t = tt(length)
    f = mtof(m)
    saw = 2 * ((t * f) % 1) - 1
    s = 0.8 * np.sin(2 * np.pi * f * t) + 0.25 * fft_filter(saw, hi=520)
    env = np.minimum(1, t / 0.006) * (0.55 + 0.45 * np.exp(-t * 9)) * np.minimum(1, (length - t) / 0.02)
    return s * env


def pad(notes, length):
    t = tt(length + 0.6)
    s = np.zeros(len(t))
    for m in notes:
        for det in (-0.08, 0.0, 0.08):
            f = mtof(m + det)
            s += 2 * ((t * f + rng.random()) % 1) - 1
    s = fft_filter(s, lo=150, hi=1500, slope=2)
    env = np.minimum(1, t / 0.25) * np.clip((length + 0.6 - t) / 0.6, 0, 1)
    return s * env / (len(notes) * 3)


def pluck(m, length=0.5):
    t = tt(length)
    w = 2 * np.pi * mtof(m) * t
    s = np.sin(w) + 0.45 * np.sin(2 * w) * np.exp(-t * 14) + 0.2 * np.sin(3 * w) * np.exp(-t * 24)
    return s * np.exp(-t * 7) * np.minimum(1, t / 0.003)


def bell(m, length=1.6):
    t = tt(length)
    f = mtof(m)
    s = (np.sin(2 * np.pi * f * t) * np.exp(-t * 2.5)
         + 0.35 * np.sin(2 * np.pi * f * 2.0 * t) * np.exp(-t * 4)
         + 0.15 * np.sin(2 * np.pi * f * 3.01 * t) * np.exp(-t * 7))
    return s * np.minimum(1, t / 0.004)


def pop(m):
    """Метка всплывает: короткий тон с подъёмом высоты, в тональности."""
    t = tt(0.22)
    f = mtof(m) * (0.82 + 0.18 * (1 - np.exp(-t * 90)))
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 16)
    s += 0.3 * np.sin(2 * np.pi * np.cumsum(f * 2) / SR) * np.exp(-t * 30)
    return s * np.minimum(1, t / 0.002)


def tick(m=84):
    t = tt(0.05)
    s = 0.6 * np.sin(2 * np.pi * mtof(m) * t) * np.exp(-t * 90)
    s += fft_filter(rng.standard_normal(len(t)), lo=2500, hi=7000) * np.exp(-t * 160) * 0.5
    return s


def key():
    t = tt(0.04)
    return fft_filter(rng.standard_normal(len(t)), lo=1800, hi=5500) * np.exp(-t * 150)


def whoosh(length=0.45):
    """Мягкий шорох, кончается ровно на склейке."""
    t = tt(length)
    s = fft_filter(rng.standard_normal(len(t)), lo=500, hi=4500, slope=2)
    env = (t / length) ** 2.2 * np.clip((length - t) / 0.03, 0, 1)
    return s * env


# ---------- партитура ----------
music, drums, sfx = Bus(), Bus(), Bus()
END_DRUMS = 18.0
beats = np.arange(0, DUR, BEAT)

for b, t0 in enumerate(beats):
    if t0 < END_DRUMS:
        drums.add(t0, kick(), 0.95)
        if t0 >= 3.0 and b % 2 == 1:
            drums.add(t0, clap(), 0.32, pan=0.05)
        drums.add(t0 + 0.25, hat(), 0.16, pan=0.25)          # хэт на слабую восьмую
        if t0 >= 3.0:
            drums.add(t0, hat(), 0.07, pan=0.25)
        if t0 >= 3.0:
            drums.add(t0 + 0.125, hat(), 0.04, pan=-0.2)
            drums.add(t0 + 0.375, hat(), 0.05, pan=-0.2)

# бас на восьмых: корень, корень, октава, корень
for i in range(int(END_DRUMS / (BEAT / 2))):
    t0 = i * BEAT / 2
    r = ROOT[chord_at(t0)]
    m = r + (12 if i % 4 == 2 else 0)
    g = 0.5 if i % 2 else 0.36
    music.add(t0, bass(m, BEAT / 2 * 0.92), g * (0.7 if t0 < 3.0 else 1.0))

# пэд по тактам
for bar, name in enumerate(PROG):
    t0 = bar * 4 * BEAT
    if t0 >= DUR:
        break
    length = (4 * BEAT) if bar < 9 else DUR - t0
    music.add(t0, pad(CHORD[name], length), 0.5 if t0 >= 3.0 else 0.32, pan=0)

# арпеджио на восьмых (на октаву выше аккорда)
ARP = [0, 1, 2, 1, 2, 0, 1, 2]
for i in range(int(END_DRUMS / (BEAT / 2))):
    t0 = i * BEAT / 2
    notes = CHORD[chord_at(t0)]
    m = notes[ARP[i % 8]] + 12
    music.add(t0, pluck(m), 0.13 if t0 >= 3.0 else 0.09, pan=0.3 if i % 2 else -0.3)

# финал: колокольчик по нотам до мажора
for i, m in enumerate([72, 76, 79, 84]):
    music.add(18.0 + i * 0.25, bell(m, 2.6), 0.16, pan=(-0.3, -0.1, 0.1, 0.3)[i])
music.add(18.0, bass(36, 2.5), 0.42)
music.add(18.0, kick(), 0.6)

# ---------- эффекты (в тональности аккорда) ----------
def chord_tone(t, idx, octave=2):
    return CHORD[chord_at(t)][idx % 3] + 12 * octave


# Сцена 1: уголки сходятся, сумма вписывается в ценник, карточка выезжает
sfx.add(0.5, tick(91), 0.18)
sfx.add(1.0, whoosh(0.3)[::-1].copy(), 0.06, pan=0.2)       # подложка закрывает цену
sfx.add(1.25, pop(chord_tone(1.25, 2)), 0.32)                # «3 749 ₽»
sfx.add(1.25, pluck(chord_tone(1.25, 0, 2), 0.6), 0.08)
sfx.add(1.5, pop(chord_tone(1.5, 0)), 0.12, pan=-0.2)        # карточка цены
for cut in (3.0, 6.5, 11.0, 15.0, 18.0):
    sfx.add(cut - 0.45, whoosh(0.45), 0.10, pan=-0.1)
sfx.add(3.0, bell(chord_tone(3.0, 1, 1) + 12, 1.4), 0.13)    # значок
# Сцена 3: палец ведёт сумму, карта сменилась, цифры перекатываются
sfx.add(7.5, tick(84), 0.12, pan=0.2)
sfx.add(8.0, tick(88), 0.16)
sfx.add(8.0, pop(chord_tone(8.0, 2)), 0.26)
for i in range(4):
    sfx.add(8.0 + i * 0.0625, key(), 0.05, pan=0.3 - i * 0.15)
sfx.add(9.5, tick(79), 0.08)                                  # ↻ курс ЦБ
# Сцена 4: тап, скриншот влетает, поля заполняются
sfx.add(11.75, tick(86), 0.18)
sfx.add(11.875, whoosh(0.25)[::-1].copy(), 0.06, pan=0.3)
sfx.add(12.5, tick(79), 0.1)
sfx.add(12.75, pop(chord_tone(12.75, 0)), 0.22, pan=-0.1)
sfx.add(12.875, pop(chord_tone(12.875, 2)), 0.22, pan=0.1)
sfx.add(13.125, bell(chord_tone(13.1, 1, 2), 1.0), 0.07)
# Сцена 5: строки шпаргалки каскадом, полоса по строке 167
for i in range(6):
    sfx.add(15.125 + i * 0.125, key(), 0.04, pan=-0.3 + i * 0.12)
sfx.add(15.875, whoosh(0.4)[::-1].copy(), 0.05)
sfx.add(16.25, pop(chord_tone(16.25, 1)), 0.18)
# Финал
sfx.add(18.0, pop(72), 0.22)

# ---------- сведение ----------
# «пампинг» от бочки на музыке
duck = np.ones(N)
for t0 in beats:
    if t0 < END_DRUMS:
        i = int(t0 * SR)
        d = tt(0.32)
        g = 1 - 0.45 * np.exp(-d * 11)
        duck[i:i + len(g)] = np.minimum(duck[i:i + len(g)], g[: N - i])
music.l *= duck
music.r *= duck

# общий ревер: экспоненциальный шум как импульс
ir_t = tt(1.7)
ir_l = rng.standard_normal(len(ir_t)) * np.exp(-ir_t * 3.6)
ir_r = rng.standard_normal(len(ir_t)) * np.exp(-ir_t * 3.6)
ir_l = fft_filter(ir_l, lo=250, hi=6000) / np.sqrt(np.sum(ir_l ** 2))
ir_r = fft_filter(ir_r, lo=250, hi=6000) / np.sqrt(np.sum(ir_r ** 2))


def conv(x, ir):
    n = len(x) + len(ir)
    size = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[: len(x)]


send_l = 0.22 * music.l + 0.35 * sfx.l + 0.08 * drums.l
send_r = 0.22 * music.r + 0.35 * sfx.r + 0.08 * drums.r
wet_l, wet_r = conv(send_l, ir_l), conv(send_r, ir_r)

L = music.l + drums.l + sfx.l + 0.5 * wet_l
R = music.r + drums.r + sfx.r + 0.5 * wet_r
L, R = fft_filter(L, lo=30), fft_filter(R, lo=30)

# громкость: RMS около −15 dBFS, мягкий лимитер, пик −1 dBFS
rms = np.sqrt(np.mean((L ** 2 + R ** 2) / 2))
gain = 10 ** (-15 / 20) / rms
L, R = np.tanh(L * gain * 1.1) / 1.1, np.tanh(R * gain * 1.1) / 1.1
fade = np.clip((DUR - 0.05 - np.arange(N) / SR) / 0.8, 0, 1)
L, R = L * fade, R * fade
peak = max(np.abs(L).max(), np.abs(R).max())
L, R = L / peak * 0.89, R / peak * 0.89

out = np.stack([L, R], axis=1)
with wave.open(sys.argv[1] if len(sys.argv) > 1 else "music.wav", "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes((out * 32767).astype("<i2").tobytes())
print("rms dBFS", 20 * np.log10(np.sqrt(np.mean(out ** 2))), "peak", np.abs(out).max())
