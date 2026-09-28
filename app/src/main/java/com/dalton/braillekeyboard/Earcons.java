/*
 * Copyright (C) 2026 The Soft Braille Keyboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dalton.braillekeyboard;

import java.util.EnumMap;
import java.util.Map;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Short sounds that tell actions apart without listening to speech, in the
 * spirit of Braille Screen Input on the iPhone: a tick for each character, a
 * knock for space, a falling tone for delete, a rising chime for a new line
 * and a swoosh for sending. The sounds are synthesised, so there are no
 * recordings to ship. They play on the accessibility volume, like the speech.
 */
public class Earcons {
    /** The sounds, one for each kind of action. */
    public enum Sound {
        TYPE, SPACE, DELETE, NEWLINE, SUBMIT, MOVE, SELECT, TOGGLE,
        MENU_OPEN, MENU_CLOSE, ERROR
    }

    private static final int SAMPLE_RATE = 22050;

    private final Map<Sound, AudioTrack> tracks = new EnumMap<Sound, AudioTrack>(
            Sound.class);

    /** Plays a sound, cutting off the same sound if it is still playing. */
    public void play(Sound sound) {
        AudioTrack track = tracks.get(sound);
        if (track == null) {
            track = createTrack(synthesise(sound));
            if (track == null) {
                return;
            }
            tracks.put(sound, track);
        }
        try {
            if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                track.stop();
            }
            track.reloadStaticData();
            track.play();
        } catch (IllegalStateException e) {
            // The audio system dropped the track; make it again next time.
            track.release();
            tracks.remove(sound);
        }
    }

    /** Releases the audio tracks. The sounds are made again when played. */
    public void release() {
        for (AudioTrack track : tracks.values()) {
            track.release();
        }
        tracks.clear();
    }

    private static AudioTrack createTrack(short[] samples) {
        try {
            AudioTrack track = new AudioTrack(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(), new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                    samples.length * 2, AudioTrack.MODE_STATIC,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (track.write(samples, 0, samples.length) != samples.length) {
                track.release();
                return null;
            }
            return track;
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            return null;
        }
    }

    static short[] synthesise(Sound sound) {
        switch (sound) {
        case TYPE:
            return tone(0.25, new double[] { 2200 }, 0.018, 0.004);
        case SPACE:
            return sweep(0.45, 520, 320, 0.05);
        case DELETE:
            return sweep(0.4, 1000, 450, 0.08);
        case NEWLINE:
            return tone(0.35, new double[] { 660, 990 }, 0.07, 0.03);
        case SUBMIT:
            return sweep(0.4, 450, 1800, 0.2);
        case MOVE:
            return tone(0.18, new double[] { 1400 }, 0.012, 0.003);
        case SELECT:
            return tone(0.25, new double[] { 1500, 1500 }, 0.02, 0.02);
        case TOGGLE:
            return tone(0.3, new double[] { 880, 1320 }, 0.04, 0.01);
        case MENU_OPEN:
            return tone(0.3, new double[] { 523, 659, 784 }, 0.05, 0.01);
        case MENU_CLOSE:
            return tone(0.3, new double[] { 784, 659, 523 }, 0.05, 0.01);
        case ERROR:
            return tone(0.4, new double[] { 196, 185 }, 0.07, 0.0);
        default:
            return new short[1];
        }
    }

    // A sequence of notes, each a sine with a soft overtone that dies away.
    private static short[] tone(double volume, double[] frequencies,
            double noteSeconds, double gapSeconds) {
        int note = (int) (noteSeconds * SAMPLE_RATE);
        int gap = (int) (gapSeconds * SAMPLE_RATE);
        short[] samples = new short[frequencies.length * (note + gap)];
        for (int n = 0; n < frequencies.length; n++) {
            int offset = n * (note + gap);
            double step = 2 * Math.PI * frequencies[n] / SAMPLE_RATE;
            for (int i = 0; i < note; i++) {
                double value = Math.sin(step * i) + 0.3
                        * Math.sin(2 * step * i);
                samples[offset + i] = toSample(volume * envelope(i, note)
                        * value / 1.3);
            }
        }
        return samples;
    }

    // A tone gliding from one frequency to another.
    private static short[] sweep(double volume, double from, double to,
            double seconds) {
        int length = (int) (seconds * SAMPLE_RATE);
        short[] samples = new short[length];
        double phase = 0;
        for (int i = 0; i < length; i++) {
            double frequency = from + (to - from) * i / length;
            phase += 2 * Math.PI * frequency / SAMPLE_RATE;
            double value = Math.sin(phase) + 0.3 * Math.sin(2 * phase);
            samples[i] = toSample(volume * envelope(i, length) * value / 1.3);
        }
        return samples;
    }

    // A quick attack, so there is no click, then an exponential decay.
    private static double envelope(int i, int length) {
        int attack = Math.min(length / 4, SAMPLE_RATE / 400);
        if (i < attack) {
            return (double) i / attack;
        }
        double decay = Math.exp(-4.0 * (i - attack) / (length - attack));
        // Fade the last samples to silence.
        int release = Math.min(length / 8, SAMPLE_RATE / 500);
        if (i > length - release) {
            decay *= (double) (length - i) / release;
        }
        return decay;
    }

    private static short toSample(double value) {
        return (short) Math.round(Math.max(-1, Math.min(1, value))
                * Short.MAX_VALUE);
    }
}
