/*
 * Copyright (C) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.braille.translate;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.dalton.braillekeyboard.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Gives access to the braille tables and translators backed by the bundled
 * liblouis library.
 *
 * Originally a client for the BrailleBack translation service, which ran
 * liblouis in a separate process. liblouis now runs in-process, but the
 * asynchronous initialisation contract is kept: the {@link OnInitListener} is
 * called on the main thread once the tables are ready.
 */
public class TranslatorClient {
    private static final String TAG = "TranslatorClient";

    public static final int SUCCESS = 0;
    public static final int ERROR = -1;

    public interface OnInitListener {
        void onInit(int status);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile List<TableInfo> tables = Collections.emptyList();
    private volatile boolean destroyed;

    public TranslatorClient(Context context, final OnInitListener listener) {
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int status = initialize(appContext);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!destroyed && listener != null) {
                            listener.onInit(status);
                        }
                    }
                });
            }
        }, TAG).start();
    }

    /**
     * Stops any pending initialisation callback. liblouis itself stays loaded
     * since other clients in the process may still be using it.
     */
    public void destroy() {
        destroyed = true;
    }

    /** Returns the tables declared in res/xml/tablelist.xml. */
    public List<TableInfo> getTables() {
        return new ArrayList<TableInfo>(tables);
    }

    /**
     * Returns a translator for the given table or null if the table doesn't
     * compile.
     */
    public BrailleTranslator getTranslator(TableInfo table) {
        final String fileName = table.getFileName();
        if (!LibLouis.checkTable(fileName)) {
            Log.e(TAG, "Braille table failed to compile: " + fileName);
            return null;
        }
        return new BrailleTranslator() {
            @Override
            public String backTranslate(byte[] cells) {
                return LibLouis.backTranslate(fileName, cells);
            }
        };
    }

    private int initialize(Context context) {
        try {
            tables = parseTableList(context);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Failed to read the braille table list", e);
            return ERROR;
        }
        return LibLouis.init(context) ? SUCCESS : ERROR;
    }

    private static List<TableInfo> parseTableList(Context context)
            throws XmlPullParserException, IOException {
        List<TableInfo> result = new ArrayList<TableInfo>();
        XmlResourceParser parser = context.getResources().getXml(
                R.xml.tablelist);
        try {
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG
                        || !"table".equals(parser.getName())) {
                    continue;
                }
                String id = parser.getAttributeValue(null, "id");
                String locale = parser.getAttributeValue(null, "locale");
                String fileName = parser.getAttributeValue(null, "fileName");
                boolean eightDot = "8".equals(
                        parser.getAttributeValue(null, "dots"));
                String grade = parser.getAttributeValue(null, "grade");
                if (id == null || locale == null || fileName == null) {
                    Log.w(TAG, "Skipping incomplete table entry " + id);
                    continue;
                }
                result.add(new TableInfo(id, parseLocale(locale), eightDot,
                        grade != null ? Integer.parseInt(grade) : 0, fileName));
            }
        } finally {
            parser.close();
        }
        return result;
    }

    // Parses locales of the form ll, ll_CC or ll_CC_variant.
    private static Locale parseLocale(String value) {
        String[] parts = value.split("_", 3);
        switch (parts.length) {
        case 1:
            return new Locale(parts[0]);
        case 2:
            return new Locale(parts[0], parts[1]);
        default:
            return new Locale(parts[0], parts[1], parts[2]);
        }
    }
}
