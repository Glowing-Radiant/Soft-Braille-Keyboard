#!/usr/bin/env python3
# Copyright (C) 2026 The Soft Braille Keyboard Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Synthesises the candidate sound themes of the keyboard as WAV files.

The themes imitate real keyboards and braille writers. A key sound is made
the way a real key makes it: a very short impulse, the impact, rings the
resonances of the keycap, switch, case or metal parts, each dying away at its
own rate.

Every theme has one sound for each kind of action, named
<theme number>-<theme>-<sound number>-<sound>.wav, and a demo,
<theme number>-<theme>-00-demo.wav, that plays the sounds as they would be
heard while typing a short message in braille.

Usage: generate_sound_themes.py [output directory]
"""

import os
import sys
import wave

import numpy as np
from scipy.signal import butter, lfilter

RATE = 44100
# The sounds of each theme, in the order they are numbered.
SOUNDS = ['type', 'space', 'delete', 'newline', 'submit', 'move', 'select',
          'toggle', 'menu-open', 'menu-close', 'error']

rng = np.random.default_rng(7)


# Building blocks.

def samples(seconds):
    return int(round(seconds * RATE))


def lowpass(x, cutoff, order=2):
    b, a = butter(order, cutoff / (RATE / 2), 'low')
    return lfilter(b, a, x)


def highpass(x, cutoff, order=2):
    b, a = butter(order, cutoff / (RATE / 2), 'high')
    return lfilter(b, a, x)


def bandpass(x, low, high, order=2):
    b, a = butter(order, [low / (RATE / 2), high / (RATE / 2)], 'band')
    return lfilter(b, a, x)


def impulse(ms, low=None, high=None):
    """The impact exciting a key: noise dying away within about ms."""
    n = samples(max(ms * 6, 1.0) / 1000)
    x = rng.uniform(-1, 1, n) * np.exp(-np.arange(n) / (ms / 1000 * RATE))
    if low:
        x = highpass(x, low)
    if high:
        x = lowpass(x, high)
    return x


def resonate(x, freq, decay):
    """A resonance at freq, whose ringing falls by 1/e every decay seconds."""
    r = np.exp(-1 / (decay * RATE))
    w = 2 * np.pi * freq / RATE
    y = lfilter([1.0], [1, -2 * r * np.cos(w), r * r], x)
    m = np.max(np.abs(y))
    return y / m if m else y


def hit(seconds, excitation, modes, click=0.0):
    """An impact ringing the given (frequency, decay, level) modes. The click
    is how much of the raw impact is heard, its crispness."""
    x = np.zeros(samples(seconds))
    x[:min(len(x), len(excitation))] = excitation[:len(x)]
    y = sum(level * resonate(x, f, d) for f, d, level in modes)
    m = np.max(np.abs(x))
    if click and m:
        y = y + click * x / m * np.max(np.abs(y))
    return fade(y)


def fade(x, ms=4):
    n = min(len(x) // 6, samples(ms / 1000))
    if n > 0:
        x = x.copy()
        x[-n:] *= np.linspace(1, 0, n)
    return x


def noise_env(seconds, attack, low, high, shape=None):
    """Band limited noise, for friction, paper and air."""
    n = samples(seconds)
    x = bandpass(rng.uniform(-1, 1, n), low, high)
    env = np.ones(n) if shape is None else shape
    a = samples(attack)
    if a:
        env = env.copy()
        env[:a] *= np.linspace(0, 1, a)
    x = x * env
    x = x / (np.max(np.abs(x)) or 1)
    return fade(x, 20)


def swish(seconds, low, high, peak=0.3):
    """Air or paper sliding: noise whose band sweeps upwards."""
    n = samples(seconds)
    x = rng.uniform(-1, 1, n)
    out = np.zeros(n)
    bands = 8
    for i in range(bands):
        lo = low * (high / low) ** (i / bands)
        hi = low * (high / low) ** ((i + 1) / bands)
        centre = (i + 0.5) / bands
        env = np.exp(-((np.linspace(0, 1, n) - centre) / 0.18) ** 2)
        out += bandpass(x, lo, hi) * env
    return level(fade(out, 20), peak)


def at(x, seconds):
    return np.concatenate([np.zeros(samples(seconds)), x])


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def seq(parts, gap):
    """Starts each sound gap seconds after the previous one started."""
    return mix(*(at(p, i * gap) for i, p in enumerate(parts)))


def level(x, peak):
    m = np.max(np.abs(x))
    return x * (peak / m) if m else x


def bell(freq, seconds, peak):
    return level(hit(seconds, impulse(0.2), [(freq, seconds / 3, 1),
                                             (freq * 2.76, seconds / 6, 0.4),
                                             (freq * 5.4, seconds / 12, 0.15)],
                     0.05), peak)


def keyboard(key, space, big, small, stab, whoosh, thud):
    """The sounds of a computer keyboard from its keys: a letter key, the
    space bar, a big key such as backspace, a small one such as an arrow,
    the rattle of a stabiliser wire under enter, and the send swoosh."""
    return {
        'type': lambda v=1, dots=3: key(v),
        'space': space,
        'delete': lambda: big(0.88),
        'newline': lambda: mix(big(1.0), at(stab(), 0.006)),
        'submit': lambda: mix(big(1.0), at(stab(), 0.006), at(whoosh(), 0.05)),
        'move': lambda: small(1.0),
        'select': lambda: seq([small(0.85), small(1.0)], 0.06),
        'toggle': lambda: seq([small(0.8), key(1.05)], 0.07),
        'menu-open': lambda: seq([small(0.9), small(1.15)], 0.06),
        'menu-close': lambda: seq([small(1.15), small(0.9)], 0.06),
        'error': lambda: seq([thud(), thud()], 0.09),
    }


# The themes. Each returns a dict from sound name to a function giving the
# samples. The type sound takes a small pitch variation, so a stream of key
# presses isn't tiring, and the number of dots in the braille character.

def theme_phone():
    """A touch screen keyboard's key click: a clean tick, a lower tock for
    space and enter, a swoosh for send and a haptic buzz for errors."""
    def tick(v, freq, decay, peak):
        return level(hit(0.04, impulse(0.15, low=800),
                         [(freq * v, decay, 1), (freq * 1.6 * v, decay * 0.6,
                                                 0.35)], 0.2), peak)
    return {
        'type': lambda v=1, dots=3: tick(v, 1900, 0.006, 0.3),
        'space': lambda: tick(1, 1050, 0.01, 0.34),
        'delete': lambda: tick(1, 1450, 0.009, 0.32),
        'newline': lambda: seq([tick(1, 900, 0.012, 0.34),
                                tick(1, 1350, 0.008, 0.2)], 0.045),
        'submit': lambda: mix(tick(1, 900, 0.012, 0.3),
                              at(swish(0.3, 400, 5000, 0.28), 0.03)),
        'move': lambda: tick(1, 2600, 0.004, 0.18),
        'select': lambda: seq([tick(1, 2400, 0.004, 0.22),
                               tick(1, 2400, 0.004, 0.22)], 0.05),
        'toggle': lambda: seq([tick(1, 1500, 0.006, 0.26),
                               tick(1, 2000, 0.006, 0.26)], 0.06),
        'menu-open': lambda: seq([tick(1, f, 0.007, 0.26)
                                  for f in (1300, 1700, 2200)], 0.055),
        'menu-close': lambda: seq([tick(1, f, 0.007, 0.26)
                                   for f in (2200, 1700, 1300)], 0.055),
        'error': lambda: level(lowpass(np.sign(np.sin(2 * np.pi * 160 * np.arange(
            samples(0.12)) / RATE)), 900) * np.hanning(samples(0.12)), 0.35),
    }


def theme_scissor():
    """A laptop's scissor switch keys: light, short and quiet taps."""
    def key(v):
        return level(mix(hit(0.05, impulse(0.35, low=700),
                             [(2300 * v, 0.008, 1), (3600 * v, 0.005, 0.6),
                              (5200 * v, 0.003, 0.35), (320, 0.012, 0.3)],
                             0.25),
                         at(0.25 * hit(0.02, impulse(0.1, low=2000),
                                       [(3900 * v, 0.003, 1)], 0.3), 0.05)),
                     0.3)

    def space():
        return level(mix(hit(0.07, impulse(0.6, high=6000),
                             [(900, 0.015, 1), (1600, 0.01, 0.6),
                              (2600, 0.006, 0.4), (180, 0.02, 0.45)], 0.15),
                         at(0.3 * hit(0.02, impulse(0.1, low=2000),
                                      [(3100, 0.004, 1)]), 0.008)), 0.36)

    def big(v):
        return level(hit(0.06, impulse(0.45, low=500),
                         [(1500 * v, 0.01, 1), (2600 * v, 0.007, 0.55),
                          (4100 * v, 0.004, 0.3), (260, 0.015, 0.35)], 0.2),
                     0.33)

    def small(v):
        return level(hit(0.03, impulse(0.25, low=1000),
                         [(2900 * v, 0.005, 1), (4400 * v, 0.003, 0.5)], 0.25),
                     0.2)

    def stab():
        return 0.22 * hit(0.03, impulse(0.1, low=2500), [(3400, 0.006, 1),
                                                         (5200, 0.004, 0.5)])

    def thud():
        return level(hit(0.06, impulse(1.0, high=800),
                         [(240, 0.02, 1), (520, 0.01, 0.4)]), 0.4)
    return keyboard(key, space, big, small, stab,
                    lambda: swish(0.22, 500, 4000, 0.16), thud)


