# Package Name Invalid Error - Resolution

## Problem
When attempting to install the APK, Android showed an error: **"package name invalid"**

## Root Cause
The APK was built without version information. When examining the APK with `aapt dump badging`, it showed:
```
package: name='com.dalton.braillekeyboard' versionCode='' versionName=''
```

Empty `versionCode` and `versionName` fields cause Android's package manager to reject the APK as invalid.

## Solution
Updated the `manual_build.sh` script to include version information during the APK build process.

### Changes Made

**File: manual_build.sh**
```bash
$BUILD_TOOLS/aapt2 link \
    -I "$ANDROID_JAR" \
    -o "$BUILD_DIR/app-unsigned.apk" \
    --manifest "$PROJECT_DIR/app/src/main/AndroidManifest.xml" \
    --java "$BUILD_DIR/gen" \
    --auto-add-overlay \
    --version-code 56 \          # ADDED
    --version-name "3.1.0" \     # ADDED
    "$BUILD_DIR/compiled_res"/*.flat
```

### Version Information
The version numbers were taken from `app/build.gradle` to ensure consistency:
- **Version Code:** 56
- **Version Name:** 3.1.0

## Verification

After the fix, the APK now shows correct package information:
```
package: name='com.dalton.braillekeyboard' versionCode='56' versionName='3.1.0'
```

## Result
✅ The APK can now be installed successfully on Android devices
✅ Package name is properly validated
✅ Version information is correctly displayed in system settings

## Technical Details

The `aapt2 link` command is responsible for creating the final APK package. The `--version-code` and `--version-name` parameters override any version information in the AndroidManifest.xml and ensure the APK metadata is properly set.

Without these parameters, aapt2 defaults to empty strings for version information, which Android's package manager considers invalid for installation.

## Prevention

Going forward, any manual builds should always include:
- `--version-code` parameter with an integer value
- `--version-name` parameter with a semantic version string

These should match the values in `app/build.gradle` for consistency across build methods.
