package com.sidepanel.app.data.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

const val PROTOCOL_VERSION = "1.0"

object MessageType {
    const val AUTH_REQUEST = "AUTH_REQUEST"
    const val AUTH_RESPONSE = "AUTH_RESPONSE"
    const val PING = "PING"
    const val PONG = "PONG"
    const val SUBSCRIBE_METRICS = "SUBSCRIBE_METRICS"
    const val UNSUBSCRIBE_METRICS = "UNSUBSCRIBE_METRICS"
    const val METRICS_STREAM = "METRICS_STREAM"
    const val COMMAND_EXECUTE = "COMMAND_EXECUTE"
    const val COMMAND_ACK = "COMMAND_ACK"
    const val GET_SYSTEM_INFO = "GET_SYSTEM_INFO"
    const val SYSTEM_INFO_RESPONSE = "SYSTEM_INFO_RESPONSE"
    const val ERROR_NOTIFICATION = "ERROR_NOTIFICATION"
}

/**
 * Standard top-level wire envelope defined in PROTOCOL.md.
 */
@Serializable
data class MessageEnvelope(
    val version: String = PROTOCOL_VERSION,
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: JsonObject
)

// ==============================================================================
// Authentication Payloads
// ==============================================================================

@Serializable
data class AuthRequestPayload(
    val token: String,
    val deviceName: String = "Android Device",
    val clientVersion: String = "1.0.0"
)

@Serializable
data class AuthResponsePayload(
    val status: String,
    val serverName: String = "",
    val serverVersion: String = "",
    val sessionToken: String? = null,
    val error: String? = null,
    val capabilities: ServerCapabilities? = null
)

@Serializable
data class ServerCapabilities(
    val hasNvidiaGpu: Boolean = false,
    val audioControl: Boolean = true,
    val mediaControl: Boolean = true,
    val supportedIntervalsMs: List<Int> = emptyList()
)

// ==============================================================================
// Heartbeat Payloads
// ==============================================================================

@Serializable
data class PingPayload(
    val clientTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PongPayload(
    val echoTimestamp: Long? = null
)

// ==============================================================================
// Telemetry Payloads
// ==============================================================================

@Serializable
data class SubscribeMetricsPayload(
    val intervalMs: Int = 1000,
    val metrics: List<String> = listOf("cpu", "gpu", "ram", "disk", "network", "audio")
)

@Serializable
data class MetricsStreamPayload(
    val cpu: CpuMetrics,
    val ram: RamMetrics,
    val gpu: GpuMetrics,
    val disk: DiskMetrics,
    val network: NetworkMetrics,
    val audio: AudioMetrics? = null
)

@Serializable
data class CpuMetrics(
    val usagePercent: Float,
    val frequencyGhz: Float = 0.0f,
    val temperatureC: Float? = null,
    val coreCount: Int,
    val physicalCores: Int = 0,
    val perCoreUsage: List<Float> = emptyList()
)

@Serializable
data class RamMetrics(
    val usedBytes: Long,
    val totalBytes: Long,
    val usagePercent: Float,
    val availableBytes: Long,
    val swapUsedBytes: Long = 0,
    val swapTotalBytes: Long = 0,
    val swapPercent: Float = 0.0f
)

@Serializable
data class GpuMetrics(
    val name: String,
    val usagePercent: Float,
    val temperatureC: Float? = null,
    val hotspotTempC: Float? = null,
    val vramUsedBytes: Long = 0,
    val vramTotalBytes: Long = 0,
    val vramUsagePercent: Float = 0.0f,
    val fanSpeedPercent: Int? = null,
    val powerWatts: Float? = null,
    val isDedicatedGpu: Boolean = false
)

@Serializable
data class DiskMetrics(
    val readBytesPerSec: Long = 0,
    val writeBytesPerSec: Long = 0,
    val primaryDrive: PrimaryDriveInfo? = null
)

@Serializable
data class PrimaryDriveInfo(
    val letter: String = "C:",
    val usedPercent: Float = 0.0f,
    val freeBytes: Long = 0,
    val totalBytes: Long = 0
)

@Serializable
data class NetworkMetrics(
    val downloadBytesPerSec: Long = 0,
    val uploadBytesPerSec: Long = 0,
    val activeInterface: String = "Unknown"
)

@Serializable
data class AudioMetrics(
    val masterVolumePercent: Int = 0,
    val isMuted: Boolean = false,
    val activeOutputDevice: String = ""
)

// ==============================================================================
// Command & Error Payloads
// ==============================================================================

@Serializable
data class CommandExecutePayload(
    val action: String,
    val params: JsonObject
)

@Serializable
data class CommandAckPayload(
    val commandId: String,
    val success: Boolean,
    val error: String? = null
)

@Serializable
data class ErrorNotificationPayload(
    val code: String,
    val message: String,
    val referenceId: String? = null
)
