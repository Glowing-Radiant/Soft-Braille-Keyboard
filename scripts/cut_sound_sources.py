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

"""Cuts single key sounds out of recordings of typing.

It finds each sound in a recording, keeps the cleanest ones, those loud
above the background and with a pause before and after, and sorts them into
groups of similar sound, so that for example the letter keys, the space bar
and the line space lever of a brailler end up in different groups. Short
recordings of one event, such as a bell, are only trimmed.

Needs ffmpeg to read MP3 and OGG files.

Usage: cut_sound_sources.py <recordings directory> <output directory>
The files are named <recording>/<recording>-group<n>-<nn>.wav.
"""

import glob
import os
import subprocess
import sys
import wave

import numpy as np
from scipy.cluster.vq import kmeans2

RATE = 44100
MAX_CUTS = 30          # Per recording.
GROUPS = 4
MIN_GAP = 0.09         # Seconds of pause wanted before and after a sound.
MAX_LENGTH = 0.8       # Longest cut, in seconds, for bells and ratchets.
SHORT_RECORDING = 4.0  # Recordings up to this long are one event.


def write(path, x):
    x = np.clip(x, -1, 1)
    with wave.open(path, 'wb') as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes((x * 32767).astype('<i2').tobytes())


def trim(x, floor, pre=0.004):
    """Trims silence, keeping a few ms before the start, and fades out."""
    env = np.abs(x)
    above = np.nonzero(env > floor)[0]
    if len(above) == 0:
        return x
    start = max(0, above[0] - int(pre * RATE))
    end = min(len(x), above[-1] + int(0.01 * RATE))
    x = x[start:end].copy()
    fade = min(len(x) // 5, int(0.01 * RATE))
    if fade:
        x[-fade:] *= np.linspace(1, 0, fade)
    return x


def normalise(x, peak=0.7):
    m = np.max(np.abs(x))
    return x * (peak / m) if m else x


def load(path):
    """Decodes an audio file to mono samples at RATE with ffmpeg."""
    pcm = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-ac', '1',
                          '-ar', str(RATE), '-f', 's16le', '-'],
                         check=True, capture_output=True).stdout
    return np.frombuffer(pcm, '<i2').astype(float) / 32768


def rms(x, frame, hop):
    """Short term loudness, one value per hop samples."""
    if len(x) < frame:
        x = np.pad(x, (0, frame - len(x)))
    count = 1 + (len(x) - frame) // hop
    idx = np.arange(frame)[None, :] + hop * np.arange(count)[:, None]
    return np.sqrt(np.mean(x[idx] ** 2, axis=1))


def onsets(y, floor, hop):
    """Where sounds start: the loudness jumps well above the background and
    above the moment just before. A new sound can't start within 35 ms."""
    env = rms(y, hop * 2, hop)
    before = np.concatenate([np.full(4, env[0]), env[:-4]])
    rising = (env > floor * 4) & (env > before * 2.5)
    found = []
    last = -10 ** 9
    for i in np.nonzero(rising)[0]:
        if (i - last) * hop >= 0.035 * RATE:
            # Back up to where the rise began.
            j = i
            while j > 0 and env[j - 1] < env[j] and env[j - 1] > floor * 1.5:
                j -= 1
            found.append(j * hop)
            last = i
    return found


def cut_recording(y):
    """Returns the cleanest single sounds of a recording, with features."""
    # Background level: a quiet percentile of the short term loudness.
    hop = 64
    floor = max(np.percentile(rms(y, 512, 256), 20), 1e-4)
    onsets_found = onsets(y, floor, hop)
    cuts = []
    for i, start in enumerate(onsets_found):
        prev = onsets_found[i - 1] if i else -RATE
        nxt = (onsets_found[i + 1] if i + 1 < len(onsets_found)
               else len(y))
        gap_before = (start - prev) / RATE
        gap_after = (nxt - start) / RATE
        if gap_before < MIN_GAP or gap_after < MIN_GAP:
            continue
        end = min(nxt, start + int(MAX_LENGTH * RATE))
        piece = y[max(0, start - int(0.003 * RATE)):end]
        # Stop where the sound has died back into the background.
        env = rms(piece, 256, 64)
        loud = np.nonzero(env > floor * 2.5)[0]
        if len(loud) == 0:
            continue
        piece = piece[:min(len(piece), (loud[-1] + 4) * 64)]
        if len(piece) < int(0.012 * RATE):
            continue
        peak_rms = np.max(env)
        snr = peak_rms / floor
        if snr < 6:
            continue
        spectrum = np.abs(np.fft.rfft(piece * np.hanning(len(piece))))
        freqs = np.fft.rfftfreq(len(piece), 1 / RATE)
        centroid = np.sum(spectrum * freqs) / max(np.sum(spectrum), 1e-9)
        cuts.append(dict(samples=piece, snr=snr,
                         clean=min(gap_before, 1.0) + min(gap_after, 1.0),
                         length=len(piece) / RATE, centroid=centroid,
                         loudness=peak_rms, floor=floor))
    # The cleanest: loud above the background, with the longest pauses.
    cuts.sort(key=lambda c: -(np.log(c['snr']) + c['clean']))
    return cuts[:MAX_CUTS], floor


def group(cuts):
    """Sorts cuts into groups of similar sound, numbered from the most
    common group."""
    if len(cuts) <= GROUPS:
        return [0] * len(cuts)
    features = np.array([[np.log(c['centroid']), np.log(c['length']),
                          np.log(c['loudness'])] for c in cuts])
    features = (features - features.mean(0)) / (features.std(0) + 1e-9)
    _, labels = kmeans2(features, GROUPS, seed=3, minit='++')
    order = [g for g, _ in sorted(((g, -np.sum(labels == g))
                                   for g in set(labels)), key=lambda p: p[1])]
    return [order.index(label) for label in labels]


def main():
    source_dir, out_dir = sys.argv[1], sys.argv[2]
    paths = sorted(glob.glob(os.path.join(source_dir, '**', '*.mp3'),
                             recursive=True)
                   + glob.glob(os.path.join(source_dir, '**', '*.ogg'),
                               recursive=True))
    for path in paths:
        name = os.path.splitext(os.path.basename(path))[0]
        y = load(path)
        target = os.path.join(out_dir, name)
        os.makedirs(target, exist_ok=True)
        duration = len(y) / RATE
        if duration <= SHORT_RECORDING:
            floor = max(np.percentile(np.abs(y), 30), 1e-4) * 3
            write(os.path.join(target, name + '.wav'),
                  normalise(trim(y, floor)))
            print('%-40s whole, %.2f s' % (name, duration))
            continue
        cuts, floor = cut_recording(y)
        if not cuts:
            # One continuous sound rather than separate ones.
            write(os.path.join(target, name + '.wav'), normalise(y))
            print('%-40s whole, no separate sounds, %.1f s' % (name, duration))
            continue
        labels = group(cuts)
        counts = {}
        for cut, label in sorted(zip(cuts, labels), key=lambda p: p[1]):
            counts[label] = counts.get(label, 0) + 1
            write(os.path.join(target, '%s-group%d-%02d.wav'
                               % (name, label + 1, counts[label])),
                  normalise(trim(cut['samples'], floor * 1.5)))
        print('%-40s %d cuts in %d groups from %.0f s' % (
            name, len(cuts), len(counts), duration))


if __name__ == '__main__':
    main()
