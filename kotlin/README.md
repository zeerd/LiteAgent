# Text Adventure - Android Application

An interactive text adventure game powered by LiteRT-LM for Android.

## Features

- **Multiple Story Genres**: Choose from Fantasy, Sci-Fi, Horror, Romance, and Mystery
- **Real-time AI Generation**: Powered by LiteRT-LM for fast, on-device responses
- **Persistent Progress**: All your adventures are saved locally
- **Customizable Settings**: Adjust temperature, max tokens, and system prompts
- **Material 3 Design**: Modern, responsive UI following Material Design 3 guidelines

## Architecture

- **UI Layer**: Jetpack Compose with Material 3
- **Data Layer**: Room Database for local persistence
- **Architecture**: MVVM with Hilt dependency injection
- **Service Layer**: LiteRT-LM integration for model inference

## Project Structure

```
LiteAgent_Planner/
├── app/
│   ├── src/main/
│   │   ├── java/com/liteagent/textadventure/
│   │   │   ├── MainActivity.kt              # Main activity and navigation
│   │   │   ├── TextAdventureApp.kt          # Application class
│   │   │   ├── data/
│   │   │   │   ├── db/                      # Room database
│   │   │   │   ├── local/                   # Local data sources
│   │   │   │   └── repository/              # Data repositories
│   │   │   ├── di/                          # Hilt dependency injection
│   │   │   ├── model/                       # Data models
│   │   │   ├── navigation/                  # Navigation setup
│   │   │   ├── service/                     # LiteRT-LM service
│   │   │   └── ui/
│   │   │       ├── main/                    # Main chat screen
│   │   │       ├── settings/                # Settings screen
│   │   │       └── newstory/                # New story screen
│   │   └── res/
│   │       ├── values/                       # Resources
│   │       ├── drawable/                     # Drawable resources
│   │       └── mipmap-*/                     # App icons
│   └── build.gradle.kts
├── gradle/wrapper/
├── build.gradle.kts
└── settings.gradle.kts
```

## Building the App

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK 34 (API 34)
- Gradle 8.4+
- Kotlin 1.9.20+

#### Runtime

Disclaimer: No official data found. The info below is AI-generated for reference purposes only.

| Dimension	| Minimum Requirements | Recommended Specs |
| --------- | -------------------- | ----------------- |
| SoC (Qualcomm) | Snapdragon 8 Gen 1 / 7+ Gen 2 | Snapdragon 8 Gen 3 (QPR/NPU optimized) |
| RAM | 6GB (Total System Capacity) | 8GB or 12GB |
| Storage | At least 2GB free space	| 5GB+ (for storing various quantized versions) |
| Acceleration Interface | GPU (Vulkan/OpenCL)| NPU (Hexagon/Tensor) |

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Install on connected device
./gradlew installDebug
```

## LiteRT-LM Integration

To integrate LiteRT-LM:

1. Download the LiteRT-LM Android SDK from Google AI Edge
2. Copy the `.aar` file to `app/libs/`
3. Uncomment the following line in `app/build.gradle.kts`:

```kotlin
implementation(files("libs/lite-rtml-android.aar"))
```

## Available Story Settings

1. **Fantasy Adventure** - Epic quests in magical realms
2. **Sci-Fi Mystery** - Investigate cosmic anomalies aboard space stations
3. **Horror Thriller** - Survive supernatural encounters
4. **Romance Drama** - Explore relationships and emotional choices
5. **Detective Mystery** - Solve intricate crimes in noir worlds

## Configuration

### Temperature
Controls the creativity of responses. Range: 0.5 - 2.0
- Lower values: More deterministic, focused responses
- Higher values: More creative, diverse responses

### Max Tokens
Maximum tokens per response. Default: 2048

### System Prompt
Customize the AI's role and behavior. Default provides a text adventure game master persona.

## License

This project is open source and available under the MIT License.
