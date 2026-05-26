# Jupiterp - KMP Course Planner

A Kotlin Multiplatform mobile application for University of Maryland students to search courses, view sections with professor ratings, and build visual weekly schedules.

> This app is a mobile client built on top of the [Jupiterp](https://github.com/atcupps/Jupiterp) web platform. Jupiterp is a website that helps University of Maryland students plan their schedules and make informed course decisions — it provides course search, section listings with professor reviews from PlanetTerp, and a visual schedule builder. It is primarily managed and maintained by [@atcupps](https://github.com/atcupps) and is not officially affiliated with the University of Maryland. The backend API powering this app comes from the [jupiterp-umd](https://github.com/jupiterp-umd) GitHub group.

![Platform](https://img.shields.io/badge/platform-Android%20%7C%20iOS-brightgreen)
![Kotlin](https://img.shields.io/badge/kotlin-2.3.0-purple)
![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.10.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)

## Features

- **Course Search** — Real-time search with 300ms debounce, department filter, and GenEd multi-select
- **Instructor Search** — Type `@Name` in the search bar to filter by professor; tap a suggestion to apply a sticky filter chip
- **Professor Ratings** — Color-coded RateMyProfessors rating chips on each section
- **Seat Availability** — Color-coded open/waitlisted/closed seat badges
- **Schedule Builder** — Visual weekly grid (8am–10pm) with pastel color-coded blocks and automatic conflict detection
- **Calendar Export** — Adds recurring weekly events directly to the device calendar (EventKit on iOS, calendar app import on Android)
- **Save / Load Schedules** — Save multiple named schedules and restore them later
- **Single-Screen Design** — Schedule is always visible; search panel slides up from the bottom

## Architecture

```
composeApp/
└── src/
    ├── commonMain/           # Shared Kotlin code (UI, ViewModels, repositories, API)
    ├── androidMain/          # Android platform code (calendar intent, context holder)
    ├── appleMain/            # Apple platform code (storage)
    └── iosMain/              # iOS platform code (EventKit calendar, view controller)
iosApp/                       # Swift/Xcode iOS wrapper
gradle/                       # Version catalog
```

## Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | Kotlin 2.3.0 |
| **UI Framework** | Compose Multiplatform 1.10.0 |
| **Design System** | Material 3 |
| **Networking** | Ktor Client 3.3.3 |
| **Serialization** | kotlinx.serialization 1.9.0 |
| **DI** | Koin 4.1.1 |
| **State Management** | Kotlin Flow + ViewModel |
| **Persistence** | DataStore Preferences |

## Project Structure

```
composeApp/src/commonMain/kotlin/com/jupiterp/jupiterpmobile/
├── App.kt                              # Compose entry point
├── Platform.kt                         # expect declarations (calendar, date)
├── Util.kt                             # ICS generation, semester dates
├── data/
│   ├── api/JupiterpApiClient.kt        # Ktor HTTP client
│   ├── model/ApiModels.kt              # API response models
│   ├── model/Mappers.kt                # API → domain model mappers
│   ├── repository/CourseRepository.kt  # Course + instructor search
│   ├── repository/ScheduleRepository.kt
│   └── storage/
├── domain/model/DomainModels.kt        # Domain models
├── ui/
│   ├── components/                     # CourseCard, SearchBar, ScheduleView, …
│   ├── screens/
│   │   ├── MainScreen.kt               # Main UI (phone + tablet layouts)
│   │   └── MainViewModel.kt            # UI state & business logic
│   └── theme/Theme.kt
└── di/AppModule.kt                     # Koin dependency injection

composeApp/src/androidMain/
├── AndroidManifest.xml
├── kotlin/com/jupiterp/jupiterpmobile/
│   ├── MainActivity.kt
│   └── Platform.android.kt             # Calendar intent (ACTION_VIEW + ICS file)
└── res/xml/network_security_config.xml

composeApp/src/iosMain/kotlin/com/jupiterp/jupiterpmobile/
├── MainViewController.kt
└── Platform.ios.kt                     # EventKit calendar integration
```

## Building

### Prerequisites

- Android Studio Meerkat or newer
- JDK 21 (`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`)
- For iOS: Xcode 15+ with command-line tools (`xcode-select --install`)

### Android

Open the project in Android Studio and run the `composeApp` configuration, or from the terminal:

```bash
./gradlew :composeApp:assembleDebug
```

### iOS

Open `iosApp/iosApp.xcworkspace` in Xcode and run on a simulator or device, or build the shared framework first:

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

## Configuration

### Android
- Min SDK: 24 (Android 7.0)
- Target SDK: 36

### iOS
- Deployment target: iOS 15+
- Calendar access: the app requests `NSCalendarsFullAccessUsageDescription` permission at export time to write recurring events via EventKit

## License

MIT — see [LICENSE](LICENSE)
