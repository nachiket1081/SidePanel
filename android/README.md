# SidePanel: Native Android Application

The **SidePanel Android Application** is a native, landscape-first companion dashboard built with **Kotlin** and **Jetpack Compose**. It connects to the Windows Companion service via local Wi-Fi, rendering real-time hardware telemetry and providing responsive system controls.

---

## Technical Specifications

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose (Material3, Canvas graphics)
- **Minimum SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 35 / 36 (Android 15 / 16)
- **Orientation**: Locked Landscape (`android:screenOrientation="landscape"`)
- **Networking**: OkHttp WebSocket Client with `kotlinx.coroutines` Flow streaming
- **Serialization**: `kotlinx.serialization` (JSON)
- **Local Storage**: Jetpack DataStore Preferences

---

## Planned Directory Structure

```text
android/
├── README.md
├── build.gradle.kts                 # Root project Gradle configuration
├── settings.gradle.kts               # Gradle project modules declaration
├── gradle.properties                 # JVM and AndroidX configuration
├── app/
│   ├── build.gradle.kts             # App module Gradle configuration & dependencies
│   ├── proguard-rules.pro           # ProGuard / R8 optimization rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml  # Permissions (INTERNET, ACCESS_NETWORK_STATE, CAMERA)
│       │   ├── java/com/sidepanel/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── SidePanelApp.kt  # Application class
│       │   │   ├── data/
│       │   │   │   ├── model/       # Data transfer objects conforming to PROTOCOL.md
│       │   │   │   ├── network/     # OkHttp WebSocket connection manager
│       │   │   │   └── repository/  # Telemetry & settings repository
│       │   │   ├── domain/
│       │   │   │   └── usecase/     # Business logic use cases
│       │   │   └── ui/
│       │   │       ├── dashboard/   # Landscape dashboard screen & grid
│       │   │       ├── widgets/     # CPU, GPU, RAM, Network, and Control widgets
│       │   │       ├── pairing/     # CameraX QR code scanner dialog
│       │   │       └── theme/       # Material3 typography, color schemes, shapes
│       │   └── res/
│       │       ├── values/
│       │       └── mipmap/
│       └── test/                    # Unit tests
```
