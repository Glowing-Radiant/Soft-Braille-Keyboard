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

import org.junit.Test;

public class AutoCorrectTest {

    @Test
    public void findsTheWordBeforeTheSpace() {
        assertEquals("teh", AutoCorrect.wordBeforeSpace("teh "));
        assertEquals("wrod", AutoCorrect.wordBeforeSpace("I typed a wrod "));
        assertEquals("dont", AutoCorrect.wordBeforeSpace("so\ndont "));
        assertEquals("don't", AutoCorrect.wordBeforeSpace("I don't "));
        assertEquals("qoute", AutoCorrect.wordBeforeSpace("a \"qoute "));
        assertEquals("paren", AutoCorrect.wordBeforeSpace("(paren "));
    }

    @Test
    public void ignoresWhatIsNotAPlainWord() {
        // Not after a space.
        assertNull(AutoCorrect.wordBeforeSpace("teh"));
        // Punctuation, numbers, addresses and mentions.
        assertNull(AutoCorrect.wordBeforeSpace("end. "));
        assertNull(AutoCorrect.wordBeforeSpace("3rd "));
        assertNull(AutoCorrect.wordBeforeSpace("@nmae "));
        assertNull(AutoCorrect.wordBeforeSpace("example.com/pgae "));
        // Two spaces or nothing at all.
        assertNull(AutoCorrect.wordBeforeSpace("word  "));
        assertNull(AutoCorrect.wordBeforeSpace(" "));
        assertNull(AutoCorrect.wordBeforeSpace(null));
        // Possessives and closing quotes are left alone.
        assertNull(AutoCorrect.wordBeforeSpace("the dogs' "));
    }

    @Test
    public void keepsTheCaseOfTheTypedWord() {
        assertEquals("the", AutoCorrect.matchCase("teh", "the"));
        assertEquals("The", AutoCorrect.matchCase("Teh", "the"));
        assertEquals("THE", AutoCorrect.matchCase("TEH", "the"));
        assertEquals("I", AutoCorrect.matchCase("i", "I"));
        assertEquals("London", AutoCorrect.matchCase("londn", "London"));
    }
}
