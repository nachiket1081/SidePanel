package com.sidepanel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import com.sidepanel.app.ui.theme.AlertRed
import com.sidepanel.app.ui.theme.BorderSubtle
import com.sidepanel.app.ui.theme.NeonAmber
import com.sidepanel.app.ui.theme.NeonCyan
import com.sidepanel.app.ui.theme.SurfaceCard
import com.sidepanel.app.ui.theme.TextMuted
import com.sidepanel.app.ui.theme.TextPrimary
import com.sidepanel.app.ui.theme.TextSecondary

@Composable
fun SettingsDialog(
    initialHost: String,
    initialPort: Int,
    initialToken: String,
    currentWarnTemp: Float = 75f,
    currentCritTemp: Float = 85f,
    onDismiss: () -> Unit,
    onApplySettings: (host: String, port: Int, token: String, warnTemp: Float, critTemp: Float, intervalMs: Int) -> Unit,
    onDisconnect: () -> Unit
) {
    var host by remember { mutableStateOf(initialHost) }
    var portText by remember { mutableStateOf(initialPort.toString()) }
    var token by remember { mutableStateOf(initialToken) }
    var warnTemp by remember { mutableFloatStateOf(currentWarnTemp) }
    var critTemp by remember { mutableFloatStateOf(currentCritTemp) }
    var selectedIntervalMs by remember { mutableIntStateOf(1000) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SIDEPANEL SETTINGS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Refresh Interval
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("TELEMETRY REFRESH RATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(250, 500, 1000, 2000).forEach { ms ->
                            val isSelected = selectedIntervalMs == ms
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) NeonCyan.copy(alpha = 0.2f) else Color(0xFF0D121A),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else BorderSubtle,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedIntervalMs = ms }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${ms}ms",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) NeonCyan else TextMuted
                                )
                            }
                        }
                    }
                }

                // Thermal Thresholds
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "THERMAL WARNING THRESHOLD: ${warnTemp.toInt()}°C",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber
                    )
                    Slider(
                        value = warnTemp,
                        onValueChange = { warnTemp = it },
                        valueRange = 60f..85f,
                        steps = 5,
                        colors = SliderDefaults.colors(thumbColor = NeonAmber, activeTrackColor = NeonAmber)
                    )

                    Text(
                        text = "CRITICAL ALERT THRESHOLD: ${critTemp.toInt()}°C",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertRed
                    )
                    Slider(
                        value = critTemp,
                        onValueChange = { critTemp = it },
                        valueRange = 80f..100f,
                        steps = 4,
                        colors = SliderDefaults.colors(thumbColor = AlertRed, activeTrackColor = AlertRed)
                    )
                }

                // PC Connection Credentials
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("PC IP", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(0.4f)
                    )
                }

                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onDisconnect()
                            onDismiss()
                        },
                        modifier = Modifier.weight(0.4f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1515))
                    ) {
                        Text("Disconnect", color = AlertRed, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val p = portText.toIntOrNull() ?: 8765
                            onApplySettings(host.trim(), p, token.trim(), warnTemp, critTemp, selectedIntervalMs)
                            onDismiss()
                        },
                        modifier = Modifier.weight(0.6f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F6FEB))
                    ) {
                        Text("Save & Apply", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
