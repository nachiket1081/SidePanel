package com.sidepanel.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidepanel.app.data.network.ConnectionState
import com.sidepanel.app.data.preferences.PairingSettings
import com.sidepanel.app.data.preferences.PreferencesManager
import com.sidepanel.app.data.protocol.MetricsStreamPayload
import com.sidepanel.app.data.repository.TelemetryRepository
import com.sidepanel.app.ui.pairing.QrScannerDialog
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: TelemetryRepository
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = application as SidePanelApp
        repository = app.telemetryRepository
        preferencesManager = app.preferencesManager

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0A0E14)
                ) {
                    val connectionState by repository.connectionState.collectAsState()
                    val telemetry by repository.telemetry.collectAsState(initial = null)
                    val pairingSettings by preferencesManager.pairingSettings.collectAsState(
                        initial = PairingSettings()
                    )

                    SidePanelDashboardScreen(
                        connectionState = connectionState,
                        telemetry = telemetry,
                        savedSettings = pairingSettings,
                        onConnect = { host, port, token ->
                            repository.connect(host = host, port = port, token = token)
                        },
                        onDisconnect = {
                            repository.disconnect()
                        },
                        onSavePairing = { host, port, token ->
                            // Coroutine save in background
                            app.applicationContext?.let {
                                (app as SidePanelApp).let { a ->
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                        a.preferencesManager.savePairing(host, port, token)
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SidePanelDashboardScreen(
    connectionState: ConnectionState,
    telemetry: MetricsStreamPayload?,
    savedSettings: PairingSettings,
    onConnect: (String, Int, String) -> Unit,
    onDisconnect: () -> Unit,
    onSavePairing: (String, Int, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var host by remember { mutableStateOf(savedSettings.host) }
    var portText by remember { mutableStateOf(savedSettings.port.toString()) }
    var token by remember { mutableStateOf(savedSettings.token) }
    var showScanner by remember { mutableStateOf(false) }

    // Auto-update input fields when saved settings load from DataStore
    LaunchedEffect(savedSettings) {
        if (savedSettings.isConfigured) {
            host = savedSettings.host
            portText = savedSettings.port.toString()
            token = savedSettings.token

            // Auto-connect on startup if configured and disconnected
            if (connectionState is ConnectionState.Disconnected && savedSettings.autoConnect) {
                onConnect(savedSettings.host, savedSettings.port, savedSettings.token)
            }
        }
    }

    if (showScanner) {
        QrScannerDialog(
            onDismiss = { showScanner = false },
            onQrScanned = { parsed ->
                showScanner = false
                host = parsed.host
                portText = parsed.port.toString()
                token = parsed.token
                onSavePairing(parsed.host, parsed.port, parsed.token)
                onConnect(parsed.host, parsed.port, parsed.token)
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Connection Controls, QR Scanner & Status
        Card(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SidePanel Companion",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                ConnectionStatusPill(connectionState)

                // QR Scanner Button
                Button(
                    onClick = { showScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Scan PC QR Code", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("PC IP Address (e.g. 192.168.x.x)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port") },
                        singleLine = true,
                        modifier = Modifier.weight(0.35f)
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Pairing Token") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (connectionState is ConnectionState.Connected ||
                    connectionState is ConnectionState.Connecting ||
                    connectionState is ConnectionState.Authenticating
                ) {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDA3633)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Disconnect", color = Color.White)
                    }
                } else {
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 8765
                            val h = host.trim()
                            val tok = token.trim()
                            onSavePairing(h, port, tok)
                            onConnect(h, port, tok)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Connect to PC", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Right Column: Live Telemetry Preview
        Card(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Live Hardware Telemetry Stream",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                if (telemetry != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricPreviewTile(
                            modifier = Modifier.weight(1f),
                            title = "CPU Load",
                            value = "${telemetry.cpu.usagePercent}%",
                            subtitle = "${telemetry.cpu.coreCount} Cores @ ${telemetry.cpu.frequencyGhz} GHz",
                            color = Color(0xFF58A6FF)
                        )
                        MetricPreviewTile(
                            modifier = Modifier.weight(1f),
                            title = "GPU (${telemetry.gpu.name.take(15)})",
                            value = "${telemetry.gpu.usagePercent}%",
                            subtitle = "${telemetry.gpu.temperatureC ?: "N/A"} °C | VRAM ${telemetry.gpu.vramUsagePercent}%",
                            color = Color(0xFF7EE787)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricPreviewTile(
                            modifier = Modifier.weight(1f),
                            title = "RAM Used",
                            value = "${telemetry.ram.usagePercent}%",
                            subtitle = "${telemetry.ram.usedBytes / (1024 * 1024 * 1024)} GB / ${telemetry.ram.totalBytes / (1024 * 1024 * 1024)} GB",
                            color = Color(0xFFFFA657)
                        )
                        MetricPreviewTile(
                            modifier = Modifier.weight(1f),
                            title = "Network Rate",
                            value = "${telemetry.network.downloadBytesPerSec / 1024} KB/s",
                            subtitle = "Up: ${telemetry.network.uploadBytesPerSec / 1024} KB/s | ${telemetry.network.activeInterface}",
                            color = Color(0xFFD2A8FF)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0D1117), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Awaiting connection to Windows companion…",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Tap 'Scan PC QR Code' or enter your PC's IP and Token",
                                color = Color(0xFF8B949E),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricPreviewTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    color: Color
) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, fontSize = 12.sp, color = Color.Gray)
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = subtitle, fontSize = 11.sp, color = Color.LightGray)
        }
    }
}

@Composable
fun ConnectionStatusPill(state: ConnectionState) {
    val (bgColor, textColor, text) = when (state) {
        is ConnectionState.Disconnected -> Triple(
            Color(0xFF30363D),
            Color.LightGray,
            state.reason ?: "Disconnected"
        )
        is ConnectionState.Connecting -> Triple(
            Color(0xFF388BFD),
            Color.White,
            "Connecting to ${state.host}:${state.port}…"
        )
        is ConnectionState.Authenticating -> Triple(
            Color(0xFFD29922),
            Color.Black,
            "Authenticating with ${state.host}…"
        )
        is ConnectionState.Connected -> Triple(
            Color(0xFF238636),
            Color.White,
            "Connected (${state.serverName}) - ${state.pingMs}ms"
        )
        is ConnectionState.Reconnecting -> Triple(
            Color(0xFFDA3633),
            Color.White,
            "Reconnecting #${state.attempt} in ${state.delaySeconds.toInt()}s"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
