#!/bin/bash

# This script generates necessary local files and configurations
# Run this after cloning the project to set up the environment

set -e

echo "🚀 Generating project files..."

# Create necessary directories
mkdir -p app/libs
mkdir -p .gradle

# Create Gradle wrapper
cd gradle/wrapper
if [ ! -f "gradle-wrapper.jar" ]; then
    echo "⚠️  gradle-wrapper.jar not found, you may need to run: ./gradlew"
fi

# Create placeholder for LiteRT-LM .aar file
touch app/libs/lite-rtml-android.aar.placeholder
echo "# LiteRT-LM Android SDK" > app/libs/lite-rtml-android.aar.placeholder
echo "Download the LiteRT-LM Android SDK and place the .aar file here" >> app/libs/lite-rtml-android.aar.placeholder

echo "✅ Project files generated!"
echo ""
echo "Next steps:"
echo "1. Download LiteRT-LM Android SDK from Google AI Edge"
echo "2. Place the .aar file in app/libs/lite-rtml-android.aar"
echo "3. Uncomment the dependency in app/build.gradle.kts:"
echo '   implementation(files("libs/lite-rtml-android.aar"))'
echo "4. Run: ./gradlew assembleDebug"
