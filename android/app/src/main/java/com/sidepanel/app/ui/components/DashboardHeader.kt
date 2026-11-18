package com.sidepanel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidepanel.app.data.network.ConnectionState
import com.sidepanel.app.ui.theme.BorderSubtle
import com.sidepanel.app.ui.theme.ElectricBlue
import com.sidepanel.app.ui.theme.NeonCyan
import com.sidepanel.app.ui.theme.NeonGreen
import com.sidepanel.app.ui.theme.NeonPurple
import com.sidepanel.app.ui.theme.SurfaceCard
import com.sidepanel.app.ui.theme.TextMuted
import com.sidepanel.app.ui.theme.TextPrimary
import com.sidepanel.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardHeader(
    connectionState: ConnectionState,
    uptimeSeconds: Long?,
    onOpenSettings: () -> Unit,
    onOpenScanner: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTimeString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(1000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(SurfaceCard, RoundedCornerShape(8.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Logo & Host
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        Brush.linearGradient(listOf(NeonCyan, NeonPurple)),
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "SIDEPANEL",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))

            // Connected Host Badge
            val hostName = when (connectionState) {
                is ConnectionState.Connected -> connectionState.serverName.ifBlank { connectionState.host }
                is ConnectionState.Connecting -> "Connecting to ${connectionState.host}..."
                is ConnectionState.Authenticating -> "Authenticating..."
                is ConnectionState.Reconnecting -> "Reconnecting..."
                is ConnectionState.Disconnected -> "Offline"
            }

            Box(
                modifier = Modifier
                    .background(Color(0xFF0A0E14), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = hostName,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonCyan
                )
            }
        }

        // Center: Connection Status Pill & Uptime
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConnectionStatusHeaderPill(connectionState)

            if (uptimeSeconds != null && uptimeSeconds > 0) {
                val hours = uptimeSeconds / 3600
                val mins = (uptimeSeconds % 3600) / 60
                Box(
                    modifier = Modifier
                        .background(Color(0xFF0A0E14), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "UP: ${hours}h ${mins}m",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }

            // Real-time Clock
            if (currentTimeString.isNotBlank()) {
                Text(
                    text = currentTimeString,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // Right: Actions (Scan QR, Settings, Disconnect)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onOpenScanner,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (connectionState is ConnectionState.Connected) {
                IconButton(
                    onClick = onDisconnect,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Disconnect",
                        tint = Color(0xFFDA3633),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusHeaderPill(state: ConnectionState) {
    val (dotColor, text) = when (state) {
        is ConnectionState.Connected -> Pair(NeonGreen, "LIVE (${state.pingMs}ms)")
        is ConnectionState.Connecting -> Pair(ElectricBlue, "CONNECTING")
        is ConnectionState.Authenticating -> Pair(Color(0xFFFFD166), "AUTH")
        is ConnectionState.Reconnecting -> Pair(Color(0xFFFF3333), "RETRY #${state.attempt}")
        is ConnectionState.Disconnected -> Pair(Color.Gray, "DISCONNECTED")
    }

    Row(
        modifier = Modifier
            .background(Color(0xFF0A0E14), RoundedCornerShape(4.dp))
            .border(0.5.dp, dotColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = text,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = dotColor
        )
    }
}
