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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Checks spelling with the bundled dictionaries through Hunspell.
 */
@RunWith(AndroidJUnit4.class)
public class SpellDictionariesDeviceTest {

    private static Hunspell load(String name) {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        Hunspell hunspell = SpellDictionaries.load(context, name);
        assertNotNull(name + " didn't load", hunspell);
        return hunspell;
    }

    @Test
    public void americanEnglish() {
        Hunspell hunspell = load("en_US");
        assertTrue(hunspell.isCorrect("receive"));
        assertTrue(hunspell.isCorrect("Receive"));
        assertTrue(hunspell.isCorrect("don't"));
        assertTrue(hunspell.isCorrect("don\u2019t"));
        assertTrue(hunspell.isCorrect("color"));
        assertFalse(hunspell.isCorrect("colour"));
        assertFalse(hunspell.isCorrect("recieve"));
        assertEquals("receive", hunspell.suggest("recieve", 1).get(0));
        assertEquals("the", hunspell.suggest("teh", 1).get(0));
    }

    @Test
    public void britishEnglish() {
        Hunspell hunspell = load("en_GB");
        assertTrue(hunspell.isCorrect("colour"));
        assertTrue(hunspell.isCorrect("organise"));
        assertFalse(hunspell.isCorrect("wrod"));
        assertFalse(hunspell.suggest("wrod", 3).isEmpty());
    }
}
