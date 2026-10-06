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

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks spelling with a Hunspell dictionary, the kind LibreOffice and
 * Firefox use: a .aff file of rules and a .dic file of words.
 */
final class Hunspell {
    static {
        System.loadLibrary("hunspellwrap");
    }

    private long handle;
    // The dictionary's encoding, which words are passed in.
    private final Charset charset;

    /**
     * Loads a dictionary. This reads the files, which takes a moment, so
     * don't do it on the main thread.
     */
    Hunspell(File aff, File dic) {
        handle = nativeOpen(aff.getAbsolutePath(), dic.getAbsolutePath());
        charset = toCharset(nativeGetEncoding(handle));
    }

    /** Whether the dictionary has the word, in any of its forms. */
    synchronized boolean isCorrect(String word) {
        return handle != 0 && nativeSpell(handle, toBytes(word));
    }

    /** Up to max suggestions for a misspelled word, best first. */
    synchronized List<String> suggest(String word, int max) {
        List<String> suggestions = new ArrayList<String>();
        if (handle == 0) {
            return suggestions;
        }
        byte[][] results = nativeSuggest(handle, toBytes(word));
        for (int i = 0; results != null && i < results.length
                && suggestions.size() < max; i++) {
            suggestions.add(new String(results[i], charset));
        }
        return suggestions;
    }

    synchronized void close() {
        if (handle != 0) {
            nativeClose(handle);
            handle = 0;
        }
    }

    private byte[] toBytes(String word) {
        // Dictionaries spell apostrophes as straight quotes.
        return word.replace('\u2019', '\'').getBytes(charset);
    }

    // Hunspell names encodings as in "SET ISO8859-1", which Java may not
    // know by that name.
    private static Charset toCharset(String encoding) {
        String name = encoding == null ? "" : encoding.trim();
        if (name.toUpperCase(Locale.ROOT).startsWith("ISO8859-")) {
            name = "ISO-8859-" + name.substring("ISO8859-".length());
        } else if (name.toLowerCase(Locale.ROOT).startsWith("microsoft-cp")) {
            name = "windows-" + name.substring("microsoft-cp".length());
        }
        try {
            return Charset.forName(name);
        } catch (RuntimeException e) {
            return Charset.forName("UTF-8");
        }
    }

    private static native long nativeOpen(String affPath, String dicPath);

    private static native void nativeClose(long handle);

    private static native String nativeGetEncoding(long handle);

    private static native boolean nativeSpell(long handle, byte[] word);

    private static native byte[][] nativeSuggest(long handle, byte[] word);
}
