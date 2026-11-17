# SidePanel: System Architecture (ARCHITECTURE.md)

This document describes the end-to-end architectural design for the SidePanel project, spanning the Native Android Client, the Windows Companion Service, and their local networking subsystem.

---

## 1. High-Level Architecture & Network Topology

SidePanel operates on a peer-to-peer client-server topology restricted to the user's Local Area Network (LAN):

```mermaid
graph TD
    subgraph WindowsHost ["Windows 10/11 Host Machine"]
        subgraph ServerCore ["Windows Companion Service (Python)"]
            TrayUI["System Tray Manager"]
            ConfigMgr["Config & Pairing Store"]
            Engine["Telemetry Engine (Async Loop)"]
            Sensors["Hardware Probes (psutil, NVML, WMI)"]
            ControlDispatcher["System Control Dispatcher"]
            WSServer["WebSocket Server (asyncio / websockets)"]
            
            TrayUI --> ConfigMgr
            Engine --> Sensors
            WSServer <--> ControlDispatcher
            WSServer <--> Engine
            WSServer <--> ConfigMgr
        end
        WinAPI["Windows OS APIs (CoreAudio, User32, Kernel32)"]
        ControlDispatcher --> WinAPI
    end

    subgraph LocalNetwork ["Local Wi-Fi Subnet (Zero Cloud)"]
        Pairing["Pairing Handshake (PIN / QR Token)"]
        WSSocket["WebSocket Connection (ws:// or wss://:8765)"]
    end

    subgraph AndroidClient ["Android Tablet / Phone (Client)"]
        subgraph NativeApp ["Native Android Application (Kotlin)"]
            WSManager["WebSocket Connection Manager (OkHttp)"]
            ProtoParser["Protocol Serialization & Framing (Kotlinx.serialization)"]
            Repo["Telemetry & Control Repository"]
            ViewModel["Dashboard & Widget ViewModels"]
            ComposeUI["Jetpack Compose Dashboard (Landscape UI)"]
            DataStore["Encrypted DataStore (Saved Host & Auth Keys)"]
            
            ComposeUI --> ViewModel
            ViewModel --> Repo
            Repo <--> WSManager
            WSManager <--> ProtoParser
            Repo <--> DataStore
        end
    end

    WSServer <===> WSSocket <===> WSManager
```

---

## 2. Android Client Architecture

The Android application is structured following modern Google-recommended Android Architecture principles (Clean Architecture + Unidirectional Data Flow / MVI).

```mermaid
flowchart TD
    subgraph PresentationLayer ["Presentation Layer (Jetpack Compose)"]
        DashboardScreen["DashboardScreen (Landscape Grid)"]
        WidgetGrid["WidgetGrid (Draggable / Configurable)"]
        MetricCard["MetricCard (Gauge, Sparkline, Counter)"]
        ControlDock["ControlDock (Volume, Media, Hotkeys)"]
        PairingDialog["PairingScreen (CameraX QR Scanner)"]
        
        DashboardScreen --> WidgetGrid
        WidgetGrid --> MetricCard
        DashboardScreen --> ControlDock
    end

    subgraph ViewModelLayer ["State & ViewModel Layer"]
        DashboardVM["DashboardViewModel (StateFlow<DashboardUiState>)"]
        ControlVM["ControlViewModel"]
        ConnectionVM["ConnectionViewModel (StateFlow<ConnectionState>)"]
    end

    subgraph DomainLayer ["Domain Layer (Use Cases)"]
        ObserveMetricsUC["ObserveMetricsUseCase"]
        SendControlCommandUC["SendControlCommandUseCase"]
        PairDeviceUC["PairDeviceUseCase"]
    end

    subgraph DataLayer ["Data & Infrastructure Layer"]
        TelemetryRepo["TelemetryRepositoryImpl"]
        ConnectionRepo["ConnectionRepositoryImpl"]
        WSClient["SidePanelWebSocketClient (OkHttp)"]
        PreferencesSource["PreferencesDataSource (Jetpack DataStore)"]
    end

    PresentationLayer --> ViewModelLayer
    ViewModelLayer --> DomainLayer
    DomainLayer --> DataLayer
    WSClient --> TelemetryRepo
    PreferencesSource --> ConnectionRepo
```

