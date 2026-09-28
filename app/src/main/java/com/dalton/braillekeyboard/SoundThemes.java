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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.dalton.braillekeyboard.Earcons.Sound;

/**
 * The keyboard's sound themes. There are two built in themes: minimal, the
 * single system click for every action, and abstract, the synthesised tones
 * of {@link Earcons}. Other themes are recordings, one folder per theme,
 * either in the assets under sounds/ or imported by the user, see
 * {@link SoundThemeImporter}.
 *
 * A theme has a file per sound named after it, such as type.ogg or
 * menu-open.ogg. Several files such as type-1.ogg and type-2.ogg are
 * variations played at random. A sound the theme doesn't have falls back to
 * a similar one, and in the end to the abstract theme. An optional
 * theme.properties gives the theme's name and a volume from 0 to 1.
 */
public class SoundThemes {
    public static final String MINIMAL = "minimal";
    public static final String ABSTRACT = "abstract";
    /** Starts the id of a theme the user imported. */
    public static final String IMPORTED_PREFIX = "imported:";
    public static final String PROPERTIES = "theme.properties";
    private static final String ASSETS = "sounds";
    private static final String IMPORTED = "sound_themes";
    private static final int MAX_STREAMS = 6;

    private final Context context;
    private final Earcons earcons = new Earcons();
    private final Map<Sound, int[]> samples = new EnumMap<Sound, int[]>(
            Sound.class);
    private final Map<Sound, Integer> lastVariation = new EnumMap<Sound, Integer>(
            Sound.class);
    private final Random random = new Random();
    private String loadedTheme;
    private SoundPool pool;
    private float volume = 1f;

    public SoundThemes(Context context) {
        this.context = context;
    }

    /** The theme the user chose in the settings. */
    public static String getTheme(Context context) {
        return Options.getStringPreference(context,
                R.string.pref_sound_theme_key,
                context.getString(R.string.pref_sound_theme_default));
    }

    /**
     * Plays the sound of an action in the chosen theme.
     *
     * @return false if the theme is minimal, and the caller should play the
     *         system click.
     */
    public boolean play(Sound sound) {
        String theme = getTheme(context);
        if (MINIMAL.equals(theme)) {
            return false;
        }
        if (ABSTRACT.equals(theme)) {
            earcons.play(sound);
            return true;
        }
        if (!theme.equals(loadedTheme)) {
            load(theme);
        }
        int[] ids = find(sound);
        if (ids == null || pool == null) {
            earcons.play(sound);
            return true;
        }
        pool.play(ids[pickVariation(sound, ids.length)], volume, volume, 1, 0,
                1f);
        return true;
    }

    /** Loads the chosen theme ahead of the first sound. */
    public void prepare() {
        String theme = getTheme(context);
        if (!MINIMAL.equals(theme) && !ABSTRACT.equals(theme)
                && !theme.equals(loadedTheme)) {
            load(theme);
        }
    }

    public void release() {
        earcons.release();
        if (pool != null) {
            pool.release();
            pool = null;
        }
        samples.clear();
        loadedTheme = null;
    }

    /**
     * The recorded themes, those in the assets then those the user
     * imported, as {id, name} pairs.
     */
    public static List<String[]> listThemes(Context context) {
        List<String[]> themes = new ArrayList<String[]>();
        AssetManager assets = context.getAssets();
        try {
            String[] folders = assets.list(ASSETS);
            if (folders != null) {
                for (String folder : folders) {
                    themes.add(new String[] { folder,
                            assetProperties(assets, folder).getProperty(
                                    "name", folder) });
                }
            }
        } catch (IOException e) {
            // No themes in the assets.
        }
        themes.addAll(listImported(context));
        return themes;
    }

    /** The themes the user imported, as {id, name} pairs. */
    public static List<String[]> listImported(Context context) {
        List<String[]> themes = new ArrayList<String[]>();
        File[] folders = importedRoot(context).listFiles();
        if (folders != null) {
            Arrays.sort(folders);
            for (File folder : folders) {
                if (folder.isDirectory() && !folder.getName().startsWith(".")) {
                    themes.add(new String[] {
                            IMPORTED_PREFIX + folder.getName(),
                            readProperties(new File(folder, PROPERTIES))
                                    .getProperty("name", folder.getName()) });
                }
            }
        }
        return themes;
    }

    /** Where imported themes are kept, one folder each. */
    static File importedRoot(Context context) {
        File root = new File(context.getFilesDir(), IMPORTED);
        root.mkdirs();
        return root;
    }

    /** The folder of an imported theme, or null if the id isn't one. */
    static File importedFolder(Context context, String id) {
        if (id == null || !id.startsWith(IMPORTED_PREFIX)) {
            return null;
        }
        String folder = id.substring(IMPORTED_PREFIX.length());
        if (folder.isEmpty() || folder.contains("/") || folder.contains("\\")
                || folder.startsWith(".")) {
            return null;
        }
        return new File(importedRoot(context), folder);
    }

