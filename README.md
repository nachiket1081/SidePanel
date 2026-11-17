# SidePanel: Android-to-Windows Companion Dashboard

SidePanel is a high-performance, real-time PC companion dashboard that transforms an Android device (tablet or phone) into a dedicated secondary hardware monitoring display and system control surface for Windows 10/11 PCs.

---

## Repository Structure

```text
SidePanel/
├── .gitignore               # Root git ignore covering Android, Gradle, and Python
├── README.md                # Project introduction and quickstart overview
├── PROJECT_SPEC.md          # Comprehensive product requirements & specifications
├── ARCHITECTURE.md          # High-level and component architecture documentation
├── PROTOCOL.md              # Local WebSocket communication protocol specification
├── DEVELOPMENT_PLAN.md      # Milestones, task breakdowns, and roadmap
│
├── android/                 # Native Android Client Application
│   ├── README.md            # Android architecture and setup instructions
│   └── app/                 # Scaffolding for Kotlin + Jetpack Compose project
│
└── windows/                 # Windows Companion Application
    ├── README.md            # Windows server architecture and setup instructions
    ├── requirements.txt     # Planned Python dependencies with rationale
    ├── src/                 # Application source code
    │   ├── core/            # Configuration, state, and lifecycle management
    │   ├── hardware/        # Hardware telemetry collectors (CPU, GPU, RAM, etc.)
    │   ├── server/          # WebSocket server, protocol framing, and auth
    │   └── controls/        # Windows system control executors (audio, media, shortcuts)
    └── tests/               # Unit and integration test suite
```

---

## Architectural Principles

1. **Native Android First**: Built strictly using Kotlin and Jetpack Compose for fluid UI, low battery drain, and deep Android OS integration.
2. **Local-Only Communication**: 100% local Wi-Fi communication via authenticated WebSockets. No cloud relays, third-party databases, Firebase, or Supabase.
3. **Low Latency & High Resilience**: Sub-50ms telemetry streaming, automatic reconnection with exponential backoff, and robust heartbeat monitoring.
4. **Clean Decoupling**: Strict boundary between the Android client and the Windows companion, governed by a versioned protocol specification (`PROTOCOL.md`).

For detailed architectural diagrams and implementation roadmaps, refer to:
- [PROJECT_SPEC.md](file:///d:/Work/Sidepanel/PROJECT_SPEC.md)
- [ARCHITECTURE.md](file:///d:/Work/Sidepanel/ARCHITECTURE.md)
- [PROTOCOL.md](file:///d:/Work/Sidepanel/PROTOCOL.md)
- [DEVELOPMENT_PLAN.md](file:///d:/Work/Sidepanel/DEVELOPMENT_PLAN.md)
