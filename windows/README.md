# SidePanel: Windows Companion Service

The **SidePanel Windows Companion** is a lightweight, asynchronous background service written in Python. It monitors PC hardware telemetry in real time, hosts an authenticated local WebSocket server, and executes system controls triggered from the paired Android device.

---

## Directory Layout

```text
windows/
├── README.md               # Windows service overview and run instructions
├── requirements.txt        # Python package dependencies with purpose explanations
├── src/                    # Application source code
│   ├── __init__.py
│   ├── core/               # Configuration, security tokens, and state
│   │   ├── __init__.py
│   │   └── config.py       # Host settings and token storage
│   ├── hardware/           # Sensor and telemetry collectors
│   │   ├── __init__.py
│   │   ├── cpu.py          # CPU usage, frequency, and thermals (psutil)
│   │   ├── gpu.py          # GPU utilization and VRAM (NVML)
│   │   ├── ram.py          # Memory and swap usage
│   │   └── io.py           # Network and disk I/O rates
│   ├── server/             # WebSocket server & protocol dispatcher
│   │   ├── __init__.py
│   │   ├── ws_server.py    # Asyncio WebSocket server
│   │   └── protocol.py     # Protocol envelope serialization & validation
│   └── controls/           # Windows action executors
│       ├── __init__.py
│       ├── audio.py        # Windows CoreAudio volume & mute control (pycaw)
│       └── media.py        # Win32 media key simulation & system actions
└── tests/                  # Test suite
    ├── __init__.py
    └── test_protocol.py    # Protocol serialization and handshake tests
```

---

## Execution Environment
- **Runtime**: Python 3.10+ (detected on host: Python 3.14.6)
- **Networking**: Binds to `0.0.0.0:8765` on the local Wi-Fi / Ethernet subnet.
- **Security**: Requires pairing token exchange before streaming data or accepting commands.
