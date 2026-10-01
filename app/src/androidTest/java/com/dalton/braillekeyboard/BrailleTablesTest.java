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

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.googlecode.eyesfree.braille.translate.LibLouis;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Types real words with every braille table: the emoji names in the table's
 * language are translated to braille and the braille back to text, as the
 * keyboard does when it is typed. Logs how many come back unchanged, and
 * fails if an offered table gets too many wrong.
 */
@RunWith(AndroidJUnit4.class)
public class BrailleTablesTest {
    private static final String TAG = "BrailleTablesTest";
    private static final int SAMPLES = 100;
    private static final int MIN_PERCENT_TYPED = 90;
    // Offered tables liblouis back translates wrongly, to be fixed. Where a
    // letter and a punctuation mark share dots, back translation gives the
    // one the table defines first, eg. Slovak š comes back as ':'. Chinese
    // braille spells sounds, so many characters share the same braille.
    private static final List<String> KNOWN_WRONG = Arrays.asList(
            "hr-comp8", "ro-comp8", "sk-g1", "vi-g1", "zh-comp8",
            "zh-TW-comp8");

    @Test
    public void everyTableTypesItsLanguage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        assertTrue(LibLouis.init(context));

        List<String> offered = Arrays.asList(context.getResources()
                .getStringArray(R.array.braille_tables));
        List<String> broken = new ArrayList<String>();
        List<String> wrongTables = new ArrayList<String>();
        XmlResourceParser parser = context.getResources().getXml(
                R.xml.tablelist);
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event != XmlPullParser.START_TAG
                    || !"table".equals(parser.getName())) {
                continue;
            }
            String id = parser.getAttributeValue(null, "id");
            String locale = parser.getAttributeValue(null, "locale");
            String fileName = parser.getAttributeValue(null, "fileName");
            if (!LibLouis.checkTable(fileName)) {
                Log.i(TAG, "RESULT " + id + " does not compile");
                broken.add(id);
                continue;
            }

            List<String> words = samples(context, locale);
            int same = 0;
            List<String> wrong = new ArrayList<String>();
            for (String word : words) {
                byte[] cells = LibLouis.translate(fileName, word);
                String back = cells == null ? null : LibLouis.backTranslate(
                        fileName, cells);
                if (back != null && comparable(back).equals(comparable(word))) {
                    ++same;
                } else if (wrong.size() < 3) {
                    wrong.add(word + " -> " + back);
                }
            }
            Log.i(TAG, String.format(Locale.ROOT, "RESULT %s%s %d/%d %s", id,
                    offered.contains(id) ? "" : " (not offered)", same,
                    words.size(), wrong));
            if (offered.contains(id) && !KNOWN_WRONG.contains(id)
                    && same * 100 < MIN_PERCENT_TYPED * words.size()) {
                wrongTables.add(id);
            }
        }
        assertTrue("Tables that don't compile: " + broken, broken.isEmpty());
        assertTrue("Offered tables that type wrongly: " + wrongTables,
                wrongTables.isEmpty());
    }

    // Serbian braille is the same for both alphabets and back translates
    // to the Latin one.
    private static final String SERBIAN_CYRILLIC = "абвгдђежзијклљмнњопрстћуфхцчџш";
    private static final String[] SERBIAN_LATIN = { "a", "b", "v", "g", "d",
            "\u0111", "e", "\u017e", "z", "i", "j", "k", "l", "lj", "m", "n",
            "nj", "o", "p", "r", "s", "t", "\u0107", "u", "f", "h", "c",
            "\u010d", "d\u017e", "\u0161" };

    // Ignores differences that can't be typed apart, such as curly quotes.
    private static String comparable(String text) {
        String lower = Normalizer.normalize(text, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).replace('\u2019', '\'')
                .replace('\u2010', '-').replace('\u2013', '-')
                .replace(" -- ", "-");
        StringBuilder latin = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            int letter = SERBIAN_CYRILLIC.indexOf(lower.charAt(i));
            if (letter >= 0) {
                latin.append(SERBIAN_LATIN[letter]);
            } else {
                latin.append(lower.charAt(i));
            }
        }
        return latin.toString();
    }

    private static List<String> samples(Context context, String locale)
            throws Exception {
        String language = locale.equals("zh_TW") ? "zh-Hant" : locale
                .split("_")[0];
        List<String> words = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("emoji/" + language + ".txt"),
                "UTF-8"));
        try {
            String line;
            while (words.size() < SAMPLES
                    && (line = reader.readLine()) != null) {
                // Typed with the characters a braille keyboard has.
                words.add(line.split("\t")[1].replace('\u2019', '\'')
                        .replace('\u2010', '-').replace('\u2013', '-'));
            }
        } finally {
            reader.close();
        }
        return words;
    }
}
