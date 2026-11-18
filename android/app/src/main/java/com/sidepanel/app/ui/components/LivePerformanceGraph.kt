package com.sidepanel.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidepanel.app.data.model.PerformanceHistoryPoint
import com.sidepanel.app.ui.theme.BorderHighlight
import com.sidepanel.app.ui.theme.BorderSubtle
import com.sidepanel.app.ui.theme.DarkCanvas
import com.sidepanel.app.ui.theme.NeonCyan
import com.sidepanel.app.ui.theme.NeonGreen
import com.sidepanel.app.ui.theme.NeonPurple
import com.sidepanel.app.ui.theme.SurfaceCard
import com.sidepanel.app.ui.theme.TextMuted
import com.sidepanel.app.ui.theme.TextPrimary
import com.sidepanel.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun LivePerformanceGraph(
    history: List<PerformanceHistoryPoint>,
    modifier: Modifier = Modifier,
    maxPoints: Int = 60
) {
    var showCpu by remember { mutableStateOf(true) }
    var showGpu by remember { mutableStateOf(true) }
    var showRam by remember { mutableStateOf(true) }

    val latest = history.lastOrNull()
    val cpuVal = latest?.cpuPercent ?: 0f
    val gpuVal = latest?.gpuPercent ?: 0f
    val ramVal = latest?.ramPercent ?: 0f

    Card(
        modifier = modifier
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Top Bar: Title & Series Visibility Toggle Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "PERFORMANCE HISTORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${maxPoints}s window)",
                        fontSize = 9.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeriesTogglePill(
                        label = "CPU",
                        value = String.format(Locale.US, "%.0f%%", cpuVal),
                        color = NeonCyan,
                        isActive = showCpu,
                        onToggle = { showCpu = !showCpu }
                    )
                    SeriesTogglePill(
                        label = "GPU",
                        value = String.format(Locale.US, "%.0f%%", gpuVal),
                        color = NeonPurple,
                        isActive = showGpu,
                        onToggle = { showGpu = !showGpu }
                    )
                    SeriesTogglePill(
                        label = "RAM",
                        value = String.format(Locale.US, "%.0f%%", ramVal),
                        color = NeonGreen,
                        isActive = showRam,
                        onToggle = { showRam = !showRam }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Canvas Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(DarkCanvas.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .border(1.dp, BorderSubtle.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val paddingBottom = 16f
                    val chartHeight = h - paddingBottom

                    // 1. Horizontal Percentage Grid Lines (0%, 25%, 50%, 75%, 100%)
                    val gridSteps = 4
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                    for (i in 0..gridSteps) {
                        val y = chartHeight * (1f - (i.toFloat() / gridSteps))
                        drawLine(
                            color = BorderHighlight.copy(alpha = 0.25f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = dashEffect
                        )
                    }

                    if (history.size >= 2) {
                        val count = history.size
                        val stepX = w / (maxPoints - 1).coerceAtLeast(1)
                        val startOffsetIndex = maxPoints - count
                        val startX = startOffsetIndex * stepX

                        // Helper function to build smooth curves
                        fun drawSeries(
                            values: List<Float>,
                            color: Color
                        ) {
                            val linePath = Path()
                            val fillPath = Path()

                            values.forEachIndexed { i, value ->
                                val x = startX + (i * stepX)
                                val clampedVal = value.coerceIn(0f, 100f)
                                val y = chartHeight * (1f - (clampedVal / 100f))

                                if (i == 0) {
                                    linePath.moveTo(x, y)
                                    fillPath.moveTo(x, chartHeight)
                                    fillPath.lineTo(x, y)
                                } else {
                                    val prevX = startX + ((i - 1) * stepX)
                                    val prevY = chartHeight * (1f - (values[i - 1].coerceIn(0f, 100f) / 100f))
                                    val controlX1 = prevX + (x - prevX) / 2f
                                    val controlX2 = controlX1
                                    linePath.cubicTo(controlX1, prevY, controlX2, y, x, y)
                                    fillPath.cubicTo(controlX1, prevY, controlX2, y, x, y)
                                }

                                if (i == values.lastIndex) {
                                    fillPath.lineTo(x, chartHeight)
                                    fillPath.close()

                                    // Endpoint circle glow
                                    drawCircle(color = color, radius = 4f, center = Offset(x, y))
                                    drawCircle(color = Color.White, radius = 2f, center = Offset(x, y))
                                }
                            }

                            // Fill translucent gradient under curve
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.22f),
                                        color.copy(alpha = 0.02f)
                                    ),
                                    startY = 0f,
                                    endY = chartHeight
                                )
                            )

                            // Draw continuous stroke
                            drawPath(
                                path = linePath,
                                color = color,
                                style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                            )
                        }

                        if (showRam) {
                            drawSeries(history.map { it.ramPercent }, NeonGreen)
                        }
                        if (showGpu) {
                            drawSeries(history.map { it.gpuPercent }, NeonPurple)
                        }
                        if (showCpu) {
                            drawSeries(history.map { it.cpuPercent }, NeonCyan)
                        }
                    }
                }

                // X-Axis Time Markers overlay at bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-60s", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text("-45s", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text("-30s", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text("-15s", fontSize = 8.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    Text("NOW", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextSecondary, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun SeriesTogglePill(
    label: String,
    value: String,
    color: Color,
    isActive: Boolean,
    onToggle: () -> Unit
) {
    val bg = if (isActive) color.copy(alpha = 0.15f) else Color(0xFF141922)
    val borderCol = if (isActive) color.copy(alpha = 0.6f) else BorderSubtle

    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, borderCol, RoundedCornerShape(4.dp))
            .clickable { onToggle() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (isActive) color else Color.Gray, CircleShape)
        )
        Text(
            text = "$label: $value",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isActive) TextPrimary else TextMuted
        )
    }
}
