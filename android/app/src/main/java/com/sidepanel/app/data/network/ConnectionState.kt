package com.sidepanel.app.data.network

/**
 * Represents the connection lifecycle state between the Android client and Windows Companion.
 */
sealed interface ConnectionState {

    /** Idle or cleanly disconnected from host. */
    data class Disconnected(val reason: String? = null) : ConnectionState

    /** Actively attempting to open TCP and WebSocket connection to IP:Port. */
    data class Connecting(val host: String, val port: Int) : ConnectionState

    /** WebSocket is open; actively exchanging AUTH_REQUEST and validating token. */
    data class Authenticating(val host: String, val port: Int) : ConnectionState

    /** Authenticated and active; streaming telemetry and ready for commands. */
    data class Connected(
        val host: String,
        val port: Int,
        val serverName: String,
        val sessionToken: String,
        val pingMs: Long = 0
    ) : ConnectionState

    /** Connection dropped unexpectedly; currently waiting to retry connection. */
    data class Reconnecting(
        val attempt: Int,
        val delaySeconds: Double,
        val host: String,
        val port: Int,
        val error: String? = null
    ) : ConnectionState
}
