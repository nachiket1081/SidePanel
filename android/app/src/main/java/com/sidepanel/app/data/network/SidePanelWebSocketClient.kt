package com.sidepanel.app.data.network

import android.util.Log
import com.sidepanel.app.data.protocol.AuthRequestPayload
import com.sidepanel.app.data.protocol.AuthResponsePayload
import com.sidepanel.app.data.protocol.CommandAckPayload
import com.sidepanel.app.data.protocol.CommandExecutePayload
import com.sidepanel.app.data.protocol.ErrorNotificationPayload
import com.sidepanel.app.data.protocol.MessageEnvelope
import com.sidepanel.app.data.protocol.MessageType
import com.sidepanel.app.data.protocol.MetricsStreamPayload
import com.sidepanel.app.data.protocol.PingPayload
import com.sidepanel.app.data.protocol.PongPayload
import com.sidepanel.app.data.protocol.SubscribeMetricsPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

private const val TAG = "SidePanelWS"

/**
 * Robust OkHttp-based WebSocket client for SidePanel.
 * Manages authenticated sessions, message framing, heartbeat tracking, and automatic reconnection.
 */
class SidePanelWebSocketClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(0, TimeUnit.SECONDS) // Application-level ping/pong managed internally
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    // Target connection parameters
    private var targetHost: String = ""
    private var targetPort: Int = 8765
    private var pairingToken: String = ""
    private var deviceName: String = "Android Device"

    private var reconnectAttempt = 0
    private var userInitiatedDisconnect = false

    // State Flows
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableSharedFlow<MetricsStreamPayload>(replay = 1)
    val telemetry: SharedFlow<MetricsStreamPayload> = _telemetry.asSharedFlow()

    private val _commandAcks = MutableSharedFlow<CommandAckPayload>()
    val commandAcks: SharedFlow<CommandAckPayload> = _commandAcks.asSharedFlow()

    private val _errors = MutableSharedFlow<ErrorNotificationPayload>()
    val errors: SharedFlow<ErrorNotificationPayload> = _errors.asSharedFlow()

    /**
     * Connect to the Windows companion server.
     */
    fun connect(host: String, port: Int = 8765, token: String, device: String = "Android Device") {
        userInitiatedDisconnect = false
        targetHost = host
        targetPort = port
        pairingToken = token
        deviceName = device
        reconnectAttempt = 0

        initiateConnection()
    }

    /**
     * Manually disconnect from the server and halt reconnection attempts.
     */
    fun disconnect() {
        userInitiatedDisconnect = true
        stopHeartbeat()
        reconnectJob?.cancel()
        reconnectJob = null

        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected("User disconnected")
    }

    /**
     * Send a control command to the Windows companion.
     */
    fun sendCommand(action: String, params: kotlinx.serialization.json.JsonObject) {
        val payload = CommandExecutePayload(action = action, params = params)
        val envelope = MessageEnvelope(
            type = MessageType.COMMAND_EXECUTE,
            payload = json.encodeToJsonElement(payload) as kotlinx.serialization.json.JsonObject
        )
        sendEnvelope(envelope)
    }

    private fun initiateConnection() {
        if (targetHost.isBlank()) return

        stopHeartbeat()
        _connectionState.value = ConnectionState.Connecting(targetHost, targetPort)
        val url = "ws://$targetHost:$targetPort"
        Log.i(TAG, "Initiating WebSocket connection to $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, createSocketListener())
    }

    private fun createSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket connection opened. Sending AUTH_REQUEST...")
                _connectionState.value = ConnectionState.Authenticating(targetHost, targetPort)

                // 1. Send AUTH_REQUEST
                val authPayload = AuthRequestPayload(
                    token = pairingToken,
                    deviceName = deviceName,
                    clientVersion = "1.0.0"
                )
                val envelope = MessageEnvelope(
                    type = MessageType.AUTH_REQUEST,
                    payload = json.encodeToJsonElement(authPayload) as kotlinx.serialization.json.JsonObject
                )
                sendEnvelope(envelope)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closing (code: $code, reason: $reason)")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed (code: $code, reason: $reason)")
                handleConnectionLoss("Closed: $reason (code $code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                handleConnectionLoss(t.message ?: "Connection failure")
            }
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val envelope = json.decodeFromString<MessageEnvelope>(text)
            when (envelope.type) {
                MessageType.AUTH_RESPONSE -> {
                    val auth = json.decodeFromJsonElement<AuthResponsePayload>(envelope.payload)
                    if (auth.status == "SUCCESS") {
                        Log.i(TAG, "Authenticated with host: ${auth.serverName}")
                        reconnectAttempt = 0
                        _connectionState.value = ConnectionState.Connected(
                            host = targetHost,
                            port = targetPort,
                            serverName = auth.serverName,
                            sessionToken = auth.sessionToken ?: ""
                        )
                        // Subscribe to telemetry
                        subscribeMetrics()
                        // Start heartbeat
                        startHeartbeat()
                    } else {
                        Log.e(TAG, "Authentication failed: ${auth.error}")
                        userInitiatedDisconnect = true // Do not auto-reconnect on rejected credentials
                        _connectionState.value = ConnectionState.Disconnected("Auth Failed: ${auth.error}")
                        webSocket?.close(4001, "Auth rejected")
                    }
                }

                MessageType.PONG -> {
                    val pong = json.decodeFromJsonElement<PongPayload>(envelope.payload)
                    val echo = pong.echoTimestamp ?: envelope.timestamp
                    val rtt = System.currentTimeMillis() - echo
                    val current = _connectionState.value
                    if (current is ConnectionState.Connected) {
                        _connectionState.value = current.copy(pingMs = rtt)
                    }
                }

                MessageType.METRICS_STREAM -> {
                    val metrics = json.decodeFromJsonElement<MetricsStreamPayload>(envelope.payload)
                    _telemetry.tryEmit(metrics)
                }

                MessageType.COMMAND_ACK -> {
                    val ack = json.decodeFromJsonElement<CommandAckPayload>(envelope.payload)
                    _commandAcks.tryEmit(ack)
                }

                MessageType.ERROR_NOTIFICATION -> {
                    val err = json.decodeFromJsonElement<ErrorNotificationPayload>(envelope.payload)
                    Log.w(TAG, "Server error notification: ${err.code} - ${err.message}")
                    _errors.tryEmit(err)
                }

                else -> {
                    Log.d(TAG, "Unhandled message type: ${envelope.type}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing message: ${e.message}", e)
        }
    }

    private fun subscribeMetrics(intervalMs: Int = 1000) {
        val payload = SubscribeMetricsPayload(intervalMs = intervalMs)
        val envelope = MessageEnvelope(
            type = MessageType.SUBSCRIBE_METRICS,
            payload = json.encodeToJsonElement(payload) as kotlinx.serialization.json.JsonObject
        )
        sendEnvelope(envelope)
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(5000)
                val ping = PingPayload(clientTimestamp = System.currentTimeMillis())
                val envelope = MessageEnvelope(
                    type = MessageType.PING,
                    payload = json.encodeToJsonElement(ping) as kotlinx.serialization.json.JsonObject
                )
                sendEnvelope(envelope)
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun handleConnectionLoss(reason: String) {
        stopHeartbeat()
        webSocket = null

        if (userInitiatedDisconnect) {
            _connectionState.value = ConnectionState.Disconnected(reason)
            return
        }

        scheduleReconnect(reason)
    }

    private fun scheduleReconnect(error: String) {
        reconnectAttempt++
        // Exponential backoff: 1.0s, 1.5s, 2.25s, ..., max 30s
        val delaySec = min(30.0, 1.0 * (1.5).pow(min(reconnectAttempt, 8)))
        Log.w(TAG, "Scheduling reconnect attempt #$reconnectAttempt in ${delaySec}s (Error: $error)")

        _connectionState.value = ConnectionState.Reconnecting(
            attempt = reconnectAttempt,
            delaySeconds = delaySec,
            host = targetHost,
            port = targetPort,
            error = error
        )

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay((delaySec * 1000).toLong())
            if (!userInitiatedDisconnect) {
                initiateConnection()
            }
        }
    }

    private fun sendEnvelope(envelope: MessageEnvelope) {
        try {
            val text = json.encodeToString(envelope)
            webSocket?.send(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}", e)
        }
    }
}
