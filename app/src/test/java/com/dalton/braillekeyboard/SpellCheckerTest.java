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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import android.view.textservice.SuggestionsInfo;

public class SpellCheckerTest {

    private static List<String> words(String text) {
        List<String> words = new ArrayList<String>();
        for (int[] word : SpellChecker.findWords(text)) {
            words.add(text.substring(word[0], word[1]));
        }
        return words;
    }

    @Test
    public void findsWordsWithApostrophesInside() {
        assertEquals(Arrays.asList("I", "don't", "know", "Jo's",
                "dog"), words("I don't know. Jo's dog!"));
        // Quotes around a word aren't part of it.
        assertEquals(Arrays.asList("hi", "there"),
                words("'hi' \u2018there\u2019"));
    }

    @Test
    public void skipsWhatIsntAPlainWord() {
        assertEquals(Arrays.asList("the", "place"),
                words("the 3rd place"));
        assertEquals(Arrays.asList("mail"),
                words("mail me@example.com"));
        assertEquals(Arrays.asList("see"),
                words("see example.com/page"));
        assertEquals(Arrays.asList("end"), words("end."));
    }

    @Test
    public void tellsTyposApart() {
        int typo = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO;
        int known = SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY;
        assertTrue(SpellChecker.isMisspelled(typo));
        assertFalse(SpellChecker.isMisspelled(known));
        // Words the spell checker doesn't know, but aren't typos.
        assertFalse(SpellChecker.isMisspelled(0));
    }
}
