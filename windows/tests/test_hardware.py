"""Unit tests for SidePanel Hardware Telemetry Engine."""

import time
from windows.src.hardware.cpu import CPUCollector
from windows.src.hardware.ram import RAMCollector
from windows.src.hardware.gpu import GPUCollector
from windows.src.hardware.io import IOCollector
from windows.src.hardware.aggregator import HardwareAggregator


def test_cpu_collector() -> None:
    """Test CPU telemetry collector output."""
    collector = CPUCollector()
    data = collector.collect()

    assert "usagePercent" in data
    assert 0.0 <= data["usagePercent"] <= 100.0
    assert "coreCount" in data
    assert data["coreCount"] >= 1
    assert "perCoreUsage" in data
    assert len(data["perCoreUsage"]) == data["coreCount"]
    for core_usage in data["perCoreUsage"]:
        assert 0.0 <= core_usage <= 100.0
    assert "frequencyGhz" in data
    assert isinstance(data["frequencyGhz"], (int, float))


def test_ram_collector() -> None:
    """Test RAM telemetry collector output."""
    collector = RAMCollector()
    data = collector.collect()

    assert "usedBytes" in data
    assert "totalBytes" in data
    assert "usagePercent" in data
    assert "availableBytes" in data

    assert data["totalBytes"] > 0
    assert data["usedBytes"] > 0
    assert 0.0 <= data["usagePercent"] <= 100.0
    assert data["usedBytes"] + data["availableBytes"] <= data["totalBytes"] * 1.05


def test_gpu_collector() -> None:
    """Test GPU telemetry collector output."""
    collector = GPUCollector()
    try:
        data = collector.collect()
        assert "name" in data
        assert "usagePercent" in data
        assert "vramUsedBytes" in data
        assert "vramTotalBytes" in data
        assert "vramUsagePercent" in data
        assert "isDedicatedGpu" in data

        if data["isDedicatedGpu"]:
            assert 0.0 <= data["usagePercent"] <= 100.0
            assert data["vramTotalBytes"] > 0
            assert 0.0 <= data["vramUsagePercent"] <= 100.0
            if data["temperatureC"] is not None:
                assert data["temperatureC"] > 0
    finally:
        collector.close()


def test_io_collector() -> None:
    """Test Disk and Network I/O collector output."""
    collector = IOCollector(primary_drive_letter="C:")
    time.sleep(0.05)
    data = collector.collect()

    assert "disk" in data
    assert "network" in data

    disk = data["disk"]
    assert "readBytesPerSec" in disk
    assert "writeBytesPerSec" in disk
    assert disk["readBytesPerSec"] >= 0
    assert disk["writeBytesPerSec"] >= 0
    assert "primaryDrive" in disk
    assert disk["primaryDrive"]["letter"] == "C:"
    assert 0.0 <= disk["primaryDrive"]["usedPercent"] <= 100.0
    assert disk["primaryDrive"]["freeBytes"] > 0

    net = data["network"]
    assert "downloadBytesPerSec" in net
    assert "uploadBytesPerSec" in net
    assert net["downloadBytesPerSec"] >= 0
    assert net["uploadBytesPerSec"] >= 0
    assert "activeInterface" in net


def test_hardware_aggregator() -> None:
    """Test consolidated hardware aggregator conforming to PROTOCOL.md."""
    aggregator = HardwareAggregator()
    try:
        telemetry = aggregator.collect()

        assert "cpu" in telemetry
        assert "ram" in telemetry
        assert "gpu" in telemetry
        assert "disk" in telemetry
        assert "network" in telemetry
        assert "audio" in telemetry

        # Verify caching mechanism
        t1 = aggregator.collect()
        assert t1 is telemetry  # Object reference equality due to < 50ms cache TTL
    finally:
        aggregator.close()
