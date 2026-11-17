"""SidePanel Pairing & QR Code Discovery Utilities.

Discovers local network addresses and renders ASCII/GUI QR codes for pairing.
"""

from __future__ import annotations

import io
import logging
import socket
import sys
from typing import Optional

import qrcode
from PIL import Image

logger = logging.getLogger("sidepanel.core.pairing")


def get_local_ip() -> str:
    """Detect the host's primary reachable local IPv4 address."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # Connect to a public DNS IP (no packet is actually sent over UDP)
        s.connect(("8.8.8.8", 80))
        local_ip = s.getsockname()[0]
    except Exception:
        local_ip = "127.0.0.1"
    finally:
        s.close()
    return local_ip


def build_pairing_uri(ip: str, port: int, token: str) -> str:
    """Format the standard SidePanel pairing URI."""
    return f"sidepanel://{ip}:{port}?token={token}"


def print_ascii_qr(uri: str) -> None:
    """Print an ASCII QR code directly into the terminal console."""
    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_L,
        box_size=1,
        border=2,
    )
    qr.add_data(uri)
    qr.make(fit=True)

    print("\n" + "=" * 50)
    print(" SCAN WITH SIDEPANEL ANDROID APP TO PAIR:")
    print("=" * 50)
    f = io.StringIO()
    qr.print_ascii(out=f, invert=True)
    print(f.getvalue())
    print("=" * 50)
    print(f" Pairing URI : {uri}")
    print("=" * 50 + "\n")


def generate_qr_image(uri: str) -> Image.Image:
    """Generate a high-resolution PIL Image of the QR code."""
    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=10,
        border=4,
    )
    qr.add_data(uri)
    qr.make(fit=True)
    return qr.make_image(fill_color="#0D1117", back_color="#FFFFFF").convert("RGB")


def show_gui_qr_dialog(uri: str, ip: str, port: int, token: str) -> None:
    """Display a native desktop window showing the QR code and connection info."""
    import tkinter as tk
    from PIL import ImageTk

    root = tk.Tk()
    root.title("SidePanel - Pair Android Device")
    root.geometry("420x560")
    root.configure(bg="#0D1117")
    root.resizable(False, False)

    # Title
    lbl_title = tk.Label(
        root,
        text="SidePanel Device Pairing",
        font=("Segoe UI", 16, "bold"),
        fg="#FFFFFF",
        bg="#0D1117",
    )
    lbl_title.pack(pady=(16, 4))

    lbl_subtitle = tk.Label(
        root,
        text="Scan this QR code with the SidePanel Android app",
        font=("Segoe UI", 10),
        fg="#8B949E",
        bg="#0D1117",
    )
    lbl_subtitle.pack(pady=(0, 12))

    # QR Code Image
    img = generate_qr_image(uri)
    img = img.resize((300, 300), Image.Resampling.LANCZOS)
    photo = ImageTk.PhotoImage(img)

    qr_label = tk.Label(root, image=photo, bg="#FFFFFF", bd=2, relief="solid")
    qr_label.image = photo  # Keep reference
    qr_label.pack(pady=8)

    # Info Labels
    info_frame = tk.Frame(root, bg="#161B22", bd=1, relief="flat", padx=12, pady=8)
    info_frame.pack(fill="x", padx=24, pady=12)

    lbl_ip = tk.Label(
        info_frame,
        text=f"Server IP: {ip}  |  Port: {port}",
        font=("Consolas", 10, "bold"),
        fg="#58A6FF",
        bg="#161B22",
    )
    lbl_ip.pack(anchor="w")

    lbl_token = tk.Label(
        info_frame,
        text=f"Token: {token[:12]}... (32 chars)",
        font=("Consolas", 9),
        fg="#8B949E",
        bg="#161B22",
    )
    lbl_token.pack(anchor="w")

    btn_close = tk.Button(
        root,
        text="Close",
        font=("Segoe UI", 10),
        bg="#21262D",
        fg="#FFFFFF",
        activebackground="#30363D",
        activeforeground="#FFFFFF",
        bd=0,
        padx=20,
        pady=6,
        command=root.destroy,
    )
    btn_close.pack(pady=8)

    root.mainloop()
