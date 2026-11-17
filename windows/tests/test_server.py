"""Integration tests for SidePanel WebSocket Server and Authentication."""

import asyncio
import json
import tempfile
from pathlib import Path
from typing import AsyncGenerator

import pytest
import pytest_asyncio
from websockets.asyncio.client import ClientConnection, connect
from websockets.exceptions import ConnectionClosed

from windows.src.core.config import ConfigManager
from windows.src.server.protocol import (
    MessageEnvelope,
    MessageType,
)
from windows.src.server.ws_server import SidePanelServer

TEST_PORT = 18765  # Use high ephemeral port for testing


@pytest_asyncio.fixture
async def running_server() -> AsyncGenerator[SidePanelServer, None]:
    """Fixture providing a running SidePanelServer on test port."""
    with tempfile.TemporaryDirectory() as tmp_dir:
        cfg_file = Path(tmp_dir) / "test_config.json"
        config_mgr = ConfigManager(config_path=cfg_file)
        config_mgr.config.port = TEST_PORT
        config_mgr.config.host = "127.0.0.1"
        config_mgr.config.auth_timeout_seconds = 2.0
        config_mgr.save()

        server = SidePanelServer(config_manager=config_mgr)
        await server.start()
        try:
            yield server
        finally:
            await server.stop()


@pytest.mark.asyncio
async def test_auth_success(running_server: SidePanelServer) -> None:
    """Test successful client authentication with valid pairing token."""
    token = running_server.config_manager.config.pairing_token
    uri = f"ws://127.0.0.1:{TEST_PORT}"

    async with connect(uri) as client:
        # Send AUTH_REQUEST
        auth_req = MessageEnvelope(
            id="auth-test-1",
            type=MessageType.AUTH_REQUEST.value,
            payload={"token": token, "deviceName": "Test Tablet"},
        )
        await client.send(auth_req.to_json())

        # Receive AUTH_RESPONSE
        raw_resp = await client.recv()
        resp = MessageEnvelope.from_json(str(raw_resp))

        assert resp.type == MessageType.AUTH_RESPONSE.value
        assert resp.payload["status"] == "SUCCESS"
        assert "sessionToken" in resp.payload
        assert resp.payload["serverName"] == running_server.config.server_name


@pytest.mark.asyncio
async def test_auth_invalid_token_rejected(running_server: SidePanelServer) -> None:
    """Test that connections with invalid tokens are rejected and closed."""
    uri = f"ws://127.0.0.1:{TEST_PORT}"

    async with connect(uri) as client:
        # Send AUTH_REQUEST with bad token
        auth_req = MessageEnvelope(
            id="auth-bad-1",
            type=MessageType.AUTH_REQUEST.value,
            payload={"token": "completely-invalid-token", "deviceName": "Rogue Device"},
        )
        await client.send(auth_req.to_json())

        # Receive AUTH_RESPONSE with UNAUTHORIZED status
        raw_resp = await client.recv()
        resp = MessageEnvelope.from_json(str(raw_resp))
        assert resp.type == MessageType.AUTH_RESPONSE.value
        assert resp.payload["status"] == "UNAUTHORIZED"

        # The server should now close the socket with 4001
        with pytest.raises(ConnectionClosed) as exc_info:
            await client.recv()
        assert exc_info.value.rcvd.code == 4001


@pytest.mark.asyncio
async def test_non_auth_first_message_rejected(running_server: SidePanelServer) -> None:
    """Test that sending a command or ping before authenticating results in rejection."""
    uri = f"ws://127.0.0.1:{TEST_PORT}"

    async with connect(uri) as client:
        ping_msg = MessageEnvelope(
            id="ping-early",
            type=MessageType.PING.value,
            payload={},
        )
        await client.send(ping_msg.to_json())

        raw_resp = await client.recv()
        resp = MessageEnvelope.from_json(str(raw_resp))
        assert resp.type == MessageType.ERROR_NOTIFICATION.value
        assert resp.payload["code"] == "UNAUTHORIZED"

        with pytest.raises(ConnectionClosed):
            await client.recv()


@pytest.mark.asyncio
async def test_ping_pong_exchange(running_server: SidePanelServer) -> None:
    """Test ping-pong keep-alive exchange after authenticating."""
    token = running_server.config_manager.config.pairing_token
    uri = f"ws://127.0.0.1:{TEST_PORT}"

    async with connect(uri) as client:
        # 1. Authenticate
        auth_req = MessageEnvelope(
            id="auth-1",
            type=MessageType.AUTH_REQUEST.value,
            payload={"token": token},
        )
        await client.send(auth_req.to_json())
        await client.recv()  # Auth response

        # 2. Send PING
        ping = MessageEnvelope(
            id="ping-test-1",
            type=MessageType.PING.value,
            payload={"clientTimestamp": 123456789},
        )
        await client.send(ping.to_json())

        # 3. Receive PONG
        raw_pong = await client.recv()
        pong = MessageEnvelope.from_json(str(raw_pong))
        assert pong.type == MessageType.PONG.value
        assert pong.payload["echoTimestamp"] == 123456789


@pytest.mark.asyncio
async def test_metrics_subscription_stream(running_server: SidePanelServer) -> None:
    """Test subscribing to metrics and receiving METRICS_STREAM packets."""
    token = running_server.config_manager.config.pairing_token
    uri = f"ws://127.0.0.1:{TEST_PORT}"

    async with connect(uri) as client:
        # Authenticate
        auth_req = MessageEnvelope(
            id="auth-1",
            type=MessageType.AUTH_REQUEST.value,
            payload={"token": token},
        )
        await client.send(auth_req.to_json())
        await client.recv()

        # Subscribe to metrics at 150ms interval
        sub_req = MessageEnvelope(
            id="sub-1",
            type=MessageType.SUBSCRIBE_METRICS.value,
            payload={"intervalMs": 150, "metrics": ["cpu", "gpu", "ram"]},
        )
        await client.send(sub_req.to_json())

        # Receive streamed metrics
        raw_metric = await asyncio.wait_for(client.recv(), timeout=2.0)
        metric_msg = MessageEnvelope.from_json(str(raw_metric))
        assert metric_msg.type == MessageType.METRICS_STREAM.value
        assert "cpu" in metric_msg.payload
        assert "gpu" in metric_msg.payload
        assert "ram" in metric_msg.payload
        assert "usagePercent" in metric_msg.payload["cpu"]


@pytest.mark.asyncio
async def test_command_execution_ack(running_server: SidePanelServer) -> None:
    """Test sending a COMMAND_EXECUTE and receiving COMMAND_ACK."""
    token = running_server.config_manager.config.pairing_token
    uri = f"ws://127.0.0.1:{TEST_PORT}"

    async with connect(uri) as client:
        # Authenticate
        auth_req = MessageEnvelope(
            id="auth-1",
            type=MessageType.AUTH_REQUEST.value,
            payload={"token": token},
        )
        await client.send(auth_req.to_json())
        await client.recv()

        # Send command
        cmd = MessageEnvelope(
            id="cmd-test-1",
            type=MessageType.COMMAND_EXECUTE.value,
            payload={"action": "AUDIO_SET_VOLUME", "params": {"volumePercent": 50}},
        )
        await client.send(cmd.to_json())

        # Receive ACK
        raw_ack = await client.recv()
        ack = MessageEnvelope.from_json(str(raw_ack))
        assert ack.type == MessageType.COMMAND_ACK.value
        assert ack.payload["commandId"] == "cmd-test-1"
        assert ack.payload["success"] is True
