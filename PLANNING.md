# Text Adventure Android App - Development Plan

## Project Overview

**App Name**: Text Adventure  
**Description**: A text adventure game chatbot app with LiteRT-LM integration  
**Target Platform**: Android (Jetpack Compose, Kotlin)

---

## Target Audience & Style

According to Material Design 3 guidelines:
- **App Category**: Productivity/Entertainment
- **Visual Style**: Expressive & Engaging (bright colors, gesture-rich)
- **Key Focus**: Clean UI, high contrast for reading game stories

---

## Application Structure

### Main Pages

1. **Main Page (MainActivity.kt)** - Chat Interface
   - Chat conversation display (like general chatbot)
   - Top App Bar with Settings and New Story buttons
   - Input area with quick-action buttons (2-4 dynamic buttons)
   - Message input and send button

2. **Settings Page (SettingsFragment.kt / SettingsScreen.kt)**
   - HuggingFace/ModelScope selection (RadioButtons)
   - Download button (download models)
   - Open folder button
   - LLM configuration parameters

3. **New Story Page (NewStoryScreen.kt)**
   - Story setting selection (dropdown/checkboxes)
   - History view button
   - Start new story button
   - Cancel button

---

## Technical Stack

### Core Technologies
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Compose + ViewModel
- **ML Engine**: LiteRT-LM (Android version)
- **Navigation**: Navigation Compose

### Dependencies

```kotlin
dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // ViewModel & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // Activity & ViewModel
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    
    // Preferences for settings
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // LiteRT-LM (local dependency to be added)
    implementation(files("libs/lite-rtml-android.aar"))
    
    // Coroutines lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

---

## File Structure

```
LiteAgent_Planner/
├── gradle.properties
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/liteagent/textadventure/
            │   ├── MainActivity.kt
            │   ├── MainActivityViewModel.kt
            │   ├── di/
            │   │   └── AppModule.kt (DI)
            │   ├── data/
            │   │   ├── repository/
            │   │   │   ├── ConversationRepository.kt
            │   │   │   └── SettingsRepository.kt
            │   │   ├── db/
            │   │   │   └── AppDatabase.kt
            │   │   │   ├── ConversationEntity.kt
            │   │   │   └── StorySettingEntity.kt
            │   │   └── local/
            │   │       └── StorySettingDataSource.kt
            │   ├── model/
            │   │   ├── StorySetting.kt
            │   │   ├── ChatMessage.kt
            │   │   └── AppSettings.kt
            │   ├── navigation/
            │   │   └── AppNavHost.kt
            │   ├── ui/
            │   │   ├── theme/
            │   │   │   ├── Color.kt
            │   │   │   ├── Theme.kt
            │   │   │   └── Type.kt
            │   │   ├── main/
            │   │   │   ├── MainScreen.kt
            │   │   │   ├── MainViewModel.kt
            │   │   │   └── components/
            │   │   │       ├── ChatMessageItem.kt
            │   │   │       ├── ChatInputBar.kt
            │   │   │       └── QuickActionButtons.kt
            │   │   ├── settings/
            │   │   │   ├── SettingsScreen.kt
            │   │   │   └── SettingsViewModel.kt
            │   │   └── newstory/
            │   │   │   ├── NewStoryScreen.kt
            │   │   │   └── NewStoryViewModel.kt
            │   ├── service/
            │   │   └── LiteRtLmService.kt
            │   └── util/
            │       └── Constants.kt
            └── res/
                ├── values/
                │   ├── strings.xml
                │   ├── colors.xml
                │   └── themes.xml
                ├── values-night/
                │   └── themes.xml
                ├── drawable/
                │   ├── ic_logo.xml
                │   ├── ic_settings.xml
                │   ├── ic_new_story.xml
                │   ├── ic_download.xml
                │   └── ic_cancel.xml
                └── mipmap-*/
                    └── ic_launcher.xml