    static Properties readProperties(File file) {
        Properties properties = new Properties();
        try {
            InputStream in = new FileInputStream(file);
            try {
                properties.load(new InputStreamReader(in, "UTF-8"));
            } finally {
                in.close();
            }
        } catch (IOException e) {
            // The defaults apply.
        }
        return properties;
    }

    // Where a theme lacks a sound, the one used instead. null means the
    // abstract theme's sound, used for errors so they stay distinct.
    private static Sound fallback(Sound sound) {
        switch (sound) {
        case SUBMIT:
            return Sound.NEWLINE;
        case MENU_CLOSE:
            return Sound.MENU_OPEN;
        case SELECT:
        case TOGGLE:
        case MENU_OPEN:
            return Sound.MOVE;
        case SPACE:
        case DELETE:
        case NEWLINE:
        case MOVE:
            return Sound.TYPE;
        default:
            return null;
        }
    }

    private int[] find(Sound sound) {
        for (Sound s = sound; s != null; s = fallback(s)) {
            int[] ids = samples.get(s);
            if (ids != null) {
                return ids;
            }
        }
        return null;
    }

    // A random variation, but not the same one twice in a row.
    private int pickVariation(Sound sound, int count) {
        if (count == 1) {
            return 0;
        }
        Integer last = lastVariation.get(sound);
        int pick = random.nextInt(count);
        if (last != null && pick == last) {
            pick = (pick + 1 + random.nextInt(count - 1)) % count;
        }
        lastVariation.put(sound, pick);
        return pick;
    }

    private void load(String theme) {
        release();
        loadedTheme = theme;
        File folder = importedFolder(context, theme);
        AssetManager assets = context.getAssets();
        String[] files;
        Properties properties;
        if (folder != null) {
            files = folder.list();
            properties = readProperties(new File(folder, PROPERTIES));
        } else {
            try {
                files = assets.list(ASSETS + "/" + theme);
            } catch (IOException e) {
                return;
            }
            properties = assetProperties(assets, theme);
        }
        if (files == null || files.length == 0) {
            return;
        }
        pool = new SoundPool.Builder().setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()).build();
        try {
            volume = Math.max(0f, Math.min(1f, Float.parseFloat(properties
                    .getProperty("volume", "1"))));
        } catch (NumberFormatException e) {
            volume = 1f;
        }
        Map<Sound, List<Integer>> found = new EnumMap<Sound, List<Integer>>(
                Sound.class);
        Arrays.sort(files);
        for (String file : files) {
            Sound sound = soundOf(file);
            if (sound == null) {
                continue;
            }
            int id;
            if (folder != null) {
                id = pool.load(new File(folder, file).getPath(), 1);
            } else {
                try {
                    AssetFileDescriptor fd = assets.openFd(ASSETS + "/"
                            + theme + "/" + file);
                    id = pool.load(fd, 1);
                    fd.close();
                } catch (IOException e) {
                    continue; // Such as a compressed asset.
                }
            }
            if (id == 0) {
                continue;
            }
            List<Integer> ids = found.get(sound);
            if (ids == null) {
                ids = new ArrayList<Integer>();
                found.put(sound, ids);
            }
            ids.add(id);
        }
        for (Map.Entry<Sound, List<Integer>> entry : found.entrySet()) {
            int[] ids = new int[entry.getValue().size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = entry.getValue().get(i);
            }
            samples.put(entry.getKey(), ids);
        }
    }

    // The sound a file is for: type.ogg and type-2.ogg are both TYPE.
    static Sound soundOf(String file) {
        int dot = file.lastIndexOf('.');
        if (dot <= 0) {
            return null;
        }
        String name = file.substring(0, dot).toLowerCase(Locale.ROOT);
        int dash = name.lastIndexOf('-');
        if (dash > 0 && isNumber(name.substring(dash + 1))) {
            name = name.substring(0, dash);
        }
        for (Sound sound : Sound.values()) {
            if (sound.name().toLowerCase(Locale.ROOT).replace('_', '-')
                    .equals(name)) {
                return sound;
            }
        }
        return null;
    }

    private static boolean isNumber(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static Properties assetProperties(AssetManager assets,
            String theme) {
        Properties properties = new Properties();
        try {
            InputStream in = assets.open(ASSETS + "/" + theme + "/"
                    + PROPERTIES);
            try {
                properties.load(new InputStreamReader(in, "UTF-8"));
            } finally {
                in.close();
            }
        } catch (IOException e) {
            // The defaults apply.
        }
        return properties;
    }
}
