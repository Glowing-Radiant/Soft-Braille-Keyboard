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

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

import android.content.Context;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;

/**
 * Checks each word as a space is typed after it and suggests the correction
 * of a misspelling, like the auto-correct of phone keyboards. It has its own
 * spell checker session so it doesn't interfere with the spelling gestures.
 */
public class AutoCorrect {
    // Longer words aren't corrected; it also bounds the text read back.
    public static final int MAX_WORD_LENGTH = 40;
    private static final int MAX_SUGGESTIONS = 3;
    // Characters that may come right before a word, apart from whitespace.
    private static final String OPENING_PUNCTUATION = "([{\"'“‘«¿¡";

    /** Receives the result of checking a word. */
    public interface Listener {
        /**
         * @param word
         *            The word that was checked.
         * @param correction
         *            The correction, in the case of the word, or null if the
         *            word is spelled correctly or there is no suggestion.
         * @param misspelled
         *            Whether the spell checker thinks the word is misspelled.
         */
        void onChecked(String word, String correction, boolean misspelled);
    }

    private static final class Request {
        final String word;
        final Listener listener;

        Request(String word, Listener listener) {
            this.word = word;
            this.listener = listener;
        }
    }

    // Results arrive in the order the words were sent.
    private final Queue<Request> pending = new ArrayDeque<Request>();
    private final SpellCheckerSession session;

    private final SpellCheckerSessionListener sessionListener = new SpellCheckerSessionListener() {
        @Override
        public void onGetSuggestions(SuggestionsInfo[] results) {
        }

        @Override
        public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
            Request request = pending.poll();
            if (request == null) {
                return;
            }
            SuggestionsInfo info = null;
            if (results != null && results.length == 1 && results[0] != null) {
                SentenceSuggestionsInfo sentence = results[0];
                for (int i = 0; i < sentence.getSuggestionsCount(); i++) {
                    if (sentence.getOffsetAt(i) == 0
                            && sentence.getLengthAt(i) == request.word.length()) {
                        info = sentence.getSuggestionsInfoAt(i);
                        break;
                    }
                }
            }
            if (info == null || !isMisspelled(info.getSuggestionsAttributes())) {
                request.listener.onChecked(request.word, null, false);
                return;
            }
            String correction = null;
            if (info.getSuggestionsCount() > 0) {
                correction = matchCase(request.word, info.getSuggestionAt(0));
                if (correction.equals(request.word)) {
                    correction = null;
                }
            }
            request.listener.onChecked(request.word, correction, true);
        }
    };

    public AutoCorrect(Context context) {
        TextServicesManager tsm = (TextServicesManager) context
                .getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
        session = tsm == null ? null : tsm.newSpellCheckerSession(null, null,
                sessionListener, true);
    }

    /** Whether a spell checker is available to correct words. */
    public boolean isAvailable() {
        return session != null;
    }

    /**
     * Checks the spelling of a word. The listener is called later on this
     * thread.
     *
     * @return false if there is no spell checker.
     */
    public boolean check(String word, Listener listener) {
        if (session == null) {
            return false;
        }
        pending.add(new Request(word, listener));
        // The space helps some spell checkers see the end of the word.
        session.getSentenceSuggestions(new TextInfo[] { new TextInfo(word
                + " ") }, MAX_SUGGESTIONS);
        return true;
    }

    public void destroy() {
        pending.clear();
        if (session != null) {
            session.close();
        }
    }

    private static boolean isMisspelled(int attributes) {
        return (attributes & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) == 0
                && (attributes & (SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
                        | SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS)) != 0;
    }

    /**
     * Finds the word that was just finished by typing a space.
     *
     * @param textBeforeCursor
     *            The text before the cursor, ending with the space.
     * @return The word or null if the text doesn't end with a space after a
     *         word made of letters, for example after a number, an address
     *         or punctuation.
     */
    public static String wordBeforeSpace(CharSequence textBeforeCursor) {
        if (textBeforeCursor == null) {
            return null;
        }
        int end = textBeforeCursor.length() - 1;
        if (end < 1 || textBeforeCursor.charAt(end) != ' ') {
            return null;
        }
        int start = end;
        while (start > 0 && isWordCharacter(textBeforeCursor.charAt(start - 1))) {
            start--;
        }
        // Apostrophes can quote a word but not start it.
        while (start < end && isApostrophe(textBeforeCursor.charAt(start))) {
            start++;
        }
        if (start == end || end - start > MAX_WORD_LENGTH) {
            return null;
        }
        if (start > 0) {
            char before = textBeforeCursor.charAt(start - 1);
            if (!Character.isWhitespace(before)
                    && OPENING_PUNCTUATION.indexOf(before) < 0) {
                return null; // Part of something else, like 3rd or @name.
            }
        }
        String word = textBeforeCursor.subSequence(start, end).toString();
        if (isApostrophe(word.charAt(word.length() - 1))) {
            return null; // Could be a closing quote or a possessive.
        }
        return word;
    }

    /**
     * Gives the suggestion the case of the typed word: all capitals or a
     * capital first letter. A lower case word keeps the suggestion's case, so
     * "i" becomes "I" and names are capitalised.
     */
    public static String matchCase(String word, String suggestion) {
        if (suggestion.isEmpty()) {
            return suggestion;
        }
        if (word.length() > 1 && word.equals(word.toUpperCase(Locale.ROOT))
                && !word.equals(word.toLowerCase(Locale.ROOT))) {
            return suggestion.toUpperCase(Locale.getDefault());
        }
        if (Character.isUpperCase(word.charAt(0))) {
            return suggestion.substring(0, 1).toUpperCase(Locale.getDefault())
                    + suggestion.substring(1);
        }
        return suggestion;
    }

    private static boolean isWordCharacter(char c) {
        return Character.isLetter(c) || isApostrophe(c);
    }

    private static boolean isApostrophe(char c) {
        return c == '\'' || c == '’';
    }
}
