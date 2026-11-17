"""RAM and Memory Telemetry Collector for Windows.

Collects physical memory and pagefile/swap metrics via psutil.
"""

from __future__ import annotations

import logging
from typing import Any, Dict

import psutil

logger = logging.getLogger("sidepanel.hardware.ram")


class RAMCollector:
    """Collects system memory and swap statistics."""

    def collect(self) -> Dict[str, Any]:
        """Collect current RAM metrics.

        Returns:
            Dictionary matching the 'ram' schema in PROTOCOL.md.
        """
        vm = psutil.virtual_memory()
        swap = psutil.swap_memory()

        return {
            "usedBytes": int(vm.used),
            "totalBytes": int(vm.total),
            "usagePercent": round(float(vm.percent), 1),
            "availableBytes": int(vm.available),
            "swapUsedBytes": int(swap.used),
            "swapTotalBytes": int(swap.total),
            "swapPercent": round(float(swap.percent), 1),
        }
