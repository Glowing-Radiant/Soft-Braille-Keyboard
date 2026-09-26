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

package com.googlecode.eyesfree.braille.translate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Checks the bundled liblouis works on the device: every declared table
 * compiles and back translation gives the expected text.
 */
@RunWith(AndroidJUnit4.class)
public class TranslatorClientTest {
    private static TranslatorClient client;
    private static int initStatus;

    @BeforeClass
    public static void setUp() throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        final CountDownLatch latch = new CountDownLatch(1);
        client = new TranslatorClient(context,
                new TranslatorClient.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        initStatus = status;
                        latch.countDown();
                    }
                });
        assertTrue("Translator init timed out",
                latch.await(60, TimeUnit.SECONDS));
    }

    @Test
    public void initSucceeds() {
        assertEquals(TranslatorClient.SUCCESS, initStatus);
        assertFalse(client.getTables().isEmpty());
    }

    @Test
    public void allTablesCompile() {
        List<String> failed = new ArrayList<String>();
        for (TableInfo table : client.getTables()) {
            if (client.getTranslator(table) == null) {
                failed.add(table.getId() + " (" + table.getFileName() + ")");
            }
        }
        assertTrue("Tables failed to compile: " + failed, failed.isEmpty());
    }

    @Test
    public void englishGrade1() {
        // h e l l o
        assertEquals("hello", backTranslate("en-US-g1", 125, 15, 123, 123, 135));
    }

    @Test
    public void englishGrade2Contractions() {
        // "the" and "and" are single cell contractions.
        assertEquals("the", backTranslate("en-US-g2", 2346));
        assertEquals("and", backTranslate("en-UEB-g2", 12346));
        // k on its own is "knowledge".
        assertEquals("knowledge", backTranslate("en-UEB-g2", 13));
    }

    @Test
    public void englishComputerBraille() {
        // Dot 7 marks a capital letter in 8 dot computer braille.
        assertEquals("aA", backTranslate("en-US-comp8", 1, 17));
    }

    @Test
    public void germanGrade1() {
        // The German table was renamed in liblouis 3.x (de-g1.ctb).
        assertEquals("hallo", backTranslate("de-DE-g1", 125, 1, 123, 123, 135));
    }

    // Translates like BrailleParser does: padded with blank cells, trimmed.
    private static String backTranslate(String tableId, int... dotPatterns) {
        TableInfo table = null;
        for (TableInfo t : client.getTables()) {
            if (t.getId().equals(tableId)) {
                table = t;
            }
        }
        assertNotNull("No table " + tableId, table);
        BrailleTranslator translator = client.getTranslator(table);
        assertNotNull(translator);

        byte[] cells = new byte[dotPatterns.length + 2];
        for (int i = 0; i < dotPatterns.length; i++) {
            cells[i + 1] = toCell(dotPatterns[i]);
        }
        String text = translator.backTranslate(cells);
        assertNotNull(text);
        return text.trim();
    }

    // Converts a dot number pattern such as 125 into a cell bit field.
    private static byte toCell(int dots) {
        int cell = 0;
        for (char c : String.valueOf(dots).toCharArray()) {
            cell |= 1 << (c - '1');
        }
        return (byte) cell;
    }
}
