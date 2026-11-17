"""Hardware Telemetry Aggregator for SidePanel.

Unifies CPU, GPU, RAM, Disk, and Network collectors into a consolidated payload.
"""

from __future__ import annotations

import logging
import time
from typing import Any, Dict, Optional

from .cpu import CPUCollector
from .gpu import GPUCollector
from .io import IOCollector
from .ram import RAMCollector

logger = logging.getLogger("sidepanel.hardware.aggregator")


class HardwareAggregator:
    """Unified hardware metrics aggregator conforming to PROTOCOL.md."""

    def __init__(self, primary_drive: str = "C:", gpu_index: int = 0) -> None:
        self.cpu_collector = CPUCollector()
        self.ram_collector = RAMCollector()
        self.gpu_collector = GPUCollector(device_index=gpu_index)
        self.io_collector = IOCollector(primary_drive_letter=primary_drive)

        self._cached_telemetry: Optional[Dict[str, Any]] = None
        self._last_poll_time: float = 0.0
        self._cache_ttl_seconds: float = 0.05  # 50ms cache to deduplicate bursts

    def collect(self) -> Dict[str, Any]:
        """Collect and return unified telemetry dictionary.

        Returns:
            Dictionary matching the METRICS_STREAM payload schema in PROTOCOL.md.
        """
        now = time.time()
        if self._cached_telemetry is not None and (now - self._last_poll_time) < self._cache_ttl_seconds:
            return self._cached_telemetry

        cpu_data = self.cpu_collector.collect()
        ram_data = self.ram_collector.collect()
        gpu_data = self.gpu_collector.collect()
        io_data = self.io_collector.collect()

        telemetry: Dict[str, Any] = {
            "cpu": cpu_data,
            "ram": ram_data,
            "gpu": gpu_data,
            "disk": io_data["disk"],
            "network": io_data["network"],
            "audio": {
                "masterVolumePercent": 50,
                "isMuted": False,
                "activeOutputDevice": "Default Audio Device",
            },
        }

        self._cached_telemetry = telemetry
        self._last_poll_time = now
        return telemetry

    def close(self) -> None:
        """Release any hardware handles (such as NVML)."""
        self.gpu_collector.close()
