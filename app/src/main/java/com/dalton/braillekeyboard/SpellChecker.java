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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;

public class SpellChecker {
    private static final int MAX_SUGGESTIONS = 8;
    private static final int MAX_SENTENCE_LENGTH = 200;
    private static final int SENTENCES_TO_CONSIDER = 6;

    public interface SpellingSuggestionsReadyListener {
        void suggestionsReady(Suggestion result);
    }

    public enum Direction {
        LEFT, UNDER_CURSOR, RIGHT;
    }

    private final SpellCheckerSessionListener spellCheckerListener = new SpellCheckerSessionListener() {
        @Override
        public void onGetSuggestions(final SuggestionsInfo[] arg0) {
        }

        /**
         * Callback for
         * {@link SpellCheckerSession#getSentenceSuggestions(TextInfo[], int)}
         * 
         * @param results
         *            an array of {@link SentenceSuggestionsInfo}s. These
         *            results are suggestions for {@link TextInfo}s queried by
         *            {@link SpellCheckerSession#getSentenceSuggestions(TextInfo[], int)}
         *            .
         */
        @Override
        @SuppressLint("NewApi")
        public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] arg0) {
            if (arg0.length == 1) {
                SentenceSuggestionsInfo ssi = arg0[0];
                Suggestion results = null;
                if (ssi != null) {
                    int start = direction == Direction.LEFT ? ssi
                            .getSuggestionsCount() - 1 : 0;
                    int end = direction == Direction.LEFT ? -1 : ssi
                            .getSuggestionsCount();
                    int step = end < start ? -1 : 1;
                    int i = start;
                    while (i != end && results == null) {
                        if (isDirection(direction, cursor, ssi.getLengthAt(i),
                                ssi.getOffsetAt(i) + startOffset)) {
                            results = compileSuggestions(
                                    ssi.getSuggestionsInfoAt(i),
                                    ssi.getLengthAt(i), ssi.getOffsetAt(i)
                                            + startOffset);
                        }
                        i += step;
                    }
                }

                boolean moreToExpand = expandOffsets(true);
                if (results != null || !moreToExpand) {
                    listener.suggestionsReady(results);
                } else {
                    doSpellCheck();
                }
            } else {
                throw new IllegalArgumentException(
                        "Only supports one texinfo - got : " + arg0.length);
            }
        }
    };

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TextServicesManager textServices;
    private SpellCheckerSession spellChecker;
    // The built-in dictionary used instead of the session, if any.
    private String dictionary;
    private Locale locale;
    private SpellingSuggestionsReadyListener listener;
    private int cursor;
    private Direction direction;
    private String text;
    private int startOffset;
    private int endOffset;

    public SpellChecker(Context context) {
        this.context = context.getApplicationContext();
        textServices = (TextServicesManager) context
                .getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
        spellChecker = textServices.newSpellCheckerSession(null, null,
                spellCheckerListener, true);
    }

    /**
     * Sets the language to check, the braille table's, as auto-correct does
     * so the two agree. A built-in dictionary for it is used if there is
     * one. Otherwise, if the spell checker lacks the language, its own
     * language setting is used.
     */
    public void setLocale(Locale locale) {
        dictionary = SpellDictionaries.find(context, locale);
        if (locale == null ? this.locale == null : locale.equals(this.locale)) {
            return;
        }
        this.locale = locale;
        SpellCheckerSession session = locale == null ? null : textServices
                .newSpellCheckerSession(null, locale, spellCheckerListener,
                        false);
        if (session == null) {
            session = textServices.newSpellCheckerSession(null, null,
                    spellCheckerListener, true);
        }
        if (spellChecker != null) {
            spellChecker.close();
        }
        spellChecker = session;
    }

    public boolean checkSpelling(SpellingSuggestionsReadyListener listener,
            String text, int cursor, Direction direction) {
        if (dictionary != null && text.length() > 0) {
            checkWithDictionary(dictionary, listener, text, cursor, direction);
            return true;
        }
        if (isSpellCheckAvailable() && text.length() > 0) {
            this.cursor = cursor;
            this.direction = direction;
            this.text = text;
            this.listener = listener;
            initOffsets();
            doSpellCheck();
            return true;
        } else {
            return false;
        }
    }

    @SuppressLint("NewApi")
    private void doSpellCheck() {
        spellChecker.cancel();

        // Append a space (" ") to the input string to the spelling checker.
        // This resolves some edge cases like a word followed by a period
        // without a following space.
        spellChecker.getSentenceSuggestions(
                new TextInfo[] { new TextInfo(text.substring(startOffset,
                        endOffset) + " ") }, MAX_SUGGESTIONS);
    }

    public void destroy() {
        if (spellChecker != null) {
            spellChecker.close();
        }
    }

    public boolean isSpellCheckAvailable() {
        return dictionary != null
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                        && spellChecker != null);
    }

    // Finds the misspelled word in the given direction from the cursor with
    // a built-in dictionary, in the background.
    private void checkWithDictionary(final String name,
            final SpellingSuggestionsReadyListener listener, final String text,
            final int cursor, final Direction direction) {
        SpellDictionaries.run(new Runnable() {
            @Override
            public void run() {
                final Suggestion result = findMisspelling(
                        SpellDictionaries.load(context, name), text, cursor,
                        direction);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.suggestionsReady(result);
                    }
                });
            }
        });
    }

    private static Suggestion findMisspelling(Hunspell hunspell, String text,
            int cursor, Direction direction) {
        if (hunspell == null) {
            return null;
        }
        List<int[]> words = findWords(text);
        if (direction == Direction.LEFT) {
            Collections.reverse(words);
        }
        for (int[] word : words) {
            if (!isDirection(direction, cursor, word[1] - word[0], word[0])) {
                continue;
            }
            String spelling = text.substring(word[0], word[1]);
            if (!hunspell.isCorrect(spelling)) {
                Suggestion suggestion = new Suggestion(spelling, word[0]);
                suggestion.results.addAll(hunspell.suggest(spelling,
                        MAX_SUGGESTIONS));
                return suggestion;
            }
            if (direction == Direction.UNDER_CURSOR) {
                return null;
            }
        }
        return null;
    }

    /**
     * Finds the words to check in a text: letters, with apostrophes inside
     * them, that aren't part of something else such as 3rd, an address or a
     * user name.
     *
     * @return The start and end of each word.
     */
    static List<int[]> findWords(String text) {
        List<int[]> words = new ArrayList<int[]>();
        int i = 0;
        while (i < text.length()) {
            if (!Character.isLetter(text.charAt(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < text.length() && (isWordCharacter(text.charAt(i))
                    || (isApostrophe(text.charAt(i)) && i + 1 < text.length()
                            && Character.isLetter(text.charAt(i + 1))))) {
                i++;
            }
            if (!joinsSomethingElse(text, start - 1, -1)
                    && !joinsSomethingElse(text, i, 1)) {
                words.add(new int[] { start, i });
            }
        }
        return words;
    }

    private static boolean isWordCharacter(char c) {
        int type = Character.getType(c);
        return Character.isLetter(c) || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    private static boolean isApostrophe(char c) {
        return c == '\'' || c == '\u2019';
    }

    // Whether the character next to a word, at index, makes it part of
    // something else: a digit or a symbol used in addresses, or a full stop
    // or colon with more letters after it, as in example.com.
    private static boolean joinsSomethingElse(String text, int index,
            int step) {
        if (index < 0 || index >= text.length()) {
            return false;
        }
        char c = text.charAt(index);
        if (Character.isDigit(c) || "@_/\\#".indexOf(c) >= 0) {
            return true;
        }
        int beyond = index + step;
        return (c == '.' || c == ':') && beyond >= 0
                && beyond < text.length()
                && Character.isLetterOrDigit(text.charAt(beyond));
    }

    /**
     * Whether a spell checker's result means the word is misspelled. Words
     * it doesn't know but that don't look like typos, such as names, are
     * not.
     */
    static boolean isMisspelled(int attributes) {
        return (attributes & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) == 0
                && (attributes
                        & SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0;
    }

    private Suggestion compileSuggestions(SuggestionsInfo suggestionInfo,
            int length, int offset) {
        if (!isMisspelled(suggestionInfo.getSuggestionsAttributes())
                || !isPotentialWord(text.substring(offset, offset + length))) {
            return null;
        }

        Suggestion suggestion = new Suggestion(text.substring(offset, length
                + offset), offset);
        for (int i = 0; i < suggestionInfo.getSuggestionsCount(); i++) {
            suggestion.results.add(suggestionInfo.getSuggestionAt(i));
        }

        return suggestion;
    }

    private static boolean isDirection(Direction direction, int cursor,
            int length, int offset) {
        switch (direction) {
        case UNDER_CURSOR:
            return cursor >= offset && cursor < (length + offset);
        case LEFT:
            return (length + offset) <= cursor;
        case RIGHT:
            return offset > cursor;
        default:
            throw new IllegalArgumentException(
                    "No implementation for direction = " + direction);
        }
    }

    private static boolean isPotentialWord(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (Character.isLetter(word.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void initOffsets() {
        startOffset = Math.max(0, cursor - MAX_SENTENCE_LENGTH);
        endOffset = Math.min(cursor + MAX_SENTENCE_LENGTH, text.length());
        expandOffsets(false);
    }

    private boolean expandOffsets(boolean shrinkVisitedRegion) {
        int tempOffset;
        boolean expanded;
        switch (direction) {
        case UNDER_CURSOR:
            return false;
        case LEFT:
            tempOffset = Math.max(0, startOffset - MAX_SENTENCE_LENGTH
                    * SENTENCES_TO_CONSIDER);
            expanded = startOffset != tempOffset;
            if (shrinkVisitedRegion) {
                endOffset = startOffset;
            }
            startOffset = tempOffset;
            break;
        case RIGHT:
            tempOffset = Math.min(text.length(), endOffset
                    + MAX_SENTENCE_LENGTH * SENTENCES_TO_CONSIDER);
            expanded = endOffset != tempOffset;
            if (shrinkVisitedRegion) {
                startOffset = endOffset;
            }
            endOffset = tempOffset;
            break;
        default:
            throw new IllegalArgumentException("No implementation for: "
                    + direction);
        }

        if (expanded) {
            normaliseOffsets();
        }
        return expanded;
    }

    private void normaliseOffsets() {
        for (int i = startOffset; i >= 0; i--) {
            startOffset = i;
            if (Character.isWhitespace(text.charAt(i))) {
                break;
            }
        }

        for (int i = endOffset; i < text.length(); i++) {
            endOffset = i;
            if (Character.isWhitespace(text.charAt(i))) {
                break;
            }
        }
    }

    public static class Suggestion {
        public final List<String> results = new ArrayList<String>();
        public final int offset;
        private int current = 0;
        private int length = -1;

        public Suggestion(String word, int offset) {
            this.offset = offset;
            results.add(word);
        }

        public String next() {
            current = ++current >= results.size() ? 0 : current;
            return results.get(current);
        }

        public String prev() {
            current = --current < 0 ? results.size() - 1 : current;
            return results.get(current);
        }

        /** Goes back to the original, possibly misspelled, word. */
        public String reset() {
            current = 0;
            return results.get(current);
        }

        public String getCurrent() {
            return results.get(current);
        }

        public void setLength() {
            length = results.get(current).length();
        }

        public int getLength() {
            return length == -1 ? results.get(0).length() : length;
        }

        public boolean isMisspelledWord() {
            return current == 0;
        }
    }
}
