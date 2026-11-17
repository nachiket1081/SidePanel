# SidePanel: Native Android Application

The **SidePanel Android Application** is a native, landscape-first companion dashboard built with **Kotlin** and **Jetpack Compose**. It connects to the Windows Companion service via local Wi-Fi, rendering real-time hardware telemetry and providing responsive system controls.

---

## Technical Specifications

- **Language**: Kotlin 2.0.21
- **UI Framework**: Jetpack Compose 1.7+ (Material3, Canvas graphics)
- **Minimum SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 35 (Android 15)
- **Orientation**: Locked Landscape (`android:screenOrientation="landscape"`)
- **Networking**: OkHttp 4.12.0 WebSocket Client with `kotlinx.coroutines` Flow streaming
- **Serialization**: `kotlinx.serialization` 1.7.3 (JSON)
- **Local Storage**: Jetpack DataStore Preferences 1.1.1

---

## Architecture & Module Structure

```text
android/
├── build.gradle.kts                 # Root project Gradle configuration
├── settings.gradle.kts               # Plugin & dependency management
├── gradle.properties                 # JVM arguments & AndroidX flags
├── gradlew / gradlew.bat             # Gradle wrapper scripts (v8.13)
├── gradle/
│   ├── libs.versions.toml           # Version Catalog for dependencies & plugins
│   └── wrapper/gradle-wrapper.jar   # Gradle distribution wrapper
│
└── app/
    ├── build.gradle.kts             # App module configuration & dependencies
    ├── proguard-rules.pro           # ProGuard & R8 serialization keep rules
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml  # Cleartext traffic, landscape orientation, network permissions
        │   ├── java/com/sidepanel/app/
        │   │   ├── SidePanelApp.kt  # Application entry point & repository container
        │   │   ├── MainActivity.kt  # Landscape Compose Activity with live metrics preview
        │   │   ├── data/
        │   │   │   ├── protocol/
        │   │   │   │   └── ProtocolModels.kt  # DTOs conforming strictly to PROTOCOL.md
        │   │   │   ├── network/
        │   │   │   │   ├── ConnectionState.kt # Lifecycle states (Disconnected, Connecting, Authenticating, Connected, Reconnecting)
        │   │   │   │   └── SidePanelWebSocketClient.kt # OkHttp client with exponential backoff & heartbeat
        │   │   │   └── repository/
        │   │   │       └── TelemetryRepository.kt # Clean Architecture reactive repository
        │   └── res/
        │       └── values/
        │           ├── strings.xml
        │           └── themes.xml
        └── test/
            └── java/com/sidepanel/app/
                └── ProtocolModelsTest.kt # Unit tests verifying JSON serialization & deserialization
```

---

## Running & Building

### Running Unit Tests
```powershell
cd android
.\gradlew.bat test
```

### Assembling Debug APK
```powershell
cd android
.\gradlew.bat assembleDebug
```
The resulting APK will be located at:
`android/app/build/outputs/apk/debug/app-debug.apk`