def theme_membrane():
    """An office keyboard's rubber domes: dull, soft and muted."""
    def key(v):
        return level(hit(0.05, impulse(1.2, high=1600),
                         [(650 * v, 0.008, 1), (1200 * v, 0.005, 0.5),
                          (220, 0.015, 0.55)]), 0.36)

    def space():
        return level(mix(hit(0.08, impulse(1.5, high=1200),
                             [(420, 0.014, 1), (800, 0.008, 0.5),
                              (150, 0.02, 0.6)]),
                         at(0.25 * hit(0.03, impulse(0.4, high=3000),
                                       [(1500, 0.006, 1)]), 0.01)), 0.4)

    def big(v):
        return level(hit(0.06, impulse(1.3, high=1400),
                         [(520 * v, 0.01, 1), (950 * v, 0.006, 0.5),
                          (190, 0.016, 0.55)]), 0.38)

    def small(v):
        return level(hit(0.04, impulse(1.0, high=2000),
                         [(820 * v, 0.006, 1), (1500 * v, 0.004, 0.4)]), 0.24)

    def stab():
        return 0.25 * hit(0.03, impulse(0.4, high=3000), [(1300, 0.008, 1)])

    def thud():
        return level(hit(0.07, impulse(2.0, high=600),
                         [(170, 0.02, 1), (380, 0.01, 0.4)]), 0.42)
    return keyboard(key, space, big, small, stab,
                    lambda: swish(0.22, 300, 2500, 0.18), thud)


