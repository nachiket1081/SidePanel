package com.sidepanel.app.data.repository

import com.sidepanel.app.data.network.ConnectionState
import com.sidepanel.app.data.network.SidePanelWebSocketClient
import com.sidepanel.app.data.protocol.CommandAckPayload
import com.sidepanel.app.data.protocol.ErrorNotificationPayload
import com.sidepanel.app.data.protocol.MetricsStreamPayload
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

interface TelemetryRepository {
    val connectionState: StateFlow<ConnectionState>
    val telemetry: SharedFlow<MetricsStreamPayload>
    val commandAcks: SharedFlow<CommandAckPayload>
    val errors: SharedFlow<ErrorNotificationPayload>

    fun connect(host: String, port: Int = 8765, token: String, deviceName: String = "Android Device")
    fun disconnect()
    fun sendCommand(action: String, params: JsonObject)
}

class TelemetryRepositoryImpl(
    private val client: SidePanelWebSocketClient = SidePanelWebSocketClient()
) : TelemetryRepository {

    override val connectionState: StateFlow<ConnectionState> = client.connectionState
    override val telemetry: SharedFlow<MetricsStreamPayload> = client.telemetry
    override val commandAcks: SharedFlow<CommandAckPayload> = client.commandAcks
    override val errors: SharedFlow<ErrorNotificationPayload> = client.errors

    override fun connect(host: String, port: Int, token: String, deviceName: String) {
        client.connect(host = host, port = port, token = token, device = deviceName)
    }

    override fun disconnect() {
        client.disconnect()
    }

    override fun sendCommand(action: String, params: JsonObject) {
        client.sendCommand(action, params)
    }
}
