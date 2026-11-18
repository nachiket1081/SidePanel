"""Mock Hardware Telemetry Generator for Milestone 1 Testing.

Generates realistic mock metrics payloads conforming to PROTOCOL.md.
"""

import math
import time
from typing import Any, Dict


class MockTelemetryGenerator:
    """Generates dynamically varying mock metrics simulating real PC hardware."""

    def __init__(self) -> None:
        self.step = 0

    def generate(self) -> Dict[str, Any]:
        """Generate a complete mock telemetry dictionary."""
        self.step += 1
        t = self.step * 0.1

        # Smooth sine-wave simulated CPU and GPU loads
        cpu_usage = round(20.0 + 15.0 * math.sin(t) + 5.0 * math.cos(t * 1.5), 1)
        cpu_usage = max(5.0, min(95.0, cpu_usage))
        cpu_temp = round(45.0 + (cpu_usage * 0.3), 1)

        gpu_usage = round(35.0 + 25.0 * math.cos(t * 0.8), 1)
        gpu_usage = max(0.0, min(99.0, gpu_usage))
        gpu_temp = round(50.0 + (gpu_usage * 0.25), 1)

        per_core = [
            round(max(2.0, min(98.0, cpu_usage + (math.sin(t + i) * 12.0))), 1)
            for i in range(16)
        ]

        # Varying network rates
        dl_rate = int(1_500_000 + 1_200_000 * math.sin(t * 2))
        ul_rate = int(250_000 + 150_000 * math.cos(t * 2))

        return {
            "cpu": {
                "usagePercent": cpu_usage,
                "frequencyGhz": 4.25,
                "temperatureC": cpu_temp,
                "coreCount": 16,
                "perCoreUsage": per_core,
            },
            "gpu": {
                "name": "NVIDIA GeForce RTX 4080 (Mock)",
                "usagePercent": gpu_usage,
                "temperatureC": gpu_temp,
                "hotspotTempC": round(gpu_temp + 8.5, 1),
                "vramUsedBytes": 6_442_450_944,
                "vramTotalBytes": 17_179_869_184,
                "vramUsagePercent": 37.5,
                "fanSpeedPercent": 45,
                "powerWatts": round(120.0 + (gpu_usage * 1.2), 1),
            },
            "ram": {
                "usedBytes": 17_179_869_184,
                "totalBytes": 34_359_738_368,
                "usagePercent": 50.0,
                "availableBytes": 17_179_869_184,
            },
            "disk": {
                "readBytesPerSec": 1_048_576,
                "writeBytesPerSec": 4_194_304,
                "primaryDrive": {
                    "letter": "C:",
                    "usedPercent": 64.2,
                    "freeBytes": 384_829_067_264,
                },
            },
            "network": {
                "downloadBytesPerSec": max(0, dl_rate),
                "uploadBytesPerSec": max(0, ul_rate),
                "activeInterface": "Ethernet 10Gbps",
            },
            "system": {
                "uptimeSeconds": 14250 + self.step,
            },
            "audio": {
                "masterVolumePercent": 75,
                "isMuted": False,
                "activeOutputDevice": "Speakers (Realtek High Definition Audio)",
            },
        }
