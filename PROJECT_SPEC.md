# SidePanel: Product Specification (PROJECT_SPEC.md)

| Field | Detail |
| :--- | :--- |
| **Product Name** | SidePanel |
| **Product Type** | Real-Time Android-to-Windows PC Companion Dashboard |
| **Architecture** | Client-Server over Local Subnet (Wi-Fi) |
| **Target Client** | Android Tablet / Phone (Landscape-first, Android 8.0+ / API 26+) |
| **Target Host** | Windows 10 / Windows 11 (64-bit) |
| **Document Version** | 1.0.0 |
| **Status** | Approved for Scaffolding |

---

## 1. Executive Summary & Vision

Gamers, streamers, content creators, software engineers, and power users frequently require real-time visibility into their PC's hardware status (CPU/GPU utilization, thermal conditions, RAM saturation, network throughput, VRAM consumption) and rapid access to system-level toggles (master volume, per-app audio mixer, media controls, macro keys, screen locking).

Existing solutions either occupy valuable primary display real estate, rely on heavy third-party cloud infrastructure (introducing privacy risks and latency), or offer outdated, clunky user interfaces.

**SidePanel** bridges this gap:
- An **Android application** crafted natively in **Kotlin** with modern **Jetpack Compose** providing a responsive, landscape-optimized dashboard with customizable widget layouts.
- A lightweight **Windows companion service** in **Python** providing real-time hardware telemetry probing and system command execution.
- Direct, authenticated **Local Wi-Fi communication via WebSockets**, operating entirely offline with zero cloud dependency.

---

## 2. Core Stakeholders & Personas

1. **PC Gamers & Overclockers**: Require low-latency framerate and temperature tracking during intense gameplay without overlays triggering anti-cheat or breaking immersion.
2. **Streamers & Creators**: Need a secondary control pad to adjust audio channels, trigger hotkeys, or switch scenes while live.
3. **Power Users & Developers**: Desire hardware utilization graphs, process insight, and convenient system shortcuts on an idle tablet propped beneath or beside the primary monitor.

---

## 3. Product Architecture Overview

```mermaid
graph LR
    subgraph AndroidDevice ["Android Device (Client)"]
        UI["Jetpack Compose UI (Landscape Dashboard)"]
        StateMgr["State Engine & ViewModels"]
        WSClient["OkHttp WebSocket Client"]
        LocalStore["Jetpack DataStore (Settings & Pairing Keys)"]
        UI --> StateMgr
        StateMgr <--> WSClient
        StateMgr <--> LocalStore
    end

    subgraph LocalWiFi ["Local Wi-Fi Network (Encrypted / Authenticated)"]
        WSClient <=== "WebSocket (JSON Envelope / Ping-Pong)" ===> WSServer
        Discovery["mDNS / QR Code Pairing"]
    end

    subgraph WindowsPC ["Windows 10/11 Host (Server)"]
        WSServer["asyncio WebSocket Server"]
        AuthModule["Token & Pairing Validator"]
        TelemetryEngine["Hardware Telemetry Engine (psutil / NVML / WMI)"]
        ControlEngine["Windows Control Engine (Audio / Media / System)"]
        TrayApp["Background Tray & Configuration GUI"]
        
        WSServer <--> AuthModule
        WSServer <--> TelemetryEngine
        WSServer <--> ControlEngine
        TrayApp --> WSServer
    end
```

---

## 4. Detailed Feature Requirements