def theme_mechanical_clicky():
    """Clicky mechanical switches, like Cherry MX Blue: a sharp click as the
    switch trips, then the key cap bottoming out, and a softer click as it
    comes back up."""
    def click(v, peak=1.0):
        return peak * hit(0.02, impulse(0.12, low=2000),
                          [(4800 * v, 0.004, 1), (7000 * v, 0.002, 0.5)], 0.5)

    def bottom(v, low=1.0):
        return hit(0.07, impulse(0.5, high=7000),
                   [(1500 * v * low, 0.011, 1), (2900 * v * low, 0.007, 0.6),
                    (520 * low, 0.02, 0.5), (180, 0.03, 0.3)], 0.15)

    def key(v):
        return level(mix(click(v, 0.8), at(bottom(v), 0.012),
                         at(click(v * 0.97, 0.3), 0.075)), 0.45)

    def space():
        return level(mix(click(0.85, 0.6), at(bottom(0.75, 0.55), 0.014),
                         at(stab(), 0.02)), 0.5)

    def big(v):
        return level(mix(click(v * 0.95, 0.7), at(bottom(v, 0.8), 0.013)),
                     0.47)

    def small(v):
        return level(mix(click(v * 1.05, 0.7), at(bottom(v * 1.1), 0.01)),
                     0.3)

    def stab():
        return 0.28 * hit(0.03, impulse(0.1, low=2500),
                          [(3300, 0.008, 1), (5100, 0.005, 0.5)])

    def thud():
        return level(hit(0.07, impulse(1.5, high=900),
                         [(200, 0.025, 1), (480, 0.012, 0.4)]), 0.45)
    return keyboard(key, space, big, small, stab,
                    lambda: swish(0.22, 500, 5000, 0.18), thud)


