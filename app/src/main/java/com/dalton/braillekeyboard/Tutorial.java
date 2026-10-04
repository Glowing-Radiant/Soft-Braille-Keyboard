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

import com.dalton.braillekeyboard.Options.GestureStyle;
import com.dalton.braillekeyboard.Pad.Swipe;
import com.dalton.braillekeyboard.TalkBackGesture.Action;

/**
 * An interactive tutorial that teaches typing and the gestures of the user's
 * gesture style one step at a time. While it runs, typing and gestures don't
 * change the text: each is checked against the step being taught, which
 * moves on once it is done right and otherwise says what the keyboard felt.
 */
public class Tutorial {

    /** What the tutorial says about something the user did. */
    public static final class Response {
        /** Whether it was what the step asked for. */
        public final boolean right;
        public final String message;

        Response(boolean right, String message) {
            this.right = right;
            this.message = message;
        }
    }

    // What a step asks for: a cell to type, a classic gesture or a TalkBack
    // gesture.
    private static final class Step {
        final int instruction;
        final byte cell;
        final Swipe swipe;
        final Action action;

        Step(int instruction, int cell, Swipe swipe, Action action) {
            this.instruction = instruction;
            this.cell = (byte) cell;
            this.swipe = swipe;
            this.action = action;
        }
    }

    private static Step type(int instruction, int cell) {
        return new Step(instruction, cell, null, null);
    }

    private static Step swipe(int instruction, Swipe swipe) {
        return new Step(instruction, 0, swipe, null);
    }

    private static Step action(int instruction, Action action) {
        return new Step(instruction, 0, null, action);
    }

    private static final Step[] CLASSIC = {
            type(R.string.tutorial_type_a, 0x01),
            type(R.string.tutorial_type_b, 0x03),
            type(R.string.tutorial_type_l, 0x07),
            swipe(R.string.tutorial_classic_space, Swipe.FOUR_RIGHT),
            swipe(R.string.tutorial_classic_delete, Swipe.FOUR_LEFT),
            swipe(R.string.tutorial_classic_delete_word, Swipe.FIVE_LEFT),
            swipe(R.string.tutorial_classic_enter, Swipe.FOUR_DOWN),
            swipe(R.string.tutorial_classic_previous_character,
                    Swipe.ONE_LEFT),
            swipe(R.string.tutorial_classic_next_word, Swipe.TWO_RIGHT),
            swipe(R.string.tutorial_classic_read_line, Swipe.THREE_UP),
            swipe(R.string.tutorial_classic_read_all, Swipe.HOLD_SIX_UP),
            action(R.string.tutorial_menu, Action.HELP_AND_OTHER_ACTIONS) };

    private static final Step[] TALKBACK = {
            type(R.string.tutorial_type_a, 0x01),
            type(R.string.tutorial_type_b, 0x03),
            type(R.string.tutorial_type_l, 0x07),
            action(R.string.tutorial_talkback_space, Action.ADD_SPACE),
            action(R.string.tutorial_talkback_delete, Action.DELETE_CHARACTER),
            action(R.string.tutorial_talkback_delete_word, Action.DELETE_WORD),
            action(R.string.tutorial_talkback_new_line, Action.ADD_NEWLINE),
            action(R.string.tutorial_talkback_move_back,
                    Action.MOVE_CURSOR_BACKWARD),
            action(R.string.tutorial_talkback_previous_word,
                    Action.PREVIOUS_WORD),
            action(R.string.tutorial_talkback_granularity,
                    Action.NEXT_GRANULARITY),
            action(R.string.tutorial_talkback_submit, Action.SUBMIT_TEXT),
            action(R.string.tutorial_menu, Action.HELP_AND_OTHER_ACTIONS) };

    // One finger gestures are the classic ones, gestures with more fingers
    // TalkBack's.
    private static final Step[] MIXED = {
            type(R.string.tutorial_type_a, 0x01),
            type(R.string.tutorial_type_b, 0x03),
            type(R.string.tutorial_type_l, 0x07),
            swipe(R.string.tutorial_classic_space, Swipe.FOUR_RIGHT),
            swipe(R.string.tutorial_classic_delete, Swipe.FOUR_LEFT),
            action(R.string.tutorial_talkback_delete_word, Action.DELETE_WORD),
            action(R.string.tutorial_talkback_new_line, Action.ADD_NEWLINE),
            swipe(R.string.tutorial_classic_previous_character,
                    Swipe.ONE_LEFT),
            swipe(R.string.tutorial_classic_next_word, Swipe.TWO_RIGHT),
            swipe(R.string.tutorial_classic_read_line, Swipe.THREE_UP),
            swipe(R.string.tutorial_classic_read_all, Swipe.HOLD_SIX_UP),
            action(R.string.tutorial_menu, Action.HELP_AND_OTHER_ACTIONS) };

    private final Step[] steps;
    private int position = 0;

    /**
     * Creates the tutorial for a gesture style.
     */
    public Tutorial(GestureStyle style) {
        switch (style) {
        case TALKBACK:
            steps = TALKBACK;
            break;
        case MIXED:
            steps = MIXED;
            break;
        default:
            steps = CLASSIC;
        }
    }

    /** Returns the introduction and the first step. */
    public String start(Context context) {
        position = 0;
        return context.getString(R.string.tutorial_intro) + " "
                + context.getString(steps[0].instruction);
    }

    /** Whether every step has been done. */
    public boolean isFinished() {
        return position >= steps.length;
    }

    /** Whether the current step asks for a TalkBack gesture. */
    public boolean expects(Action action) {
        return !isFinished() && steps[position].action == action;
    }

    /** Checks a typed cell. */
    public Response onCell(Context context, byte cell) {
        StringBuilder dots = new StringBuilder();
        int count = 0;
        for (int dot = 0; dot < 8; dot++) {
            if ((cell & (1 << dot)) != 0) {
                if (count++ > 0) {
                    dots.append(' ');
                }
                dots.append(dot + 1);
            }
        }
        return check(context, !isFinished() && steps[position].swipe == null
                && steps[position].action == null
                && steps[position].cell == cell, context.getResources()
                .getQuantityString(R.plurals.tutorial_dots, count,
                        dots.toString()));
    }

    /** Checks a classic gesture. */
    public Response onSwipe(Context context, Swipe swipe) {
        return check(context, !isFinished() && steps[position].swipe == swipe,
                GesturePractice.describe(context, swipe));
    }

    /**
     * Checks a TalkBack gesture.
     *
     * @param action
     *            The recognised action, or null if the gesture has none.
     * @param directions
     *            The direction of each dot, see
     *            {@link TalkBackGesture#classify}.
     */
    public Response onAction(Context context, Action action,
            byte[] directions) {
        return check(context, !isFinished() && action != null
                && steps[position].action == action,
                GesturePractice.describe(context, action, directions));
    }

    // Moves on to the next step if the user did what this one asks, and
    // otherwise says what they did and asks again.
    private Response check(Context context, boolean right, String done) {
        if (isFinished()) {
            return new Response(false, "");
        }
        if (!right) {
            return new Response(false, context.getString(
                    R.string.tutorial_try_again, done,
                    context.getString(steps[position].instruction)));
        }
        position++;
        if (isFinished()) {
            return new Response(true,
                    context.getString(R.string.tutorial_complete));
        }
        return new Response(true, context.getString(
                R.string.tutorial_well_done,
                context.getString(steps[position].instruction)));
    }
}
