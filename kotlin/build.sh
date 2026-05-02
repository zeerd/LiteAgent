#!/bin/bash

# Build script for Text Adventure Android application

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

echo "🚀 Building Text Adventure application..."

# Check for required files
REQUIRED_FILES=(
    "settings.gradle.kts"
    "build.gradle.kts"
    "app/build.gradle.kts"
    "app/src/main/AndroidManifest.xml"
    "gradle/wrapper/gradle-wrapper.properties"
)

MISSING_FILES=()
for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -f "$file" ]; then
        MISSING_FILES+=("$file")
    fi
done

if [ ${#MISSING_FILES[@]} -gt 0 ]; then
    echo "❌ Missing required files:"
    for file in "${MISSING_FILES[@]}"; do
        echo "   - $file"
    done
    exit 1
fi

echo "✅ All required files present"

# Check for Gradle wrapper
if [ ! -f "./gradlew" ]; then
    echo "⚠️  Gradle wrapper not found. Run './gradlew wrapper' to generate it."
    exit 1
fi

# Check for LiteRT-LM SDK
if [ -f "./app/libs/lite-rtml-android.aar" ]; then
    echo "✅ LiteRT-LM SDK found"
else
    echo "⚠️  LiteRT-LM SDK placeholder detected. Please download and place the actual SDK."
fi

# Run build
echo ""
echo "📦 Running build..."

if ./gradlew clean assembleDebug --no-daemon --warning-mode all; then
    echo ""
    echo "✅ Build successful!"
    echo "📱 APK location: app/build/outputs/apk/debug/app-debug.apk"
    exit 0
else
    echo ""
    echo "❌ Build failed. Check the error messages above."
    exit 1
fi
