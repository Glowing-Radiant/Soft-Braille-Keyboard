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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import com.dalton.braillekeyboard.BrailleParser.BrailleType;
import com.dalton.braillekeyboard.Options.KeyboardEcho;
import com.dalton.braillekeyboard.Options.KeyboardFeedback;
import com.dalton.braillekeyboard.Options.OptionList;
import com.googlecode.eyesfree.braille.translate.TableInfo;

// TODO fix the keyboard echo / feedback prefs
public class PreferenceIME extends PreferenceActivity {
    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }

    @Override
    public Intent getIntent() {
        final Intent modIntent = new Intent(super.getIntent());
        modIntent.putExtra(EXTRA_SHOW_FRAGMENT, Settings.class.getName());
        modIntent.putExtra(EXTRA_NO_HEADERS, true);
        return modIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.settings_name);
    }

    public static class Settings extends PreferenceFragment {
        private static final String TAG = "PreferenceIME";
        // Opens the system text-to-speech settings screen.
        private static final String ACTION_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";
        private static final int REQUEST_IMPORT_SOUND_THEME = 1;

        private BrailleParser brailleParser;
        private TextToSpeech tts;
        private Speech testSpeech;
        private String defaultEngine;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.ime_preferences);
            ListPreference keyboardEcho = (ListPreference) findPreference(getString(R.string.pref_echo_feedback_key));
            ListPreference keyboardFeedback = (ListPreference) findPreference(getString(R.string.pref_keyboard_feedback_key));
            ListPreference textToSpeechPreference = (ListPreference) findPreference(getString(R.string.pref_text_to_speech_engine_key));

            addOptions(keyboardFeedback, KeyboardFeedback.ALL);
            addOptions(keyboardEcho, KeyboardEcho.ALL);
            addSoundThemes();
            setUpSoundThemeImport();
            addTTSList(textToSpeechPreference);
            setUpSpeechPreferences();

            Preference preference = findPreference(getActivity().getString(
                    R.string.pref_app_version_key));
            try {
                String versionCode = getActivity().getPackageManager()
                        .getPackageInfo(getActivity().getPackageName(), 0).versionName;
                preference.setTitle(String.format(
                        getActivity()
                                .getString(R.string.pref_app_version_title),
                        versionCode));
            } catch (Exception e) {
                preference.setEnabled(false);
            }

            brailleParser = new BrailleParser(getActivity(),
                    new BrailleParser.BrailleParserListener() {

                        @Override
                        public void onTranslatorReady(int status) {
                            addTables(status);
                        }
                    });
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (brailleParser != null) {
                brailleParser.destroy();
            }
            if (testSpeech != null) {
                testSpeech.shutdown(null);
                testSpeech = null;
            }
            if (tts != null) {
                tts.shutdown();
                tts = null;
            }
        }

        private void addTables(int status) {
            List<String> entries = new ArrayList<String>();
            List<String> entryValues = new ArrayList<String>();

            ListPreference compBraille = (ListPreference) findPreference(getString(R.string.pref_braille_computer_table_key));
            ListPreference literaryBraille = (ListPreference) findPreference(getString(R.string.pref_braille_literary_table_key));
            MultiSelectListPreference switchPref = (MultiSelectListPreference) findPreference(getActivity()
                    .getString(R.string.pref_switch_tables_key));

            List<TableInfo> tables = new ArrayList<TableInfo>();
            if (status == BrailleParser.STATUS_OK) {
                tables = brailleParser.getTables(BrailleType.ALL);
            }
            populateWithTables(tables, entries, entryValues, true, null);
            switchPref.setEntries(entries.toArray(new String[entries.size()]));
            switchPref.setEntryValues(entryValues
                    .toArray(new String[entryValues.size()]));

            resetLists(entries, entryValues);
            if (status == BrailleParser.STATUS_OK) {
                tables = brailleParser.getTables(BrailleType.LITERARY);
            }
            populateWithTables(tables, entries, entryValues, true,
                    brailleParser.getDefaultId(getActivity(),
                            BrailleType.LITERARY));
            literaryBraille.setEntries(entries.toArray(new String[entries
                    .size()]));
            literaryBraille.setEntryValues(entryValues
                    .toArray(new String[entryValues.size()]));

            resetLists(entries, entryValues);
            if (status == BrailleParser.STATUS_OK) {
                tables = brailleParser.getTables(BrailleType.COMPUTER);
            }
            populateWithTables(tables, entries, entryValues, false,
                    brailleParser.getDefaultId(getActivity(),
                            BrailleType.COMPUTER));
            compBraille.setEntries(entries.toArray(new String[entries.size()]));
            compBraille.setEntryValues(entryValues
                    .toArray(new String[entryValues.size()]));
        }

        // Lists the built in sound themes, then the recorded ones.
        private void addSoundThemes() {
            ListPreference pref = (ListPreference) findPreference(getString(R.string.pref_sound_theme_key));
            List<CharSequence> entries = new ArrayList<CharSequence>(
                    Arrays.asList(getResources().getTextArray(
                            R.array.sound_theme_entries)));
            List<CharSequence> values = new ArrayList<CharSequence>(
                    Arrays.asList(getResources().getTextArray(
                            R.array.sound_theme_values)));
            for (String[] theme : SoundThemes.listThemes(getActivity())) {
                values.add(theme[0]);
                entries.add(theme[1]);
            }
            pref.setEntries(entries.toArray(new CharSequence[entries.size()]));
            pref.setEntryValues(values.toArray(new CharSequence[values.size()]));
            if (!values.contains(pref.getValue())) {
                pref.setValue(getString(R.string.pref_sound_theme_default));
            }
        }

        private void setUpSoundThemeImport() {
            findPreference(getString(R.string.pref_import_sound_theme_key))
                    .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("*/*");
                            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                                    "application/zip", "application/x-zip-compressed",
                                    "audio/*" });
                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                            try {
                                startActivityForResult(intent,
                                        REQUEST_IMPORT_SOUND_THEME);
                            } catch (ActivityNotFoundException e) {
                                toast(getString(R.string.sound_theme_import_failed,
                                        e.getMessage()));
                            }
                            return true;
                        }
                    });
            findPreference(getString(R.string.pref_remove_sound_theme_key))
                    .setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            chooseThemeToRemove();
                            return true;
                        }
                    });
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode,
                Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode != REQUEST_IMPORT_SOUND_THEME
                    || resultCode != Activity.RESULT_OK || data == null) {
                return;
            }
            List<Uri> uris = new ArrayList<Uri>();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    uris.add(clip.getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) {
                askThemeName(uris);
            }
        }

        private void askThemeName(final List<Uri> uris) {
            final EditText name = new EditText(getActivity());
            String suggestion = SoundThemeImporter.suggestName(getActivity(),
                    uris);
            name.setText(suggestion == null ? getString(R.string.sound_theme_default_name)
                    : suggestion);
            name.setSelectAllOnFocus(true);
            name.setContentDescription(getString(R.string.sound_theme_name_title));
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.sound_theme_name_title)
                    .setView(name)
                    .setPositiveButton(R.string.sound_theme_import,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    importTheme(uris, name.getText().toString());
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null).show();
        }

        private void importTheme(final List<Uri> uris, final String name) {
            final Activity activity = getActivity();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String id = null;
                    String error = null;
                    try {
                        id = SoundThemeImporter.importTheme(activity, uris,
                                name);
                    } catch (SoundThemeImporter.ImportException e) {
                        error = e.getMessage();
                    } catch (Exception e) {
                        error = activity.getString(
                                R.string.sound_theme_import_failed,
                                e.getMessage());
                    }
                    final String theme = id;
                    final String message = error;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAdded()) {
                                return;
                            }
                            if (theme == null) {
                                toast(message);
                                return;
                            }
                            addSoundThemes();
                            ((ListPreference) findPreference(getString(R.string.pref_sound_theme_key)))
                                    .setValue(theme);
                            toast(getString(R.string.sound_theme_imported, name));
                        }
                    });
                }
            }).start();
        }

        private void chooseThemeToRemove() {
            final List<String[]> themes = SoundThemes.listImported(getActivity());
            if (themes.isEmpty()) {
                toast(getString(R.string.sound_theme_none_imported));
                return;
            }
            String[] names = new String[themes.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = themes.get(i)[1];
            }
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.pref_remove_sound_theme_title)
                    .setItems(names, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            confirmRemoveTheme(themes.get(which));
                        }
                    }).setNegativeButton(android.R.string.cancel, null).show();
        }

        private void confirmRemoveTheme(final String[] theme) {
            new AlertDialog.Builder(getActivity())
                    .setMessage(getString(R.string.sound_theme_remove_confirm,
                            theme[1]))
                    .setPositiveButton(R.string.sound_theme_remove,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    SoundThemeImporter.deleteTheme(getActivity(),
                                            theme[0]);
                                    addSoundThemes();
                                    toast(getString(R.string.sound_theme_removed,
                                            theme[1]));
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null).show();
        }

        private void toast(String message) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
        }

        private void addOptions(ListPreference pref, OptionList option) {
            OptionList[] types = option.getValues();
            CharSequence[] entries = new CharSequence[types.length];
            CharSequence[] entryValues = new CharSequence[entries.length];
            for (int i = 0; i < entries.length; i++) {
                entries[i] = getString(types[i].getResource());
                entryValues[i] = types[i].getValue();
            }
            pref.setEntries(entries);
            pref.setEntryValues(entryValues);
        }

        private void populateWithTables(List<TableInfo> tables,
                List<String> entries, List<String> entryValues,
                boolean verbose, String defaultId) {
            for (TableInfo table : tables) {
                String text = table.getLocale().getDisplayLanguage();
                String country = table.getLocale().getDisplayCountry();
                text += (country.equals("") ? "" : " (" + country + ")");
                if (verbose) {
                    String grade = getActivity().getString(
                            R.string.grade_computer);
                    if (table.getGrade() > 0) {
                        grade = String.format(
                                getActivity().getString(R.string.grade_table),
                                table.getGrade());
                    }
                    text += ": " + grade;
                }
                entries.add(text);
                if (table.getId().equals(defaultId)) {
                    entryValues.add(getActivity().getString(
                            R.string.pref_braille_table_auto));
                } else {
                    entryValues.add(table.getId());
                }
            }
        }

        private static void resetLists(List<String> list1, List<String> list2) {
            list1.clear();
            list2.clear();
        }

        private void addTTSList(final ListPreference preference) {
            tts = new TextToSpeech(getActivity(),
                    new TextToSpeech.OnInitListener() {

                        @Override
                        public void onInit(int status) {
                            doEnginesList(preference);
                        }
                    });
        }

        private void doEnginesList(ListPreference preference) {
            if (tts == null) {
                return;
            }
            List<EngineInfo> engines = tts.getEngines();
            defaultEngine = tts.getDefaultEngine();
            tts.shutdown();
            tts = null;
            Collections.sort(engines, new Comparator<EngineInfo>() {
                @Override
                public int compare(EngineInfo o1, EngineInfo o2) {
                    return o1.label.toLowerCase(Locale.getDefault()).compareTo(
                            o2.label.toLowerCase(Locale.getDefault()));
                }
            });

            // The first entry follows whatever engine is the system default.
            CharSequence[] entries = new CharSequence[engines.size() + 1];
            CharSequence[] entryValues = new CharSequence[engines.size() + 1];
            entries[0] = getString(R.string.tts_engine_system_default);
            entryValues[0] = "";

            for (int i = 0; i < engines.size(); i++) {
                entries[i + 1] = engines.get(i).label;
                entryValues[i + 1] = engines.get(i).name;
            }

            preference.setEntries(entries);
            preference.setEntryValues(entryValues);
            // Refresh the "%s" summary now the entries are known.
            preference.setValue(preference.getValue());
        }

        private void setUpSpeechPreferences() {
            findPreference(getString(R.string.pref_speech_test_key))
                    .setOnPreferenceClickListener(
                            new Preference.OnPreferenceClickListener() {
                                @Override
                                public boolean onPreferenceClick(Preference p) {
                                    testSpeech();
                                    return true;
                                }
                            });
            findPreference(getString(R.string.pref_tts_engine_settings_key))
                    .setOnPreferenceClickListener(
                            new Preference.OnPreferenceClickListener() {
                                @Override
                                public boolean onPreferenceClick(Preference p) {
                                    openEngineSettings();
                                    return true;
                                }
                            });
            findPreference(getString(R.string.pref_tts_system_settings_key))
                    .setOnPreferenceClickListener(
                            new Preference.OnPreferenceClickListener() {
                                @Override
                                public boolean onPreferenceClick(Preference p) {
                                    startSettingsActivity(new Intent(
                                            ACTION_TTS_SETTINGS));
                                    return true;
                                }
                            });
        }

        // Speaks a sample with the chosen engine, rate, pitch and volume.
        private void testSpeech() {
            if (testSpeech != null) {
                testSpeech.shutdown(null);
            }
            testSpeech = new Speech(getActivity(), new Speech.OnReadyListener() {
                @Override
                public void ttsReady() {
                    if (testSpeech != null && getActivity() != null) {
                        testSpeech.speak(getActivity(),
                                getString(R.string.speech_test_sample),
                                Speech.QUEUE_FLUSH);
                    }
                }
            });
        }

        // Opens the settings screen of the selected engine, where its voices,
        // languages and other options can be configured.
        private void openEngineSettings() {
            String engine = Options.getStringPreference(getActivity(),
                    R.string.pref_text_to_speech_engine_key, "");
            if (engine.length() == 0) {
                engine = defaultEngine;
            }
            Intent intent = engine != null ? getEngineSettingsIntent(engine)
                    : null;
            if (intent == null) {
                Toast.makeText(getActivity(),
                        R.string.tts_engine_no_settings, Toast.LENGTH_LONG)
                        .show();
                intent = new Intent(ACTION_TTS_SETTINGS);
            }
            startSettingsActivity(intent);
        }

        private void startSettingsActivity(Intent intent) {
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getActivity(),
                        R.string.tts_engine_no_settings, Toast.LENGTH_LONG)
                        .show();
            }
        }

        // TTS engines declare their settings activity in the
        // android.speech.tts meta-data of their service.
        private Intent getEngineSettingsIntent(String engine) {
            PackageManager pm = getActivity().getPackageManager();
            Intent query = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
            query.setPackage(engine);
            List<ResolveInfo> services = pm.queryIntentServices(query,
                    PackageManager.GET_META_DATA);
            for (ResolveInfo info : services) {
                XmlResourceParser parser = info.serviceInfo.loadXmlMetaData(pm,
                        TextToSpeech.Engine.SERVICE_META_DATA);
                if (parser == null) {
                    continue;
                }
                try {
                    int event;
                    while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG
                                && "tts-engine".equals(parser.getName())) {
                            String activity = parser.getAttributeValue(
                                    "http://schemas.android.com/apk/res/android",
                                    "settingsActivity");
                            if (activity == null) {
                                return null;
                            }
                            if (activity.startsWith(".")) {
                                activity = engine + activity;
                            }
                            return new Intent(Intent.ACTION_MAIN)
                                    .setClassName(engine, activity);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Can't read settings of TTS engine " + engine, e);
                } finally {
                    parser.close();
                }
            }
            return null;
        }
    }
}
