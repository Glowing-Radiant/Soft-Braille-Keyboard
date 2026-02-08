# Android 14 and 64-bit Migration Guide for Soft Braille Keyboard

## Summary of Changes

This document outlines the changes made to migrate the Soft Braille Keyboard project to support Android 14 (API 34) and 64-bit architectures.

### Key Updates

1. **Build System Migration**: Converted from Ant to Gradle build system
2. **API Level Updates**:
   - minSdkVersion: 14 → 21 (required for 64-bit support)
   - targetSdkVersion: 23 → 34 (Android 14)
   - compileSdkVersion: → 34

3. **AndroidX Migration**: Replaced deprecated Android Support Library with AndroidX
4. **64-bit Architecture Support**: Configured NDK for arm64-v8a, x86_64, armeabi-v7a, x86
5. **Android 14 Compliance**: Updated manifest with required declarations

## Project Structure Changes

```
Soft-Braille-Keyboard/
├── app/
│   ├── build.gradle (NEW)
│   ├── proguard-rules.pro (NEW)
│   └── src/main/
│       ├── AndroidManifest.xml (UPDATED)
│       ├── java/
│       └── res/
├── build.gradle (NEW)
├── settings.gradle (NEW)
├── gradle.properties (NEW)
└── local.properties (NEW)
```

## Building the Project

### Prerequisites

- Android SDK (API 34)
- NDK r27 or later
- Gradle 8.x or later
- Java 17 or later

### Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Build all variants
./gradlew build
```

### Manual Build (When Gradle is not available)

See `manual_build.sh` for building with Android SDK tools directly.

## Android 14 Specific Changes

### Manifest Updates

1. Added `android:exported` attribute to all services and activities
2. Added backup rules:
   - `res/xml/data_extraction_rules.xml`
   - `res/xml/backup_rules.xml`
3. Moved package declaration from manifest to build.gradle (namespace)

### Dependencies

- `androidx.appcompat:appcompat:1.6.1`
- `androidx.preference:preference:1.2.1`
- `androidx.core:core:1.12.0`
- `com.google.android.material:material:1.11.0`

## 64-bit Architecture Support

The app now includes native library support for:
- `arm64-v8a` (64-bit ARM)
- `x86_64` (64-bit Intel)
- `armeabi-v7a` (32-bit ARM, for compatibility)
- `x86` (32-bit Intel, for compatibility)

### Building Native Libraries

The BrailleBack native libraries (liblouis and brltty) need to be compiled for all target architectures:

1. Clone BrailleBack: `git clone https://github.com/google/brailleback.git`
2. Run the build script: `cd brailleback && bash build.sh`
3. Native libraries will be generated in `braille/service/libs/`

## Testing

### Verify 64-bit Support

```bash
# Extract and check APK
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "lib/"
```

Expected output should include:
- lib/arm64-v8a/
- lib/x86_64/

### Install and Test

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Known Issues

1. **Native Libraries**: Full braille translation requires native libraries (liblouis, brltty) to be built for all target architectures. Stub implementations are provided for compilation.

2. **Network Requirements**: Initial Gradle build requires internet connectivity to download dependencies.

## Troubleshooting

### Build Fails with "Could not resolve dependencies"

Ensure you have internet connectivity and your Android SDK is properly configured in `local.properties`:

```properties
sdk.dir=/path/to/android/sdk
```

### Native Library Issues

If you encounter `UnsatisfiedLinkError`, ensure native libraries are compiled for the target architecture or use the stub implementation.

## Version History

- v3.1.0: Android 14 and 64-bit support
- v3.0-2: Original version (Android 6.0, 32-bit only)
