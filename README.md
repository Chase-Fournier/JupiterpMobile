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
