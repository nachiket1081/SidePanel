"""SidePanel Sample Test WebSocket Client.

Connects to the SidePanel Windows companion server over WebSocket,
authenticates with the pairing token from config.json, and prints incoming live telemetry packets.
"""

from __future__ import annotations

import asyncio
import json
import os
import sys

# Ensure project root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from websockets.asyncio.client import connect
from windows.src.core.config import ConfigManager
from windows.src.server.protocol import MessageEnvelope, MessageType


async def main() -> None:
    config_mgr = ConfigManager()
    cfg = config_mgr.config
    uri = f"ws://127.0.0.1:{cfg.port}"

    print(f"Connecting to SidePanel Server at {uri}...")
    try:
        async with connect(uri) as ws:
            # 1. Send AUTH_REQUEST
            print(f"Authenticating with token: {cfg.pairing_token[:8]}...")
            auth_msg = MessageEnvelope(
                id="client-auth-1",
                type=MessageType.AUTH_REQUEST.value,
                payload={
                    "token": cfg.pairing_token,
                    "deviceName": "PC Test Client",
                    "clientVersion": "1.0.0",
                },
            )
            await ws.send(auth_msg.to_json())

            # 2. Receive AUTH_RESPONSE
            auth_resp_raw = await ws.recv()
            auth_resp = MessageEnvelope.from_json(str(auth_resp_raw))
            print(f"Auth Response: {auth_resp.payload.get('status')} (Server: {auth_resp.payload.get('serverName')})")

            if auth_resp.payload.get("status") != "SUCCESS":
                print("Authentication rejected. Exiting.")
                return

            # 3. Subscribe to real hardware metrics (1000ms interval)
            print("Subscribing to hardware metrics stream (1000ms interval)...")
            sub_msg = MessageEnvelope(
                id="client-sub-1",
                type=MessageType.SUBSCRIBE_METRICS.value,
                payload={
                    "intervalMs": 1000,
                    "metrics": ["cpu", "gpu", "ram", "disk", "network", "audio"],
                },
            )
            await ws.send(sub_msg.to_json())

            print("Streaming live telemetry packets (Press Ctrl+C to stop):\n")
            packet_count = 0
            while True:
                raw_metric = await ws.recv()
                packet_count += 1
                msg = MessageEnvelope.from_json(str(raw_metric))
                p = msg.payload

                cpu_pct = p.get("cpu", {}).get("usagePercent", 0)
                gpu_name = p.get("gpu", {}).get("name", "N/A")
                gpu_pct = p.get("gpu", {}).get("usagePercent", 0)
                gpu_temp = p.get("gpu", {}).get("temperatureC", "N/A")
                ram_pct = p.get("ram", {}).get("usagePercent", 0)
                disk_read = p.get("disk", {}).get("readBytesPerSec", 0) / 1024
                disk_write = p.get("disk", {}).get("writeBytesPerSec", 0) / 1024
                net_down = p.get("network", {}).get("downloadBytesPerSec", 0) / 1024

                print(
                    f"[Packet #{packet_count}] "
                    f"CPU: {cpu_pct:4.1f}% | "
                    f"RAM: {ram_pct:4.1f}% | "
                    f"GPU ({gpu_name}): {gpu_pct:4.1f}% ({gpu_temp}°C) | "
                    f"Disk: R {disk_read:5.1f} KB/s W {disk_write:5.1f} KB/s | "
                    f"Net: {net_down:5.1f} KB/s"
                )
    except ConnectionRefusedError:
        print(f"\n[ERROR] Could not connect to {uri}.")
        print("Please ensure the companion server is running first:")
        print("   .\\windows\\.venv\\Scripts\\python.exe -m windows.src.main\n")
    except (KeyboardInterrupt, asyncio.CancelledError):
        print("\nTest client disconnected.")


if __name__ == "__main__":
    asyncio.run(main())
