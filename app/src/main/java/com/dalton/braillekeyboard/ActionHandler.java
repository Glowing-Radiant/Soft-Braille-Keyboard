/*
 * Copyright (C) 2016 The Soft Braille Keyboard Authors
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

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import android.Manifest;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.Keyboard;
import android.os.Process;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.InputMethodManager;

import com.dalton.braillekeyboard.EditingUtilities.Word;
import com.dalton.braillekeyboard.Options.KeyboardEcho;
import com.dalton.braillekeyboard.Options.KeyboardFeedback;
import com.dalton.braillekeyboard.Pad.Swipe;
import com.dalton.braillekeyboard.SpellChecker.SpellingSuggestionsReadyListener;
import com.dalton.braillekeyboard.SpellChecker.Suggestion;

/**
 * ActionHandler handles actions from a View or other interface and performs the
 * appropriate logic before returning the results back to the calling View in
 * the form of a callback. This performs higher level logic than the
 * InputService itself and communicates directly with the InputService to
 * collaborate and solve the requests from the View.
 * 
 * A View should instantiate this class once upon initialisation and call it's
 * handleSwipe or handleCharacter methods to perform actions. Before such
 * activity the view shall set the IME listener by calling
 * setKeyboardListener(KeyboardListener listener) and also set the callback by
 * calling setCallback(OnActionListener callback). You should always call the
 * shutdown() method when you are done.
 * 
 * The callback is how the results are sent back to the View. The View should
 * implement the respective callbacks and implement them appropriate to their
 * View and user interface. See ActionHandler.OnActionListener for details.
 */
public class ActionHandler {
    // The maximum time between two identical swipe patterns which constitutes a
    // double swipe.
    private static final long DOUBLE_TOUCH_THRESHOLD = 1300;

    /**
     * Listener for handling the results of requests to the input methods. You
     * should implement these callbacks in your View and display the results to
     * the user.
     */
    public interface OnActionListener {
        /**
         * Deliver a string of text to the view as output of a certain action
         * that was performed. This might be some sort of message, a key name to
         * echo or some other text. Your UI should communicate this to the user
         * somehow in the form of audible or visual representation whatever is
         * appropriate for the use case.
         * 
         * @param format
         *            Format string to be used to display the message.
         * @param text
         *            Any text of the message.
         * @param isPasswordField
         *            True if it should be displayed with the same rules of
         *            showing passwords.
         */
        void onText(String format, String text, boolean isPasswordField);

        void onText(String format, String text, boolean isPasswordField,
                int mode);

        /**
         * Called when a notification should be delivered to the user.
         * 
         * @param vibrate
         *            true if the device should be vibrated for this
         *            notification.
         * @param sound
         *            The sound for the action, or null for no sound.
         */
        void onNotify(boolean vibrate, Earcons.Sound sound);

        /**
         * Called when dots 7 and 8 should be set in the View.
         * 
         * @param dot7
         *            Whether dot7 is pressed.
         * @param dot8
         *            Whether dot8 is pressed.
         */
        void onSetDots(boolean dot7, boolean dot8);

        /**
         * Called when the View should update it's Locale. This is generally
         * called when the Braille table is changed because there is the
         * possibility for language change.
         */
        void onSetLocale(Locale locale);

        /**
         * Called when the View should shrink itself.
         */
        void onShrink();

        /**
         * Called when the View should update the state of it's privacy mode.
         */
        void onPrivacy();

        void onShutup();
    }

    /**
     * Representation of varying textual granularities from the smallest level
     * character up until the entire text.
     */
    private enum Granularity {
        CHARACTER, WORD, LINE, ALL;
    }

    /**
     * Representation of the currently available edit actions that can be
     * performed on a selection of text.
     */
    private enum EditAction {
        SELECT_ALL(R.string.select_all), COPY(android.R.string.copy), CUT(
                android.R.string.cut), PASTE(android.R.string.paste), SPEAK(
                R.string.speak_selection), DELETE(R.string.delete_selection);

        // The Android resource for the text UI string for this action.
        public final int resource;

        EditAction(int resource) {
            this.resource = resource;
        }

        // Find the position of this action in the enum values() array.
        private int getIndexOfThis() {
            EditAction[] values = EditAction.values();
            int i;
            for (i = 0; i < values.length; i++) {
                if (this == values[i]) {
                    break;
                }
            }
            return i;
        }

        /**
         * Move in order to the next EditAction in the enum list. If we are at
         * the end of the list it will wrap.
         * 
         * @return The new EditAction instance.
         */
        public EditAction next(ClipboardManager clipboard) {
            int i = getIndexOfThis();

            if (++i == values().length) {
                i = 0; // point at first item in list.
            }

            if (values()[i] == PASTE) {
                boolean canPaste;
                // Check that there is text on the clipboard so it makes sense
                // showing paste.
                if (!(clipboard.hasPrimaryClip())) {
                    canPaste = false;
                } else {
                    // This enables the paste menu item, since the clipboard
                    // contains plain text.
                    canPaste = true;
                }
                if (!canPaste) {
                    if (++i == values().length) {
                        i = 0; // Point at start if we exceed the end
                    }
                }
            }

            return values()[i];
        }
    }

    /**
     * The reading granularities cycled by TalkBack's three finger left and
     * right swipes. One finger up and down swipes move by the current one.
     */
    private enum ReadingGranularity {
        CHARACTER(R.string.granularity_characters, Granularity.CHARACTER), WORD(
                R.string.granularity_words, Granularity.WORD), LINE(
                R.string.granularity_lines, Granularity.LINE), SPELLING(
                R.string.granularity_spelling, null);

        public final int resource;
        public final Granularity granularity;

        ReadingGranularity(int resource, Granularity granularity) {
            this.resource = resource;
            this.granularity = granularity;
        }
    }

    /**
     * Items of the spoken menu opened with TalkBack's three finger swipe up.
     * It gives TalkBack gesture users the features that have no TalkBack
     * gesture. Most items perform the equivalent classic gesture.
     */
    private enum MenuItem {
        READ_ALL(R.string.menu_read_all, Swipe.HOLD_SIX_UP), SWITCH_GRADE(
                R.string.menu_switch_grade, Swipe.HOLD_THREE_RIGHT), SWITCH_TABLE(
                R.string.menu_switch_table, Swipe.HOLD_THREE_DOWN), VOICE_INPUT(
                R.string.menu_voice_input, null), SHRINK_KEYBOARD(
                R.string.menu_shrink_keyboard, Swipe.HOLD_ONE_LEFT), WORD_COUNT(
                R.string.menu_word_count, Swipe.HOLD_ONE_DOWN), KEYBOARD_ECHO(
                R.string.menu_keyboard_echo, Swipe.TWO_DOWN), KEYBOARD_FEEDBACK(
                R.string.menu_keyboard_feedback, Swipe.ONE_DOWN), AUTO_CAPS(
                R.string.menu_auto_caps, Swipe.HOLD_ONE_UP), AUTO_CORRECT(
                R.string.menu_auto_correct, null), SPEAK_PASSWORDS(
                R.string.menu_speak_passwords, Swipe.HOLD_SIX_DOWN), PRIVACY(
                R.string.menu_privacy, Swipe.FOUR_UP), GESTURE_PRACTICE(
                R.string.menu_gesture_practice, null), SETTINGS(
                R.string.menu_settings, null), HELP(R.string.menu_help, null);

