"""Disk and Network I/O Telemetry Collector for Windows.

Computes real-time transfer rates (Bytes/sec) and capacity utilization.
"""

from __future__ import annotations

import logging
import os
import time
from typing import Any, Dict, Optional

import psutil

logger = logging.getLogger("sidepanel.hardware.io")


class IOCollector:
    """Collects real-time Disk and Network I/O statistics."""

    def __init__(self, primary_drive_letter: str = "C:") -> None:
        self.primary_drive_letter = primary_drive_letter.rstrip("\\")
        if not self.primary_drive_letter.endswith(":"):
            self.primary_drive_letter += ":"

        self._last_time: float = time.time()
        self._last_disk_read: int = 0
        self._last_disk_write: int = 0
        self._last_net_recv: int = 0
        self._last_net_sent: int = 0

        # Prime the initial counters
        self._prime_counters()

    def _prime_counters(self) -> None:
        """Read initial counter states."""
        try:
            d_io = psutil.disk_io_counters()
            if d_io:
                self._last_disk_read = d_io.read_bytes
                self._last_disk_write = d_io.write_bytes
        except Exception:
            pass

        try:
            n_io = psutil.net_io_counters()
            if n_io:
                self._last_net_recv = n_io.bytes_recv
                self._last_net_sent = n_io.bytes_sent
        except Exception:
            pass

        self._last_time = time.time()

    def _get_active_network_interface(self) -> str:
        """Attempt to find the active non-loopback network interface name."""
        try:
            stats = psutil.net_if_stats()
            addrs = psutil.net_if_addrs()

            # Prefer interfaces that are up and have a non-loopback IPv4 address
            for iface_name, iface_stat in stats.items():
                if iface_stat.isup and "loopback" not in iface_name.lower():
                    if iface_name in addrs:
                        for addr in addrs[iface_name]:
                            if addr.family.name == "AF_INET" and not addr.address.startswith("127."):
                                return iface_name
            # Fallback to the first interface marked UP
            for iface_name, iface_stat in stats.items():
                if iface_stat.isup and "loopback" not in iface_name.lower():
                    return iface_name
        except Exception:
            pass

        return "Default Interface"

    def collect(self) -> Dict[str, Any]:
        """Collect and compute current Disk and Network I/O metrics.

        Returns:
            Dictionary containing 'disk' and 'network' objects matching PROTOCOL.md.
        """
        now = time.time()
        dt = max(0.001, now - self._last_time)

        # 1. Disk I/O Rates
        read_rate = 0
        write_rate = 0
        try:
            d_io = psutil.disk_io_counters()
            if d_io:
                read_delta = max(0, d_io.read_bytes - self._last_disk_read)
                write_delta = max(0, d_io.write_bytes - self._last_disk_write)
                read_rate = int(read_delta / dt)
                write_rate = int(write_delta / dt)
                self._last_disk_read = d_io.read_bytes
                self._last_disk_write = d_io.write_bytes
        except Exception as e:
            logger.debug("Failed to read disk I/O counters: %s", e)

        # Primary Drive Usage
        drive_path = f"{self.primary_drive_letter}\\"
        drive_used_percent = 0.0
        drive_free_bytes = 0
        drive_total_bytes = 0
        try:
            d_usage = psutil.disk_usage(drive_path)
            drive_used_percent = round(float(d_usage.percent), 1)
            drive_free_bytes = int(d_usage.free)
            drive_total_bytes = int(d_usage.total)
        except Exception as e:
            logger.debug("Failed to read disk usage for %s: %s", drive_path, e)

        # 2. Network I/O Rates
        dl_rate = 0
        ul_rate = 0
        try:
            n_io = psutil.net_io_counters()
            if n_io:
                recv_delta = max(0, n_io.bytes_recv - self._last_net_recv)
                sent_delta = max(0, n_io.bytes_sent - self._last_net_sent)
                dl_rate = int(recv_delta / dt)
                ul_rate = int(sent_delta / dt)
                self._last_net_recv = n_io.bytes_recv
                self._last_net_sent = n_io.bytes_sent
        except Exception as e:
            logger.debug("Failed to read network I/O counters: %s", e)

        self._last_time = now

        return {
            "disk": {
                "readBytesPerSec": read_rate,
                "writeBytesPerSec": write_rate,
                "primaryDrive": {
                    "letter": self.primary_drive_letter,
                    "usedPercent": drive_used_percent,
                    "freeBytes": drive_free_bytes,
                    "totalBytes": drive_total_bytes,
                },
            },
            "network": {
                "downloadBytesPerSec": dl_rate,
                "uploadBytesPerSec": ul_rate,
                "activeInterface": self._get_active_network_interface(),
            },
        }
