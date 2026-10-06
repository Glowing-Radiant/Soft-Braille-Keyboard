# Soft Braille Keyboard

This is an Android input method which displays a virtual on screen
Braille keyboard for use by a blind person. This facilitates much
faster and more comfortable input for the blind on Android with a
variety of powerful editing commands and support for a number of
different languages.

The user guide is bundled with the app (**Read the user guide** on the
app's main screen). Its source is
[`app/src/main/assets/manual/index.html`](app/src/main/assets/manual/index.html).

If you are interested in becoming involved with any development or have
questions please contact Daniel Dalton <daniel.dalton10@gmail.com>.

Please see the [TODO](TODO) file in this directory for a list of things
that need attention.

## License

This application is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Braille translation uses [liblouis](http://liblouis.io), which is
licensed under the GNU Lesser General Public License version 2.1 or
later. See
[`app/src/main/cpp/liblouis/COPYING.LESSER`](app/src/main/cpp/liblouis/COPYING.LESSER).

Spell checking uses [Hunspell](https://hunspell.github.io), which is
licensed under the Mozilla Public License 1.1, the GNU General Public
License version 2 or later, or the GNU Lesser General Public License version
2.1 or later. See [`app/src/main/cpp/hunspell`](app/src/main/cpp/hunspell).
The bundled English dictionaries come from
[LibreOffice](https://github.com/LibreOffice/dictionaries); their authors
and licenses are in
[`app/src/main/assets/dictionaries`](app/src/main/assets/dictionaries).

## Building the app

You need JDK 17 or later and the Android SDK with:

- Android SDK Platform 35
- NDK 27.1.12297006
- CMake 3.22.1

Android Studio installs these when you open the project. From the
command line, point `ANDROID_HOME` (or `sdk.dir` in `local.properties`)
at the SDK and run:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. It contains
liblouis built for arm64-v8a, armeabi-v7a, x86_64 and x86.

To run the tests on the computer:

```bash
./gradlew testDebugUnitTest
```

And on a connected device:

```bash
./gradlew connectedDebugAndroidTest
```

See [RELEASING.md](RELEASING.md) for building signed releases.

## Braille tables

liblouis is included as source in `app/src/main/cpp/liblouis` and is
built with CMake. The braille tables the keyboard offers are declared in
`app/src/main/res/xml/tablelist.xml`, and only the ones whose ids are
listed in the `braille_tables` array in
`app/src/main/res/values/arrays.xml` are shown to the user.

The table files are bundled in `app/src/main/assets/liblouis/tables` and
copied to the device's internal storage when the app is first used after
an install or update.

To add a table:

1. Add a `table` element to `tablelist.xml` with the liblouis file name.
2. Add its id to the `braille_tables` array in `arrays.xml`.
3. Copy the table files from a liblouis release. This copies every table
   in `tablelist.xml` plus the files they include:

   ```bash
   python scripts/update_liblouis_tables.py /path/to/liblouis-x.y.z/tables
   ```

4. Check that it types correctly on a device. `BrailleTablesTest` translates
   the emoji names of every table's language to braille and back, and fails
   if an offered table gets more than one in ten wrong:

   ```bash
   ./gradlew connectedDebugAndroidTest
   ```

   Some liblouis tables back translate badly, often because a letter and a
   punctuation mark share dots, so only offer tables that pass.

To update liblouis, replace the C sources in `app/src/main/cpp/liblouis`
with those of the new release, regenerating `liblouis.h` from
`liblouis.h.in` with `widechar` as `unsigned short int` and updating
`PACKAGE_VERSION` in `config.h`. Then refresh the tables as above. If a
table has been renamed upstream, update its `fileName` in
`tablelist.xml` but keep the id, so users' settings are preserved.
