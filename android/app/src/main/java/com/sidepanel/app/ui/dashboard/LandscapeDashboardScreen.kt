package com.sidepanel.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidepanel.app.data.model.PerformanceHistoryBuffer
import com.sidepanel.app.data.network.ConnectionState
import com.sidepanel.app.data.preferences.PairingSettings
import com.sidepanel.app.data.protocol.MetricsStreamPayload
import com.sidepanel.app.ui.components.CpuHardwareCard
import com.sidepanel.app.ui.components.DashboardHeader
import com.sidepanel.app.ui.components.DiskHardwareCard
import com.sidepanel.app.ui.components.GpuHardwareCard
import com.sidepanel.app.ui.components.LivePerformanceGraph
import com.sidepanel.app.ui.components.NetworkMonitorPanel
import com.sidepanel.app.ui.components.RamHardwareCard
import com.sidepanel.app.ui.components.SettingsDialog
import com.sidepanel.app.ui.components.TechnicalStatusBar
import com.sidepanel.app.ui.components.TemperaturePanel
import com.sidepanel.app.ui.pairing.QrScannerDialog
import com.sidepanel.app.ui.theme.BorderSubtle
import com.sidepanel.app.ui.theme.DarkCanvas
import com.sidepanel.app.ui.theme.NeonCyan
import com.sidepanel.app.ui.theme.SurfaceCard
import com.sidepanel.app.ui.theme.TextMuted
import com.sidepanel.app.ui.theme.TextPrimary
import com.sidepanel.app.ui.theme.TextSecondary

@Composable
fun LandscapeDashboardScreen(
    connectionState: ConnectionState,
    telemetry: MetricsStreamPayload?,
    savedSettings: PairingSettings,
    historyBuffer: PerformanceHistoryBuffer,
    onConnect: (String, Int, String) -> Unit,
    onDisconnect: () -> Unit,
    onSavePairing: (String, Int, String) -> Unit,
    onClearPairing: () -> Unit = {},
    onUpdateInterval: (Int) -> Unit = {}
) {
    var showScanner by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var warnThreshold by remember { mutableFloatStateOf(75f) }
    var critThreshold by remember { mutableFloatStateOf(85f) }

    // Feeds incoming telemetry into the bounded sliding-window history buffer
    LaunchedEffect(telemetry) {
        if (telemetry != null) {
            historyBuffer.push(telemetry)
        }
    }

    val history = remember(telemetry) { historyBuffer.getSnapshot() }
    val pingMs = (connectionState as? ConnectionState.Connected)?.pingMs

    // QR Scanner Dialog Overlay
    if (showScanner) {
        QrScannerDialog(
            onDismiss = { showScanner = false },
            onQrScanned = { parsed ->
                showScanner = false
                onSavePairing(parsed.host, parsed.port, parsed.token)
                onConnect(parsed.host, parsed.port, parsed.token)
            }
        )
    }

    // Settings Dialog Overlay
    if (showSettings) {
        SettingsDialog(
            initialHost = savedSettings.host,
            initialPort = savedSettings.port,
            initialToken = savedSettings.token,
            currentWarnTemp = warnThreshold,
            currentCritTemp = critThreshold,
            onDismiss = { showSettings = false },
            onApplySettings = { h, p, tok, warn, crit, intervalMs ->
                warnThreshold = warn
                critThreshold = crit
                onSavePairing(h, p, tok)
                onUpdateInterval(intervalMs)
                if (connectionState is ConnectionState.Disconnected || h != savedSettings.host) {
                    onConnect(h, p, tok)
                }
            },
            onDisconnect = onDisconnect
        )
    }

    // Main Edge-to-Edge Landscape Canvas
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCanvas)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Top Header Bar
            DashboardHeader(
                connectionState = connectionState,
                uptimeSeconds = telemetry?.system?.uptimeSeconds,
                onOpenSettings = { showSettings = true },
                onOpenScanner = { showScanner = true },
                onDisconnect = onDisconnect
            )

            if (!savedSettings.isConfigured) {
                // First-time Disconnected Setup View
                DisconnectedSetupView(
                    connectionState = connectionState,
                    savedSettings = savedSettings,
                    onOpenScanner = { showScanner = true },
                    onConnect = onConnect,
                    onSavePairing = onSavePairing
                )
            } else if (telemetry != null) {
                // 2. Hardware Gauges Row (CPU, GPU, RAM, DISK)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CpuHardwareCard(cpu = telemetry.cpu, modifier = Modifier.weight(1f))
                    GpuHardwareCard(gpu = telemetry.gpu, modifier = Modifier.weight(1f))
                    RamHardwareCard(ram = telemetry.ram, modifier = Modifier.weight(1f))
                    DiskHardwareCard(disk = telemetry.disk, modifier = Modifier.weight(1f))
                }

                // 3. Lower Analytics Split (Performance Graph + Thermal/Network Side Panels)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Left 60%: Live Performance Graph
                    LivePerformanceGraph(
                        history = history,
                        modifier = Modifier.weight(0.58f)
                    )

                    // Right 40%: Thermal Panel & Network Panel
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TemperaturePanel(
                            cpuTempC = telemetry.cpu.temperatureC,
                            gpuTempC = telemetry.gpu.temperatureC,
                            warnThreshold = warnThreshold,
                            criticalThreshold = critThreshold,
                            modifier = Modifier.weight(1f)
                        )

                        NetworkMonitorPanel(
                            network = telemetry.network,
                            history = history,
                            pingMs = pingMs,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 4. Compact Bottom Status Bar
                TechnicalStatusBar(
                    telemetry = telemetry,
                    pingMs = pingMs
                )
            } else {
                // Configured, waiting for connection or initial telemetry stream
                ConfiguredWaitingView(
                    connectionState = connectionState,
                    savedSettings = savedSettings,
                    onReconnect = { onConnect(savedSettings.host, savedSettings.port, savedSettings.token) },
                    onOpenScanner = { showScanner = true }
                )
            }
        }
    }
}

