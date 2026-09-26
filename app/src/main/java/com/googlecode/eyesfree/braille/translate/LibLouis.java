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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Thin, thread safe wrapper around the native liblouis library.
 *
 * liblouis needs its tables on the file system, so the tables bundled in the
 * APK assets are copied to internal storage the first time they are needed
 * after each install or update of the app.
 */
public final class LibLouis {
    private static final String TAG = "LibLouis";
    // Asset directory holding the tables. liblouis looks for tables in
    // <data path>/liblouis/tables.
    private static final String ASSET_TABLES_DIR = "liblouis/tables";
    private static final String DATA_DIR = "louis";
    private static final String STAMP_FILE = "stamp";

    // liblouis keeps global state and is not thread safe.
    private static final Object LOCK = new Object();
    private static boolean initialized;

    static {
        System.loadLibrary("louiswrap");
    }

    private LibLouis() {
    }

    /**
     * Prepares liblouis for use, extracting the tables if necessary. Safe to
     * call repeatedly and from any thread; the work is only done once per
     * process. This does disk I/O so don't call it on the main thread.
     *
     * @return true if liblouis is ready to translate.
     */
    public static boolean init(Context context) {
        synchronized (LOCK) {
            if (initialized) {
                return true;
            }
            try {
                File dataDir = new File(context.getFilesDir(), DATA_DIR);
                extractTablesIfNeeded(context, dataDir);
                nativeInit(dataDir.getAbsolutePath());
                initialized = true;
            } catch (IOException e) {
                Log.e(TAG, "Failed to extract braille tables", e);
            }
            return initialized;
        }
    }

    /**
     * Checks whether a table compiles. The compiled table is cached by
     * liblouis so this also speeds up the first translation.
     */
    public static boolean checkTable(String tableList) {
        synchronized (LOCK) {
            return initialized && nativeCheckTable(tableList);
        }
    }

    /**
     * Back translates braille cells to text.
     *
     * @param cells
     *            One byte per cell, dot 1 in the least significant bit.
     * @return The text or null if translation failed.
     */
    public static String backTranslate(String tableList, byte[] cells) {
        synchronized (LOCK) {
            if (!initialized) {
                return null;
            }
            return nativeBackTranslate(tableList, cells);
        }
    }

    private static void extractTablesIfNeeded(Context context, File dataDir)
            throws IOException {
        String stamp = installStamp(context);
        File stampFile = new File(dataDir, STAMP_FILE);
        if (stamp.equals(readFile(stampFile))) {
            return;
        }

        File tablesDir = new File(dataDir, ASSET_TABLES_DIR);
        deleteRecursively(dataDir);
        if (!tablesDir.mkdirs()) {
            throw new IOException("Can't create " + tablesDir);
        }

        AssetManager assets = context.getAssets();
        String[] names = assets.list(ASSET_TABLES_DIR);
        if (names == null || names.length == 0) {
            throw new IOException("No braille tables in the APK");
        }
        byte[] buffer = new byte[16 * 1024];
        for (String name : names) {
            InputStream in = assets.open(ASSET_TABLES_DIR + "/" + name);
            OutputStream out = new FileOutputStream(new File(tablesDir, name));
            try {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } finally {
                in.close();
                out.close();
            }
        }

        // Written last so a partial extraction is redone next time.
        OutputStream out = new FileOutputStream(stampFile);
        try {
            out.write(stamp.getBytes("UTF-8"));
        } finally {
            out.close();
        }
        Log.i(TAG, "Extracted " + names.length + " braille tables");
    }

    // Changes whenever the app is installed or updated, so updated tables in a
    // new version of the app are always picked up.
    private static String installStamp(Context context) {
        try {
            return String.valueOf(context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).lastUpdateTime);
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    private static String readFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            InputStream in = new FileInputStream(file);
            try {
                byte[] data = new byte[(int) file.length()];
                int offset = 0;
                int read;
                while (offset < data.length
                        && (read = in.read(data, offset, data.length - offset)) != -1) {
                    offset += read;
                }
                return new String(data, 0, offset, "UTF-8");
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static native void nativeInit(String dataPath);

    private static native boolean nativeCheckTable(String tableList);

    private static native String nativeBackTranslate(String tableList,
            byte[] cells);
}
