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

import org.junit.Test;

// Readings are what the English UEB grade 1 and grade 2 tables give for a
// cell typed after "a".
public class CellEchoTest {

    @Test
    public void saysLettersAsTyped() {
        // Dots 1 2 after "a" contract to "about", but it is a "b" so far.
        assertEquals("b", BrailleIME.chooseCellEcho("b", "bout"));
        assertEquals("T", BrailleIME.chooseCellEcho("T", "T"));
    }

    @Test
    public void saysContractionsInTheMiddleOfAWord() {
        // Grade 1 has nothing for "ch" and reads "in" and "en" as digits.
        assertEquals("ch", BrailleIME.chooseCellEcho("", "ch"));
        assertEquals("in", BrailleIME.chooseCellEcho("9", "in"));
        assertEquals("en", BrailleIME.chooseCellEcho("5", "en"));
        assertEquals("the", BrailleIME.chooseCellEcho("\u222b", "the"));
    }

    @Test
    public void saysPunctuationAndDigits() {
        assertEquals(";", BrailleIME.chooseCellEcho(";", ";"));
        assertEquals("1", BrailleIME.chooseCellEcho("1", "1"));
        assertEquals("'", BrailleIME.chooseCellEcho("'", ""));
    }

    @Test
    public void saysNothingForTheStartOfAContraction() {
        assertEquals("", BrailleIME.chooseCellEcho("", ""));
    }
}
