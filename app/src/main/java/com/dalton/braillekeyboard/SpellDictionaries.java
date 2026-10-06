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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * The Hunspell dictionaries for the built-in spell checker: English ones
 * bundled with the app and any the user imports, named after their language
 * such as en_GB.dic and en_GB.aff.
 *
 * Dictionaries are loaded and used on one background thread, see run(), and
 * only one is kept loaded at a time, as each takes megabytes of memory.
 */
public final class SpellDictionaries {
    private static final String TAG = "SpellDictionaries";
    private static final String ASSET_DIR = "dictionaries";
    private static final String BUNDLED_DIR = "dictionaries/bundled";
    private static final String IMPORTED_DIR = "dictionaries/imported";
    private static final String STAMP_FILE = "stamp";
    private static final String DIC = ".dic";
    private static final String AFF = ".aff";
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

    /** A dictionary that couldn't be imported, with a message for the user. */
    public static class ImportException extends IOException {
        public ImportException(String message) {
            super(message);
        }
    }

    private static HandlerThread thread;
    private static Handler handler;
    // The loaded dictionary and its name. Only used on the background thread.
    private static Hunspell loaded;
    private static String loadedName;

    private SpellDictionaries() {
    }

    /**
     * Whether the user chose the built-in dictionaries over the phone's
     * spell checker.
     */
    public static boolean isBuiltInOn(Context context) {
        String choice = Options.getStringPreference(context,
                R.string.pref_spell_checker_key,
                context.getString(R.string.pref_spell_checker_default));
        return context.getString(R.string.pref_spell_checker_builtin_value)
                .equals(choice);
    }

    /**
     * Finds the dictionary for a language, preferring an imported one. Fast
     * enough for the main thread: no dictionary is read.
     *
     * @return The dictionary's name, such as en_GB, or null if there is none
     *         or the user chose the phone's spell checker.
     */
    public static String find(Context context, Locale locale) {
        if (locale == null || !isBuiltInOn(context)) {
            return null;
        }
        List<String> names = listImported(context);
        names.addAll(listBundled(context));
        return choose(names, locale, Locale.getDefault());
    }

    /**
     * Chooses the dictionary for a language: the one for its country, else
     * the one for the phone's country, else the first for the language.
     *
     * @param names
     *            Dictionary names such as en_GB, in order of preference.
     */
    static String choose(List<String> names, Locale locale, Locale phone) {
        String language = new Locale(locale.getLanguage()).getLanguage();
        String bestForPhone = null;
        String first = null;
        for (String name : names) {
            Locale dictionary = toLocale(name);
            if (dictionary == null
                    || !dictionary.getLanguage().equals(language)) {
                continue;
            }
            String country = dictionary.getCountry();
            if (!locale.getCountry().isEmpty()
                    && locale.getCountry().equals(country)) {
                return name;
            }
            if (bestForPhone == null && !country.isEmpty()
                    && country.equals(phone.getCountry())
                    && language.equals(phone.getLanguage())) {
                bestForPhone = name;
            }
            if (first == null) {
                first = name;
            }
        }
        return bestForPhone != null ? bestForPhone : first;
    }

    /**
     * The language of a dictionary named like en_GB, en-GB or de, or null
     * if the name isn't a language.
     */
    static Locale toLocale(String name) {
        String[] parts = name.split("[_-]");
        if (parts.length > 2 || !parts[0].matches("[A-Za-z]{2,3}")
                || (parts.length == 2 && !parts[1].matches("[A-Za-z]{2}"))) {
            return null;
        }
        return new Locale(parts[0].toLowerCase(Locale.ROOT),
                parts.length == 2 ? parts[1].toUpperCase(Locale.ROOT) : "");
    }

    /** Names of the imported dictionaries, sorted. */
    public static List<String> listImported(Context context) {
        return listDictionaries(new File(context.getFilesDir(), IMPORTED_DIR)
                .list());
    }

    private static List<String> listBundled(Context context) {
        try {
            return listDictionaries(context.getAssets().list(ASSET_DIR));
        } catch (IOException e) {
            return new ArrayList<String>();
        }
    }

