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

import android.content.Context;
import android.content.res.Resources;

import com.dalton.braillekeyboard.Pad.Coords;
import com.dalton.braillekeyboard.Pad.Swipe;
import com.dalton.braillekeyboard.TalkBackGesture.Action;

/**
 * Describes gestures for the gesture practice mode, where gestures say what
 * they do and how they were recognised instead of doing it.
 */
public final class GesturePractice {

    private GesturePractice() {
    }

    /** Whether the user turned on gesture practice. */
    public static boolean isOn(Context context) {
        return Options.getBooleanPreference(context,
                R.string.pref_gesture_practice_key, Boolean.parseBoolean(context
                        .getString(R.string.pref_gesture_practice_default)));
    }

    /**
     * Describes a TalkBack style gesture, such as "New line. Swipe right with
     * two fingers".
     *
     * @param action
     *            The recognised action, or null if the gesture has none.
     * @param directions
     *            The direction of each dot, see
     *            {@link TalkBackGesture#classify}.
     */
    public static String describe(Context context, Action action,
            byte[] directions) {
        String gesture = describeShape(context, directions);
        if (action == null) {
            return context.getString(R.string.practice_unknown_gesture, gesture);
        }
        return context.getString(R.string.practice_gesture,
                context.getString(actionName(action)), gesture);
    }

    /**
     * Describes a classic gesture, such as "Delete character. Swipe dot 4
     * left".
     */
    public static String describe(Context context, Swipe swipe) {
        int name = actionName(swipe);
        String gesture = describeShape(context, swipe);
        if (name == 0 || gesture == null) {
            return context.getString(R.string.practice_unknown_gesture,
                    gesture == null ? "" : gesture);
        }
        return context.getString(R.string.practice_gesture,
                context.getString(name), gesture);
    }

    // For example "hold dot 4, swipe up with two fingers".
    private static String describeShape(Context context, byte[] directions) {
        Resources resources = context.getResources();
        byte direction = TalkBackGesture.swipeDirection(directions);
        String swipe;
        if (direction == Coords.DOT_NONE) {
            swipe = context.getString(R.string.gesture_different_directions);
        } else {
            int fingers = TalkBackGesture.countSwipingFingers(directions);
            swipe = context.getString(R.string.gesture_swipe,
                    directionName(context, direction), resources
                            .getQuantityString(R.plurals.gesture_fingers,
                                    fingers, fingers));
        }
        int held = TalkBackGesture.heldDots(directions);
        if (held == 0) {
            return swipe;
        }
        StringBuilder dots = new StringBuilder();
        int count = 0;
        for (int dot = 0; dot < 8; dot++) {
            if ((held & (1 << dot)) != 0) {
                if (count++ > 0) {
                    dots.append(' ');
                }
                dots.append(dot + 1);
            }
        }
        return context.getString(R.string.gesture_hold, resources
                .getQuantityString(R.plurals.gesture_held_dots, count,
                        dots.toString()), swipe);
    }

    // Classic gestures are named after the dot that swipes, or the dot held
    // while any finger of the other hand swipes, for example HOLD_SIX_UP.
    private static String describeShape(Context context, Swipe swipe) {
        String[] parts = swipe.name().split("_");
        boolean hold = parts[0].equals("HOLD");
        if (parts.length != (hold ? 3 : 2)) {
            return null;
        }
        int dot = dotNumber(parts[hold ? 1 : 0]);
        byte direction = direction(parts[hold ? 2 : 1]);
        if (dot == 0 || direction == 0) {
            return null;
        }
        return context.getString(hold ? R.string.gesture_classic_hold
                : R.string.gesture_classic_swipe, dot,
                directionName(context, direction));
    }

    private static int dotNumber(String name) {
        String[] numbers = { "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX" };
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i].equals(name)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static byte direction(String name) {
        switch (name) {
        case "UP":
            return Coords.DOT_UP;
        case "DOWN":
            return Coords.DOT_DOWN;
        case "LEFT":
            return Coords.DOT_LEFT;
        case "RIGHT":
            return Coords.DOT_RIGHT;
        default:
            return 0;
        }
    }

    private static String directionName(Context context, byte direction) {
        switch (direction) {
        case Coords.DOT_UP:
            return context.getString(R.string.direction_up);
        case Coords.DOT_DOWN:
            return context.getString(R.string.direction_down);
        case Coords.DOT_LEFT:
            return context.getString(R.string.direction_left);
        default:
            return context.getString(R.string.direction_right);
        }
    }