```

---

## Navigation Structure

```
Main Destination: MainFragment (MainScreen)
├── Destination: SettingsDestination (SettingsScreen)
└── Destination: NewStoryDestination (NewStoryScreen)
```

Using single activity architecture with Compose Navigation.

---

## Key Features Implementation

### 1. Chat Interface (MainScreen)
- Scrollable message list
- Message bubbles (user: right-aligned, AI: left-aligned)
- Input bar with text field + send button
- Quick action buttons with dynamic configuration
- Placeholder text when no active story
- Welcome message with instructions

### 2. Settings Screen
- RadioButtons: HuggingFace vs ModelScope
- Download button: Download selected model
- Open folder button: Open model storage directory
- LLM Configuration:
  - Temperature slider (0.5-2.0)
  - Max tokens input
  - System prompt textarea
  - Save/Cancel buttons

### 3. New Story Screen
- Dropdown for story settings (genre, plot, characters)
- History button: View past stories
- Start Story button: Begin new adventure
- Cancel button: Return to main screen

### 4. Core Services
- LiteRT-LM Service: Handle model loading and inference
- Conversation Repository: Store chat history
- Story Setting Repository: Predefined story options
- Settings Repository: Persist user preferences

---

## UI Design Specifications

### Colors (Material 3)
- Primary: Vibrant purple/blue (gamification feel)
- Secondary: Amber accent
- Tertiary: Teal for backgrounds
- Error: Red for errors/buttons

### Typography
- Display: 57sp, 45sp, 36sp
- Headline: 32sp (main titles)
- Title: 22sp (secondary titles)
- Body: 16sp (messages)
- Label: 14sp (button labels)

### Spacing
- Message padding: 12dp
- Screen margins: 16dp
- Button spacing: 8dp
- Section spacing: 24dp

### Touch Targets
- All buttons: 48dp minimum (56dp recommended)
- Input fields: 56dp height
- Quick action buttons: 48dp height

---

## Development Phases

### Phase 1: Project Setup (Days 1-2)
- [ ] Create Gradle project structure
- [ ] Configure build files
- [ ] Setup Material Design 3 theme
- [ ] Implement navigation framework

### Phase 2: Core UI Components (Days 3-5)
- [ ] Chat message display component
- [ ] Input bar with quick actions
- [ ] Main screen layout
- [ ] Settings screen with configuration
- [ ] New story screen with selection

### Phase 3: Data Layer (Days 6-8)
- [ ] Room database setup
- [ ] Conversation repository
- [ ] Settings repository
- [ ] Story settings data source

### Phase 4: Service Integration (Days 9-11)
- [ ] LiteRT-LM service integration
- [ ] Model download functionality
- [ ] LLM inference logic
- [ ] Error handling

### Phase 5: Testing & Polish (Days 12-14)
- [ ] Unit tests (ViewModel, Repository)
- [ ] UI tests (Compose)
- [ ] Performance optimization
- [ ] Bug fixes
- [ ] Build optimization

---

## Key Considerations

### Performance
- LazyColumn for message list (efficient scrolling)
- LaunchedEffect for data loading
- Debounce input for real-time suggestions

### Accessibility
- contentDescription on all buttons
- Proper focus order
- TalkBack compatibility
- Color contrast ≥ 4.5:1

### Error Handling
- Try-catch around model operations
- Error dialogs for failures
- Retry mechanisms for downloads
- Fallback states

### Security
- No sensitive data in logs
- Encrypted shared preferences for settings
- File security for model storage

---

## Testing Strategy

### Unit Tests
- MainActivityViewModel tests
- ViewModel business logic tests
- Repository tests with MockK
- Coroutine flow tests with Turbine

### UI Tests
- Navigation flow tests
- UI component interaction tests
- Accessibility tests
- Compose UI tests with createComposeRule

---

## Build Configuration Notes

- Minimum SDK: API 24 (Android 7.0)
- Target SDK: API 34 (Android 14)
- Compile SDK: API 34
- Kotlin version: 1.9.20
- Gradle version: 8.1+
- AGP version: 8.1+

### ProGuard/R8 Rules
Add to `proguard-rules.pro`:
```
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.LiteRtLmJniException
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** w(...);
    public static *** e(...);
    public static *** i(...);
}
```

---

## Next Steps

1. ✅ Review requirements and create this plan
2. 🚀 Start implementing Phase 1: Project Setup
3. 🚀 Continue with each phase sequentially
4. 🧪 Test continuously at each phase
5. 📝 Document as you develop

---

**Generated**: 2026-05-01  
**Project**: LiteAgent_Planner  
**Version**: 1.0
