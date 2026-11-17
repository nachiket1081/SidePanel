"""SidePanel WebSocket Server.

Implements the RFC 6455 authenticated WebSocket server for local communications.
"""

from __future__ import annotations

import asyncio
import logging
import time
import uuid
from dataclasses import dataclass, field
from typing import Any, Callable, Dict, Optional, Set

from websockets.asyncio.server import Server, ServerConnection, serve

from ..core.config import ConfigManager, ServerConfig
from .mock_telemetry import MockTelemetryGenerator
from .protocol import (
    ErrorCode,
    MessageEnvelope,
    MessageType,
    ProtocolError,
    create_auth_response,
    create_command_ack,
    create_error_notification,
    create_metrics_stream,
    create_pong,
)

logger = logging.getLogger("sidepanel.server")


@dataclass
class ClientSession:
    """Represents an active client connection."""

    connection: ServerConnection
    remote_ip: str
    session_id: str = field(default_factory=lambda: f"sess-{uuid.uuid4().hex[:12]}")
    authenticated: bool = False
    device_name: str = "Unknown"
    client_version: str = "Unknown"
    connected_at: float = field(default_factory=time.time)
    last_activity: float = field(default_factory=time.time)
    interval_ms: int = 1000
    subscribed_metrics: Set[str] = field(default_factory=set)
    streaming_task: Optional[asyncio.Task[None]] = None


