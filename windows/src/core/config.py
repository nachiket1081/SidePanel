"""SidePanel Configuration & Security Manager.

Manages host settings, pairing tokens, and network configurations.
"""

from __future__ import annotations

import json
import os
import secrets
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Dict


@dataclass
class ServerConfig:
    """Server configuration data model."""

    version: str = "1.0.0"
    host: str = "0.0.0.0"
    port: int = 8765
    server_name: str = "SidePanel-Host"
    pairing_token: str = field(default_factory=lambda: secrets.token_hex(16))
    stream_interval_ms: int = 1000
    max_connections: int = 5
    auth_timeout_seconds: float = 5.0
    ping_timeout_seconds: float = 15.0

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> ServerConfig:
        """Create a ServerConfig from a dictionary, keeping defaults for missing keys."""
        valid_keys = {f.name for f in cls.__dataclass_fields__.values()}  # type: ignore
        filtered = {k: v for k, v in data.items() if k in valid_keys}
        return cls(**filtered)


class ConfigManager:
    """Handles loading, persisting, and token validation for SidePanel."""

    def __init__(self, config_path: Path | str | None = None) -> None:
        if config_path is None:
            # Default to windows/config.json in workspace
            base_dir = Path(__file__).resolve().parent.parent.parent
            self.config_path = base_dir / "config.json"
        else:
            self.config_path = Path(config_path)

        self.config = self.load()

    def load(self) -> ServerConfig:
        """Load configuration from disk or create a fresh default configuration."""
        if self.config_path.exists():
            try:
                with open(self.config_path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    return ServerConfig.from_dict(data)
            except Exception:
                # If corrupted, fall back to fresh config
                pass

        cfg = ServerConfig()
        self.save(cfg)
        return cfg

    def save(self, cfg: ServerConfig | None = None) -> None:
        """Persist configuration to disk."""
        if cfg is not None:
            self.config = cfg

        self.config_path.parent.mkdir(parents=True, exist_ok=True)
        with open(self.config_path, "w", encoding="utf-8") as f:
            json.dump(asdict(self.config), f, indent=2)

    def regenerate_token(self) -> str:
        """Generate, save, and return a new pairing token."""
        self.config.pairing_token = secrets.token_hex(16)
        self.save()
        return self.config.pairing_token

    def validate_token(self, candidate_token: str) -> bool:
        """Constant-time token validation to prevent timing attacks."""
        if not candidate_token:
            return False
        return secrets.compare_digest(self.config.pairing_token, candidate_token.strip())
