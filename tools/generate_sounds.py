#!/usr/bin/env python3
"""Synthesises every keyboard sample (original audio, no third-party recordings).
Each hit is layered: contact noise + inharmonic metal modes + low body thud + spring tail.
Output: app/src/main/res/raw/<style>_<event>_<n>.wav  (44.1 kHz, 16-bit mono)."""
import os, wave, zlib
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main', 'res', 'raw')

def t_axis(d): return np.arange(int(d * SR)) / SR

def band(x, lo, hi):
    f = np.fft.rfft(x, len(x) * 2)
    hz = np.fft.rfftfreq(len(x) * 2, 1 / SR)
    gain = 1 / (1 + (lo / np.maximum(hz, 1)) ** 4) / (1 + (hz / hi) ** 4)
    return np.fft.irfft(f * gain)[:len(x)]

def place(buf, sig, at, amp=1.0):
    i = int(at * SR); n = min(len(sig), len(buf) - i)
    if n > 0: buf[i:i + n] += sig[:n] * amp

def noise(r, dur, lo, hi, tau):
    t = t_axis(dur)
    x = band(r.standard_normal(len(t)), lo, hi)
    x *= np.exp(-t / tau) * np.minimum(1, t / 0.0004)
    return x / (np.max(np.abs(x)) + 1e-9)

def modes(r, dur, freqs, taus, amps, jitter=0.025):
    t = t_axis(dur); x = np.zeros_like(t)
    for f, tau, a in zip(freqs, taus, amps):
        f *= 1 + r.uniform(-jitter, jitter)
        x += a * np.sin(2 * np.pi * f * t + r.uniform(0, 6.28)) * np.exp(-t / tau)
    x *= np.minimum(1, t / 0.0003)
    return x / (np.max(np.abs(x)) + 1e-9)

def thud(dur, f0, tau):
    t = t_axis(dur)
    f = f0 * (1 + 0.8 * np.exp(-t / 0.006))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / tau) * np.minimum(1, t / 0.001)

def spring(r, dur, f0, tau):
    t = t_axis(dur); x = np.zeros_like(t)
    for k, a in ((1, 1), (1.53, .6), (2.31, .35)):
        wob = 1 + 0.006 * np.sin(2 * np.pi * r.uniform(24, 38) * t)
        x += a * np.sin(2 * np.pi * np.cumsum(f0 * k * wob) / SR)
    return x * np.exp(-t / tau) * np.minimum(1, t / 0.004) / 1.95

# bright: filter/mode pitch, body: thud level, ring: metal level, tail: decay scale, level: peak
STYLES = {
    'classic': dict(bright=1.00, body=0.55, ring=0.50, tail=1.00, level=0.88),
    'soft':    dict(bright=0.72, body=0.45, ring=0.22, tail=0.70, level=0.58),
    'heavy':   dict(bright=0.80, body=0.95, ring=0.62, tail=1.30, level=0.96),
    'minimal': dict(bright=0.95, body=0.12, ring=0.08, tail=0.40, level=0.42),
}

def strike(r, s, pitch=1.0, hard=1.0, dur=0.17):
    b = np.zeros(int(dur * SR)); p = pitch * s['bright']; k = s['tail']
    place(b, noise(r, .02, 900*p, 5200*p, .0018), 0.000, 0.28)       # key lever bottoming out
    hit = 0.0085 + r.uniform(-.001, .0015)                          # type bar reaches the platen
    place(b, noise(r, .04, 1300*p, 9000*p, .0035*k), hit, 0.95*hard)
    place(b, noise(r, .06, 300*p, 2400*p, .011*k), hit, 0.50)
    place(b, modes(r, .12, [1180*p, 2050*p, 3170*p, 4620*p, 6100*p],
                   [.020*k, .016*k, .012*k, .008*k, .005*k], [1, .8, .65, .45, .25]), hit, s['ring'])
    place(b, thud(.09, 150*s['bright']*pitch, .017*k), hit, s['body'])
    place(b, noise(r, .02, 2200*p, 7000*p, .002), hit + r.uniform(.017, .028), 0.16)  # rebound
    place(b, spring(r, .12, 2750*p, .028*k), hit + .004, 0.07*s['ring'])
    place(b, noise(r, .03, 2600*p, 11000, .0045), hit + .0006, 0.45*hard*(.4 + s['ring']))   # paper slap
    place(b, modes(r, .12, [236*p, 372*p, 590*p], [.030*k, .022*k, .015*k], [1, .7, .45]), hit + .001, 0.42*s['body'])  # wooden case
    return room(r, b, s)

def room(r, x, s):
    """A few early reflections off the desk and case, so the hit sits in a real space."""
    out = x.copy()
    for delay, gain in ((.0041, .20), (.0073, .14), (.0127, .09), (.0191, .055)):
        place(out, band(x, 150, 4200), delay * r.uniform(.92, 1.08), gain * min(1, s['tail']))
    return out

def release(r, s):
    b = np.zeros(int(.08 * SR)); p = s['bright']
    place(b, noise(r, .025, 1500*p, 6500*p, .0025), 0.0, 0.7)
    place(b, noise(r, .03, 700*p, 3000*p, .004), 0.011, 0.45)
    place(b, spring(r, .06, 1900*p, .016*s['tail']), 0.004, 0.14*s['ring'])
    return b * 0.34

