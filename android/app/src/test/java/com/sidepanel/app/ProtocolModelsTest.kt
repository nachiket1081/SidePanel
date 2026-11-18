package com.sidepanel.app

import com.sidepanel.app.data.protocol.AuthRequestPayload
import com.sidepanel.app.data.protocol.AuthResponsePayload
import com.sidepanel.app.data.protocol.CpuMetrics
import com.sidepanel.app.data.protocol.DiskMetrics
import com.sidepanel.app.data.protocol.GpuMetrics
import com.sidepanel.app.data.protocol.MessageEnvelope
import com.sidepanel.app.data.protocol.MessageType
import com.sidepanel.app.data.protocol.MetricsStreamPayload
import com.sidepanel.app.data.protocol.NetworkMetrics
import com.sidepanel.app.data.protocol.PrimaryDriveInfo
import com.sidepanel.app.data.protocol.RamMetrics
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun testEnvelopeSerialization() {
        val authReq = AuthRequestPayload(
            token = "secret-token-12345",
            deviceName = "Test Tablet"
        )
        val envelope = MessageEnvelope(
            type = MessageType.AUTH_REQUEST,
            payload = json.encodeToJsonElement(authReq).jsonObject
        )

        val raw = json.encodeToString(envelope)
        assertTrue(raw.contains("\"type\":\"AUTH_REQUEST\""))
        assertTrue(raw.contains("\"version\":\"1.0\""))

        val decoded = json.decodeFromString<MessageEnvelope>(raw)
        assertEquals(MessageType.AUTH_REQUEST, decoded.type)
        assertEquals("1.0", decoded.version)

        val decodedPayload = json.decodeFromJsonElement<AuthRequestPayload>(decoded.payload)
        assertEquals("secret-token-12345", decodedPayload.token)
        assertEquals("Test Tablet", decodedPayload.deviceName)
    }

    @Test
    fun testMetricsStreamDeserialization() {
        val samplePayload = MetricsStreamPayload(
            cpu = CpuMetrics(
                usagePercent = 25.4f,
                frequencyGhz = 3.8f,
                temperatureC = 55.0f,
                coreCount = 12,
                perCoreUsage = listOf(20f, 30f, 15f, 40f)
            ),
            ram = RamMetrics(
                usedBytes = 8589934592L,
                totalBytes = 17179869184L,
                usagePercent = 50.0f,
                availableBytes = 8589934592L
            ),
            gpu = GpuMetrics(
                name = "NVIDIA GeForce RTX 3060",
                usagePercent = 45.0f,
                temperatureC = 60.0f,
                vramUsedBytes = 4294967296L,
                vramTotalBytes = 12884901888L,
                vramUsagePercent = 33.3f,
                isDedicatedGpu = true
            ),
            disk = DiskMetrics(
                readBytesPerSec = 1048576,
                writeBytesPerSec = 2097152,
                primaryDrive = PrimaryDriveInfo(letter = "C:", usedPercent = 65.0f, freeBytes = 100000000000L)
            ),
            network = NetworkMetrics(
                downloadBytesPerSec = 524288,
                uploadBytesPerSec = 65536,
                activeInterface = "Wi-Fi"
            ),
            system = com.sidepanel.app.data.protocol.SystemInfoMetrics(
                uptimeSeconds = 3600
            )
        )

        val envelope = MessageEnvelope(
            type = MessageType.METRICS_STREAM,
            payload = json.encodeToJsonElement(samplePayload).jsonObject
        )

        val serialized = json.encodeToString(envelope)
        val deserializedEnvelope = json.decodeFromString<MessageEnvelope>(serialized)
        val telemetry = json.decodeFromJsonElement<MetricsStreamPayload>(deserializedEnvelope.payload)

        assertEquals(25.4f, telemetry.cpu.usagePercent, 0.01f)
        assertEquals(12, telemetry.cpu.coreCount)
        assertEquals("NVIDIA GeForce RTX 3060", telemetry.gpu.name)
        assertEquals(50.0f, telemetry.ram.usagePercent, 0.01f)
        assertEquals("Wi-Fi", telemetry.network.activeInterface)
        assertEquals(3600L, telemetry.system?.uptimeSeconds)
        assertNotNull(telemetry.disk.primaryDrive)
        assertEquals("C:", telemetry.disk.primaryDrive?.letter)
    }

    @Test
    fun testAuthResponseParsing() {
        val rawWireJson = """
            {
              "version": "1.0",
              "id": "resp-001",
              "type": "AUTH_RESPONSE",
              "timestamp": 1731864000000,
              "payload": {
                "status": "SUCCESS",
                "serverName": "DESKTOP-TEST",
                "serverVersion": "1.0.0",
                "sessionToken": "sess-xyz"
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString<MessageEnvelope>(rawWireJson)
        val auth = json.decodeFromJsonElement<AuthResponsePayload>(envelope.payload)

        assertEquals("SUCCESS", auth.status)
        assertEquals("DESKTOP-TEST", auth.serverName)
        assertEquals("sess-xyz", auth.sessionToken)
    }
}
