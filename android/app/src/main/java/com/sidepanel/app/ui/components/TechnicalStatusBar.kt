package com.sidepanel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidepanel.app.data.protocol.MetricsStreamPayload
import com.sidepanel.app.ui.theme.BorderSubtle
import com.sidepanel.app.ui.theme.NeonAmber
import com.sidepanel.app.ui.theme.NeonCyan
import com.sidepanel.app.ui.theme.NeonGreen
import com.sidepanel.app.ui.theme.NeonPurple
import com.sidepanel.app.ui.theme.SurfaceCard
import com.sidepanel.app.ui.theme.TextMuted
import com.sidepanel.app.ui.theme.TextPrimary
import com.sidepanel.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun TechnicalStatusBar(
    telemetry: MetricsStreamPayload?,
    pingMs: Long?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(SurfaceCard, RoundedCornerShape(6.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (telemetry == null) {
            Text(
                text = "STATUS: AWAITING TELEMETRY STREAM...",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        } else {
            // GPU Power
            val powerStr = telemetry.gpu.powerWatts?.let { String.format(Locale.US, "%.0fW", it) } ?: "N/A"
            StatusChip(label = "GPU PWR", value = powerStr, color = NeonPurple)

            // GPU Fan
            val fanStr = telemetry.gpu.fanSpeedPercent?.let { "$it%" } ?: "N/A"
            StatusChip(label = "FAN", value = fanStr, color = NeonCyan)

            // Active Audio
            val audioName = telemetry.audio?.activeOutputDevice?.ifBlank { "Speakers" } ?: "Default Audio"
            val volStr = telemetry.audio?.masterVolumePercent ?: 0
            StatusChip(
                label = "AUDIO",
                value = "$volStr% ($audioName)",
                color = NeonGreen,
                maxWidth = 150.dp
            )

            // Latency
            val pingStr = pingMs?.let { "${it}ms" } ?: "N/A"
            StatusChip(label = "LATENCY", value = pingStr, color = NeonAmber)
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    value: String,
    color: Color,
    maxWidth: androidx.compose.ui.unit.Dp? = null
) {
    val mod = if (maxWidth != null) Modifier.width(maxWidth) else Modifier
    Row(
        modifier = mod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(4.dp).background(color, CircleShape))
        Text(
            text = "$label: ",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
