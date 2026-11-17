"""SidePanel Local Wire Protocol (SPLWP 1.0) Framing and Serialization.

Defines envelope models, serialization, deserialization, and standard constructors.
"""

from __future__ import annotations

import json
import time
import uuid
from dataclasses import asdict, dataclass, field
from enum import Enum
from typing import Any, Dict, Optional

PROTOCOL_VERSION = "1.0"


class MessageType(str, Enum):
    """Standardized message type enums per PROTOCOL.md."""

    # Client -> Server
    AUTH_REQUEST = "AUTH_REQUEST"
    PING = "PING"
    SUBSCRIBE_METRICS = "SUBSCRIBE_METRICS"
    UNSUBSCRIBE_METRICS = "UNSUBSCRIBE_METRICS"
    COMMAND_EXECUTE = "COMMAND_EXECUTE"
    GET_SYSTEM_INFO = "GET_SYSTEM_INFO"

    # Server -> Client
    AUTH_RESPONSE = "AUTH_RESPONSE"
    PONG = "PONG"
    METRICS_STREAM = "METRICS_STREAM"
    COMMAND_ACK = "COMMAND_ACK"
    SYSTEM_INFO_RESPONSE = "SYSTEM_INFO_RESPONSE"
    ERROR_NOTIFICATION = "ERROR_NOTIFICATION"


class ErrorCode(str, Enum):
    """Standard error codes defined in PROTOCOL.md."""

    UNAUTHORIZED = "UNAUTHORIZED"
    INVALID_PAYLOAD = "INVALID_PAYLOAD"
    UNSUPPORTED_ACTION = "UNSUPPORTED_ACTION"
    EXECUTION_FAILED = "EXECUTION_FAILED"
    RATE_LIMITED = "RATE_LIMITED"
    SERVER_SHUTTING_DOWN = "SERVER_SHUTTING_DOWN"


class ProtocolError(Exception):
    """Raised when an incoming packet violates protocol specification."""

    def __init__(self, code: ErrorCode, message: str, ref_id: Optional[str] = None):
        super().__init__(message)
        self.code = code
        self.message = message
        self.ref_id = ref_id


@dataclass
class MessageEnvelope:
    """Standard top-level JSON envelope for all SidePanel wire messages."""

    version: str = PROTOCOL_VERSION
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    type: str = ""
    timestamp: int = field(default_factory=lambda: int(time.time() * 1000))
    payload: Dict[str, Any] = field(default_factory=dict)

    def to_json(self) -> str:
        """Serialize envelope to JSON string."""
        return json.dumps(asdict(self), separators=(",", ":"))

    @classmethod
    def from_json(cls, raw: str) -> MessageEnvelope:
        """Parse and validate JSON string into a MessageEnvelope."""
        try:
            data = json.loads(raw)
        except Exception as e:
            raise ProtocolError(ErrorCode.INVALID_PAYLOAD, f"Invalid JSON syntax: {e}") from e

        if not isinstance(data, dict):
            raise ProtocolError(ErrorCode.INVALID_PAYLOAD, "Message must be a JSON object")

        version = data.get("version")
        if version != PROTOCOL_VERSION:
            raise ProtocolError(
                ErrorCode.INVALID_PAYLOAD,
                f"Protocol version mismatch: expected '{PROTOCOL_VERSION}', got '{version}'",
            )

        msg_id = data.get("id")
        if not msg_id or not isinstance(msg_id, str):
            raise ProtocolError(ErrorCode.INVALID_PAYLOAD, "Missing or invalid 'id' field")

        msg_type = data.get("type")
        if not msg_type or not isinstance(msg_type, str):
            raise ProtocolError(ErrorCode.INVALID_PAYLOAD, "Missing or invalid 'type' field")

        timestamp = data.get("timestamp")
        if timestamp is None or not isinstance(timestamp, (int, float)):
            timestamp = int(time.time() * 1000)

        payload = data.get("payload")
        if payload is None or not isinstance(payload, dict):
            raise ProtocolError(ErrorCode.INVALID_PAYLOAD, "Missing or invalid 'payload' object", ref_id=msg_id)

        return cls(
            version=str(version),
            id=str(msg_id),
            type=str(msg_type),
            timestamp=int(timestamp),
            payload=payload,
        )


# ==============================================================================
# Helper Factory Constructors
# ==============================================================================


def create_auth_response(
    request_id: str,
    status: str,
    server_name: str,
    server_version: str,
    session_token: Optional[str] = None,
    error: Optional[str] = None,
    capabilities: Optional[Dict[str, Any]] = None,
) -> MessageEnvelope:
    """Create an AUTH_RESPONSE message envelope."""
    payload: Dict[str, Any] = {
        "status": status,
        "serverName": server_name,
        "serverVersion": server_version,
    }
    if session_token:
        payload["sessionToken"] = session_token
    if error:
        payload["error"] = error
    if capabilities:
        payload["capabilities"] = capabilities
    else:
        payload["capabilities"] = {
            "hasNvidiaGpu": False,
            "audioControl": True,
            "mediaControl": True,
            "supportedIntervalsMs": [250, 500, 1000, 2000, 5000],
        }

    return MessageEnvelope(
        id=f"resp-auth-{uuid.uuid4().hex[:8]}",
        type=MessageType.AUTH_RESPONSE.value,
        payload=payload,
    )


def create_pong(request_id: str, client_timestamp: Optional[int] = None) -> MessageEnvelope:
    """Create a PONG response envelope echoing client timestamp."""
    payload: Dict[str, Any] = {}
    if client_timestamp is not None:
        payload["echoTimestamp"] = client_timestamp

    return MessageEnvelope(
        id=f"pong-{uuid.uuid4().hex[:8]}",
        type=MessageType.PONG.value,
        payload=payload,
    )


def create_error_notification(
    code: ErrorCode,
    message: str,
    ref_id: Optional[str] = None,
) -> MessageEnvelope:
    """Create a standardized ERROR_NOTIFICATION envelope."""
    payload: Dict[str, Any] = {
        "code": code.value,
        "message": message,
    }
    if ref_id:
        payload["referenceId"] = ref_id

    return MessageEnvelope(
        id=f"err-{uuid.uuid4().hex[:8]}",
        type=MessageType.ERROR_NOTIFICATION.value,
        payload=payload,
    )


def create_metrics_stream(metrics_data: Dict[str, Any]) -> MessageEnvelope:
    """Create a METRICS_STREAM envelope with hardware telemetry."""
    return MessageEnvelope(
        id=f"metric-{uuid.uuid4().hex[:8]}",
        type=MessageType.METRICS_STREAM.value,
        payload=metrics_data,
    )


def create_command_ack(
    command_id: str,
    success: bool,
    result: Optional[Dict[str, Any]] = None,
    error: Optional[str] = None,
) -> MessageEnvelope:
    """Create a COMMAND_ACK envelope acknowledging client control action."""
    payload: Dict[str, Any] = {
        "commandId": command_id,
        "success": success,
    }
    if result is not None:
        payload["result"] = result
    if error is not None:
        payload["error"] = error

    return MessageEnvelope(
        id=f"ack-{uuid.uuid4().hex[:8]}",
        type=MessageType.COMMAND_ACK.value,
        payload=payload,
    )