    /** The string resource naming what a TalkBack gesture does. */
    static int actionName(Action action) {
        switch (action) {
        case MOVE_CURSOR_BACKWARD:
            return R.string.action_move_back;
        case MOVE_CURSOR_FORWARD:
            return R.string.action_move_forward;
        case ADD_SPACE:
            return R.string.action_space;
        case DELETE_CHARACTER:
            return R.string.action_delete_character;
        case SUBMIT_TEXT:
            return R.string.action_submit;
        case HIDE_KEYBOARD:
            return R.string.action_hide_keyboard;
        case ADD_NEWLINE:
            return R.string.newline;
        case DELETE_WORD:
            return R.string.action_delete_word;
        case HELP_AND_OTHER_ACTIONS:
            return R.string.action_keyboard_menu;
        case SWITCH_KEYBOARD:
            return R.string.action_switch_keyboard;
        case NEXT_GRANULARITY:
            return R.string.action_next_granularity;
        case PREVIOUS_GRANULARITY:
            return R.string.action_previous_granularity;
        case PREVIOUS_CHARACTER:
            return R.string.action_previous_character;
        case NEXT_CHARACTER:
            return R.string.action_next_character;
        case PREVIOUS_WORD:
            return R.string.action_previous_word;
        case NEXT_WORD:
            return R.string.action_next_word;
        case PREVIOUS_LINE:
            return R.string.action_previous_line;
        case NEXT_LINE:
            return R.string.action_next_line;
        case START_OF_TEXT:
            return R.string.action_start_of_text;
        case END_OF_TEXT:
            return R.string.action_end_of_text;
        case SELECT_PREVIOUS_CHARACTER:
            return R.string.action_select_previous_character;
        case SELECT_NEXT_CHARACTER:
            return R.string.action_select_next_character;
        case SELECT_PREVIOUS_WORD:
            return R.string.action_select_previous_word;
        case SELECT_NEXT_WORD:
            return R.string.action_select_next_word;
        case SELECT_PREVIOUS_LINE:
            return R.string.action_select_previous_line;
        case SELECT_NEXT_LINE:
            return R.string.action_select_next_line;
        case SELECT_TO_START:
            return R.string.action_select_to_start;
        case SELECT_TO_END:
            return R.string.action_select_to_end;
        case SELECT_ALL:
            return R.string.select_all;
        case CUT:
            return android.R.string.cut;
        case COPY:
            return android.R.string.copy;
        case PASTE:
            return android.R.string.paste;
        default:
            return 0;
        }
    }

    /** The string resource naming what a classic gesture does, or 0. */
    static int actionName(Swipe swipe) {
        switch (swipe) {
        case ONE_LEFT:
            return R.string.action_previous_character;
        case ONE_RIGHT:
            return R.string.action_next_character;
        case ONE_UP:
            return R.string.action_read_character;
        case ONE_DOWN:
            return R.string.menu_keyboard_feedback;
        case TWO_LEFT:
            return R.string.action_previous_word;
        case TWO_RIGHT:
            return R.string.action_next_word;
        case TWO_UP:
            return R.string.action_read_word;
        case TWO_DOWN:
            return R.string.menu_keyboard_echo;
        case THREE_LEFT:
            return R.string.action_previous_line;
        case THREE_RIGHT:
            return R.string.action_next_line;
        case THREE_UP:
            return R.string.action_read_line;
        case THREE_DOWN:
            return R.string.action_dot_7;
        case FOUR_LEFT:
            return R.string.action_delete_character;
        case FOUR_RIGHT:
        case FIVE_RIGHT:
            return R.string.action_space;
        case FOUR_UP:
            return R.string.menu_privacy;
        case FOUR_DOWN:
            return R.string.action_enter;
        case FIVE_LEFT:
            return R.string.action_delete_word;
        case FIVE_UP:
            return R.string.action_open_settings;
        case FIVE_DOWN:
            return R.string.action_choose_keyboard;
        case SIX_LEFT:
            return R.string.action_delete_line;
        case SIX_RIGHT:
            return R.string.action_next_selection_action;
        case SIX_UP:
            return R.string.action_perform_selection_action;
        case SIX_DOWN:
            return R.string.action_dot_8;
        case HOLD_SIX_LEFT:
            return R.string.action_start_of_text;
        case HOLD_SIX_RIGHT:
            return R.string.action_end_of_text;
        case HOLD_SIX_UP:
            return R.string.menu_read_all;
        case HOLD_SIX_DOWN:
            return R.string.menu_speak_passwords;
        case HOLD_THREE_LEFT:
            return R.string.action_delete_all;
        case HOLD_THREE_RIGHT:
            return R.string.menu_switch_grade;
        case HOLD_THREE_DOWN:
            return R.string.menu_switch_table;
        case HOLD_THREE_UP:
            return R.string.menu_voice_input;
        case HOLD_ONE_UP:
            return R.string.menu_auto_caps;
        case HOLD_ONE_DOWN:
            return R.string.menu_word_count;
        case HOLD_ONE_LEFT:
            return R.string.menu_shrink_keyboard;
        case HOLD_ONE_RIGHT:
            return R.string.action_set_mark;
        case HOLD_FOUR_LEFT:
            return R.string.action_previous_misspelling;
        case HOLD_FOUR_RIGHT:
            return R.string.action_next_misspelling;
        case HOLD_FOUR_DOWN:
            return R.string.action_next_suggestion;
        case HOLD_FOUR_UP:
            return R.string.action_previous_suggestion;
        default:
            return 0;
        }
    }
}
