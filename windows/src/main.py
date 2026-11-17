"""SidePanel Windows Companion Entry Point.

Starts the authenticated WebSocket companion service.
"""

from __future__ import annotations

import asyncio
import logging
import signal
import sys

from .core.config import ConfigManager
from .server.ws_server import SidePanelServer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s]: %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
logger = logging.getLogger("sidepanel")


async def main() -> None:
    """Run the companion server until interrupted."""
    config_mgr = ConfigManager()
    cfg = config_mgr.config

    print("=" * 60)
    print(f" SidePanel Windows Companion Service v{cfg.version}")
    print(f" Server Name   : {cfg.server_name}")
    print(f" Listening On  : ws://{cfg.host}:{cfg.port}")
    print(f" Pairing Token : {cfg.pairing_token}")
    print("=" * 60)

    server = SidePanelServer(config_manager=config_mgr)
    await server.start()

    stop_event = asyncio.Event()

    def _signal_handler() -> None:
        logger.info("Shutdown signal received")
        stop_event.set()

    # Register OS signals where available
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, _signal_handler)
        except (NotImplementedError, AttributeError):
            # Windows may not implement signal handlers on ProactorEventLoop
            pass

    try:
        await stop_event.wait()
    except (KeyboardInterrupt, asyncio.CancelledError):
        pass
    finally:
        await server.stop()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nSidePanel Companion stopped.")