### 4.1. Android Native Application (Part 1)
- **Language & UI Framework**: 100% Native Kotlin and Jetpack Compose.
- **Form Factor & Orientation**: Landscape-first responsive design; fluidly adapts to phone screens (5.5"+) and tablets (8" to 12"+).
- **Display Behavior**: Immersive sticky full-screen mode, adjustable wake lock (keep screen active while connected to power or host).
- **Telemetry Dashboard**:
  - CPU: Aggregated utilization %, per-core breakdown, clock frequency, package temperature.
  - GPU: Core utilization %, VRAM usage (GB & %), core temperature, hot spot temperature, fan speeds.
  - Memory: RAM used / total, active pagefile utilization.
  - Storage: Read/Write IOPS and MB/s throughput, capacity utilization gauges.
  - Network: Real-time download/upload rate graphing (KB/s, MB/s).
- **Customizable Widget Layout**:
  - Drag-and-drop / modular grid system for rearranging metric cards.
  - Metric alert thresholds (e.g., color warning transition when CPU/GPU exceeds 85°C).
- **Control Surface (Actions & Triggers)**:
  - System Master Volume slider and mute toggle.
  - Media Controls: Play/Pause, Next Track, Previous Track, Current Track metadata (title/artist if available).
  - Quick System Actions: Lock Workstation, Display Sleep, Custom Process Kill / Launch.
- **Connection Management**:
  - QR Code scanner using CameraX / ML Kit to instantly pair with Windows companion.
  - Manual IP/Port entry fallback.
  - Real-time connection status pill indicator (Connected, Reconnecting, Disconnected, Latency ms).
  - Exponential backoff automatic reconnection.

### 4.2. Windows Companion Application (Part 2)
- **Runtime & Language**: Python 3.10+ on Windows 10/11 64-bit.
- **Background Operation**: System tray icon (`pystray` / PyQt / Tkinter minimal tray) with status indicators, pause streaming toggle, and pairing QR code popup.
- **Hardware Telemetry Engine**:
  - Standard system metrics polled via `psutil`.
  - GPU telemetry (NVIDIA via NVML / `pynvml`; AMD / Intel via WMI / LibreHardwareMonitor wrapper).
  - Configurable sampling and broadcast interval (default: 1000ms, adjustable from 250ms to 5000ms).
- **Control Execution Engine**:
  - Audio endpoints control via Windows Core Audio APIs (`pycaw`).
  - Media key simulation via Win32 `SendInput` / `keyboard` APIs.
  - Workstation lock and screen control via `ctypes.windll.user32`.
- **Security & Pairing Engine**:
  - One-time PIN or cryptographically secure token exchange during initial pairing.
  - Rejection of unauthenticated WebSocket connections.
  - Optional TLS encryption over local Wi-Fi.

---

## 5. Non-Functional Requirements (NFRs)

| Category | Requirement | Target Metric |
| :--- | :--- | :--- |
| **Latency** | End-to-end packet delivery & rendering | < 50ms over standard 5GHz/2.4GHz Wi-Fi |
| **Host CPU Footprint** | Background telemetry & WebSocket serving | < 1.0% average CPU utilization on modern 6-core+ CPU |
| **Host RAM Footprint** | Resident memory consumption | < 80 MB RAM |
| **Android Battery Efficiency** | Active rendering & network intake | < 12% battery drain/hr at 50% brightness on 5000mAh battery |
| **Network Reliability** | Automatic session recovery | Reconnect within 1.5 seconds of transient Wi-Fi drop |
| **Local Isolation** | Data privacy | Zero outbound WAN traffic; no cloud analytics or tracking |

---

## 6. Constraints & Anti-Requirements

- **NO Cross-Platform Web Wrappers**: Strictly prohibited from using React Native, Expo, Flutter, Ionic, Capacitor, or WebView-based UI wrappers. Android must be pure Kotlin + Jetpack Compose.
- **NO Cloud Services for Local Data**: Firebase Realtime Database, Firestore, Supabase, AWS IoT, or external relays must not be used for local communications.
- **NO Polling over HTTP REST**: Continuous telemetry must use bidirectional WebSockets, not periodic HTTP GET requests.
- **NO Dangerous Uncontrolled Execution**: Remote shell commands must be strictly whitelisted and verified; arbitrary string execution (`exec()`, unrestricted shell injection) is prohibited.