@Composable
private fun ConfiguredWaitingView(
    connectionState: ConnectionState,
    savedSettings: PairingSettings,
    onReconnect: () -> Unit,
    onOpenScanner: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceCard, RoundedCornerShape(12.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            when (connectionState) {
                is ConnectionState.Connecting -> {
                    Text(
                        text = "CONNECTING TO PC...",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${savedSettings.host}:${savedSettings.port}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
                is ConnectionState.Reconnecting -> {
                    Text(
                        text = "RECONNECTING (ATTEMPT ${connectionState.attempt})...",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB454),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Attempting to reach ${savedSettings.host}:${savedSettings.port}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                is ConnectionState.Connected -> {
                    Text(
                        text = "SYNCHRONIZING TELEMETRY STREAM...",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )
                }
                else -> {
                    val errorMsg = (connectionState as? ConnectionState.Disconnected)?.reason
                    Text(
                        text = if (errorMsg != null) "CONNECTION DISCONNECTED" else "PC OFFLINE",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = errorMsg ?: "Cannot reach ${savedSettings.host}:${savedSettings.port}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onReconnect,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reconnect", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onOpenScanner,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan",
                                modifier = Modifier.size(16.dp),
                                tint = NeonCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan New QR", color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisconnectedSetupView(
    connectionState: ConnectionState,
    savedSettings: PairingSettings,
    onOpenScanner: () -> Unit,
    onConnect: (String, Int, String) -> Unit,
    onSavePairing: (String, Int, String) -> Unit
) {
    var host by remember { mutableStateOf(savedSettings.host) }
    var portText by remember { mutableStateOf(savedSettings.port.toString()) }
    var token by remember { mutableStateOf(savedSettings.token) }

    LaunchedEffect(savedSettings) {
        if (savedSettings.isConfigured) {
            host = savedSettings.host
            portText = savedSettings.port.toString()
            token = savedSettings.token
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column: Instant QR Pairing Box
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PAIR WITH WINDOWS PC",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scan the QR code shown on your Windows companion screen for instant zero-configuration pairing.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onOpenScanner,
                    modifier = Modifier.fillMaxWidth(0.7f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan PC QR Code", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Right Column: Manual IP Fallback
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "MANUAL CONNECTION",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("PC IP Address (e.g. 192.168.0.105 or 127.0.0.1)") },
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
                        label = { Text("Token") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        val p = portText.toIntOrNull() ?: 8765
                        val h = host.trim()
                        val tok = token.trim()
                        onSavePairing(h, p, tok)
                        onConnect(h, p, tok)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Connect to PC", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
