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

"""Synthesises the abstract sound themes shipped with the keyboard.

Each theme is written as a folder of OGG files in the app's assets, one per
sound, named as SoundThemes expects: type-1.ogg to type-3.ogg (slightly
different pitches, so a stream of key presses isn't tiring), space.ogg,
delete.ogg, newline.ogg, submit.ogg, move.ogg, select.ogg, toggle.ogg,
menu-open.ogg, menu-close.ogg and error.ogg, with a theme.properties naming
the theme.

Needs numpy and ffmpeg with the Vorbis encoder.

Usage: generate_abstract_themes.py [assets sounds directory]
"""

import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

RATE = 44100
SOUNDS = ['type', 'space', 'delete', 'newline', 'submit', 'move', 'select',
          'toggle', 'menu-open', 'menu-close', 'error']
TYPE_VARIATIONS = [0.97, 1.0, 1.03]

rng = np.random.default_rng(7)


def t(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def decay(seconds, time_constant, attack=0.002):
    """An envelope with a short linear attack and an exponential decay."""
    x = t(seconds)
    env = np.exp(-x / time_constant)
    a = max(1, int(attack * RATE))
    env[:a] *= np.linspace(0, 1, a)
    # Fade the tail to silence so it doesn't click.
    r = min(len(env) // 8, int(0.004 * RATE))
    if r > 0:
        env[-r:] *= np.linspace(1, 0, r)
    return env


def sine(freq, seconds, phase=0.0):
    """A sine whose frequency may be a constant or an array (a glide)."""
    n = int(seconds * RATE)
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return np.sin(phase + 2 * np.pi * np.cumsum(f) / RATE)


def glide(start, end, seconds, curve=1.0):
    x = np.linspace(0, 1, int(seconds * RATE)) ** curve
    return start * (end / start) ** x


def noise(seconds):
    return rng.uniform(-1, 1, int(seconds * RATE))


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, centre, q):
    """A resonant two-pole band-pass filter. The centre may glide."""
    n = len(x)
    centre = np.broadcast_to(np.asarray(centre, dtype=float), (n,))
    y = np.zeros(n)
    y1 = y2 = x1 = x2 = 0.0
    for i in range(n):
        w = 2 * np.pi * centre[i] / RATE
        alpha = np.sin(w) / (2 * q)
        b0, b2 = alpha, -alpha
        a0, a1, a2 = 1 + alpha, -2 * np.cos(w), 1 - alpha
        v = (b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1 = x1, x[i]
        y2, y1 = y1, v
        y[i] = v
    return y


def modal(freq, partials, seconds, time_constant, attack=0.0005):
    """Struck objects: damped sines at the given frequency ratios and levels.
    Higher partials die away faster, like wood or glass."""
    out = np.zeros(int(seconds * RATE))
    for ratio, level_ in partials:
        out += level_ * sine(freq * ratio, seconds) * decay(
            seconds, time_constant / ratio ** 0.7, attack)
    return out


def seq(*parts, gap=0.0):
    """Plays sounds one after another, each after the given gap."""
    pieces = []
    silence = np.zeros(int(gap * RATE))
    for i, p in enumerate(parts):
        if i:
            pieces.append(silence)
        pieces.append(p)
    return np.concatenate(pieces)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def level(x, peak):
    m = np.max(np.abs(x))
    return x * (peak / m) if m else x


# Each theme maps a sound name to a function returning the samples. The type
# sound takes a pitch variation.

def theme_soft():
    """Soft ticks: quiet, short and neutral."""
    def tick(f, s=0.02, peak=0.3):
        return level(sine(f, s) * decay(s, s / 5) + 0.2 * highpass(
            noise(s), 3000) * decay(s, 0.002), peak)
    return {
        'type': lambda v=1: tick(2300 * v, 0.02, 0.28),
        'space': lambda: level(sine(glide(420, 300, 0.06), 0.06)
                               * decay(0.06, 0.015), 0.45),
        'delete': lambda: level(sine(glide(1200, 600, 0.06), 0.06)
                                * decay(0.06, 0.02), 0.38),
        'newline': lambda: seq(tick(880, 0.07, 0.35), tick(1320, 0.1, 0.35),
                               gap=0.02),
        'submit': lambda: level(bandpass(noise(0.26), glide(500, 4000, 0.26),
                                         3) * decay(0.26, 0.09, 0.08), 0.4),
        'move': lambda: tick(1600, 0.012, 0.18),
        'select': lambda: seq(tick(1800, 0.02, 0.25), tick(1800, 0.02, 0.25),
                              gap=0.03),
        'toggle': lambda: seq(tick(1000, 0.04, 0.3), tick(1500, 0.05, 0.3),
                              gap=0.01),
        'menu-open': lambda: seq(*(tick(f, 0.05, 0.3) for f in
                                   (523, 659, 784)), gap=0.01),
        'menu-close': lambda: seq(*(tick(f, 0.05, 0.3) for f in
                                    (784, 659, 523)), gap=0.01),
        'error': lambda: level(sine(190, 0.14) * decay(0.14, 0.05)
                               + 0.5 * sine(201, 0.14) * decay(0.14, 0.05),
                               0.45),
    }


def theme_wood():
    """Wood blocks and marimba: warm, round knocks."""
    wood = [(1, 1.0), (2.76, 0.35), (5.4, 0.12)]
    marimba = [(1, 1.0), (4.0, 0.25), (9.9, 0.05)]

    def block(f, s=0.06, tc=0.012, peak=0.4):
        return level(modal(f, wood, s, tc), peak)

    def bar(f, s=0.25, peak=0.35):
        return level(modal(f, marimba, s, 0.07), peak)
    return {
        'type': lambda v=1: block(1250 * v, 0.045, 0.008, 0.32),
        'space': lambda: block(520, 0.09, 0.02, 0.5),
        'delete': lambda: seq(block(900, 0.04, 0.008, 0.35),
                              block(650, 0.05, 0.01, 0.35), gap=0.015),
        'newline': lambda: seq(bar(1047, 0.12), bar(1568, 0.3), gap=0.0),
        'submit': lambda: seq(*(bar(f, 0.09) for f in (784, 988, 1175)),
                              bar(1568, 0.35)),
        'move': lambda: block(1900, 0.02, 0.004, 0.2),
        'select': lambda: seq(block(1500, 0.03, 0.006, 0.3),
                              block(1500, 0.03, 0.006, 0.3), gap=0.03),
        'toggle': lambda: seq(bar(880, 0.08), bar(1320, 0.15)),
        'menu-open': lambda: seq(*(bar(f, 0.07) for f in (523, 659, 784)),
                                 bar(1047, 0.2)),
        'menu-close': lambda: seq(*(bar(f, 0.07) for f in (1047, 784, 659)),
                                  bar(523, 0.2)),
        'error': lambda: level(modal(180, wood, 0.16, 0.03), 0.5),
    }


def theme_typewriter():
    """A manual typewriter: key clacks, a carriage return and its bell."""
    def clack(v=1, s=0.05, body=190, peak=0.5, bright=2500):
        click = highpass(noise(s), bright * v) * decay(s, 0.003, 0.0003)
        thump = sine(body * v, s) * decay(s, 0.012, 0.0005)
        return level(click + 0.6 * thump, peak)

    def bell(f=2100, s=0.6, peak=0.3):
        return level(modal(f, [(1, 1), (2.32, 0.4), (4.25, 0.2)], s, 0.25),
                     peak)

    def ratchet(n=7, step=0.022, v=1):
        return seq(*(clack(v * (1 + 0.01 * i), 0.012, 400, 0.25, 3500)
                     for i in range(n)), gap=step - 0.012)
    return {
        'type': lambda v=1: clack(v, 0.05, 190, 0.45),
        'space': lambda: clack(0.8, 0.07, 140, 0.5, 1500),
        'delete': lambda: seq(clack(0.9, 0.035, 170, 0.4),
                              clack(0.75, 0.05, 150, 0.4), gap=0.02),
        'newline': lambda: mix(ratchet(8), np.concatenate(
            [np.zeros(int(0.05 * RATE)), bell()])),
        'submit': lambda: mix(level(bandpass(noise(0.3), glide(800, 3000, 0.3),
                                             2) * decay(0.3, 0.1, 0.1), 0.35),
                              np.concatenate([np.zeros(int(0.2 * RATE)),
                                              bell(2600, 0.5, 0.25)])),
        'move': lambda: clack(1.4, 0.02, 400, 0.2, 5000),
        'select': lambda: seq(clack(1.2, 0.025, 300, 0.3),
                              clack(1.2, 0.025, 300, 0.3), gap=0.03),
        'toggle': lambda: seq(clack(1.1, 0.03, 260, 0.35), clack(0.8, 0.05, 160,
                                                                 0.4), gap=0.04),
        'menu-open': lambda: ratchet(4, 0.03, 1.2),
        'menu-close': lambda: ratchet(4, 0.03, 0.8),
        'error': lambda: level(sine(95, 0.12) * decay(0.12, 0.04)
                               + 0.3 * lowpass(noise(0.12), 600)
                               * decay(0.12, 0.02), 0.55),
    }


def theme_perkins():
    """A Perkins brailler: deep embossing thunks, the carriage and line lever."""
    def thunk(v=1, s=0.07, peak=0.55):
        body = modal(160 * v, [(1, 1), (2.3, 0.5), (3.9, 0.2)], s, 0.018)
        click = bandpass(noise(s), 1800 * v, 4) * decay(s, 0.004, 0.0003)
        return level(body + 1.5 * click, peak)

    def carriage(s=0.03, v=1):
        return level(bandpass(noise(s), 3200 * v, 6) * decay(s, 0.006,
                                                             0.0005), 0.25)

    def bell(peak=0.28):
        return level(modal(1850, [(1, 1), (2.4, 0.35)], 0.5, 0.2), peak)
    return {
        'type': lambda v=1: mix(thunk(v), np.concatenate(
            [np.zeros(int(0.045 * RATE)), carriage(0.025, v)])),
        'space': lambda: mix(thunk(0.75, 0.06, 0.4), np.concatenate(
            [np.zeros(int(0.03 * RATE)), carriage(0.03)])),
        'delete': lambda: seq(carriage(0.02, 0.8), thunk(0.7, 0.06, 0.45),
                              gap=0.01),
        'newline': lambda: seq(*(carriage(0.02, 0.9 + 0.03 * i)
                                 for i in range(6)), thunk(0.6, 0.1, 0.5),
                               gap=0.012),
        'submit': lambda: seq(thunk(0.6, 0.08, 0.5), bell()),
        'move': lambda: carriage(0.018, 1.2),
        'select': lambda: seq(carriage(0.02, 1.3), carriage(0.02, 1.3),
                              gap=0.03),
        'toggle': lambda: seq(thunk(1.1, 0.05, 0.4), thunk(0.8, 0.06, 0.4),
                              gap=0.03),
        'menu-open': lambda: seq(*(carriage(0.02, 0.9 + 0.15 * i)
                                   for i in range(4)), gap=0.02),
        'menu-close': lambda: seq(*(carriage(0.02, 1.35 - 0.15 * i)
                                    for i in range(4)), gap=0.02),
        'error': lambda: level(modal(90, [(1, 1), (2.1, 0.4)], 0.18, 0.05),
                               0.6),
    }


def theme_bubble():
    """Water drops: little pops whose pitch jumps up, bloops that fall."""
    def drop(f=900, s=0.05, rise=2.0, peak=0.35):
        return level(sine(glide(f, f * rise, s, 0.5), s) * decay(s, s / 3,
                                                                0.001), peak)
    return {
        'type': lambda v=1: drop(800 * v, 0.035, 1.9, 0.3),
        'space': lambda: drop(380, 0.07, 1.8, 0.45),
        'delete': lambda: drop(1300, 0.07, 0.45, 0.4),
        'newline': lambda: seq(drop(500, 0.06, 1.8), drop(800, 0.09, 1.9),
                               gap=0.03),
        'submit': lambda: seq(*(drop(500 + 180 * i, 0.04, 1.8, 0.3)
                                for i in range(6)), gap=0.015),
        'move': lambda: drop(1500, 0.02, 1.5, 0.18),
        'select': lambda: seq(drop(1200, 0.03, 1.6, 0.28),
                              drop(1200, 0.03, 1.6, 0.28), gap=0.03),
        'toggle': lambda: seq(drop(700, 0.05, 1.8), drop(1000, 0.06, 1.9),
                              gap=0.02),
        'menu-open': lambda: seq(*(drop(f, 0.05, 1.7) for f in
                                   (500, 650, 850)), gap=0.02),
        'menu-close': lambda: seq(*(drop(f, 0.05, 0.6) for f in
                                    (1300, 1000, 800)), gap=0.02),
        'error': lambda: drop(260, 0.14, 0.55, 0.5),
    }


def theme_glass():
    """Glass and chimes: bright, clear and short."""
    glass = [(1, 1), (2.32, 0.5), (4.25, 0.25), (6.63, 0.1)]

    def ping(f, s=0.08, tc=0.03, peak=0.3):
        return level(modal(f, glass, s, tc), peak)
    return {
        'type': lambda v=1: ping(2600 * v, 0.03, 0.008, 0.22),
        'space': lambda: ping(1300, 0.06, 0.015, 0.35),
        'delete': lambda: level(sine(glide(2000, 1100, 0.07), 0.07)
                                * decay(0.07, 0.02), 0.3),
        'newline': lambda: seq(ping(1760, 0.1, 0.05), ping(2637, 0.35, 0.12)),
        'submit': lambda: mix(seq(*(ping(f, 0.06, 0.03, 0.22) for f in
                                    (1760, 2217, 2637, 3520))),
                              level(bandpass(noise(0.3), glide(2000, 7000, 0.3),
                                             4) * decay(0.3, 0.1, 0.1), 0.15)),
        'move': lambda: ping(3400, 0.015, 0.004, 0.15),
        'select': lambda: seq(ping(3000, 0.025, 0.006, 0.22),
                              ping(3000, 0.025, 0.006, 0.22), gap=0.03),
        'toggle': lambda: seq(ping(1760, 0.06, 0.02), ping(2349, 0.12, 0.04)),
        'menu-open': lambda: seq(*(ping(f, 0.06, 0.03) for f in
                                   (1568, 1976, 2349))),
        'menu-close': lambda: seq(*(ping(f, 0.06, 0.03) for f in
                                    (2349, 1976, 1568))),
        'error': lambda: level(modal(330, glass, 0.18, 0.06)
                               + modal(349, glass, 0.18, 0.06), 0.4),
    }


# Folder in the assets, name shown in the settings, and the theme.
THEMES = [
    ('soft', 'Soft', theme_soft),
    ('wood', 'Wood', theme_wood),
    ('typewriter', 'Typewriter', theme_typewriter),
    ('perkins', 'Perkins', theme_perkins),
    ('bubble', 'Bubble', theme_bubble),
    ('glass', 'Glass', theme_glass),
]


def write_ogg(path, samples):
    """Writes mono samples as an OGG Vorbis file with ffmpeg."""
    samples = np.clip(samples, -1, 1)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        name = tmp.name
    try:
        with wave.open(name, 'wb') as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(RATE)
            f.writeframes((samples * 32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', name, '-c:a',
                        'libvorbis', '-q:a', '5', path], check=True)
    finally:
        os.remove(name)


def main():
    out_dir = (sys.argv[1] if len(sys.argv) > 1
               else os.path.join('app', 'src', 'main', 'assets', 'sounds'))
    for folder, name, make in THEMES:
        sounds = make()
        target = os.path.join(out_dir, folder)
        os.makedirs(target, exist_ok=True)
        for i, variation in enumerate(TYPE_VARIATIONS, 1):
            write_ogg(os.path.join(target, 'type-%d.ogg' % i),
                      sounds['type'](variation))
        for sound in SOUNDS[1:]:
            write_ogg(os.path.join(target, sound + '.ogg'), sounds[sound]())
        with open(os.path.join(target, 'theme.properties'), 'w',
                  encoding='utf-8', newline='\n') as f:
            f.write('name=%s\n' % name)
        print(folder)


if __name__ == '__main__':
    main()
