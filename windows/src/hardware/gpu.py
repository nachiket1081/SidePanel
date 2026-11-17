"""GPU Telemetry Collector for Windows.

Collects NVIDIA GPU utilization, VRAM usage, temperature, and fan speeds via NVML.
Provides a graceful fallback when dedicated GPU hardware is absent.
"""

from __future__ import annotations

import logging
import warnings
from typing import Any, Dict, List, Optional

logger = logging.getLogger("sidepanel.hardware.gpu")

# Suppress deprecation notice from pynvml wrapper in favor of nvidia-ml-py
with warnings.catch_warnings():
    warnings.simplefilter("ignore", category=FutureWarning)
    try:
        import pynvml
        NVML_AVAILABLE = True
    except ImportError:
        NVML_AVAILABLE = False


class GPUCollector:
    """Collects GPU telemetry with automatic NVIDIA NVML integration."""

    def __init__(self, device_index: int = 0) -> None:
        self.device_index = device_index
        self._nvml_initialized = False
        self._handle = None
        self._device_name: Optional[str] = None
        self._total_vram_bytes: int = 0

        self._init_nvml()

    def _init_nvml(self) -> None:
        """Attempt to initialize NVML and grab device handle."""
        if not NVML_AVAILABLE:
            logger.info("NVML library not installed; GPU telemetry running in fallback mode.")
            return

        try:
            pynvml.nvmlInit()
            self._nvml_initialized = True
            device_count = pynvml.nvmlDeviceGetCount()
            if device_count > self.device_index:
                self._handle = pynvml.nvmlDeviceGetHandleByIndex(self.device_index)
                raw_name = pynvml.nvmlDeviceGetName(self._handle)
                # Handle bytes vs str in different nvml bindings
                self._device_name = raw_name.decode("utf-8") if isinstance(raw_name, bytes) else str(raw_name)
                mem = pynvml.nvmlDeviceGetMemoryInfo(self._handle)
                self._total_vram_bytes = int(mem.total)
                logger.info("NVML initialized successfully for GPU: %s", self._device_name)
            else:
                logger.warning("NVML device index %d exceeds count %d", self.device_index, device_count)
        except Exception as e:
            logger.warning("Could not initialize NVML: %s", e)
            self._nvml_initialized = False

    @property
    def has_gpu(self) -> bool:
        """Return True if a dedicated GPU is recognized and monitored."""
        return self._nvml_initialized and self._handle is not None

    def collect(self) -> Dict[str, Any]:
        """Collect current GPU metrics.

        Returns:
            Dictionary matching the 'gpu' schema in PROTOCOL.md.
        """
        if not self.has_gpu or self._handle is None:
            return {
                "name": "Integrated / Standard Display Adapter",
                "usagePercent": 0.0,
                "temperatureC": None,
                "hotspotTempC": None,
                "vramUsedBytes": 0,
                "vramTotalBytes": 0,
                "vramUsagePercent": 0.0,
                "fanSpeedPercent": None,
                "powerWatts": None,
                "isDedicatedGpu": False,
            }

        try:
            util = pynvml.nvmlDeviceGetUtilizationRates(self._handle)
            mem = pynvml.nvmlDeviceGetMemoryInfo(self._handle)
            temp = pynvml.nvmlDeviceGetTemperature(self._handle, pynvml.NVML_TEMPERATURE_GPU)

            # Optional fan speed
            fan_speed: Optional[int] = None
            try:
                fan_speed = int(pynvml.nvmlDeviceGetFanSpeed(self._handle))
            except Exception:
                pass

            # Optional power usage
            power_w: Optional[float] = None
            try:
                power_mw = pynvml.nvmlDeviceGetPowerUsage(self._handle)
                power_w = round(power_mw / 1000.0, 1)
            except Exception:
                pass

            vram_total = int(mem.total)
            vram_used = int(mem.used)
            vram_percent = round((vram_used / vram_total * 100.0), 1) if vram_total > 0 else 0.0

            return {
                "name": self._device_name or "NVIDIA GPU",
                "usagePercent": round(float(util.gpu), 1),
                "temperatureC": round(float(temp), 1),
                "hotspotTempC": None,  # Hotspot requires elevated driver queries or LibreHW
                "vramUsedBytes": vram_used,
                "vramTotalBytes": vram_total,
                "vramUsagePercent": vram_percent,
                "fanSpeedPercent": fan_speed,
                "powerWatts": power_w,
                "isDedicatedGpu": True,
            }
        except Exception as e:
            logger.debug("Error reading GPU metrics: %s", e)
            return {
                "name": self._device_name or "NVIDIA GPU (Error)",
                "usagePercent": 0.0,
                "temperatureC": None,
                "hotspotTempC": None,
                "vramUsedBytes": 0,
                "vramTotalBytes": self._total_vram_bytes,
                "vramUsagePercent": 0.0,
                "fanSpeedPercent": None,
                "powerWatts": None,
                "isDedicatedGpu": True,
            }

    def close(self) -> None:
        """Release NVML handles."""
        if self._nvml_initialized:
            try:
                pynvml.nvmlShutdown()
            except Exception:
                pass
            self._nvml_initialized = False
