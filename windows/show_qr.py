"""Displays the SidePanel pairing QR code in a GUI window."""

import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from windows.src.core.config import ConfigManager
from windows.src.core.pairing import build_pairing_uri, get_local_ip, show_gui_qr_dialog

if __name__ == "__main__":
    cfg = ConfigManager().config
    ip = get_local_ip()
    uri = build_pairing_uri(ip, cfg.port, cfg.pairing_token)
    print(f"Opening QR code dialog for {uri}...")
    show_gui_qr_dialog(uri, ip, cfg.port, cfg.pairing_token)