def theme_mechanical_thocky():
    """Lubricated linear mechanical switches in a solid case: a deep, round
    'thock' as the key bottoms out, with no click."""
    def bottom(v, low=1.0, seconds=0.08):
        return hit(seconds, impulse(0.8, high=2500),
                   [(420 * v * low, 0.03, 1), (780 * v * low, 0.018, 0.6),
                    (1300 * v * low, 0.01, 0.35), (160, 0.04, 0.4)], 0.05)

    def key(v):
        return level(mix(bottom(v), at(0.25 * hit(0.03, impulse(0.4,
                                                                 high=4000),
                                                  [(1600 * v, 0.006, 1)]),
                                       0.07)), 0.45)

    def space():
        return level(mix(bottom(0.9, 0.65, 0.11), at(stab(), 0.004)), 0.5)

    def big(v):
        return level(bottom(v, 0.82, 0.09), 0.47)

    def small(v):
        return level(bottom(v * 1.25, 1.0, 0.05), 0.28)

    def stab():
        return 0.18 * hit(0.03, impulse(0.2, high=5000), [(2700, 0.008, 1)])

    def thud():
        return level(hit(0.08, impulse(2.0, high=500),
                         [(130, 0.03, 1), (300, 0.012, 0.3)]), 0.45)
    return keyboard(key, space, big, small, stab,
                    lambda: swish(0.25, 300, 3000, 0.18), thud)


def escapement(v=1.0, peak=0.25):
    """The tick of a typewriter or brailler carriage moving a step."""
    return peak * hit(0.025, impulse(0.1, low=2000),
                      [(4200 * v, 0.004, 1), (6300 * v, 0.003, 0.4),
                       (1100 * v, 0.006, 0.3)], 0.4)


def ratchet(count, step, v=1.0, peak=0.22, rise=0.0):
    return seq([escapement(v * (1 + rise * i), peak) for i in range(count)],
               step)


def theme_typewriter():
    """A manual typewriter: a type bar striking the platen with a metallic
    ring, the carriage stepping, and the bell and carriage return."""
    def strike(v, peak=0.5):
        return level(hit(0.12, impulse(0.3),
                         [(1150 * v, 0.045, 1), (2450 * v, 0.035, 0.7),
                          (3700 * v, 0.022, 0.5), (6100 * v, 0.012, 0.3),
                          (160, 0.03, 0.6)], 0.3), peak)

    def key_thump(v=1.0, peak=0.35):
        return level(hit(0.06, impulse(1.0, high=2000),
                         [(260 * v, 0.018, 1), (620 * v, 0.01, 0.4)]), peak)

    def carriage_return():
        slide = 0.3 * noise_env(0.28, 0.02, 250, 1500,
                                np.linspace(1, 0.4, samples(0.28)))
        return mix(ratchet(3, 0.025, 0.9, 0.3), at(slide, 0.08),
                   at(key_thump(0.7, 0.45), 0.36))

    def type_(v=1, dots=3):
        return mix(strike(v), at(escapement(v), 0.03))
    return {
        'type': type_,
        'space': lambda: mix(key_thump(1.0, 0.35), at(escapement(), 0.02)),
        'delete': lambda: mix(key_thump(0.85, 0.35), at(escapement(0.8), 0.02),
                              at(escapement(0.8, 0.15), 0.04)),
        'newline': carriage_return,
        'submit': lambda: mix(bell(2100, 0.6, 0.3),
                              at(ratchet(10, 0.013, 1.0, 0.2), 0.15),
                              at(swish(0.3, 800, 5000, 0.2), 0.3)),
        'move': lambda: escapement(1.1, 0.18),
        'select': lambda: ratchet(2, 0.05, 1.1, 0.2),
        'toggle': lambda: level(hit(0.1, impulse(1.0, high=3000),
                                    [(180, 0.03, 1), (430, 0.02, 0.6),
                                     (1100, 0.012, 0.3)]), 0.45),
        'menu-open': lambda: mix(ratchet(6, 0.018, 0.9, 0.2, 0.03),
                                 at(key_thump(0.8, 0.35), 0.11)),
        'menu-close': lambda: mix(ratchet(6, 0.018, 1.05, 0.2, -0.03),
                                  at(key_thump(0.8, 0.35), 0.11)),
        # Two type bars jamming together.
        'error': lambda: level(mix(strike(0.95), at(strike(1.07), 0.018)),
                               0.5),
    }