        public final int resource;
        // The classic gesture performing this item, if any.
        public final Swipe swipe;

        MenuItem(int resource, Swipe swipe) {
            this.resource = resource;
            this.swipe = swipe;
        }
    }

    private final ClipboardManager clipboard;
    private final InputMethodManager inputManager;
    private final SpellChecker spellChecker;
    private final VoiceInput voiceInput = new VoiceInput();

    private EditAction editAction = EditAction.COPY;
    private ReadingGranularity readingGranularity = ReadingGranularity.CHARACTER;
    // Index into MenuItem.values() while the spoken menu is open, else -1.
    private int menuPosition = -1;
    private long lastTouchTime = 0; // Time screen was last touched.
    private Swipe lastSwipe = Swipe.NONE; // Type of last gesture.
    private KeyboardListener listener;
    private OnActionListener callback;
    private int directionThroughSuggestionList;
    private SpellChecker.Direction spellingDirection;
    private Suggestion spellingSuggestion;
    private final AutoCorrect autoCorrect;
    // The cursor when moving or reading last spoke the text at it, else -1.
    // Deleting while the cursor is still there deletes that text rather
    // than the text before the cursor.
    private int focusCursor = -1;
    // The last auto-correction, undone by deleting the space right after it.
    private String correctedFrom;
    private String correctedTo;
    // Words the user restored after they were corrected, left alone since.
    private final Set<String> keptWords = new HashSet<String>();

    /**
     * Create a new ActionHandler for the given context.
     * 
     * @param context
     *            The application context.
     */
    public ActionHandler(Context context) {
        inputManager = (InputMethodManager) context
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        clipboard = (ClipboardManager) context
                .getSystemService(Context.CLIPBOARD_SERVICE);
        spellChecker = new SpellChecker(context);
        autoCorrect = new AutoCorrect(context);
    }

    /**
     * Set the callback to deliver results to the View interracting with this
     * instance.
     * 
     * @param callback
     *            The View's implementation of ActionHandler.OnActionListener
     *            listening for updates.
     */
    public void setCallback(OnActionListener callback) {
        this.callback = callback;
    }

    /**
     * Set the listener so that the ActionHandler can communicate with the
     * underlying IME.
     * 
     * @param listener
     *            The KeyboardListener for the current input session.
     */
    public void setKeyboardListener(KeyboardListener listener) {
        this.listener = listener;
    }

    /**
     * Releases system resources. This should be called just before the
     * interracting View gets destroyed.
     */
    public void shutdown() {
        voiceInput.destroy();
        spellChecker.destroy();
        autoCorrect.destroy();
    }