def space(r, s):
    b = np.zeros(int(.22 * SR)); p = s['bright'] * r.uniform(.96, 1.04); k = s['tail']
    place(b, noise(r, .05, 180*p, 1700*p, .012*k), 0.004, 0.8)
    place(b, thud(.15, 98*p, .034*k), 0.004, 0.55 + s['body'] * .5)
    place(b, modes(r, .12, [410*p, 920*p, 1630*p], [.030*k, .020*k, .012*k], [1, .55, .3]), 0.006, 0.22 + s['ring']*.25)
    place(b, noise(r, .02, 1500*p, 5000*p, .002), 0.038, 0.20)       # escapement tick
    return b

def shift(r, s):
    b = np.zeros(int(.18 * SR)); p = s['bright']
    place(b, noise(r, .02, 1000*p, 6000*p, .0025), 0.0, 0.6)
    place(b, modes(r, .1, [760*p, 1910*p, 3350*p], [.018, .012, .007], [1, .6, .4]), 0.001, 0.3 + s['ring']*.4)
    place(b, noise(r, .03, 600*p, 3500*p, .006), 0.034, 0.75)
    place(b, thud(.08, 125*p, .018), 0.034, s['body'])
    place(b, spring(r, .12, 1450*p, .03*s['tail']), 0.038, 0.10*s['ring'])
    return b * 0.88

def ratchet(r, s, n, start_gap, end_gap, dur):
    b = np.zeros(int(dur * SR)); at = 0.0; p = s['bright']
    for i in range(n):
        gap = start_gap + (end_gap - start_gap) * i / max(1, n - 1)
        place(b, noise(r, .015, 1400*p, 6500*p, .0017), at, r.uniform(.5, .9))
        place(b, modes(r, .02, [2300*p, 3900*p], [.004, .003], [1, .6]), at, .25*s['ring'])
        at += gap * r.uniform(.9, 1.1)
    return b

def backspace(r, s):
    b = np.zeros(int(.19 * SR))
    place(b, ratchet(r, s, 3, .017, .024, .09), 0.0, 0.65)
    place(b, strike(r, s, pitch=.78, hard=.6, dur=.13), 0.050, 0.72)
    return b

def carriage(r, s):
    """Short escapement movement, used occasionally after a space."""
    return ratchet(r, s, 5, .014, .019, .13) * 0.42

def enter(r, s):
    """Carriage return: lever clack, accelerating zip, end stop, soft margin bell."""
    b = np.zeros(int(.62 * SR)); p = s['bright']
    place(b, shift(r, s), 0.0, 0.75)
    zipn = band(r.standard_normal(int(.25*SR)), 700*p, 4200*p)
    zipn /= np.max(np.abs(zipn)); u = np.linspace(0, 1, len(zipn))
    place(b, zipn * np.sin(np.pi*u)**.6, 0.055, 0.20)
    place(b, ratchet(r, s, 22, .016, .0075, .27), 0.06, 0.55)
    place(b, thud(.14, 105*p, .03), 0.325, .45 + s['body']*.5)
    place(b, noise(r, .06, 400*p, 4000*p, .01), 0.325, 0.9)
    place(b, modes(r, .27, [2093, 4120, 5630], [.085, .045, .025], [1, .3, .15], .002), 0.335, 0.42*s['ring'])
    return b

def select(r, s):
    b = np.zeros(int(.06 * SR)); p = s['bright']
    place(b, noise(r, .015, 1800*p, 5500*p, .0016), 0.0, 0.55)
    place(b, modes(r, .04, [1500*p, 2600*p], [.006, .004], [1, .4]), 0.0, 0.4)
    return b * 0.55

def opening(r, s):
    b = np.zeros(int(.3 * SR))
    place(b, ratchet(r, s, 6, .022, .012, .13), 0.0, 0.45)
    place(b, shift(r, s), 0.115, 0.45)
    return b

def save(name, x, level):
    fade = int(.006 * SR); x = x.copy(); x[-fade:] *= np.linspace(1, 0, fade)
    x = np.tanh(1.25 * x / np.max(np.abs(x))) / np.tanh(1.25) * level
    with wave.open(os.path.join(OUT, name + '.wav'), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype('<i2').tobytes())

os.makedirs(OUT, exist_ok=True)
for style, s in STYLES.items():
    def rng(tag): return np.random.default_rng(zlib.crc32(f'{style}{tag}'.encode()))
    # six takes: sharp, deeper, softer, bright-light, mid, deep-hard
    for i, (pitch, hard, gain) in enumerate([(1.10,1.15,1.0),(0.88,1.0,.97),(1.0,.62,.76),(1.05,.85,.88),(.96,1.0,.93),(.91,1.1,.98)], 1):
        save(f'{style}_key_{i}', strike(rng(f'k{i}'), s, pitch, hard), s['level'] * gain)
    for i, pitch in enumerate([1.22, 1.28, 1.17], 1):
        save(f'{style}_number_{i}', strike(rng(f'n{i}'), s, pitch, 1.05), s['level'] * .9)
    for i in (1, 2, 3): save(f'{style}_release_{i}', release(rng(f'r{i}'), s), s['level'] * .28)
    for i in (1, 2): save(f'{style}_space_{i}', space(rng(f's{i}'), s), s['level'] * .92)
    for name, fn, gain in [('shift',shift,.82),('backspace',backspace,.86),('enter',enter,.92),
                           ('carriage',carriage,.38),('emoji',select,.42),('open',opening,.4)]:
        save(f'{style}_{name}_1', fn(rng(name), s), s['level'] * gain)
print(len(os.listdir(OUT)), 'files')