def theme_perkins():
    """A Perkins brailler: the heavy key levers and embossing head thunking
    into the paper, the carriage stepping along, the line space lever and
    the bell."""
    def emboss(v, peak=0.55):
        return level(hit(0.1, impulse(0.6, high=5000),
                         [(210 * v, 0.03, 1), (480 * v, 0.02, 0.6),
                          (1250 * v, 0.015, 0.5), (2700 * v, 0.01, 0.35)],
                         0.15), peak)

    def lever(v, peak=0.4):
        return level(hit(0.08, impulse(1.0, high=3000),
                         [(170 * v, 0.025, 1), (400 * v, 0.015, 0.5),
                          (950 * v, 0.01, 0.3)]), peak)

    def type_(v=1, dots=3):
        # More dots, more pins: a slightly heavier and brighter thunk.
        heavier = 0.85 + 0.03 * dots
        return mix(emboss(v, 0.4 + 0.03 * dots),
                   at(level(emboss(v * 1.02), 0.15 * heavier), 0.004),
                   at(escapement(v * 0.8, 0.25), 0.055))
    return {
        'type': type_,
        'space': lambda: mix(lever(1.0, 0.35), at(escapement(0.8, 0.28), 0.04)),
        'delete': lambda: mix(lever(0.9, 0.35), at(escapement(0.7, 0.28), 0.04),
                              at(escapement(0.65, 0.12), 0.055)),
        'newline': lambda: mix(lever(0.75, 0.5), at(ratchet(3, 0.03, 0.7, 0.2),
                                                    0.04)),
        'submit': lambda: mix(ratchet(14, 0.02, 0.75, 0.2, 0.004),
                              at(swish(0.35, 600, 4000, 0.2), 0.2),
                              at(lever(0.7, 0.45), 0.5)),
        'move': lambda: escapement(0.8, 0.2),
        'select': lambda: ratchet(2, 0.05, 0.85, 0.22),
        'toggle': lambda: lever(1.1, 0.4),
        'menu-open': lambda: ratchet(3, 0.05, 0.75, 0.25, 0.12),
        'menu-close': lambda: ratchet(3, 0.05, 0.95, 0.25, -0.1),
        # The bell that warns of the end of the line.
        'error': lambda: bell(1850, 0.5, 0.3),
    }


