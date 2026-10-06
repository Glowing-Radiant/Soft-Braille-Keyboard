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
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class SpellDictionariesTest {
    private static final List<String> ENGLISH = Arrays.asList("en_GB",
            "en_US");

    @Test
    public void choosesTheDictionaryForTheCountry() {
        assertEquals("en_US", SpellDictionaries.choose(ENGLISH, new Locale(
                "en", "US"), new Locale("en", "GB")));
        assertEquals("en_GB", SpellDictionaries.choose(ENGLISH, new Locale(
                "en", "GB"), new Locale("en", "US")));
    }

    @Test
    public void choosesThePhonesCountryForALanguageOnlyTable() {
        // UEB tables are for English without a country.
        assertEquals("en_US", SpellDictionaries.choose(ENGLISH, new Locale(
                "en"), new Locale("en", "US")));
        // Otherwise the first, here British English, for India say.
        assertEquals("en_GB", SpellDictionaries.choose(ENGLISH, new Locale(
                "en"), new Locale("en", "IN")));
        assertEquals("en_GB", SpellDictionaries.choose(ENGLISH, new Locale(
                "en", "AU"), new Locale("hi", "IN")));
    }

    @Test
    public void hasNoneForOtherLanguages() {
        assertNull(SpellDictionaries.choose(ENGLISH, new Locale("de", "DE"),
                new Locale("en", "US")));
    }

    @Test
    public void readsTheLanguageFromTheName() {
        assertEquals(new Locale("de", "DE"), SpellDictionaries
                .toLocale("de_DE"));
        assertEquals(new Locale("pt", "BR"), SpellDictionaries
                .toLocale("pt-br"));
        assertEquals(new Locale("fr"), SpellDictionaries.toLocale("fr"));
        assertNull(SpellDictionaries.toLocale("index"));
        assertNull(SpellDictionaries.toLocale("en_US_large"));
    }
}
