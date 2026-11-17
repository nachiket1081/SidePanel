"""CPU Telemetry Collector for Windows.

Collects overall and per-core utilization, clock frequency, and core topology via psutil.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, List, Optional

import psutil

logger = logging.getLogger("sidepanel.hardware.cpu")


class CPUCollector:
    """Collects CPU hardware metrics."""

    def __init__(self) -> None:
        self.logical_cores: int = psutil.cpu_count(logical=True) or 1
        self.physical_cores: int = psutil.cpu_count(logical=False) or self.logical_cores

        # Initial call to prime psutil's internal interval calculation
        psutil.cpu_percent(interval=None, percpu=True)
        psutil.cpu_percent(interval=None, percpu=False)

    def collect(self) -> Dict[str, Any]:
        """Collect current CPU metrics.

        Returns:
            Dictionary matching the 'cpu' schema in PROTOCOL.md.
        """
        # interval=None calculates utilization since the last call (non-blocking)
        total_usage = psutil.cpu_percent(interval=None, percpu=False)
        per_core = psutil.cpu_percent(interval=None, percpu=True)

        # CPU Frequency
        freq_ghz: Optional[float] = None
        try:
            freq_info = psutil.cpu_freq()
            if freq_info and freq_info.current:
                freq_ghz = round(freq_info.current / 1000.0, 2)
        except Exception as e:
            logger.debug("Failed to read CPU frequency: %s", e)

        # Thermals (often unavailable on non-elevated consumer Windows)
        temperature_c: Optional[float] = None
        if hasattr(psutil, "sensors_temperatures"):
            try:
                temps = psutil.sensors_temperatures()
                if temps:
                    for name in ("coretemp", "k10temp", "cpu_thermal"):
                        if name in temps and temps[name]:
                            temperature_c = float(temps[name][0].current)
                            break
            except Exception:
                pass

        return {
            "usagePercent": round(float(total_usage), 1),
            "frequencyGhz": freq_ghz or 0.0,
            "temperatureC": temperature_c,
            "coreCount": self.logical_cores,
            "physicalCores": self.physical_cores,
            "perCoreUsage": [round(float(u), 1) for u in per_core],
        }
