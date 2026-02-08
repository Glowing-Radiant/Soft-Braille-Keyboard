#!/bin/bash
set -e

# Script to manually build APK using Android SDK tools
# This is a workaround for environments without Gradle/internet access

PROJECT_DIR="/home/runner/work/Soft-Braille-Keyboard/Soft-Braille-Keyboard"
BUILD_DIR="$PROJECT_DIR/manual_build"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
BUILD_TOOLS="$ANDROID_HOME/build-tools/34.0.0"

echo "Starting manual APK build..."

# Clean build directories
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen"
mkdir -p "$BUILD_DIR/obj"
mkdir -p "$BUILD_DIR/bin"
mkdir -p "$BUILD_DIR/compiled_res"

# Generate R.java
echo "Generating R.java..."
$BUILD_TOOLS/aapt2 compile \
    --dir "$PROJECT_DIR/app/src/main/res" \
    -o "$BUILD_DIR/compiled_res"

$BUILD_TOOLS/aapt2 link \
    -I "$ANDROID_JAR" \
    -o "$BUILD_DIR/app-unsigned.apk" \
    --manifest "$PROJECT_DIR/app/src/main/AndroidManifest.xml" \
    --java "$BUILD_DIR/gen" \
    --auto-add-overlay \
    "$BUILD_DIR/compiled_res"/*.flat

# Compile Java sources
echo "Compiling Java sources..."
find "$PROJECT_DIR/app/src/main/java" -name "*.java" > "$BUILD_DIR/sources.txt"
find "$BUILD_DIR/gen" -name "*.java" >> "$BUILD_DIR/sources.txt"

# Count source files
SOURCE_COUNT=$(wc -l < "$BUILD_DIR/sources.txt")
echo "Found $SOURCE_COUNT Java files to compile"

# Compile with proper error handling
javac -d "$BUILD_DIR/obj" \
    -classpath "$ANDROID_JAR" \
    -sourcepath "$PROJECT_DIR/app/src/main/java:$BUILD_DIR/gen" \
    -bootclasspath "$ANDROID_JAR" \
    -source 1.8 -target 1.8 \
    @"$BUILD_DIR/sources.txt" || {
        echo "Compilation failed. Check errors above."
        exit 1
    }

# Convert to DEX
echo "Converting to DEX..."
find "$BUILD_DIR/obj" -name "*.class" > "$BUILD_DIR/classes.txt"
CLASS_COUNT=$(wc -l < "$BUILD_DIR/classes.txt")
echo "Found $CLASS_COUNT class files"

if [ $CLASS_COUNT -eq 0 ]; then
    echo "No class files found! Compilation may have failed."
    exit 1
fi

$BUILD_TOOLS/d8 \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/bin" \
    $(find "$BUILD_DIR/obj" -name "*.class")

# Add DEX to APK
echo "Adding DEX to APK..."
cd "$BUILD_DIR/bin"
zip -u "$BUILD_DIR/app-unsigned.apk" classes.dex

# Align APK
echo "Aligning APK..."
$BUILD_TOOLS/zipalign -f 4 "$BUILD_DIR/app-unsigned.apk" "$BUILD_DIR/app-aligned.apk"

echo ""
echo "═══════════════════════════════════════════════════════"
echo "✅ APK built successfully!"
echo "═══════════════════════════════════════════════════════"
echo "Location: $BUILD_DIR/app-aligned.apk"
ls -lh "$BUILD_DIR/app-aligned.apk"
echo ""
echo "Note: This APK is unsigned and for testing only."
echo "To sign for release, use:"
echo "  $BUILD_TOOLS/apksigner sign --ks your-keystore.jks $BUILD_DIR/app-aligned.apk"
echo "═══════════════════════════════════════════════════════"
