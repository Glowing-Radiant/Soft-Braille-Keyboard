# APK Build Information

## Build Date
February 8, 2026

## APK Details
- **File:** SoftBrailleKeyboard.apk
- **Location:** Root directory of repository
- **Size:** 1.1 MB
- **Build Method:** Manual build using Android SDK tools
- **Status:** Unsigned (for development/testing)

## Build Process

The APK was built using the `manual_build.sh` script, which:
1. Compiles resources using aapt2
2. Generates R.java files
3. Compiles 24 Java source files
4. Produces 82 .class files
5. Converts to DEX format (119 KB)
6. Packages into APK and aligns

## Included Features

This APK includes all the latest changes:

### 1. Multi-Touch Improvements
- Enhanced pointer tracking in BrailleView
- Better recognition of dots 1, 2, 3 combinations
- All active pointers tracked during ACTION_MOVE events

### 2. Accessibility Service
- **BrailleAccessibilityService** class included
- Registered in AndroidManifest.xml
- Configuration in accessibility_service_config.xml
- Enables better TalkBack integration

### 3. Contraction Braille Support
- en-UEB-g2 (Grade 2 UEB) table available
- en-US-g2 (Grade 2 US English) table available
- Full documentation included

## Installation

To install this APK on an Android device:

```bash
adb install -r SoftBrailleKeyboard.apk
```

Or transfer to device and install manually.

## Signing for Release

This APK is unsigned and suitable for testing. To sign for release:

```bash
$ANDROID_HOME/build-tools/34.0.0/apksigner sign \
  --ks your-keystore.jks \
  --out SoftBrailleKeyboard-signed.apk \
  SoftBrailleKeyboard.apk
```

## Verification

The APK has been verified to contain:
- ✅ BrailleAccessibilityService class in DEX
- ✅ Accessibility service declaration in manifest
- ✅ All resource files and XMLs
- ✅ Updated BrailleView with multi-touch improvements
- ✅ All required permissions

## Compatibility

- **Minimum SDK:** 21 (Android 5.0)
- **Target SDK:** 34 (Android 14)
- **Architecture:** All (arm64-v8a, x86_64, armeabi-v7a, x86)

## Notes

- This build was created without Gradle due to network restrictions
- Uses manual_build.sh script with Android SDK tools
- APK is aligned but unsigned
- Ready for testing on physical devices

## Testing Checklist

When testing this APK:
- [ ] Install successfully on device
- [ ] Multi-touch braille input works smoothly
- [ ] Dots 1, 2, 3 combinations register correctly
- [ ] Accessibility service appears in Android Settings
- [ ] Service can be enabled without errors
- [ ] TalkBack integration works (if service enabled)
- [ ] Contraction braille tables selectable
- [ ] No crashes or errors during normal use

---

For technical details about the changes, see:
- FEATURE_UPDATE.md
- IMPLEMENTATION_DETAILS.md
- USER_GUIDE.md