### 2.1. Key Android Technologies
- **UI Toolkit**: Jetpack Compose 1.7+ (Material3, Canvas for low-overhead hardware graphs).
- **Architecture**: Single-Activity, Compose Navigation, MVVM / MVI with `StateFlow` and `SharedFlow`.
- **Concurrency**: Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Default`, `Dispatchers.Main`).
- **Networking**: OkHttp 4.12+ WebSocket engine with automated exponential backoff and custom ping/pong keep-alives.
- **Serialization**: `kotlinx.serialization.json` (efficient, type-safe, reflection-free JSON parsing).
- **Local Persistence**: Jetpack DataStore (saving paired hosts, tokens, widget configurations).
- **Camera/Scanner**: CameraX + Google ML Kit Barcode Scanning (for zero-friction QR pairing).

---

## 3. Windows Companion Architecture

The Windows application is an asynchronous event-driven daemon designed to run quietly in the system tray.

```mermaid
flowchart LR
    subgraph WindowsDaemon ["Windows Daemon Process"]
        subgraph EventLoop ["Asyncio Event Loop"]
            Server["WebSocket Server (port 8765)"]
            SessionMgr["Client Session & Heartbeat Manager"]
            Broadcaster["Telemetry Broadcaster"]
            Dispatcher["Action & Command Dispatcher"]
        end
        
        subgraph Collectors ["Hardware Telemetry Collectors"]
            CPUCollector["CPU Monitor (psutil)"]
            RAMCollector["RAM & Swap Monitor (psutil)"]
            GPUCollector["GPU Monitor (NVML / pynvml / LibreHardwareMonitor)"]
            DiskNetCollector["Disk & Network I/O (psutil)"]
        end

        subgraph Executors ["System Action Executors"]
            AudioExec["CoreAudio Controller (pycaw)"]
            MediaExec["Media Controller (win32api / keyboard)"]
            PowerExec["System Power Controller (ctypes user32)"]
        end
    end

    Collectors --> Broadcaster
    Broadcaster --> Server
    Server --> SessionMgr
    Server --> Dispatcher
    Dispatcher --> AudioExec
    Dispatcher --> MediaExec
    Dispatcher --> PowerExec
```

### 3.1. Key Windows Technologies
- **Language**: Python 3.10+ (using `asyncio`).
- **Networking / Server**: `websockets` (RFC 6455 compliant, asynchronous Python WebSocket server).
- **Hardware Telemetry**:
  - `psutil` (Cross-platform system utilization, memory, disks, network rates).
  - `pynvml` (NVIDIA Management Library for discrete GPU usage, VRAM, and thermal monitoring).
  - WMI / LibreHardwareMonitor CLI/DLL wrapper (AMD & Intel thermal/clock telemetry).
- **System Automation**:
  - `pycaw` (Python Core Audio Windows Library for volume and mute control).
  - `ctypes` / `win32api` for media key injection, desktop locking, and monitor power management.
- **System Tray**:
  - `pystray` + `Pillow` for responsive Windows taskbar tray icon, context menus, and visual status.

---

## 4. Connection Lifecycle & State Machine

```mermaid
stateDiagram-v2
    [*] --> Disconnected

    Disconnected --> Discovering : Scan Network / QR Scanned
    Discovering --> Connecting : Host Resolved (IP:Port)
    Connecting --> Authenticating : WebSocket Handshake Established
    
    Authenticating --> Connected : Auth Token Validated
    Authenticating --> Disconnected : Auth Failed (Invalid Token/PIN)
    
    Connected --> Connected : Streaming Telemetry / Command Executed
    Connected --> Reconnecting : Network Drop / Missing Heartbeat
    
    Reconnecting --> Connecting : Backoff Timer Expired
    Reconnecting --> Disconnected : Max Retries Exceeded / User Cancelled
```

### 4.1. Lifecycle Details
1. **Discovery / Configuration**: Android client learns Windows companion IP and Port either via QR code scan (generated by Windows tray) or manual IP configuration.
2. **WebSocket Handshake**: TCP connection and WebSocket upgrade request on port 8765.
3. **Authentication Phase**: First packet from client must be an `AUTH_REQUEST` with the pairing token. If valid within 3 seconds, host responds with `AUTH_SUCCESS`; otherwise connection is severed.
4. **Active Streaming**: Host continuously pushes `METRICS_STREAM` packets at the configured interval (e.g., 1000ms). Client pushes `COMMAND_EXECUTE` messages as interactive user events occur.
5. **Heartbeat Maintenance**: Ping-pong frames or application-level keep-alives every 5 seconds. If no response is received within 10 seconds, client transitions to `Reconnecting`.

---

## 5. Security Architecture

1. **Local Subnet Isolation**: The companion server binds to local IPv4 network interfaces (or optionally loopback during testing). No port forwarding (UPnP/NAT) is ever configured.
2. **Mutual Pairing Token**:
   - On first setup, the Windows companion generates a cryptographically random pairing token (or 6-digit PIN).
   - Displayed as a QR code in the Windows tray app.
   - Android client scans the QR code and stores the token securely in encrypted local storage.
3. **Replay & DoS Protection**:
   - Connection attempts from unauthorized IPs are throttled (rate-limited).
   - Maximum of 3 simultaneous client connections allowed.
4. **Command Whitelist Validation**:
   - Commands are strictly enumerated enums (e.g., `VOLUME_SET`, `MEDIA_PLAY_PAUSE`, `LOCK_WORKSTATION`).
   - Arbitrary shell script execution is structurally impossible by design.
