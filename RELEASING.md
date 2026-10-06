# Releasing Soft Braille Keyboard

Releases are built and published by the **Release** GitHub Actions workflow
(`.github/workflows/release.yml`). It runs the unit tests, builds a signed
release APK for all CPU types (arm64-v8a, armeabi-v7a, x86_64, x86) and
publishes a GitHub release with the APK and its SHA-256 checksum attached.

## One-time setup: the signing key

Android only installs an update if it is signed with the same key as the
installed app, so create one key and keep it forever. **If the key is lost,
users must uninstall the app to install future versions.** Keep a backup of
the keystore and its passwords somewhere safe outside the repository.

1. Create a keystore (any machine with a JDK):

   ```bash
   keytool -genkeypair -keystore sbk-release.jks -alias sbk -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Encode it for GitHub:

   ```bash
   base64 -w0 sbk-release.jks > sbk-release.jks.base64
   ```

   On Windows PowerShell:
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("sbk-release.jks")) > sbk-release.jks.base64`

3. In the GitHub repository open **Settings → Secrets and variables →
   Actions** and add these repository secrets:

   | Secret | Value |
   | --- | --- |
   | `SIGNING_KEYSTORE_BASE64` | Contents of `sbk-release.jks.base64` |
   | `SIGNING_KEYSTORE_PASSWORD` | The keystore password |
   | `SIGNING_KEY_ALIAS` | The key alias (`sbk` above) |
   | `SIGNING_KEY_PASSWORD` | The key password |

Never commit the keystore; `*.jks` is ignored by git.

## Publishing a release

First write the release notes in `release-notes/VERSION.md`, for example
`release-notes/3.2.0.md`, and commit them. The workflow refuses to release a
version without notes. Write them for the people using the keyboard: what
they can now do, what works differently and what was fixed, under the
headings New, Changed, Fixed and Removed as needed. The release shows them
followed by GitHub's link to the full list of changes.

Then push a tag named after the version:

```bash
git tag v3.2.0
git push origin v3.2.0
```

Or open **Actions → Release → Run workflow** and enter the version.

The version name comes from the tag (`v3.2.0` becomes `3.2.0`). Versions with
a suffix such as `3.2.0-beta1` are published as pre-releases. The version code
is `1000 + the workflow run number`, so it always increases and phones accept
the update.

## Notes

- APKs from the regular **Build Android APK** workflow and local builds are
  debug-signed with a different key. To switch between a debug build and a
  release build, uninstall the app first.
- Local release builds are unsigned unless the `SIGNING_KEYSTORE_PATH`,
  `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`
  environment variables are set.
