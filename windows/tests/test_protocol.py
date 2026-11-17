"""Unit tests for SidePanel Wire Protocol Framing and Serialization."""

import json
import pytest

from windows.src.server.protocol import (
    PROTOCOL_VERSION,
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


def test_envelope_serialization() -> None:
    """Test serializing MessageEnvelope to valid JSON."""
    envelope = MessageEnvelope(
        id="test-123",
        type=MessageType.PING.value,
        timestamp=1731864000000,
        payload={"key": "val"},
    )
    raw = envelope.to_json()
    data = json.loads(raw)

    assert data["version"] == PROTOCOL_VERSION
    assert data["id"] == "test-123"
    assert data["type"] == "PING"
    assert data["timestamp"] == 1731864000000
    assert data["payload"] == {"key": "val"}


def test_envelope_deserialization_success() -> None:
    """Test parsing a valid JSON wire message."""
    raw = json.dumps({
        "version": "1.0",
        "id": "abc-456",
        "type": "AUTH_REQUEST",
        "timestamp": 1731864000100,
        "payload": {"token": "secret123"},
    })
    envelope = MessageEnvelope.from_json(raw)
    assert envelope.version == "1.0"
    assert envelope.id == "abc-456"
    assert envelope.type == "AUTH_REQUEST"
    assert envelope.payload["token"] == "secret123"


def test_envelope_version_mismatch() -> None:
    """Test rejecting messages with incompatible protocol versions."""
    raw = json.dumps({
        "version": "9.9",
        "id": "1",
        "type": "PING",
        "timestamp": 12345,
        "payload": {},
    })
    with pytest.raises(ProtocolError) as exc_info:
        MessageEnvelope.from_json(raw)
    assert exc_info.value.code == ErrorCode.INVALID_PAYLOAD
    assert "Protocol version mismatch" in exc_info.value.message


def test_envelope_invalid_json() -> None:
    """Test rejecting non-JSON or malformed payloads."""
    with pytest.raises(ProtocolError) as exc_info:
        MessageEnvelope.from_json("{not valid json}")
    assert exc_info.value.code == ErrorCode.INVALID_PAYLOAD


def test_envelope_missing_fields() -> None:
    """Test rejection when required fields are missing."""
    # Missing type
    with pytest.raises(ProtocolError):
        MessageEnvelope.from_json(json.dumps({"version": "1.0", "id": "1", "payload": {}}))

    # Missing payload
    with pytest.raises(ProtocolError):
        MessageEnvelope.from_json(json.dumps({"version": "1.0", "id": "1", "type": "PING"}))


def test_helper_factories() -> None:
    """Test message factory helpers."""
    auth_resp = create_auth_response(
        request_id="req-1",
        status="SUCCESS",
        server_name="Test-PC",
        server_version="1.0.0",
        session_token="sess-abc",
    )
    assert auth_resp.type == MessageType.AUTH_RESPONSE.value
    assert auth_resp.payload["status"] == "SUCCESS"
    assert auth_resp.payload["sessionToken"] == "sess-abc"

    pong = create_pong(request_id="ping-1", client_timestamp=9999)
    assert pong.type == MessageType.PONG.value
    assert pong.payload["echoTimestamp"] == 9999

    err = create_error_notification(ErrorCode.UNAUTHORIZED, "Bad token", ref_id="ref-1")
    assert err.type == MessageType.ERROR_NOTIFICATION.value
    assert err.payload["code"] == "UNAUTHORIZED"
    assert err.payload["referenceId"] == "ref-1"

    metric = create_metrics_stream({"cpu": {"usage": 45.0}})
    assert metric.type == MessageType.METRICS_STREAM.value
    assert metric.payload["cpu"]["usage"] == 45.0

    ack = create_command_ack("cmd-1", True, result={"vol": 80})
    assert ack.type == MessageType.COMMAND_ACK.value
    assert ack.payload["success"] is True
    assert ack.payload["result"]["vol"] == 80
