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

import static com.dalton.braillekeyboard.Pad.Coords.DOT_DOWN;
import static com.dalton.braillekeyboard.Pad.Coords.DOT_LEFT;
import static com.dalton.braillekeyboard.Pad.Coords.DOT_NONE;
import static com.dalton.braillekeyboard.Pad.Coords.DOT_RIGHT;
import static com.dalton.braillekeyboard.Pad.Coords.DOT_UP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.dalton.braillekeyboard.TalkBackGesture.Action;

import org.junit.Test;

public class TalkBackGestureTest {
    private static final byte HOLD = DOT_NONE;

    // Builds the per dot directions for dots 1 to 8.
    private static byte[] dots(int... directions) {
        byte[] result = new byte[8];
        for (int i = 0; i < directions.length; i++) {
            result[i] = (byte) directions[i];
        }
        return result;
    }

    @Test
    public void oneFingerSwipes() {
        assertEquals(Action.ADD_SPACE, TalkBackGesture.classify(dots(DOT_RIGHT)));
        assertEquals(Action.DELETE_CHARACTER,
                TalkBackGesture.classify(dots(0, 0, 0, DOT_LEFT)));
        assertEquals(Action.MOVE_CURSOR_BACKWARD,
                TalkBackGesture.classify(dots(0, DOT_UP)));
        assertEquals(Action.MOVE_CURSOR_FORWARD,
                TalkBackGesture.classify(dots(0, 0, 0, 0, 0, DOT_DOWN)));
    }

    @Test
    public void fingerCountNotPositionMatters() {
        assertEquals(Action.ADD_NEWLINE,
                TalkBackGesture.classify(dots(DOT_RIGHT, 0, 0, DOT_RIGHT)));
        assertEquals(Action.ADD_NEWLINE,
                TalkBackGesture.classify(dots(0, DOT_RIGHT, DOT_RIGHT)));
        assertEquals(Action.DELETE_WORD,
                TalkBackGesture.classify(dots(0, 0, 0, DOT_LEFT, DOT_LEFT)));
        assertEquals(Action.SUBMIT_TEXT,
                TalkBackGesture.classify(dots(DOT_UP, DOT_UP)));
        assertEquals(Action.HIDE_KEYBOARD,
                TalkBackGesture.classify(dots(DOT_DOWN, DOT_DOWN)));
    }

    @Test
    public void threeFingerSwipes() {
        assertEquals(Action.HELP_AND_OTHER_ACTIONS,
                TalkBackGesture.classify(dots(DOT_UP, DOT_UP, DOT_UP)));
        assertEquals(Action.SWITCH_KEYBOARD,
                TalkBackGesture.classify(dots(0, 0, 0, DOT_DOWN, DOT_DOWN, DOT_DOWN)));
        assertEquals(Action.NEXT_GRANULARITY,
                TalkBackGesture.classify(dots(DOT_RIGHT, DOT_RIGHT, 0, DOT_RIGHT)));
        assertEquals(Action.PREVIOUS_GRANULARITY,
                TalkBackGesture.classify(dots(DOT_LEFT, DOT_LEFT, DOT_LEFT)));
    }

    @Test
    public void holdRightHandDots() {
        // Hold dot 6, one finger up/down: character.
        assertEquals(Action.PREVIOUS_CHARACTER,
                TalkBackGesture.classify(dots(DOT_UP, 0, 0, 0, 0, HOLD)));
        assertEquals(Action.SELECT_NEXT_CHARACTER, TalkBackGesture
                .classify(dots(DOT_DOWN, DOT_DOWN, 0, 0, 0, HOLD)));
        // Hold dot 5: word.
        assertEquals(Action.NEXT_WORD,
                TalkBackGesture.classify(dots(0, DOT_DOWN, 0, 0, HOLD)));
        // Hold dot 4: line and clipboard.
        assertEquals(Action.PREVIOUS_LINE,
                TalkBackGesture.classify(dots(DOT_UP, 0, 0, HOLD)));
        assertEquals(Action.COPY, TalkBackGesture.classify(dots(DOT_DOWN,
                DOT_DOWN, DOT_DOWN, HOLD)));
        assertEquals(Action.PASTE, TalkBackGesture.classify(dots(DOT_RIGHT,
                DOT_RIGHT, DOT_RIGHT, HOLD)));
        assertEquals(Action.SELECT_ALL, TalkBackGesture.classify(dots(DOT_LEFT,
                DOT_LEFT, DOT_LEFT, HOLD)));
        assertEquals(Action.CUT,
                TalkBackGesture.classify(dots(DOT_UP, DOT_UP, DOT_UP, HOLD)));
        // Hold dots 4 and 5: start and end.
        assertEquals(Action.END_OF_TEXT,
                TalkBackGesture.classify(dots(DOT_DOWN, 0, 0, HOLD, HOLD)));
        assertEquals(Action.SELECT_TO_START, TalkBackGesture.classify(dots(
                DOT_UP, DOT_UP, 0, HOLD, HOLD)));
    }

    @Test
    public void leftHandHoldsMirrorRightHand() {
        // Hold dot 3 acts as dot 6.
        assertEquals(Action.NEXT_CHARACTER,
                TalkBackGesture.classify(dots(0, 0, HOLD, DOT_DOWN)));
        // Hold dot 1 acts as dot 4.
        assertEquals(Action.SELECT_ALL, TalkBackGesture.classify(dots(HOLD, 0,
                0, DOT_LEFT, DOT_LEFT, DOT_LEFT)));
        // Hold dots 1 and 2 act as dots 4 and 5.
        assertEquals(Action.START_OF_TEXT,
                TalkBackGesture.classify(dots(HOLD, HOLD, 0, DOT_UP)));
    }

    @Test
    public void unrecognisedGestures() {
        // No finger moved.
        assertNull(TalkBackGesture.classify(dots(HOLD, HOLD)));
        // Fingers moved in different directions.
        assertNull(TalkBackGesture.classify(dots(DOT_UP, DOT_DOWN)));
        // Too many fingers.
        assertNull(TalkBackGesture.classify(dots(DOT_UP, DOT_UP, DOT_UP,
                DOT_UP)));
        // Holding dots on both hands.
        assertNull(TalkBackGesture.classify(dots(HOLD, 0, 0, HOLD, DOT_UP)));
        // Unassigned: hold dot 6 and swipe right.
        assertNull(TalkBackGesture.classify(dots(DOT_RIGHT, 0, 0, 0, 0, HOLD)));
        // Holding dot 7 isn't a TalkBack gesture.
        assertNull(TalkBackGesture.classify(dots(DOT_UP, 0, 0, 0, 0, 0, HOLD)));
    }
}
