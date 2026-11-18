package com.sidepanel.app.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidepanel.app.data.protocol.CpuMetrics
import com.sidepanel.app.data.protocol.DiskMetrics
import com.sidepanel.app.data.protocol.GpuMetrics
import com.sidepanel.app.data.protocol.RamMetrics
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
fun CpuHardwareCard(
    cpu: CpuMetrics,
    modifier: Modifier = Modifier
) {
    BaseHardwareCard(
        title = "CPU PROCESSOR",
        accentColor = NeonCyan,
        percent = cpu.usagePercent,
        centerLabel = "LOAD",
        primaryStatLabel = "CLOCK",
        primaryStatValue = if (cpu.frequencyGhz > 0f) String.format(Locale.US, "%.2f GHz", cpu.frequencyGhz) else "N/A",
        secondaryStatLabel = "TEMP",
        secondaryStatValue = cpu.temperatureC?.let { String.format(Locale.US, "%.0f°C", it) } ?: "N/A",
        footerText = "${cpu.coreCount} Cores (${cpu.physicalCores} Phys)",
        modifier = modifier
    )
}

@Composable
fun GpuHardwareCard(
    gpu: GpuMetrics,
    modifier: Modifier = Modifier
) {
    val vramUsedGb = gpu.vramUsedBytes / (1024f * 1024f * 1024f)
    val vramTotalGb = gpu.vramTotalBytes / (1024f * 1024f * 1024f)
    val vramStr = if (gpu.vramTotalBytes > 0) {
        String.format(Locale.US, "%.1f / %.1f GB", vramUsedGb, vramTotalGb)
    } else {
        "N/A"
    }

    BaseHardwareCard(
        title = gpu.name.ifBlank { "GPU GRAPHICS" },
        accentColor = NeonPurple,
        percent = gpu.usagePercent,
        centerLabel = "CORE",
        primaryStatLabel = "TEMP",
        primaryStatValue = gpu.temperatureC?.let { String.format(Locale.US, "%.0f°C", it) } ?: "N/A",
        secondaryStatLabel = "POWER",
        secondaryStatValue = gpu.powerWatts?.let { String.format(Locale.US, "%.0f W", it) } ?: "N/A",
        footerText = "VRAM: $vramStr",
        modifier = modifier
    )
}

@Composable
fun RamHardwareCard(
    ram: RamMetrics,
    modifier: Modifier = Modifier
) {
    val usedGb = ram.usedBytes / (1024f * 1024f * 1024f)
    val totalGb = ram.totalBytes / (1024f * 1024f * 1024f)
    val availGb = ram.availableBytes / (1024f * 1024f * 1024f)

    BaseHardwareCard(
        title = "MEMORY (RAM)",
        accentColor = NeonGreen,
        percent = ram.usagePercent,
        centerLabel = "USED",
        primaryStatLabel = "USED",
        primaryStatValue = String.format(Locale.US, "%.1f GB", usedGb),
        secondaryStatLabel = "FREE",
        secondaryStatValue = String.format(Locale.US, "%.1f GB", availGb),
        footerText = String.format(Locale.US, "Total: %.1f GB (Swap %.0f%%)", totalGb, ram.swapPercent),
        modifier = modifier
    )
}

@Composable
fun DiskHardwareCard(
    disk: DiskMetrics,
    modifier: Modifier = Modifier
) {
    val drive = disk.primaryDrive
    val percent = drive?.usedPercent ?: 0f
    val freeGb = (drive?.freeBytes ?: 0L) / (1024f * 1024f * 1024f)
    val totalGb = (drive?.totalBytes ?: 0L) / (1024f * 1024f * 1024f)

    val readRate = formatThroughput(disk.readBytesPerSec)
    val writeRate = formatThroughput(disk.writeBytesPerSec)

    BaseHardwareCard(
        title = "DRIVE ${drive?.letter ?: "C:"}",
        accentColor = NeonAmber,
        percent = percent,
        centerLabel = "STORAGE",
        primaryStatLabel = "FREE",
        primaryStatValue = String.format(Locale.US, "%.0f GB", freeGb),
        secondaryStatLabel = "TOTAL",
        secondaryStatValue = String.format(Locale.US, "%.0f GB", totalGb),
        footerText = "R: $readRate | W: $writeRate",
        modifier = modifier
    )
}

@Composable
private fun BaseHardwareCard(
    title: String,
    accentColor: Color,
    percent: Float,
    centerLabel: String,
    primaryStatLabel: String,
    primaryStatValue: String,
    secondaryStatLabel: String,
    secondaryStatValue: String,
    footerText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = title.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Middle: Circular Gauge + Key Stat Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularGauge(
                    percent = percent,
                    primaryColor = accentColor,
                    centerLabelText = centerLabel,
                    strokeWidth = 6.dp,
                    modifier = Modifier.size(68.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MiniStatRow(label = primaryStatLabel, value = primaryStatValue, accentColor = accentColor)
                    MiniStatRow(label = secondaryStatLabel, value = secondaryStatValue, accentColor = TextPrimary)
                }
            }

            // Footer Subtitle / Activity Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF090D14), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = footerText,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MiniStatRow(
    label: String,
    value: String,
    accentColor: Color
) {
    Column {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatThroughput(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 B/s"
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB/s", mb)
    } else {
        String.format(Locale.US, "%.0f KB/s", kb)
    }
}