class SidePanelServer:
    """High-performance asynchronous WebSocket server for SidePanel."""

    def __init__(
        self,
        config_manager: Optional[ConfigManager] = None,
        telemetry_provider: Optional[Callable[[], Dict[str, Any]]] = None,
    ) -> None:
        self.config_manager = config_manager or ConfigManager()
        self.config: ServerConfig = self.config_manager.config
        self.telemetry_provider = telemetry_provider or MockTelemetryGenerator().generate

        self._server: Optional[Server] = None
        self._running = False
        self._sessions: Dict[ServerConnection, ClientSession] = {}
        self._lock = asyncio.Lock()

    @property
    def is_running(self) -> bool:
        """Return True if the server is currently running."""
        return self._running and self._server is not None

    @property
    def active_sessions_count(self) -> int:
        """Return number of currently active client connections."""
        return len(self._sessions)

    async def start(self) -> None:
        """Start the WebSocket server."""
        if self._running:
            return

        self._running = True
        logger.info(
            "Starting SidePanel WebSocket server on %s:%d...",
            self.config.host,
            self.config.port,
        )

        self._server = await serve(
            self._handle_connection,
            self.config.host,
            self.config.port,
            ping_interval=None,  # We manage our own application-level ping/pong
        )
        logger.info("SidePanel WebSocket server listening on port %d", self.config.port)

    async def stop(self) -> None:
        """Stop the WebSocket server and disconnect all clients cleanly."""
        if not self._running:
            return

        logger.info("Stopping SidePanel WebSocket server...")
        self._running = False

        # Disconnect all active client sessions
        async with self._lock:
            sessions = list(self._sessions.values())
            for session in sessions:
                if session.streaming_task and not session.streaming_task.done():
                    session.streaming_task.cancel()

                try:
                    shutdown_msg = create_error_notification(
                        ErrorCode.SERVER_SHUTTING_DOWN,
                        "Companion server is stopping",
                    )
                    await session.connection.send(shutdown_msg.to_json())
                    await session.connection.close(1001, "Server shutdown")
                except Exception:
                    pass
            self._sessions.clear()

        if self._server:
            self._server.close()
            await self._server.wait_closed()
            self._server = None

        logger.info("SidePanel WebSocket server stopped.")

    async def _handle_connection(self, connection: ServerConnection) -> None:
        """Handle individual client connection lifecycle."""
        remote_ip = "unknown"
        try:
            remote_ip = str(connection.remote_address[0])
        except Exception:
            pass

        # Check maximum connections limit
        if len(self._sessions) >= self.config.max_connections:
            logger.warning("Rejected connection from %s: max connections exceeded", remote_ip)
            err = create_error_notification(
                ErrorCode.RATE_LIMITED,
                "Maximum client connections reached",
            )
            await connection.send(err.to_json())
            await connection.close(4029, "Max connections reached")
            return

        session = ClientSession(connection=connection, remote_ip=remote_ip)
        async with self._lock:
            self._sessions[connection] = session

        logger.info("New connection established from %s (Session: %s)", remote_ip, session.session_id)

        try:
            # 1. Wait for AUTH_REQUEST within timeout
            authenticated = await self._wait_for_auth(session)
            if not authenticated:
                return

            # 2. Main message processing loop
            async for raw_message in connection:
                session.last_activity = time.time()
                await self._process_message(session, str(raw_message))

        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.error("Error in connection handler for %s: %s", remote_ip, e)
        finally:
            await self._cleanup_session(session)

    async def _wait_for_auth(self, session: ClientSession) -> bool:
        """Wait for the initial AUTH_REQUEST from the client."""
        try:
            raw_msg = await asyncio.wait_for(
                session.connection.recv(),
                timeout=self.config.auth_timeout_seconds,
            )
            session.last_activity = time.time()
            envelope = MessageEnvelope.from_json(str(raw_msg))

            if envelope.type != MessageType.AUTH_REQUEST.value:
                err = create_error_notification(
                    ErrorCode.UNAUTHORIZED,
                    "First message must be AUTH_REQUEST",
                    ref_id=envelope.id,
                )
                await session.connection.send(err.to_json())
                await session.connection.close(4001, "Expected AUTH_REQUEST")
                return False

            candidate_token = str(envelope.payload.get("token", ""))
            session.device_name = str(envelope.payload.get("deviceName", "Unknown Client"))
            session.client_version = str(envelope.payload.get("clientVersion", "Unknown"))

            if not self.config_manager.validate_token(candidate_token):
                logger.warning(
                    "Authentication failed for %s (%s): invalid token",
                    session.remote_ip,
                    session.device_name,
                )
                resp = create_auth_response(
                    request_id=envelope.id,
                    status="UNAUTHORIZED",
                    server_name=self.config.server_name,
                    server_version=self.config.version,
                    error="Invalid pairing token",
                )
                await session.connection.send(resp.to_json())
                await session.connection.close(4001, "Invalid token")
                return False

            # Authentication succeeded
            session.authenticated = True
            logger.info(
                "Client authenticated: %s (%s) [Session: %s]",
                session.device_name,
                session.remote_ip,
                session.session_id,
            )

            resp = create_auth_response(
                request_id=envelope.id,
                status="SUCCESS",
                server_name=self.config.server_name,
                server_version=self.config.version,
                session_token=session.session_id,
            )
            await session.connection.send(resp.to_json())
            return True

        except asyncio.TimeoutError:
            logger.warning("Authentication timeout for %s", session.remote_ip)
            err = create_error_notification(
                ErrorCode.UNAUTHORIZED,
                "Authentication timed out",
            )
            try:
                await session.connection.send(err.to_json())
                await session.connection.close(4001, "Auth timeout")
            except Exception:
                pass
            return False
        except ProtocolError as pe:
            logger.warning("Protocol error during auth from %s: %s", session.remote_ip, pe.message)
            err = create_error_notification(pe.code, pe.message, ref_id=pe.ref_id)
            try:
                await session.connection.send(err.to_json())
                await session.connection.close(4000, pe.message)
            except Exception:
                pass
            return False

    async def _process_message(self, session: ClientSession, raw_message: str) -> None:
        """Dispatch incoming messages for authenticated clients."""
        try:
            envelope = MessageEnvelope.from_json(raw_message)
        except ProtocolError as pe:
            err = create_error_notification(pe.code, pe.message, ref_id=pe.ref_id)
            await session.connection.send(err.to_json())
            return

        msg_type = envelope.type

        # 1. PING -> PONG
        if msg_type == MessageType.PING.value:
            client_ts = envelope.payload.get("clientTimestamp") or envelope.timestamp
            pong = create_pong(request_id=envelope.id, client_timestamp=client_ts)
            await session.connection.send(pong.to_json())

        # 2. SUBSCRIBE_METRICS
        elif msg_type == MessageType.SUBSCRIBE_METRICS.value:
            interval_ms = int(envelope.payload.get("intervalMs", 1000))
            # Bound interval between 100ms and 10000ms
            session.interval_ms = max(100, min(10000, interval_ms))

            metrics = envelope.payload.get("metrics", [])
            if isinstance(metrics, list):
                session.subscribed_metrics = {str(m) for m in metrics}
            else:
                session.subscribed_metrics = {"cpu", "gpu", "ram", "disk", "network", "audio"}

            logger.info(
                "Client %s subscribed to metrics %s at %d ms",
                session.device_name,
                session.subscribed_metrics,
                session.interval_ms,
            )

            # Start or restart streaming loop for this session
            if session.streaming_task and not session.streaming_task.done():
                session.streaming_task.cancel()
            session.streaming_task = asyncio.create_task(self._stream_telemetry_loop(session))

        # 3. UNSUBSCRIBE_METRICS
        elif msg_type == MessageType.UNSUBSCRIBE_METRICS.value:
            session.subscribed_metrics.clear()
            if session.streaming_task and not session.streaming_task.done():
                session.streaming_task.cancel()
                session.streaming_task = None
            logger.info("Client %s unsubscribed from metrics", session.device_name)

        # 4. COMMAND_EXECUTE
        elif msg_type == MessageType.COMMAND_EXECUTE.value:
            action = str(envelope.payload.get("action", ""))
            params = envelope.payload.get("params", {})
            logger.info("Received command from %s: action=%s, params=%s", session.device_name, action, params)

            # Mock acknowledgment for Milestone 1 (will bind to actual hardware/controls in Milestone 6)
            ack = create_command_ack(
                command_id=envelope.id,
                success=True,
                result={"action": action, "status": "executed (mock)"},
            )
            await session.connection.send(ack.to_json())

        # 5. GET_SYSTEM_INFO
        elif msg_type == MessageType.GET_SYSTEM_INFO.value:
            info_envelope = MessageEnvelope(
                id=f"info-{uuid.uuid4().hex[:8]}",
                type=MessageType.SYSTEM_INFO_RESPONSE.value,
                payload={
                    "serverName": self.config.server_name,
                    "serverVersion": self.config.version,
                    "protocolVersion": envelope.version,
                    "activeClients": len(self._sessions),
                },
            )
            await session.connection.send(info_envelope.to_json())

        else:
            err = create_error_notification(
                ErrorCode.UNSUPPORTED_ACTION,
                f"Unsupported message type: '{msg_type}'",
                ref_id=envelope.id,
            )
            await session.connection.send(err.to_json())

    async def _stream_telemetry_loop(self, session: ClientSession) -> None:
        """Background loop pushing telemetry updates to a subscribed client."""
        try:
            while self._running and session.authenticated:
                # Gather telemetry
                telemetry = self.telemetry_provider()

                # Filter if specific metrics were requested
                if session.subscribed_metrics:
                    filtered = {
                        k: v for k, v in telemetry.items()
                        if k in session.subscribed_metrics
                    }
                else:
                    filtered = telemetry

                msg = create_metrics_stream(filtered)
                await session.connection.send(msg.to_json())

                # Sleep for configured interval
                await asyncio.sleep(session.interval_ms / 1000.0)
        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.warning("Telemetry stream ended for %s: %s", session.device_name, e)

    async def _cleanup_session(self, session: ClientSession) -> None:
        """Clean up state when a client disconnects."""
        if session.streaming_task and not session.streaming_task.done():
            session.streaming_task.cancel()

        async with self._lock:
            self._sessions.pop(session.connection, None)

        logger.info(
            "Client disconnected: %s (%s). Active sessions: %d",
            session.device_name,
            session.remote_ip,
            len(self._sessions),
        )