    /**
     * Handle swipe actions delivered from the interracting View.
     * 
     * Each View should deliver a swipe action as defined by the generic
     * Pad.Swipe type. This method will perform the appropriate action for the
     * received Swipe gesture. If need be the appropriate callbacks will be
     * invoked.
     * 
     * @param context
     *            The application context.
     * @param value
     *            The Swipe value from the View.
     * @return true if the Swipe was handled otherwise false.
     */
    public boolean handleSwipe(Context context, Swipe value) {
        // Disable all swipes while voice input is in progress.
        if (voiceInput.isListening()) {
            return true;
        }

        value = normaliseSwipe(value);
        String message = null;
        boolean notify = true;
        boolean setDots = false;
        boolean considerPassword = false;
        // states for dots 7 and 8
        boolean dots[] = { false, false };
        boolean fastDoubleSwipe = fastDoubleSwipe(value, DOUBLE_TOUCH_THRESHOLD);

        // Practising gestures only describes them. Opening settings still
        // works, so there is always a way to turn practice off.
        if (value != Swipe.NONE && GesturePractice.isOn(context)
                && !(value == Swipe.FIVE_UP && fastDoubleSwipe)) {
            callback.onNotify(true, Earcons.Sound.MOVE);
            speak(GesturePractice.describe(context, value));
            return true;
        }

        switch (value) {
        case ONE_LEFT:
            moveLeft(context, Granularity.CHARACTER);
            break;
        case ONE_RIGHT:
            moveRight(context, Granularity.CHARACTER);
            break;
        case ONE_DOWN:
            KeyboardFeedback feedback = KeyboardFeedback.valueOf(Integer
                    .parseInt(Options.getStringPreference(context,
                            R.string.pref_keyboard_feedback_key,
                            KeyboardFeedback.ALL.getValue())));
            feedback = KeyboardFeedback.next(feedback);
            Options.writeStringPreference(context,
                    R.string.pref_keyboard_feedback_key, feedback.getValue());
            message = context.getString(feedback.resource);
            break;
        case ONE_UP:
            message = getInput(Granularity.CHARACTER);
            focusCursor = listener.getCursor();
            considerPassword = true;
            break;
        case TWO_LEFT:
            moveLeft(context, Granularity.WORD);
            break;
        case TWO_RIGHT:
            moveRight(context, Granularity.WORD);
            break;
        case TWO_UP:
            message = getInput(Granularity.WORD);
            focusCursor = listener.getCursor();
            considerPassword = true;
            break;
        case TWO_DOWN:
            KeyboardEcho echo = KeyboardEcho.valueOf(Integer.parseInt(Options
                    .getStringPreference(context,
                            R.string.pref_echo_feedback_key,
                            KeyboardEcho.CHARACTER.getValue())));
            echo = KeyboardEcho.next(echo);
            Options.writeStringPreference(context,
                    R.string.pref_echo_feedback_key, echo.getValue());
            message = context.getString(echo.resource);
            break;
        case THREE_LEFT:
            moveLeft(context, Granularity.LINE);
            break;
        case THREE_RIGHT:
            moveRight(context, Granularity.LINE);
            break;
        case THREE_UP:
            message = getInput(Granularity.LINE);
            focusCursor = listener.getCursor();
            considerPassword = true;
            break;
        case THREE_DOWN:
            if (listener.getDots() == 8) {
                setDots = true;
                dots[0] = true;
            } else {
                message = context.getString(R.string.unknown_character);
            }
            break;
        case FOUR_LEFT:
            backspace(context, Granularity.CHARACTER, fastDoubleSwipe);
            break;
        case FOUR_RIGHT:
            if (fastDoubleSwipe) {
                if (handleDoubleSpace(context)) {
                    break;
                }
            }
            typeCharacter(context, (int) ' ', " ");
            break;
        case FOUR_DOWN: // newline / enter
            typeCharacter(context, Keyboard.KEYCODE_DONE,
                    context.getString(R.string.newline));
            break;
        case FOUR_UP:
            Options.switchBooleanPreference(context, R.string.pref_privacy_key,
                    Boolean.parseBoolean(context
                            .getString(R.string.pref_privacy_default)));
            callback.onPrivacy();
            message = Options.getBooleanPreference(context,
                    R.string.pref_privacy_key, Boolean.parseBoolean(context
                            .getString(R.string.pref_privacy_default))) ? context
                    .getString(R.string.privacy_enabled) : context
                    .getString(R.string.privacy_disabled);
            break;
        case FIVE_LEFT:
            backspace(context, Granularity.WORD, fastDoubleSwipe);
            break;
        case FIVE_DOWN:
            if (fastDoubleSwipe) {
                message = context.getString(R.string.show_input_switcher);
                inputManager.showInputMethodPicker();
            } else {
                message = context.getString(R.string.swipe_confirm_input);
            }
            break;
        case FIVE_UP:
            if (fastDoubleSwipe) {
                callback.onSetLocale(Locale.getDefault());
                message = context.getString(R.string.show_settings);
                Intent intent = new Intent(context, PreferenceIME.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                message = context.getString(R.string.swipe_confirm_settings);
            }
            break;
        case SIX_LEFT:
            backspace(context, Granularity.LINE, fastDoubleSwipe);
            break;
        case SIX_RIGHT:
            nextAction(context);
            break;
        case SIX_UP:
            selectAction(context);
            break;
        case SIX_DOWN:
            if (listener.getDots() == 8) {
                setDots = true;
                dots[1] = true;
            } else {
                message = context.getString(R.string.unknown_character);
            }
            break;
        case HOLD_SIX_LEFT:
            moveLeft(context, Granularity.ALL);
            break;
        case HOLD_SIX_RIGHT:
            moveRight(context, Granularity.ALL);
            break;
        case HOLD_SIX_DOWN:
            boolean echoPassword = Options.switchBooleanPreference(context,
                    R.string.pref_echo_passwords_key, false);
            message = echoPassword ? context
                    .getString(R.string.speak_passwords) : context
                    .getString(R.string.no_password_echo);
            break;
        case HOLD_SIX_UP:
            message = getInput(Granularity.ALL);
            considerPassword = true;
            break;
        case HOLD_THREE_LEFT:
            backspace(context, Granularity.ALL, fastDoubleSwipe);
            break;
        case HOLD_THREE_RIGHT:
            int brailleType = listener.switchBrailleType();
            message = brailleType == 8 ? context
                    .getString(R.string.grade_computer) : context
                    .getString(R.string.grade_literary);
            callback.onSetLocale(listener.getLocale());
            break;
        case HOLD_THREE_DOWN:
            message = listener.switchTable();
            message = message == null ? context
                    .getString(R.string.no_braille_table) : message;
            callback.onSetLocale(listener.getLocale());
            break;
        case HOLD_THREE_UP:
            doVoiceInput(context, fastDoubleSwipe);
            break;
        case HOLD_ONE_RIGHT:
            message = context
                    .getString(listener.toggleMark() ? R.string.set_mark
                            : R.string.unset_mark);
            break;
        case HOLD_ONE_LEFT:
            message = context.getString(R.string.keyboard_shrink);
            callback.onShrink();
            break;
        case HOLD_ONE_DOWN:
            CharSequence text = listener.getAllText().text;
            if (text != null) {
                message = String.format(context.getString(R.string.word_count),
                        EditingUtilities.lineCount(text),
                        EditingUtilities.wordCount(text),
                        EditingUtilities.characterCount(text));
            }
            break;
        case HOLD_ONE_UP:
            Options.switchBooleanPreference(context,
                    R.string.pref_auto_caps_key, Boolean.parseBoolean(context
                            .getString(R.string.pref_auto_caps_default)));
            message = Options.getBooleanPreference(context,
                    R.string.pref_auto_caps_key, Boolean.parseBoolean(context
                            .getString(R.string.pref_auto_caps_default))) ? context
                    .getString(R.string.auto_caps_enabled) : context
                    .getString(R.string.auto_caps_disabled);
            break;
        case HOLD_FOUR_LEFT:
            doSpellCheck(context, SpellChecker.Direction.LEFT, 0,
                    listener.getCursor());
            break;
        case HOLD_FOUR_RIGHT:
            doSpellCheck(context, SpellChecker.Direction.RIGHT, 0,
                    listener.getCursor());
            break;
        case HOLD_FOUR_DOWN:
            nextSpellCheckSuggestion(context);
            break;
        case HOLD_FOUR_UP:
            previousSpellCheckSuggestion(context);
            break;
        case NONE:
            return false;
        default:
            notify = false;
        }

        // Invoke the notification callback
        callback.onNotify(notify, notify ? soundFor(value) : null);
        if (message != null) {
            // Only invoke onText callback if there is a message to send i.e. it
            // wasn't already handled.
            callback.onText("%s", message,
                    considerPassword ? listener.isPasswordField() : false);
        }

        if (setDots) { // Dots 7 or 8 were triggered
            callback.onSetDots(dots[0], dots[1]);
        }

        lastSwipe = value; // update the last swipe
        return true;
    }

    /**
     * Handle typing a Braille character into the underlying IME. The character
     * is delivered as a byte value representing the dot pattern and will be
     * converted and written as a standard textual character by the IME.
     * 
     * @param context
     *            The application context.
     * @param value
     *            The byte value which represents the dot pattern to type. This
     *            is a bitstring that represents a Braille pattern where dot 8
     *            is represented by the MSB and dot 1 by the LSB. A value of 0
     *            means no dots are present and a value of 0b11111111 means all
     *            8 dots are pressed.
     */
    public void handleCharacter(Context context, byte value) {
        // Can't type while voice input is in progress.
        if (voiceInput.isListening()) {
            return;
        }

        lastSwipe = Swipe.NONE;
        correctedFrom = null;
        if (menuPosition >= 0) {
            callback.onText("%s",
                    context.getString(R.string.menu_instructions), false);
            return;
        }
        String result;
        if ((result = listener.handleTypedCharacter(value)) == null) {
            // IME couldn't handle the dot pattern propergate the error to the
            // callback.
            callback.onNotify(false, Earcons.Sound.ERROR);
            callback.onText("%s",
                    context.getString(R.string.unknown_character), false);
        } else {
            callback.onNotify(true, Earcons.Sound.TYPE);

            // Decide what to deliver to the callback such as a key echo or
            // autocompletion string.
            String character = echoCharacter(context, result);
            result = character == null ? "" : character;
            if (!(result = result.trim()).equals("")) {
                callback.onText("%s", result.toString(),
                        listener.isPasswordField());
            }
        }

        // dots 7 and 8 should now be unset
        callback.onSetDots(false, false);
    }

    /**
     * Handle a gesture of the TalkBack braille keyboard. See TalkBackGesture.
     *
     * @param context
     *            The application context.
     * @param action
     *            The recognised TalkBack action, or null if the gesture has
     *            none.
     * @param directions
     *            The direction of each dot, to describe the gesture in
     *            gesture practice.
     */
    public void handleTalkBackAction(Context context,
            TalkBackGesture.Action action, byte[] directions) {
        if (voiceInput.isListening()) {
            return;
        }
        lastSwipe = Swipe.NONE;
        if (menuPosition >= 0) {
            if (action != null) {
                handleMenuAction(context, action);
            }
            return;
        }
        // Practising gestures only describes them, except opening the menu
        // which has the item to stop practising.
        if (GesturePractice.isOn(context)
                && action != TalkBackGesture.Action.HELP_AND_OTHER_ACTIONS) {
            callback.onNotify(true, Earcons.Sound.MOVE);
            speak(GesturePractice.describe(context, action, directions));
            return;
        }
        if (action == null) {
            return;
        }
        if (readingGranularity == ReadingGranularity.SPELLING
                && handleSpellingAction(context, action)) {
            callback.onNotify(true, Earcons.Sound.SELECT);
            return;
        }

        callback.onNotify(true, soundFor(action));
        switch (action) {
        case MOVE_CURSOR_BACKWARD:
            moveLeft(context, readingGranularity.granularity);
            break;
        case MOVE_CURSOR_FORWARD:
            moveRight(context, readingGranularity.granularity);
            break;
        case ADD_SPACE:
            typeCharacter(context, (int) ' ', " ");
            break;
        case DELETE_CHARACTER:
            backspace(context, Granularity.CHARACTER, false);
            break;
        case SUBMIT_TEXT:
            if (listener.performEditorAction()) {
                listener.hideKeyboard();
            } else {
                speak(context.getString(R.string.no_editor_action));
            }
            break;
        case HIDE_KEYBOARD:
            listener.hideKeyboard();
            break;
        case ADD_NEWLINE:
            addNewline(context);
            break;
        case DELETE_WORD:
            backspace(context, Granularity.WORD, false);
            break;
        case HELP_AND_OTHER_ACTIONS:
            openMenu(context);
            break;
        case SWITCH_KEYBOARD:
            listener.switchToNextKeyboard();
            break;
        case NEXT_GRANULARITY:
            changeGranularity(context, 1);
            break;
        case PREVIOUS_GRANULARITY:
            changeGranularity(context, -1);
            break;
        case PREVIOUS_CHARACTER:
            moveLeft(context, Granularity.CHARACTER);
            break;
        case NEXT_CHARACTER:
            moveRight(context, Granularity.CHARACTER);
            break;
        case PREVIOUS_WORD:
            moveLeft(context, Granularity.WORD);
            break;
        case NEXT_WORD:
            moveRight(context, Granularity.WORD);
            break;
        case PREVIOUS_LINE:
            moveLeft(context, Granularity.LINE);
            break;
        case NEXT_LINE:
            moveRight(context, Granularity.LINE);
            break;
        case START_OF_TEXT:
            moveLeft(context, Granularity.ALL);
            break;
        case END_OF_TEXT:
            moveRight(context, Granularity.ALL);
            break;
        case SELECT_PREVIOUS_CHARACTER:
            extendSelection(context, Granularity.CHARACTER, false);
            break;
        case SELECT_NEXT_CHARACTER:
            extendSelection(context, Granularity.CHARACTER, true);
            break;
        case SELECT_PREVIOUS_WORD:
            extendSelection(context, Granularity.WORD, false);
            break;
        case SELECT_NEXT_WORD:
            extendSelection(context, Granularity.WORD, true);
            break;
        case SELECT_PREVIOUS_LINE:
            extendSelection(context, Granularity.LINE, false);
            break;
        case SELECT_NEXT_LINE:
            extendSelection(context, Granularity.LINE, true);
            break;
        case SELECT_TO_START:
            extendSelection(context, Granularity.ALL, false);
            break;
        case SELECT_TO_END:
            extendSelection(context, Granularity.ALL, true);
            break;
        case SELECT_ALL:
            listener.finishComposingText();
            if (listener.performContextMenuAction(android.R.id.selectAll)) {
                speak(context.getString(R.string.selected_all));
            }
            break;
        case CUT:
            clipboardAction(context, android.R.id.cut, R.string.cut_text,
                    R.string.cut_error);
            break;
        case COPY:
            clipboardAction(context, android.R.id.copy, R.string.copied,
                    R.string.copy_error);
            break;
        case PASTE:
            clipboardAction(context, android.R.id.paste, R.string.pasted,
                    R.string.paste_error);
            break;
        default:
        }
    }

    private static Earcons.Sound soundFor(TalkBackGesture.Action action) {
        switch (action) {
        case ADD_SPACE:
            return Earcons.Sound.SPACE;
        case DELETE_CHARACTER:
        case DELETE_WORD:
        case CUT:
            return Earcons.Sound.DELETE;
        case ADD_NEWLINE:
            return Earcons.Sound.NEWLINE;
        case SUBMIT_TEXT:
            return Earcons.Sound.SUBMIT;
        case HELP_AND_OTHER_ACTIONS:
            return Earcons.Sound.MENU_OPEN;
        case HIDE_KEYBOARD:
        case SWITCH_KEYBOARD:
            return Earcons.Sound.MENU_CLOSE;
        case NEXT_GRANULARITY:
        case PREVIOUS_GRANULARITY:
            return Earcons.Sound.TOGGLE;
        case SELECT_PREVIOUS_CHARACTER:
        case SELECT_NEXT_CHARACTER:
        case SELECT_PREVIOUS_WORD:
        case SELECT_NEXT_WORD:
        case SELECT_PREVIOUS_LINE:
        case SELECT_NEXT_LINE:
        case SELECT_TO_START:
        case SELECT_TO_END:
        case SELECT_ALL:
        case COPY:
        case PASTE:
            return Earcons.Sound.SELECT;
        default:
            return Earcons.Sound.MOVE;
        }
    }

    private static Earcons.Sound soundFor(Swipe swipe) {
        switch (swipe) {
        case FOUR_RIGHT:
            return Earcons.Sound.SPACE;
        case FOUR_LEFT:
        case FIVE_LEFT:
        case SIX_LEFT:
        case HOLD_THREE_LEFT:
            return Earcons.Sound.DELETE;
        case FOUR_DOWN:
            return Earcons.Sound.NEWLINE;
        case ONE_DOWN:
        case TWO_DOWN:
        case FOUR_UP:
        case HOLD_SIX_DOWN:
        case HOLD_ONE_UP:
        case HOLD_THREE_RIGHT:
        case HOLD_THREE_DOWN:
            return Earcons.Sound.TOGGLE;
        case HOLD_ONE_RIGHT:
        case SIX_RIGHT:
        case SIX_UP:
            return Earcons.Sound.SELECT;
        default:
            return Earcons.Sound.MOVE;
        }
    }

    /**
     * Returns true if the spoken menu is open. Typing is disabled meanwhile.
     */
    public boolean isMenuOpen() {
        return menuPosition >= 0;
    }

    private void speak(String message) {
        callback.onText("%s", message, false);
    }

    // Cut, copy or paste the real text selection like TalkBack does.
    private void clipboardAction(Context context, int id, int success,
            int error) {
        listener.finishComposingText();
        int[] range = listener.getSelectionRange();
        if (id != android.R.id.paste
                && (range == null || range[0] == range[1])) {
            speak(context.getString(R.string.nothing_selected));
            return;
        }
        speak(context.getString(listener.performContextMenuAction(id) ? success
                : error));
    }

    // Moves the focus end of the selection by the given granularity, keeping
    // the other end anchored, and speaks the text that was selected or
    // unselected.
    private void extendSelection(Context context, Granularity granularity,
            boolean forward) {
        listener.finishComposingText();
        int[] range = listener.getSelectionRange();
        if (range == null) {
            return;
        }
        int anchor = range[0];
        int focus = range[1];

        // Move a collapsed cursor from the focus with the movement helpers.
        listener.setSelection(focus);
        switch (granularity) {
        case CHARACTER:
            if (forward) {
                EditingUtilities.moveToNextCharacter(listener);
            } else {
                EditingUtilities.moveToPreviousCharacter(listener);
            }
            break;
        case WORD:
            if (forward) {
                EditingUtilities.moveToNextWord(listener);
            } else {
                EditingUtilities.moveToPreviousWord(listener);
            }
            break;
        case LINE:
            if (forward) {
                EditingUtilities.moveToNextLine(listener);
            } else {
                EditingUtilities.moveToPreviousLine(listener);
            }
            break;
        default:
            if (forward) {
                EditingUtilities.moveToEnd(listener);
            } else {
                EditingUtilities.moveToHome(listener);
            }
        }
        int newFocus = listener.getCursor();
        listener.selectRange(anchor, newFocus);

        if (newFocus == focus || newFocus < 0) {
            speak(context.getString(forward ? R.string.end_of_text
                    : R.string.start_of_text));
            return;
        }
        ExtractedText text = listener.getAllText();
        if (text == null || text.text == null) {
            return;
        }
        int start = Math.max(0, Math.min(focus, newFocus) - text.startOffset);
        int end = Math.min(text.text.length(), Math.max(focus, newFocus)
                - text.startOffset);
        if (start >= end) {
            return;
        }
        String changed = text.text.subSequence(start, end).toString();
        boolean selecting = Math.abs(newFocus - anchor) > Math.abs(focus
                - anchor);
        callback.onText(context.getString(selecting ? R.string.text_selected
                : R.string.text_unselected), changed, listener.isPasswordField());
    }

    private void changeGranularity(Context context, int step) {
        ReadingGranularity[] values = ReadingGranularity.values();
        int count = values.length;
        if (!spellChecker.isSpellCheckAvailable()) {
            count--; // SPELLING is last and needs a spell checker.
        }
        int index = (readingGranularity.ordinal() + step + count) % count;
        readingGranularity = values[index];
        speak(context.getString(readingGranularity.resource));
    }

    // In the spelling granularity TalkBack's typo correction gestures apply.
    // Returns false for gestures that keep their usual meaning.
    private boolean handleSpellingAction(Context context,
            TalkBackGesture.Action action) {
        switch (action) {
        case MOVE_CURSOR_BACKWARD:
            doSpellCheck(context, SpellChecker.Direction.LEFT, 0,
                    listener.getCursor());
            return true;
        case MOVE_CURSOR_FORWARD:
            doSpellCheck(context, SpellChecker.Direction.RIGHT, 0,
                    listener.getCursor());
            return true;
        case ADD_SPACE:
            nextSpellCheckSuggestion(context);
            return true;
        case DELETE_CHARACTER:
            previousSpellCheckSuggestion(context);
            return true;
        case ADD_NEWLINE: // Confirm the suggestion.
            spellingSuggestion = null;
            speak(context.getString(R.string.suggestion_confirmed));
            return true;
        case DELETE_WORD: // Undo the suggestion.
            if (spellingSuggestion != null && spellCheckerMatchesWord()) {
                spellingSuggestion.reset();
                handleSpellingSuggestion(context);
            } else {
                speak(context.getString(R.string.nothing_to_undo));
            }
            return true;
        default:
            return false;
        }
    }

    private void openMenu(Context context) {
        menuPosition = 0;
        speak(context.getString(R.string.menu_opened) + " "
                + context.getString(MenuItem.values()[0].resource));
    }

    private void closeMenu(Context context, boolean announce) {
        menuPosition = -1;
        if (announce) {
            callback.onNotify(true, Earcons.Sound.MENU_CLOSE);
            speak(context.getString(R.string.menu_closed));
        }
    }

    // While the menu is open swipe up and down to move, right to choose and
    // left (or any gesture that closes things in TalkBack) to close.
    private void handleMenuAction(Context context, TalkBackGesture.Action action) {
        MenuItem[] items = MenuItem.values();
        switch (action) {
        case MOVE_CURSOR_BACKWARD:
            menuPosition = (menuPosition - 1 + items.length) % items.length;
            callback.onNotify(true, Earcons.Sound.MOVE);
            speak(context.getString(items[menuPosition].resource));
            break;
        case MOVE_CURSOR_FORWARD:
            menuPosition = (menuPosition + 1) % items.length;
            callback.onNotify(true, Earcons.Sound.MOVE);
            speak(context.getString(items[menuPosition].resource));
            break;
        case ADD_SPACE:
            MenuItem item = items[menuPosition];
            closeMenu(context, false);
            performMenuItem(context, item);
            break;
        case DELETE_CHARACTER:
        case HIDE_KEYBOARD:
        case HELP_AND_OTHER_ACTIONS:
            closeMenu(context, true);
            break;
        default:
            speak(context.getString(R.string.menu_instructions));
        }
    }

    private void performMenuItem(Context context, MenuItem item) {
        if (item.swipe != null) {
            handleSwipe(context, item.swipe);
            return;
        }
        switch (item) {
        case VOICE_INPUT:
            doVoiceInput(context, true);
            break;
        case AUTO_CORRECT:
            boolean correct = Options.switchBooleanPreference(context,
                    R.string.pref_auto_correct_key, Boolean.parseBoolean(context
                            .getString(R.string.pref_auto_correct_default)));
            if (correct && !autoCorrect.isAvailable()) {
                speak(context.getString(R.string.auto_correct_unavailable));
            } else {
                speak(context.getString(correct ? R.string.auto_correct_enabled
                        : R.string.auto_correct_disabled));
            }
            break;
        case GESTURE_PRACTICE:
            boolean practice = Options.switchBooleanPreference(context,
                    R.string.pref_gesture_practice_key, Boolean
                            .parseBoolean(context
                                    .getString(R.string.pref_gesture_practice_default)));
            speak(context.getString(practice ? R.string.gesture_practice_enabled
                    : R.string.gesture_practice_disabled));
            break;
        case SETTINGS:
            callback.onSetLocale(Locale.getDefault());
            Intent settings = new Intent(context, PreferenceIME.class);
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settings);
            break;
        case HELP:
            callback.onSetLocale(Locale.getDefault());
            context.startActivity(ManualActivity.createIntent(context,
                    ManualActivity.SECTION_TALKBACK_GESTURES).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK));
            break;
        default:
        }
    }

    // Handle prompting user to confirm an action with a double swipe.
    private boolean isConfirmed(Context context, boolean fastDoubleTouch) {
        if (!fastDoubleTouch) {
            callback.onText("%s", context.getString(R.string.swipe_confirm),
                    false);
            return false;
        }
        return true;
    }

    // Perform the currently selected Edit Action on the region.
    private void selectAction(Context context) {
        switch (editAction) {
        case COPY:
            performContextMenuAction(context, true, android.R.id.copy,
                    R.string.copied, R.string.copy_error);
            listener.deselect();
            break;
        case CUT:
            if (performContextMenuAction(context, true, android.R.id.cut,
                    android.R.string.cut, R.string.cut_error)) {
                listener.setCursorToStartOfSelection();
            }
            break;
        case PASTE:
            if (performContextMenuAction(context, false, android.R.id.paste,
                    R.string.pasted, R.string.paste_error)) {
                int cursor = listener.getCursor();
                listener.setSelection(cursor);
            }
            break;
        case SPEAK:
        case DELETE:
            performAction(context, editAction);
            break;
        case SELECT_ALL:
            listener.selectAll();
            callback.onText("%s", context.getString(R.string.selected_all),
                    false);
            break;
        default:
        }
    }

    // Handle these actions by using the inbuilt android context menu action
    private boolean performContextMenuAction(Context context,
            boolean requiresSelection, int code, int successString,
            int errorString) {
        if (!requiresSelection || listener.setSelection()) {
            if (listener.performContextMenuAction(code)) {
                callback.onText("%s", context.getString(successString), false);
                return true;
            } else {
                callback.onText("%s", context.getString(errorString), false);
                return false;
            }
        } else {
            callback.onText("%s", context.getString(R.string.mark_not_set),
                    false);
            return false;
        }
    }

    // These actions aren't implemented by Android so do them ourselves.
    private boolean performAction(Context context, EditAction action) {
        if (listener.setSelection()) {
            CharSequence text = listener.getSelectedText(0);
            listener.deselect();
            switch (action) {
            case SPEAK:
                callback.onText(
                        "%s",
                        text == null ? context.getString(R.string.blank) : text
                                .toString(), listener.isPasswordField());
                break;
            case DELETE:
                if (listener.deleteSelection() && text != null) {
                    callback.onText(context.getString(R.string.deleted),
                            text.toString(), listener.isPasswordField());
                } else {
                    callback.onText("%s",
                            context.getString(R.string.nothing_to_delete),
                            false);
                }
                break;
            default:
                return false;
            }
            return true;
        }
        callback.onText("%s", context.getString(R.string.mark_not_set), false);
        return false;
    }

    private void nextAction(Context context) {
        editAction = editAction.next(clipboard);
        callback.onText("%s", context.getString(editAction.resource), false);
    }

    // Move the cursor left by the appropriate granularity and speak the result.
    private void moveLeft(Context context, Granularity granularity) {
        EditingUtilities.Word word = null;
        listener.finishComposingText();
        if (listener.isSelectAll()) {
            granularity = Granularity.ALL;
            listener.setSelection(0);
        }
        switch (granularity) {
        case CHARACTER:
            word = EditingUtilities.moveToPreviousCharacter(listener);
            break;
        case WORD:
            word = EditingUtilities.moveToPreviousWord(listener);
            break;
        case LINE:
            word = EditingUtilities.moveToPreviousLine(listener);
            break;
        case ALL:
            word = EditingUtilities.moveToHome(listener);
            break;
        default:
        }

        focusCursor = listener.getCursor();
        if (word != null) {
            callback.onText("%s",
                    !word.moveLeft ? context.getString(R.string.start_of_text)
                            : word.word,
                    word.moveLeft && listener.isPasswordField());
        }
    }

    // Move the cursor right by the appropriate granularity and speak the
    // result.
    private void moveRight(Context context, Granularity granularity) {
        EditingUtilities.Word word = null;
        listener.finishComposingText();
        if (listener.isSelectAll()) {
            granularity = Granularity.ALL;
            listener.setSelection(0);
        }
        switch (granularity) {
        case CHARACTER:
            word = EditingUtilities.moveToNextCharacter(listener);
            break;
        case WORD:
            word = EditingUtilities.moveToNextWord(listener);
            break;
        case LINE:
            word = EditingUtilities.moveToNextLine(listener);
            break;
        case ALL:
            word = EditingUtilities.moveToEnd(listener);
            break;
        default:
        }

        focusCursor = listener.getCursor();
        if (word != null) {
            callback.onText("%s",
                    !word.moveRight ? context.getString(R.string.end_of_text)
                            : word.word,
                    word.moveRight && listener.isPasswordField());
        }
    }

    // Perform backspace by the specified Granularity.
    private boolean backspace(Context context, Granularity granularity,
            boolean fastDoubleTouch) {
        EditingUtilities.Word word = null;
        boolean canDelete = true;
        if (granularity == Granularity.CHARACTER) {
            // A contracted word still being typed isn't in the text yet.
            String cell = listener.deleteHeldCell();
            if (cell != null) {
                if (cell.trim().length() > 0) {
                    callback.onText(context.getString(R.string.deleted), cell,
                            listener.isPasswordField());
                } else {
                    speak(context.getString(R.string.cell_deleted));
                }
                return true;
            }
        }
        if ((granularity == Granularity.CHARACTER
                || granularity == Granularity.WORD)
                && deleteSelectedText(context)) {
            focusCursor = -1;
            return true;
        }
        // After moving or reading, delete the text that was spoken.
        boolean focused = focusCursor != -1
                && focusCursor == listener.getCursor();
        focusCursor = -1;
        boolean deletedFocus = false;
        switch (granularity) {
        case CHARACTER:
            if (focused) {
                listener.finishComposingText();
                word = EditingUtilities.getFocusedCharacter(listener);
                if (word != null) {
                    deletedFocus = true;
                    correctedFrom = null;
                    break;
                }
            }
            if (undoAutoCorrection(context)) {
                return true;
            }
            listener.finishComposingText();
            word = EditingUtilities.moveToPreviousCharacter(listener);
            break;
        case WORD:
            listener.finishComposingText();
            if (focused) {
                word = EditingUtilities.getFocusedWord(listener);
                if (word != null) {
                    deletedFocus = true;
                    break;
                }
            }
            Word space = EditingUtilities.skipSepBackwards(listener,
                    EditingUtilities.WORD_SEPARATORS);
            word = EditingUtilities.getWord(listener);
            if (word != null) {
                if (space != null) {
                    word.charsBefore += space.charsBefore;
                }
                if (word.word.length() > word.charsBefore) {
                    word.word = word.word.substring(0, word.charsBefore);
                }
                EditingUtilities.moveToPreviousWord(listener);
            }
            break;
        case LINE:
            canDelete = isConfirmed(context, fastDoubleTouch);
            if (canDelete) {
                listener.finishComposingText();
                int cursor = listener.getCursor();
                word = EditingUtilities.getLine(listener);
                if (word != null && (cursor > 0 || word.charsAfter > 0)) {
                    int moveChars = (cursor - word.charsBefore) > 0 ? 1 : 0;
                    listener.setSelection(cursor - moveChars - word.charsBefore);
                    word.charsBefore = word.charsBefore + moveChars
                            + word.charsAfter;
                }
            }
            break;
        case ALL:
            canDelete = isConfirmed(context, fastDoubleTouch);
            if (canDelete) {
                listener.finishComposingText();
                word = EditingUtilities.moveToHome(listener);
                word.word = EditingUtilities.getAllText(listener);
                word.charsBefore = word.word.length();
            }
            break;
        default:
        }
        boolean deleted = performDelete(context, word, canDelete);
        if (deletedFocus) {
            // The text after the deleted text is now the focus.
            focusCursor = listener.getCursor();
        }
        return deleted;
    }

    // Given the text to delete and a canDelete flag do the actual deletion.
    // Deletes the selected text, such as text selected with the TalkBack
    // gestures, as editors do. Returns false if nothing is selected.
    private boolean deleteSelectedText(Context context) {
        listener.finishComposingText();
        int[] range = listener.getSelectionRange();
        if (range == null || range[0] == range[1]) {
            return false;
        }
        int start = Math.min(range[0], range[1]);
        int end = Math.max(range[0], range[1]);
        String deleted = null;
        ExtractedText text = listener.getAllText();
        if (text != null && text.text != null) {
            int from = Math.max(0, start - text.startOffset);
            int to = Math.min(text.text.length(), end - text.startOffset);
            if (from < to) {
                deleted = text.text.subSequence(from, to).toString();
            }
        }
        correctedFrom = null;
        listener.setSelection(end);
        if (!listener.deleteSurroundingText(end - start, 0)) {
            return false;
        }
        if (deleted != null) {
            callback.onText(context.getString(R.string.deleted), deleted,
                    listener.isPasswordField());
        }
        return true;
    }

    private boolean performDelete(Context context, EditingUtilities.Word word,
            boolean canDelete) {
        if (canDelete && word != null) {
            if (word.charsBefore > 0 || word.charsAfter > 0) {
                // cursor moved back word.charsBefore positions, so delete that
                // many chars ahead of the cursor.
                if (listener.deleteSurroundingText(0, word.charsBefore)) {
                    if (word.word != null) {
                        callback.onText(context.getString(R.string.deleted),
                                word.word, listener.isPasswordField());
                    }
                    return true;
                } else {
                    return false;
                }
            } else {
                callback.onText("%s",
                        context.getString(R.string.nothing_to_delete), false);
                return true;
            }
        }
        return false;
    }

    // Insert a certain character like a ' ' or '\n'
    private void typeCharacter(Context context, int code, String charName) {
        listener.finishComposingText();
        String word = wordBeforeCursor();
        listener.onKey(code);
        announceTyped(context, word, charName);
        if (code == ' ' && isAutoCorrectOn(context)) {
            autoCorrectWord(context);
        } else {
            echoMisspelling(context);
        }
    }

    // Adds a line break, which unlike the enter key never submits the text.
    private void addNewline(Context context) {
        listener.finishComposingText();
        String word = wordBeforeCursor();
        if (!listener.insertNewline()) {
            speak(context.getString(R.string.single_line_field));
            return;
        }
        announceTyped(context, word, context.getString(R.string.newline));
        echoMisspelling(context);
    }

    // The part of the word before the cursor.
    private String wordBeforeCursor() {
        Word word = EditingUtilities.getWord(listener);
        return word == null ? null : word.word.substring(0, word.charsBefore);
    }

    // Echoes the word that a space or new line finished. Character echo
    // names the space or new line instead, as does word echo when there is
    // no word before it.
    private void announceTyped(Context context, String word, String charName) {
        boolean hasWord = word != null && word.trim().length() > 0;
        String message = hasWord ? echoWord(context, word) : null;
        if (message == null) {
            message = echoCharacter(context, charName);
        }
        if (message == null && !hasWord) {
            message = echoWord(context, charName);
        }
        if (message != null) {
            callback.onText("%s", message, listener.isPasswordField());
        }
    }

    private void echoMisspelling(Context context) {
        if (Options.getBooleanPreference(context,
                R.string.pref_echo_misspellings_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_echo_misspellings_default)))
                && spellChecker.isSpellCheckAvailable()) {
            doSpellCheck(context, SpellChecker.Direction.UNDER_CURSOR, 0,
                    listener.getCursor() - 2);
        }
    }

    private boolean isAutoCorrectOn(Context context) {
        return Options.getBooleanPreference(context,
                R.string.pref_auto_correct_key, Boolean.parseBoolean(context
                        .getString(R.string.pref_auto_correct_default)))
                && autoCorrect.isAvailable() && listener.allowsCorrections();
    }

    // Replaces the word before the space just typed with the spell checker's
    // correction, if it is misspelled.
    private void autoCorrectWord(final Context context) {
        correctedFrom = null;
        final String word = AutoCorrect.wordBeforeSpace(listener
                .getTextBeforeCursor(AutoCorrect.MAX_WORD_LENGTH + 2));
        if (word == null || keptWords.contains(word)) {
            return;
        }
        final int cursor = listener.getCursor();
        autoCorrect.check(word, new AutoCorrect.Listener() {
            @Override
            public void onChecked(String checked, String correction,
                    boolean misspelled) {
                if (correction == null) {
                    if (misspelled) {
                        announceMisspelled(context);
                    }
                    return;
                }
                // Only correct if nothing was typed since, which could be
                // part of a contracted braille word being composed.
                CharSequence before = listener.getTextBeforeCursor(word
                        .length() + 1);
                if (listener.getCursor() != cursor || before == null
                        || !before.toString().equals(word + " ")) {
                    return;
                }
                listener.deleteSurroundingText(word.length() + 1, 0);
                listener.commitText(correction + " ", 1);
                correctedFrom = word;
                correctedTo = correction;
                callback.onText(context.getString(R.string.auto_corrected),
                        correction, listener.isPasswordField(),
                        Speech.QUEUE_ADD);
            }
        });
    }

    private void announceMisspelled(Context context) {
        if (Options.getBooleanPreference(context,
                R.string.pref_echo_misspellings_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_echo_misspellings_default)))) {
            callback.onText("%s", context.getString(R.string.word_misspelled),
                    false, Speech.QUEUE_ADD);
        }
    }

    // Deleting the space right after an auto-correction puts back the word
    // as it was typed, and leaves that word alone from then on.
    private boolean undoAutoCorrection(Context context) {
        String from = correctedFrom;
        String to = correctedTo;
        correctedFrom = null;
        if (from == null) {
            return false;
        }
        listener.finishComposingText();
        CharSequence before = listener.getTextBeforeCursor(to.length() + 1);
        if (before == null || !before.toString().equals(to + " ")) {
            return false;
        }
        listener.deleteSurroundingText(to.length() + 1, 0);
        listener.commitText(from, 1);
        keptWords.add(from);
        callback.onText(context.getString(R.string.auto_correct_undone), from,
                listener.isPasswordField());
        return true;
    }

    // Special logic for double space to insert a period followed by a space.
    private boolean handleDoubleSpace(Context context) {
        if (Options.getBooleanPreference(context,
                R.string.pref_double_space_period_key,
                Boolean.parseBoolean(context
                        .getString(R.string.pref_double_space_period_default)))) {
            CharSequence text = listener.getTextBeforeCursor(2);
            if (text != null) {
                if (text.length() == 2
                        && Character.isWhitespace(text.charAt(1))
                        && Character.isLetterOrDigit(text.charAt(0))) {
                    listener.deleteSurroundingText(1, 0);
                    listener.onKey('.');
                    typeCharacter(context, ' ', " ");
                    return true;
                }
            }
        }
        return false;
    }

    // Return true if the same gesture was typed quickly in succession.
    private boolean fastDoubleSwipe(Swipe swipe, long threshold) {
        if ((lastTouchTime + threshold) > System.currentTimeMillis()
                && lastSwipe == swipe) {
            lastTouchTime = 0;
            return true;
        } else {
            lastSwipe = swipe;
            lastTouchTime = System.currentTimeMillis();
            return false;
        }
    }

    // Get a particular granularity of text from the IME.
    private String getInput(Granularity granularity) {
        String text = "";
        Word word;
        switch (granularity) {
        case CHARACTER:
            text = EditingUtilities.getCharacter(listener);
            break;
        case WORD:
            word = EditingUtilities.getWord(listener);
            text = word == null ? null : word.word;
            break;
        case LINE:
            word = EditingUtilities.getLine(listener);
            text = word == null ? null : word.word;
            break;
        case ALL:
            text = EditingUtilities.getAllText(listener);
            break;
        default:
        }
        return text;
    }

    // Handle voice input.
    public boolean doVoiceInput(final Context context, boolean fastDoubleSwipe) {
        // Check for the "dangerous permission" for Android 6 and higher.
        if (context.checkPermission(Manifest.permission.RECORD_AUDIO,
                Process.myPid(), Process.myUid()) != PackageManager.PERMISSION_GRANTED) {
            // Fast double swipe to show the permission dialog so we don't
            // surprise the user having no screen reader or talking keyboard in
            // focus.
            if (!fastDoubleSwipe) {
                callback.onText("%s",
                        context.getString(R.string.voice_input_enable), false);
            } else { // Show permission dialog
                Intent intent = new Intent(context, IntentActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setAction(context
                        .getString(R.string.action_record_audio_permission));
                intent.putExtra(
                        context.getString(R.string.require_record_audio_now),
                        true);
                context.startActivity(intent);
            }
            return false; // We didn't do voice input.
        }

        VoiceInput.TextReadyListener textReadyListener = new VoiceInput.TextReadyListener() {

            @Override
            public void onTextReady(String text) {
                // Write the text and send it back to the callback.
                if (text != null && text.length() > 0) {
                    CharSequence before = listener.getTextBeforeCursor(1);
                    if (before != null && before.length() > 0
                            && !Character.isWhitespace(before.charAt(0))) {
                        listener.onKey(' ');
                    }
                    listener.commitText(text, 1);
                    callback.onText("%s", text, listener.isPasswordField());
                }
            }

            @Override
            public void onError(int error) {
                callback.onText("%s", String.format(
                        context.getString(R.string.voice_input_error), error),
                        false);
            }
        };

        if (voiceInput.start(context, textReadyListener)) {
            callback.onShutup();
        } else {
            callback.onText("%s",
                    context.getString(R.string.voice_input_is_not_available),
                    false);
            return false;
        }
        return true;
    }

    // Rules for echoing character. Return the character if it should be echoed
    // else null.
    private static String echoCharacter(Context context, String character) {
        if ((Integer.parseInt(Options.getStringPreference(context,
                R.string.pref_echo_feedback_key,
                KeyboardEcho.CHARACTER.getValue())) & KeyboardEcho.CHARACTER.value) != 0) {
            return character;
        }
        return null;
    }

    // Rules for echoing word. Return the word if it should be echoed
    // else null.
    private static String echoWord(Context context, String word) {
        if ((Integer.parseInt(Options.getStringPreference(context,
                R.string.pref_echo_feedback_key,
                KeyboardEcho.CHARACTER.getValue())) & KeyboardEcho.WORD.value) != 0) {
            return word;
        }
        return null;
    }

    // Some swipe actions should resolve to the same thing eg. dots 4 and 5
    // swipe right.
    private static Swipe normaliseSwipe(Swipe swipe) {
        if (swipe == Swipe.FIVE_RIGHT) {
            return Swipe.FOUR_RIGHT;
        }
        return swipe;
    }

    private void doSpellCheck(final Context context,
            SpellChecker.Direction direction, int move, int cursor) {
        SpellingSuggestionsReadyListener spellingListener = new SpellingSuggestionsReadyListener() {

            @Override
            public void suggestionsReady(Suggestion result) {
                spellingSuggestion = result;
                if (result != null
                        || spellingDirection == SpellChecker.Direction.UNDER_CURSOR) {
                    handleSpellingSuggestion(context);
                } else {
                    callback.onText("%s",
                            context.getString(R.string.no_more_misspellings),
                            false);
                }
            }
        };

        String text = getInput(Granularity.ALL);
        spellingDirection = direction;
        directionThroughSuggestionList = move;
        if (text != null && text.length() > 0) {
            if (!spellChecker.checkSpelling(spellingListener, text, cursor,
                    direction)) {
                callback.onText("%s",
                        context.getString(R.string.spellcheck_not_supported),
                        false);
            }
        } else {
            callback.onText("%s", context.getString(R.string.blank), false);
        }
    }

    private void handleSpellingSuggestion(Context context) {
        boolean password = false;
        String message = null;

        if (spellingSuggestion == null && directionThroughSuggestionList != 0) {
            message = context.getString(R.string.word_correct);
        } else if (spellingSuggestion != null
                && spellingDirection == SpellChecker.Direction.UNDER_CURSOR) {
            spellingDirection = null;
            if (directionThroughSuggestionList > 0) {
                nextSpellCheckSuggestion(context);
            } else if (directionThroughSuggestionList < 0) {
                previousSpellCheckSuggestion(context);
            } else {
                message = context.getString(R.string.word_misspelled);
            }
        } else if (spellingSuggestion != null) {
            password = true;
            message = spellingSuggestion.isMisspelledWord() ? String.format(
                    context.getString(R.string.word_correction_misspelled),
                    spellingSuggestion.getCurrent()) : spellingSuggestion
                    .getCurrent();
            listener.setSelection(spellingSuggestion.offset);
            listener.deleteSurroundingText(0, spellingSuggestion.getLength());
            listener.commitText(spellingSuggestion.getCurrent(), 1);
            listener.setSelection(spellingSuggestion.offset);
            spellingSuggestion.setLength();
        }

        if (message != null) {
            callback.onText("%s", message, listener.isPasswordField()
                    && password, Speech.QUEUE_ADD);
        }
    }

    private void nextSpellCheckSuggestion(Context context) {
        if (spellingSuggestion != null) {
            if (spellCheckerMatchesWord()) {
                spellingSuggestion.next();
                handleSpellingSuggestion(context);
                return;
            }
        }
        doSpellCheck(context, SpellChecker.Direction.UNDER_CURSOR, 1,
                listener.getCursor());
    }

    private void previousSpellCheckSuggestion(Context context) {
        if (spellingSuggestion != null) {
            if (spellCheckerMatchesWord()) {
                spellingSuggestion.prev();
                handleSpellingSuggestion(context);
                return;
            }
        }
        doSpellCheck(context, SpellChecker.Direction.UNDER_CURSOR, -1,
                listener.getCursor());
    }

    private boolean spellCheckerMatchesWord() {
        String text = getInput(Granularity.ALL);
        if (text != null && spellingSuggestion != null) {
            int offset = spellingSuggestion.offset;
            int length = spellingSuggestion.getLength();
            int cursor = listener.getCursor();
            if (text.length() > 0 && (offset + length) <= text.length()
                    && cursor >= offset && cursor < (offset + length)) {
                return text.substring(offset, offset + length).equals(
                        spellingSuggestion.getCurrent());
            }
        }
        return false;
    }
}