    // The names of the dictionaries among some files, sorted.
    private static List<String> listDictionaries(String[] files) {
        List<String> names = new ArrayList<String>();
        for (int i = 0; files != null && i < files.length; i++) {
            if (files[i].endsWith(DIC)) {
                String name = files[i].substring(0, files[i].length()
                        - DIC.length());
                if (toLocale(name) != null) {
                    names.add(name);
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    /** Runs a task on the thread that dictionaries are used on. */
    public static synchronized void run(Runnable task) {
        if (handler == null) {
            thread = new HandlerThread(TAG);
            thread.start();
            handler = new Handler(thread.getLooper());
        }
        handler.post(task);
    }

    /**
     * Loads a dictionary, unloading the one loaded before. Only call it from
     * a task given to run().
     *
     * @return The dictionary or null if it can't be read.
     */
    static Hunspell load(Context context, String name) {
        if (name.equals(loadedName)) {
            return loaded;
        }
        unload();
        File folder = new File(context.getFilesDir(), IMPORTED_DIR);
        if (!new File(folder, name + DIC).isFile()) {
            folder = new File(context.getFilesDir(), BUNDLED_DIR);
            try {
                extractBundled(context, folder);
            } catch (IOException e) {
                Log.e(TAG, "Failed to extract the dictionaries", e);
                return null;
            }
        }
        File aff = new File(folder, name + AFF);
        File dic = new File(folder, name + DIC);
        if (!aff.isFile() || !dic.isFile()) {
            return null;
        }
        loaded = new Hunspell(aff, dic);
        loadedName = name;
        return loaded;
    }

    private static void unload() {
        if (loaded != null) {
            loaded.close();
        }
        loaded = null;
        loadedName = null;
    }

    // Hunspell reads files, so the bundled dictionaries are copied out of the
    // APK, again after each install or update of the app.
    private static void extractBundled(Context context, File folder)
            throws IOException {
        String stamp = installStamp(context);
        File stampFile = new File(folder, STAMP_FILE);
        if (stamp.equals(readStamp(stampFile))) {
            return;
        }
        File[] old = folder.listFiles();
        for (int i = 0; old != null && i < old.length; i++) {
            old[i].delete();
        }
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Can't create " + folder);
        }
        for (String name : listBundled(context)) {
            for (String extension : new String[] { AFF, DIC }) {
                copy(context.getAssets().open(ASSET_DIR + "/" + name
                        + extension), new File(folder, name + extension));
            }
        }
        // Written last so a partial extraction is redone next time.
        OutputStream out = new FileOutputStream(stampFile);
        try {
            out.write(stamp.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static String installStamp(Context context) {
        try {
            return String.valueOf(context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).lastUpdateTime);
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    private static String readStamp(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            InputStream in = new FileInputStream(file);
            try {
                byte[] data = new byte[(int) Math.min(file.length(), 64)];
                int length = in.read(data);
                return length < 0 ? "" : new String(data, 0, length, "UTF-8");
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Imports a dictionary from its .dic and .aff files, replacing an
     * imported one of the same name.
     *
     * @return The dictionary's name, such as de_DE.
     */
    public static String importDictionary(Context context, List<Uri> uris)
            throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri dic = null;
        Uri aff = null;
        String name = null;
        for (Uri uri : uris) {
            String file = SoundThemeImporter.displayName(resolver, uri);
            if (file == null) {
                file = uri.getLastPathSegment();
            }
            file = file == null ? "" : SoundThemeImporter.baseName(file);
            String lower = file.toLowerCase(Locale.ROOT);
            if (lower.endsWith(DIC)) {
                dic = uri;
                name = file.substring(0, file.length() - DIC.length());
            } else if (lower.endsWith(AFF)) {
                aff = uri;
                if (name == null) {
                    name = file.substring(0, file.length() - AFF.length());
                }
            }
        }
        if (dic == null || aff == null) {
            throw new ImportException(
                    context.getString(R.string.dictionary_need_both));
        }
        Locale locale = name == null ? null : toLocale(name);
        if (locale == null) {
            throw new ImportException(
                    context.getString(R.string.dictionary_bad_name));
        }
        name = locale.getCountry().isEmpty() ? locale.getLanguage()
                : locale.getLanguage() + "_" + locale.getCountry();
        File folder = new File(context.getFilesDir(), IMPORTED_DIR);
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Can't create " + folder);
        }
        final String imported = name;
        // The old one may be loaded; it is reloaded from the new files.
        run(new Runnable() {
            @Override
            public void run() {
                if (imported.equals(loadedName)) {
                    unload();
                }
            }
        });
        try {
            copy(resolver.openInputStream(aff), new File(folder, name + AFF));
            copy(resolver.openInputStream(dic), new File(folder, name + DIC));
        } catch (IOException e) {
            new File(folder, name + AFF).delete();
            new File(folder, name + DIC).delete();
            throw e;
        }
        return name;
    }

    /** Removes an imported dictionary. */
    public static void delete(final Context context, final String name) {
        run(new Runnable() {
            @Override
            public void run() {
                if (name.equals(loadedName)) {
                    unload();
                }
                File folder = new File(context.getFilesDir(), IMPORTED_DIR);
                new File(folder, name + DIC).delete();
                new File(folder, name + AFF).delete();
            }
        });
    }

    /** Names a dictionary for the user, such as "English (United Kingdom)". */
    public static String displayName(String name) {
        Locale locale = toLocale(name);
        return locale == null ? name : locale.getDisplayName();
    }

    private static void copy(InputStream in, File file) throws IOException {
        if (in == null) {
            throw new IOException("Can't read " + file.getName());
        }
        OutputStream out = new FileOutputStream(file);
        try {
            byte[] buffer = new byte[16 * 1024];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FILE_BYTES) {
                    throw new IOException(file.getName() + " is too big");
                }
                out.write(buffer, 0, read);
            }
        } finally {
            in.close();
            out.close();
        }
    }
}
