# Jupiterp - KMP Course Planner

A beautiful Kotlin Multiplatform mobile application for University of Maryland students to search courses, view sections with professor ratings, and build visual weekly schedules.

![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS-brightgreen)
![Kotlin](https://img.shields.io/badge/kotlin-2.0.20-purple)
![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.6.11-blue)

## Features

- **Course Search** - Search by course code, name, department, or GenEd requirements
- **Professor Ratings** - View instructor ratings from PlanetTerp integration
- **Visual Schedule Builder** - Build weekly schedules with a beautiful grid interface
- **Conflict Detection** - Automatic time conflict detection when adding sections
- **Gesture Navigation** - Swipe up/down to show/hide search results
- **Material 3 Design** - Modern, intuitive UI following Material 3 expressive guidelines
- **Dark Mode** - Full light and dark theme support
- **Cross-Platform** - Runs on both iOS and Android with shared business logic

## Screenshots

The app features:
- Single-screen design with gesture-based navigation
- Schedule view as the primary screen
- Search panel that slides up from bottom
- Collapsible selected courses list
- Compact header with quick stats
- Settings accessible via menu
- Color-coded schedule blocks for easy course differentiation
- GenEd badges with orange accent colors
- Professor rating chips with color indicators
- Seat availability indicators

## Architecture

```
jupiterp/
├── composeApp/
│   └── src/
│       ├── commonMain/           # Shared code (95%+)
│       │   └── kotlin/com/jupiterp/
│       │       ├── data/
│       │       │   ├── api/      # Ktor HTTP client
│       │       │   ├── model/    # API response models
│       │       │   └── repository/ # Data repositories
│       │       ├── domain/
│       │       │   └── model/    # Domain models
│       │       ├── ui/
│       │       │   ├── components/ # Reusable UI components
│       │       │   ├── screens/   # Main screens & ViewModels
│       │       │   └── theme/     # Material 3 theme
│       │       └── di/           # Koin dependency injection
│       ├── androidMain/          # Android-specific code
│       └── iosMain/              # iOS-specific code
├── iosApp/                       # iOS app wrapper (Swift)
└── gradle/                       # Version catalog
```

## Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 2.0.20 |
| **UI Framework** | Compose Multiplatform 1.6.11 |
| **Design System** | Material 3 |
| **Networking** | Ktor Client 2.3.12 |
| **Serialization** | kotlinx.serialization 1.7.1 |
| **DI** | Koin 3.5.6 |
| **State Management** | Kotlin Flow + ViewModel |
| **Navigation** | Jetpack Navigation Compose |
| **Date/Time** | kotlinx-datetime 0.6.1 |
| **Persistence** | DataStore Preferences |

## Design System

### Color Palette

```kotlin
// Primary brand colors
val Orange = Color(0xFFF6743C)
val LightOrange = Color(0xFFE28A64)

// Schedule block colors (16 pastel shades)
val ScheduleColors = listOf(
    Color(0xFFB3C8F2), // Soft blue
    Color(0xFFF2B3B3), // Soft red
    Color(0xFFF2EFB3), // Soft yellow
    // ... 13 more pastel colors
)
```

### Animations

- Subtle, non-bouncy animations using `tween` easing
- Quick transitions (150-250ms duration)
- `FastOutSlowInEasing` for smooth motion
- Gesture-based navigation with swipe detection

### Component Library

- `CourseCard` - Expandable card with course details and sections
- `SectionRow` - Section details with add/remove functionality
- `WeeklyScheduleView` - Visual schedule grid with time slots
- `ScheduleBlockView` - Color-coded schedule blocks
- `SearchBar` - Compact search with filter chips (squircle style)
- `FilterChip` - Small squircle-shaped filter indicators
- `GenEdBadge` - Orange-accented GenEd requirement badges
- `RatingChip` - Color-coded professor rating indicators
- `SeatsBadge` - Color-coded seat availability

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Xcode 15+ (for iOS development)
- JDK 17+
- Kotlin Multiplatform Mobile plugin

### First-time Setup

After cloning, you'll need to download the Gradle wrapper JAR:

```bash
# Option 1: Open in Android Studio and let it sync
# Option 2: Run this command (requires Gradle installed globally)
gradle wrapper --gradle-version 8.5
```

### Building

#### Android

```bash
./gradlew :composeApp:assembleDebug
```

#### iOS

1. Open `iosApp/iosApp.xcworkspace` in Xcode
2. Select your target device
3. Build and run (⌘R)

### Running

```bash
# Android
./gradlew :composeApp:installDebug

# iOS (via Xcode)
# Build the shared framework first
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

## API Integration

The app connects to the Jupiterp API:

| Endpoint | Description |
|----------|-------------|
| `GET /courses` | Search courses with filters |
| `GET /departments` | List all departments |
| `GET /instructors` | Get instructor ratings |
| `GET /courses/{code}/sections` | Get sections for a course |

## Key Features Implementation

### Single-Screen Design
- Schedule view is always visible as the main screen
- Search panel slides up from bottom with swipe gestures
- Compact header shows course count and total credits
- Settings accessible via menu (three-dot icon)

### Course Search
- Real-time search with 300ms debounce
- Department filter dropdown
- GenEd multi-select grid (FSAW, FSAR, DSSP, etc.)
- Compact squircle-style filter chips
- Results overlay with swipe-down to dismiss

### Schedule Builder
- Visual weekly grid (8am-10pm, auto-adjusts to classes)
- Pastel color-coded blocks for each course
- Automatic conflict detection
- Collapsible selected courses list (collapsed by default)
- Clear schedule option in settings menu

### State Management
- `MainViewModel` handles all UI state
- `CourseRepository` for API calls
- `ScheduleRepository` for schedule state
- Kotlin Flow for reactive updates

## Project Structure

```
/home/claude/jupiterp/
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Project settings
├── gradle/libs.versions.toml     # Version catalog
└── composeApp/
    ├── build.gradle.kts          # App build config
    └── src/
        ├── commonMain/kotlin/com/jupiterp/
        │   ├── App.kt                        # Entry point
        │   ├── data/
        │   │   ├── api/JupiterpApiClient.kt  # HTTP client
        │   │   ├── model/ApiModels.kt        # API models
        │   │   ├── model/Mappers.kt          # Model mappers
        │   │   ├── repository/CourseRepository.kt
        │   │   └── repository/ScheduleRepository.kt
        │   ├── domain/model/DomainModels.kt  # Domain models
        │   ├── ui/
        │   │   ├── components/
        │   │   │   ├── CommonComponents.kt   # Shared components
        │   │   │   ├── SearchBar.kt          # Search UI
        │   │   │   ├── CourseCard.kt         # Course cards
        │   │   │   └── ScheduleView.kt       # Schedule grid
        │   │   ├── screens/
        │   │   │   ├── MainScreen.kt         # Main UI
        │   │   │   └── MainViewModel.kt      # State management
        │   │   └── theme/Theme.kt            # Material 3 theme
        │   └── di/AppModule.kt               # Koin DI
        ├── androidMain/
        │   ├── AndroidManifest.xml
        │   ├── kotlin/com/jupiterp/MainActivity.kt
        │   └── res/
        │       ├── values/themes.xml
        │       └── xml/network_security_config.xml
        └── iosMain/
            └── kotlin/com/jupiterp/MainViewController.kt
```

## Configuration

### Android
- Min SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)
- Compile SDK: 34
- Java 17 compatibility

### iOS
- iOS 15+ deployment target
- arm64 architecture support
- Edge-to-edge display

## License

MIT License - See LICENSE file for details

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## Acknowledgments

- [Jupiterp API](https://api.jupiterp.com) for course data
- [PlanetTerp](https://planetterp.com) for professor ratings
- [Material 3](https://m3.material.io) for design guidelines
- [JetBrains](https://www.jetbrains.com) for Kotlin Multiplatform