def theme_slate():
    """A slate and stylus: the stylus punching each dot through the paper
    into the slate, one after another, the stylus moving between cells and
    the slate being moved down the page."""
    def punch(v, peak=0.3):
        tip = hit(0.03, impulse(0.12, low=1500),
                  [(3100 * v, 0.005, 1), (4700 * v, 0.003, 0.6),
                   (1100 * v, 0.006, 0.4)], 0.4)
        paper = noise_env(0.012, 0.001, 2000, 7000,
                          np.exp(-np.arange(samples(0.012)) / samples(0.003)))
        return level(mix(tip, 0.4 * paper), peak)

    def tap(v, peak=0.22):
        return level(hit(0.03, impulse(0.3, high=6000),
                         [(1600 * v, 0.006, 1), (2900 * v, 0.004, 0.4)]), peak)

    def slate_clack(v=1.0, peak=0.4):
        return level(hit(0.1, impulse(0.3),
                         [(900 * v, 0.03, 1), (2100 * v, 0.025, 0.6),
                          (3400 * v, 0.015, 0.4), (5600 * v, 0.008, 0.2)],
                         0.2), peak)

    def type_(v=1, dots=3):
        parts = []
        for i in range(max(1, dots)):
            parts.append(at(punch(v * rng.uniform(0.96, 1.04)),
                            i * rng.uniform(0.055, 0.075)))
        return mix(*parts)

    def erase():
        n = samples(0.14)
        rub = 0.5 + 0.5 * np.sin(2 * np.pi * 22 * np.arange(n) / RATE)
        return level(noise_env(0.14, 0.01, 700, 3000, rub), 0.3)
    return {
        'type': type_,
        'space': lambda: mix(tap(1.0), at(0.4 * noise_env(
            0.03, 0.005, 2500, 6000, np.hanning(samples(0.03))), 0.01)),
        'delete': erase,
        'newline': lambda: seq([slate_clack(1.0, 0.35),
                                slate_clack(0.9, 0.4)], 0.09),
        'submit': lambda: mix(swish(0.4, 700, 5000, 0.3),
                              at(slate_clack(0.85, 0.35), 0.38)),
        'move': lambda: tap(1.2, 0.15),
        'select': lambda: seq([tap(1.2, 0.18), tap(1.2, 0.18)], 0.05),
        'toggle': lambda: slate_clack(1.3, 0.28),
        'menu-open': lambda: seq([tap(f) for f in (0.9, 1.1, 1.35)], 0.06),
        'menu-close': lambda: seq([tap(f) for f in (1.35, 1.1, 0.9)], 0.06),
        # The stylus slipping across the paper.
        'error': lambda: mix(level(noise_env(0.12, 0.005, 1500, 4000, np.linspace(
            1, 0.2, samples(0.12))), 0.3), at(tap(0.7, 0.25), 0.1)),
    }


THEMES = [
    ('phone', theme_phone),
    ('scissor', theme_scissor),
    ('membrane', theme_membrane),
    ('mechanical-clicky', theme_mechanical_clicky),
    ('mechanical-thocky', theme_mechanical_thocky),
    ('typewriter', theme_typewriter),
    ('perkins', theme_perkins),
    ('slate-and-stylus', theme_slate),
]

# The number of dots in the braille letters of the demo.
DOTS = {'h': 3, 'e': 2, 'l': 3, 'o': 3, 'w': 4, 'r': 4, 'd': 3, 'k': 2}


def demo(sounds):
    """Types "hello wrold", fixes the typo, adds a line, types "ok", moves
    the cursor, opens and closes the menu, makes a mistake and sends, at
    the pace of someone typing braille."""
    events = []

    def typed(word):
        for letter in word:
            events.append((sounds['type'](rng.uniform(0.97, 1.03),
                                          DOTS[letter]),
                           rng.uniform(0.3, 0.4)))
    typed('hello')
    events.append((sounds['space'](), 0.4))
    typed('wrold')
    for _ in range(3):
        events.append((sounds['delete'](), 0.32))
    typed('rld')
    events.append((sounds['newline'](), 0.7))
    typed('ok')
    events.append((sounds['move'](), 0.28))
    events.append((sounds['move'](), 0.4))
    events.append((sounds['menu-open'](), 0.7))
    events.append((sounds['menu-close'](), 0.6))
    events.append((sounds['error'](), 0.7))
    events.append((sounds['submit'](), 0.8))
    total = sum(gap for _, gap in events) + 1.0
    out = np.zeros(samples(total))
    pos = 0.0
    for sound, gap in events:
        start = samples(pos)
        out[start:start + len(sound)] += sound
        pos += gap
    return out


def write(path, x):
    x = np.clip(x, -1, 1)
    with wave.open(path, 'wb') as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes((x * 32767).astype('<i2').tobytes())


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else 'sound-themes'
    os.makedirs(out_dir, exist_ok=True)
    for number, (name, make) in enumerate(THEMES, 1):
        sounds = make()
        prefix = os.path.join(out_dir, '%d-%s-' % (number, name))
        write(prefix + '00-demo.wav', demo(sounds))
        for i, sound in enumerate(SOUNDS, 1):
            write(prefix + '%02d-%s.wav' % (i, sound), sounds[sound]())
        print('%d-%s' % (number, name))


if __name__ == '__main__':
    main()
