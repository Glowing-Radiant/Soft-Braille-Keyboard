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

import com.dalton.braillekeyboard.Pad.Coords;

/**
 * Recognises the gestures of the TalkBack braille keyboard so its users can
 * switch to Soft Braille Keyboard without relearning them.
 *
 * TalkBack gestures are defined by how many fingers swipe together (one to
 * three) and in which direction, optionally while holding one or two dots
 * still. Holding dots on either hand is equivalent: dot 1 acts as dot 4, dot 2
 * as dot 5 and dot 3 as dot 6. The mapping follows TalkBack's default
 * gestures (BrailleImeGestureAction in the TalkBack sources).
 */
public final class TalkBackGesture {

    /** The actions of the TalkBack braille keyboard. */
    public enum Action {
        MOVE_CURSOR_BACKWARD, MOVE_CURSOR_FORWARD, ADD_SPACE, DELETE_CHARACTER,
        SUBMIT_TEXT, HIDE_KEYBOARD, ADD_NEWLINE, DELETE_WORD,
        HELP_AND_OTHER_ACTIONS, SWITCH_KEYBOARD, NEXT_GRANULARITY,
        PREVIOUS_GRANULARITY, PREVIOUS_CHARACTER, NEXT_CHARACTER,
        PREVIOUS_WORD, NEXT_WORD, PREVIOUS_LINE, NEXT_LINE, START_OF_TEXT,
        END_OF_TEXT, SELECT_PREVIOUS_CHARACTER, SELECT_NEXT_CHARACTER,
        SELECT_PREVIOUS_WORD, SELECT_NEXT_WORD, SELECT_PREVIOUS_LINE,
        SELECT_NEXT_LINE, SELECT_TO_START, SELECT_TO_END, SELECT_ALL, CUT,
        COPY, PASTE
    }

    private static final int MAX_SWIPE_FINGERS = 3;
    // Held dots, as bits of a braille cell (dot 1 is bit 0).
    private static final int DOT_4 = 1 << 3;
    private static final int DOT_5 = 1 << 4;
    private static final int DOT_6 = 1 << 5;
    private static final int LEFT_HAND_DOTS = 0x07;
    private static final int RIGHT_HAND_DOTS = 0x38;

    private TalkBackGesture() {
    }

    /**
     * Classifies a completed gesture.
     *
     * @param directions
     *            The direction each dot moved in, indexed by dot (0 is dot 1)
     *            as returned by {@link Pad#getDotDirections}. 0 means the dot
     *            wasn't touched, {@link Coords#DOT_NONE} that it was held
     *            still.
     * @return The action or null if the gesture isn't a TalkBack gesture.
     */
    public static Action classify(byte[] directions) {
        byte direction = 0;
        int fingers = 0;
        int held = 0;
        for (int dot = 0; dot < directions.length; dot++) {
            byte d = directions[dot];
            if (d == 0) {
                continue;
            }
            if (d == Coords.DOT_NONE) {
                if (dot >= 6) {
                    return null; // Only dots 1 to 6 can be held.
                }
                held |= 1 << dot;
            } else if (direction == 0 || direction == d) {
                direction = d;
                fingers++;
            } else {
                return null; // Fingers swiped in different directions.
            }
        }
        if (fingers == 0 || fingers > MAX_SWIPE_FINGERS) {
            return null;
        }

        // Holding dots with the left hand mirrors holding them with the right.
        if ((held & RIGHT_HAND_DOTS) == 0) {
            held <<= 3;
        } else if ((held & LEFT_HAND_DOTS) != 0) {
            return null; // Dots held on both hands.
        }

        switch (held) {
        case 0:
            return swipe(direction, fingers);
        case DOT_4:
            return holdDot4(direction, fingers);
        case DOT_5:
            return holdDot5Or6(direction, fingers, Action.PREVIOUS_WORD,
                    Action.NEXT_WORD, Action.SELECT_PREVIOUS_WORD,
                    Action.SELECT_NEXT_WORD);
        case DOT_6:
            return holdDot5Or6(direction, fingers, Action.PREVIOUS_CHARACTER,
                    Action.NEXT_CHARACTER, Action.SELECT_PREVIOUS_CHARACTER,
                    Action.SELECT_NEXT_CHARACTER);
        case DOT_4 | DOT_5:
            return holdDots45(direction, fingers);
        default:
            return null;
        }
    }

    private static Action swipe(byte direction, int fingers) {
        switch (fingers) {
        case 1:
            return pick(direction, Action.MOVE_CURSOR_BACKWARD,
                    Action.MOVE_CURSOR_FORWARD, Action.DELETE_CHARACTER,
                    Action.ADD_SPACE);
        case 2:
            return pick(direction, Action.SUBMIT_TEXT, Action.HIDE_KEYBOARD,
                    Action.DELETE_WORD, Action.ADD_NEWLINE);
        default:
            return pick(direction, Action.HELP_AND_OTHER_ACTIONS,
                    Action.SWITCH_KEYBOARD, Action.PREVIOUS_GRANULARITY,
                    Action.NEXT_GRANULARITY);
        }
    }

    private static Action holdDot4(byte direction, int fingers) {
        switch (fingers) {
        case 1:
            return pick(direction, Action.PREVIOUS_LINE, Action.NEXT_LINE,
                    null, null);
        case 2:
            return pick(direction, Action.SELECT_PREVIOUS_LINE,
                    Action.SELECT_NEXT_LINE, null, null);
        default:
            return pick(direction, Action.CUT, Action.COPY, Action.SELECT_ALL,
                    Action.PASTE);
        }
    }

    private static Action holdDot5Or6(byte direction, int fingers,
            Action previous, Action next, Action selectPrevious,
            Action selectNext) {
        switch (fingers) {
        case 1:
            return pick(direction, previous, next, null, null);
        case 2:
            return pick(direction, selectPrevious, selectNext, null, null);
        default:
            return null;
        }
    }

    private static Action holdDots45(byte direction, int fingers) {
        switch (fingers) {
        case 1:
            return pick(direction, Action.START_OF_TEXT, Action.END_OF_TEXT,
                    null, null);
        case 2:
            return pick(direction, Action.SELECT_TO_START,
                    Action.SELECT_TO_END, null, null);
        default:
            return null;
        }
    }

    private static Action pick(byte direction, Action up, Action down,
            Action left, Action right) {
        switch (direction) {
        case Coords.DOT_UP:
            return up;
        case Coords.DOT_DOWN:
            return down;
        case Coords.DOT_LEFT:
            return left;
        case Coords.DOT_RIGHT:
            return right;
        default:
            return null;
        }
    }
}
