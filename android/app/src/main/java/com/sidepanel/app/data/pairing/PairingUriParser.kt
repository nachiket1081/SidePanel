package com.sidepanel.app.data.pairing

data class ParsedPairingInfo(
    val host: String,
    val port: Int,
    val token: String
)

object PairingUriParser {

    private val URI_REGEX = Regex(
        """^(?:sidepanel|ws|http)://([^:/?#]+)(?::(\d+))?[^?#]*\?(?:.*&)?token=([^&#]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses a pairing QR code content string into host, port, and pairing token.
     * Expected format:
     *   sidepanel://192.168.0.105:8765?token=a4f89b6c12e84d7a9b0c2d3e4f5a6b7c
     * Also supports raw host:port?token=, ws://, or http:// schemes.
     */
    fun parse(rawText: String): ParsedPairingInfo? {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return null

        val normalized = when {
            trimmed.startsWith("sidepanel://", ignoreCase = true) ||
            trimmed.startsWith("ws://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            else -> "sidepanel://$trimmed"
        }

        val match = URI_REGEX.find(normalized) ?: return null
        val host = match.groupValues[1]
        val portStr = match.groupValues[2]
        val token = match.groupValues[3]

        if (host.isBlank() || token.isBlank()) return null

        val port = portStr.toIntOrNull() ?: 8765
        return ParsedPairingInfo(host = host, port = port, token = token)
    }
}
