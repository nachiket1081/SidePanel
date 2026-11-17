"""Unit tests for SidePanel Configuration and Token Management."""

import tempfile
from pathlib import Path

from windows.src.core.config import ConfigManager, ServerConfig


def test_default_config_creation() -> None:
    """Verify default configuration values."""
    cfg = ServerConfig()
    assert cfg.port == 8765
    assert cfg.host == "0.0.0.0"
    assert len(cfg.pairing_token) == 32
    assert cfg.stream_interval_ms == 1000


def test_config_save_and_load() -> None:
    """Verify persisting and reloading configuration from disk."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        cfg_file = Path(tmp_dir) / "test_config.json"
        mgr1 = ConfigManager(config_path=cfg_file)
        token = mgr1.config.pairing_token
        mgr1.config.server_name = "Custom-PC-Name"
        mgr1.save()

        # Reload with new manager
        mgr2 = ConfigManager(config_path=cfg_file)
        assert mgr2.config.server_name == "Custom-PC-Name"
        assert mgr2.config.pairing_token == token


def test_token_validation() -> None:
    """Verify token validation behavior."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        cfg_file = Path(tmp_dir) / "test_config.json"
        mgr = ConfigManager(config_path=cfg_file)

        valid_token = mgr.config.pairing_token
        assert mgr.validate_token(valid_token) is True
        assert mgr.validate_token(f"  {valid_token}  ") is True
        assert mgr.validate_token("wrong-token-value") is False
        assert mgr.validate_token("") is False

        # Regenerate token
        new_token = mgr.regenerate_token()
        assert new_token != valid_token
        assert mgr.validate_token(new_token) is True
        assert mgr.validate_token(valid_token) is False
