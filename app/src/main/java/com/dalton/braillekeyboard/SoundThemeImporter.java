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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/**
 * Imports a sound theme the user picked: a zip of sound files, or several
 * sound files. Only files named after a sound, such as type.ogg or
 * menu-open.wav, and a theme.properties are kept, copied into the app's
 * storage. See {@link SoundThemes}.
 */
public final class SoundThemeImporter {
    private static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    private static final long MAX_THEME_BYTES = 24L * 1024 * 1024;
    private static final String[] AUDIO_EXTENSIONS = { "ogg", "oga", "wav",
            "mp3", "m4a", "aac", "flac", "opus" };

    private SoundThemeImporter() {
    }

    /** Why a theme couldn't be imported. */
    public static class ImportException extends IOException {
        public ImportException(String message) {
            super(message);
        }
    }

    /**
     * A name for the picked files: a zip's file name, or null.
     */
    public static String suggestName(Context context, List<Uri> uris) {
        if (uris.size() == 1) {
            String name = displayName(context.getContentResolver(),
                    uris.get(0));
            if (name != null && isZip(name)) {
                return name.substring(0, name.length() - 4);
            }
        }
        return null;
    }

    /**
     * Imports the picked files as a theme.
     *
     * @return The new theme's id, to store as the chosen theme.
     * @throws ImportException
     *             if no sounds were found or the files are too big.
     */
    public static String importTheme(Context context, List<Uri> uris,
            String name) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        File root = SoundThemes.importedRoot(context);
        File temp = new File(root, ".import-" + System.nanoTime());
        if (!temp.mkdirs()) {
            throw new IOException("Can't create " + temp);
        }
        try {
            long[] total = { 0 };
            int sounds = 0;
            for (Uri uri : uris) {
                String file = displayName(resolver, uri);
                if (file == null) {
                    file = uri.getLastPathSegment();
                }
                InputStream in = resolver.openInputStream(uri);
                if (in == null) {
                    continue;
                }
                try {
                    if (file != null && isZip(file)) {
                        sounds += copyZip(in, temp, total);
                    } else if (file != null && keep(file)) {
                        copy(in, new File(temp, baseName(file)), total);
                        sounds += isSound(file) ? 1 : 0;
                    }
                } finally {
                    in.close();
                }
            }
            if (sounds == 0) {
                throw new ImportException(context.getString(
                        R.string.sound_theme_import_no_sounds));
            }
            writeName(new File(temp, SoundThemes.PROPERTIES), name);
            File target = uniqueFolder(root, name);
            if (!temp.renameTo(target)) {
                throw new IOException("Can't create " + target);
            }
            temp = null;
            return SoundThemes.IMPORTED_PREFIX + target.getName();
        } finally {
            if (temp != null) {
                deleteRecursively(temp);
            }
        }
    }

    /** Deletes an imported theme. */
    public static boolean deleteTheme(Context context, String id) {
        File folder = SoundThemes.importedFolder(context, id);
        return folder != null && deleteRecursively(folder);
    }

    private static int copyZip(InputStream in, File folder, long[] total)
            throws IOException {
        int sounds = 0;
        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            // Only the file name counts, so folders in the zip don't matter
            // and a name can't point outside the theme.
            String file = baseName(entry.getName());
            if (entry.isDirectory() || file.isEmpty() || !keep(file)) {
                continue;
            }
            copy(zip, new File(folder, file), total);
            sounds += isSound(file) ? 1 : 0;
        }
        return sounds;
    }

    private static void copy(InputStream in, File file, long[] total)
            throws IOException {
        OutputStream out = new FileOutputStream(file);
        try {
            byte[] buffer = new byte[16384];
            long size = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                size += read;
                total[0] += read;
                if (size > MAX_FILE_BYTES || total[0] > MAX_THEME_BYTES) {
                    throw new ImportException("Too big: " + file.getName());
                }
                out.write(buffer, 0, read);
            }
        } finally {
            out.close();
        }
    }

    // Keeps the theme's name from theme.properties if the user left the
    // name empty, else writes the name the user gave.
    private static void writeName(File file, String name) throws IOException {
        Properties properties = SoundThemes.readProperties(file);
        if (name != null && !name.trim().isEmpty()) {
            properties.setProperty("name", name.trim());
        } else if (properties.getProperty("name") == null) {
            properties.setProperty("name", "Sound theme");
        }
        Writer out = new OutputStreamWriter(new FileOutputStream(file),
                "UTF-8");
        try {
            properties.store(out, null);
        } finally {
            out.close();
        }
    }

    private static File uniqueFolder(File root, String name) {
        String slug = name == null ? "" : name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.isEmpty()) {
            slug = "theme";
        }
        File folder = new File(root, slug);
        for (int i = 2; folder.exists(); i++) {
            folder = new File(root, slug + "-" + i);
        }
        return folder;
    }

    static boolean keep(String file) {
        return SoundThemes.PROPERTIES.equalsIgnoreCase(file) || isSound(file);
    }

    static boolean isSound(String file) {
        if (SoundThemes.soundOf(file) == null) {
            return false;
        }
        String extension = file.substring(file.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
        for (String audio : AUDIO_EXTENSIONS) {
            if (audio.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isZip(String file) {
        return file.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    static String baseName(String path) {
        String name = path.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return SoundThemes.PROPERTIES.equalsIgnoreCase(name) ? SoundThemes.PROPERTIES
                : name.toLowerCase(Locale.ROOT);
    }

    static String displayName(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri,
                    new String[] { OpenableColumns.DISPLAY_NAME }, null, null,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (RuntimeException e) {
            // Fall back to the path.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static boolean deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        return file.delete();
    }
}
