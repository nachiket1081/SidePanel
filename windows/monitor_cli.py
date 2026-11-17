"""SidePanel Live Terminal Hardware Monitor.

Connects to the SidePanel server (or probes hardware directly) and prints
a continuously updating live hardware dashboard in the terminal.
"""

from __future__ import annotations

import os
import sys
import time

# Ensure project root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from windows.src.hardware.aggregator import HardwareAggregator


def render_bar(percent: float, length: int = 20) -> str:
    """Render a text-based progress bar."""
    filled = int(round(length * percent / 100.0))
    filled = max(0, min(length, filled))
    return f"[{'#' * filled}{'-' * (length - filled)}] {percent:5.1f}%"


def format_bytes(b: int) -> str:
    """Format bytes into readable units."""
    if b >= 1024**3:
        return f"{b / (1024**3):.2f} GB"
    if b >= 1024**2:
        return f"{b / (1024**2):.1f} MB"
    if b >= 1024:
        return f"{b / 1024:.1f} KB"
    return f"{b} B"


def main() -> None:
    aggregator = HardwareAggregator()
    print("Initializing SidePanel Hardware Sensors... (Press Ctrl+C to stop)")
    time.sleep(1.0)

    try:
        while True:
            # Clear screen for Windows PowerShell/CMD
            os.system("cls" if os.name == "nt" else "clear")

            t = aggregator.collect()
            cpu = t["cpu"]
            ram = t["ram"]
            gpu = t["gpu"]
            disk = t["disk"]
            net = t["network"]

            print("=" * 66)
            print("       SIDEPANEL HARDWARE TELEMETRY LIVE MONITOR (MILESTONE 2)     ")
            print("=" * 66)

            # CPU
            freq_str = f"{cpu['frequencyGhz']} GHz" if cpu.get("frequencyGhz") else "N/A"
            temp_str = f"{cpu['temperatureC']} °C" if cpu.get("temperatureC") else "N/A"
            print(f"\n[CPU]  Load: {render_bar(cpu['usagePercent'])}  Freq: {freq_str}  Temp: {temp_str}")
            print(f"       Topology: {cpu.get('physicalCores', '?')} Physical / {cpu['coreCount']} Logical Cores")

            # Per-core preview (first 8 or all)
            cores = cpu.get("perCoreUsage", [])
            core_line = "       Cores: " + " ".join(f"[{i}:{int(c)}%]" for i, c in enumerate(cores[:8]))
            print(core_line)
            if len(cores) > 8:
                print("              " + " ".join(f"[{i}:{int(c)}%]" for i, c in enumerate(cores[8:], start=8)))

            # RAM
            ram_used = format_bytes(ram["usedBytes"])
            ram_total = format_bytes(ram["totalBytes"])
            print(f"\n[RAM]  Usage: {render_bar(ram['usagePercent'])}  ({ram_used} / {ram_total})")

            # GPU
            gpu_name = gpu.get("name", "Unknown GPU")
            gpu_temp = f"{gpu['temperatureC']} °C" if gpu.get("temperatureC") is not None else "N/A"
            fan_str = f"{gpu['fanSpeedPercent']} %" if gpu.get("fanSpeedPercent") is not None else "N/A"
            power_str = f"{gpu['powerWatts']} W" if gpu.get("powerWatts") is not None else "N/A"
            vram_used = format_bytes(gpu["vramUsedBytes"])
            vram_total = format_bytes(gpu["vramTotalBytes"])

            print(f"\n[GPU]  Device: {gpu_name}")
            print(f"       Core : {render_bar(gpu['usagePercent'])}  Temp: {gpu_temp}  Fan: {fan_str}")
            print(f"       VRAM : {render_bar(gpu['vramUsagePercent'])}  ({vram_used} / {vram_total})  Power: {power_str}")

            # DISK & NETWORK
            read_str = f"{format_bytes(disk['readBytesPerSec'])}/s"
            write_str = f"{format_bytes(disk['writeBytesPerSec'])}/s"
            drive = disk.get("primaryDrive", {})
            drive_free = format_bytes(drive.get("freeBytes", 0))

            print(f"\n[DISK] Drive {drive.get('letter', 'C:')} {render_bar(drive.get('usedPercent', 0))}  Free: {drive_free}")
            print(f"       Activity: Read {read_str} | Write {write_str}")

            dl_str = f"{format_bytes(net['downloadBytesPerSec'])}/s"
            ul_str = f"{format_bytes(net['uploadBytesPerSec'])}/s"
            print(f"\n[NET]  Interface: {net.get('activeInterface', 'Unknown')}")
            print(f"       Speed: Down {dl_str} | Up {ul_str}")

            print("\n" + "=" * 66)
            print(" Press Ctrl+C in this terminal to exit.")

            time.sleep(1.0)
    except KeyboardInterrupt:
        print("\nMonitor stopped.")
    finally:
        aggregator.close()


if __name__ == "__main__":
    main()
