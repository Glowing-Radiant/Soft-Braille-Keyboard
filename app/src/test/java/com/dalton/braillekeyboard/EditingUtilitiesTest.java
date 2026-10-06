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

import java.lang.reflect.Proxy;

import org.junit.Test;

public class EditingUtilitiesTest {

    // A text field holding text with the cursor at '|'.
    private static class Field {
        StringBuilder text;
        int cursor;
        final KeyboardListener listener;

        Field(String textWithCursor) {
            cursor = textWithCursor.indexOf('|');
            text = new StringBuilder(textWithCursor.replace("|", ""));
            listener = (KeyboardListener) Proxy.newProxyInstance(
                    KeyboardListener.class.getClassLoader(),
                    new Class<?>[] { KeyboardListener.class },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                        case "getCursor":
                            return cursor;
                        case "getTextBeforeCursor":
                            return text.substring(
                                    Math.max(0, cursor - (int) args[0]),
                                    cursor);
                        case "getTextAfterCursor":
                            return text.substring(cursor, Math.min(
                                    text.length(), cursor + (int) args[0]));
                        case "setSelection":
                            cursor = (int) args[0];
                            return true;
                        default:
                            return null;
                        }
                    });
        }

        // Deletes the focused text as ActionHandler does.
        String delete(EditingUtilities.Word word) {
            text.delete(cursor, cursor + word.charsBefore);
            return text.substring(0, cursor) + "|" + text.substring(cursor);
        }
    }

    @Test
    public void deletesTheCharacterAtTheCursor() {
        Field field = new Field("ab|cd");
        EditingUtilities.Word word = EditingUtilities
                .getFocusedCharacter(field.listener);
        assertEquals("c", word.word);
        assertEquals("ab|d", field.delete(word));
    }

    @Test
    public void deletesBothHalvesOfAnEmoji() {
        Field field = new Field("a|😀b");
        EditingUtilities.Word word = EditingUtilities
                .getFocusedCharacter(field.listener);
        assertEquals("😀", word.word);
        assertEquals("a|b", field.delete(word));
    }

    @Test
    public void hasNoCharacterAtTheEnd() {
        assertNull(EditingUtilities.getFocusedCharacter(new Field("abc|").listener));
    }

    // Deletes a word as ActionHandler does.
    private static String deleteWord(String textWithCursor, boolean focused) {
        Field field = new Field(textWithCursor);
        EditingUtilities.Word word = EditingUtilities.getWordToDelete(
                field.listener, focused);
        return field.delete(word);
    }

    @Test
    public void deletesTheWordBeforeTheCursor() {
        assertEquals("one |three", deleteWord("one two|three", false));
        assertEquals("one |", deleteWord("one two|", false));
        assertEquals("one |", deleteWord("one two |", false));
        assertEquals("one |wo", deleteWord("one t|wo", false));
        assertEquals("| two", deleteWord("one| two", false));
        assertEquals("|one", deleteWord("|one", false));
    }

    @Test
    public void neverDeletesTheWordAfterTheCursor() {
        // Moving by word leaves the cursor at the start of the word it
        // speaks. Only its first character, which is focused, goes.
        assertEquals("one |wo three", deleteWord("one |two three", true));
        // Moving by character focuses the character after the cursor.
        assertEquals("one | three", deleteWord("one tw|o three", true));
        // A focused space goes with the word before it.
        assertEquals("|two", deleteWord("one| two", true));
        // At the end nothing is focused.
        assertEquals("one |", deleteWord("one two|", true));
    }
}
