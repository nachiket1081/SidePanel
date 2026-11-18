package com.sidepanel.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidepanel.app.ui.theme.AlertRed
import com.sidepanel.app.ui.theme.BorderSubtle
import com.sidepanel.app.ui.theme.DarkCanvas
import com.sidepanel.app.ui.theme.NeonAmber
import com.sidepanel.app.ui.theme.NeonCyan
import com.sidepanel.app.ui.theme.NeonGreen
import com.sidepanel.app.ui.theme.SurfaceCard
import com.sidepanel.app.ui.theme.TextMuted
import com.sidepanel.app.ui.theme.TextPrimary
import com.sidepanel.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun TemperaturePanel(
    cpuTempC: Float?,
    gpuTempC: Float?,
    modifier: Modifier = Modifier,
    warnThreshold: Float = 75f,
    criticalThreshold: Float = 85f
) {
    Card(
        modifier = modifier
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFFFF5252), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "THERMAL SENSORS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "Warn: ${warnThreshold.toInt()}°C | Crit: ${criticalThreshold.toInt()}°C",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted
                )
            }

            // CPU Temp Row
            TemperatureBarRow(
                sensorName = "CPU Core Package",
                tempC = cpuTempC,
                warnThreshold = warnThreshold,
                criticalThreshold = criticalThreshold
            )

            // GPU Temp Row
            TemperatureBarRow(
                sensorName = "GPU Core",
                tempC = gpuTempC,
                warnThreshold = warnThreshold,
                criticalThreshold = criticalThreshold
            )
        }
    }
}

@Composable
private fun TemperatureBarRow(
    sensorName: String,
    tempC: Float?,
    warnThreshold: Float,
    criticalThreshold: Float
) {
    val (statusLabel, statusColor) = when {
        tempC == null -> Pair("NO SENSOR", TextMuted)
        tempC >= criticalThreshold -> Pair("CRITICAL", AlertRed)
        tempC >= warnThreshold -> Pair("ELEVATED", NeonAmber)
        tempC >= 65f -> Pair("WARM", Color(0xFFFFD166))
        else -> Pair("NORMAL", NeonGreen)
    }

    val displayTemp = if (tempC != null) String.format(Locale.US, "%.0f°C", tempC) else "N/A"
    val progress = ((tempC ?: 0f) / 100f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "TempBarProgress"
    )

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sensorName,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = statusColor
                    )
                }

                Text(
                    text = displayTemp,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = statusColor
                )
            }
        }

        // Horizontal Temperature Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(DarkCanvas, RoundedCornerShape(3.dp))
                .border(0.5.dp, BorderSubtle, RoundedCornerShape(3.dp))
        ) {
            if (tempC != null && animatedProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(statusColor, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}
