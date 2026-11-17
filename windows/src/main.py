"""SidePanel Windows Companion Entry Point.

Starts the authenticated WebSocket companion service.
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import signal
import sys
import threading

from .core.config import ConfigManager
from .core.pairing import build_pairing_uri, get_local_ip, print_ascii_qr, show_gui_qr_dialog
from .server.ws_server import SidePanelServer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s]: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("sidepanel")


async def main(open_gui: bool = False) -> None:
    """Run the companion server until interrupted."""
    config_mgr = ConfigManager()
    cfg = config_mgr.config

    local_ip = get_local_ip()
    pairing_uri = build_pairing_uri(local_ip, cfg.port, cfg.pairing_token)

    print("=" * 60)
    print(f" SidePanel Windows Companion Service v{cfg.version}")
    print(f" Server Name   : {cfg.server_name}")
    print(f" Local IP      : {local_ip} (port {cfg.port})")
    print(f" Listening On  : ws://0.0.0.0:{cfg.port}")
    print(f" Pairing Token : {cfg.pairing_token}")
    print(f" Pairing URI   : {pairing_uri}")
    print("=" * 60)

    # Print scannable ASCII QR code to terminal
    print_ascii_qr(pairing_uri)

    if open_gui:
        # Launch GUI popup in background thread so asyncio event loop is not blocked
        threading.Thread(
            target=show_gui_qr_dialog,
            args=(pairing_uri, local_ip, cfg.port, cfg.pairing_token),
            daemon=True,
        ).start()

    server = SidePanelServer(config_manager=config_mgr)
    await server.start()

    stop_event = asyncio.Event()

    def _signal_handler() -> None:
        logger.info("Shutdown signal received")
        stop_event.set()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, _signal_handler)
        except (NotImplementedError, AttributeError):
            pass

    try:
        await stop_event.wait()
    except (KeyboardInterrupt, asyncio.CancelledError):
        pass
    finally:
        await server.stop()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="SidePanel Companion Server")
    parser.add_argument("--gui", action="store_true", help="Display pairing QR code in desktop GUI window")
    args = parser.parse_args()

    try:
        asyncio.run(main(open_gui=args.gui))
    except KeyboardInterrupt:
        print("\nSidePanel Companion stopped.")
