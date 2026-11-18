package com.sidepanel.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidepanel.app.ui.theme.BorderSubtle
import com.sidepanel.app.ui.theme.TextPrimary
import com.sidepanel.app.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-performance, hardware-accelerated Canvas circular gauge with animated sweep.
 */
@Composable
fun CircularGauge(
    percent: Float,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    secondaryColor: Color = primaryColor.copy(alpha = 0.6f),
    trackColor: Color = BorderSubtle.copy(alpha = 0.4f),
    strokeWidth: Dp = 7.dp,
    centerValueText: String? = null,
    centerLabelText: String? = null
) {
    val clamped = percent.coerceIn(0f, 100f)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped / 100f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "GaugeSweep"
    )

    // Standard 260-degree speedometer arc (from 140° through 260°)
    val startAngle = 140f
    val sweepTotal = 260f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val diameter = size.minDimension - strokePx
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)

            // 1. Background Inactive Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // 2. Foreground Animated Active Arc with Gradient Brush
            val sweepCurrent = sweepTotal * animatedProgress
            if (sweepCurrent > 1f) {
                val brush = Brush.sweepGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = 0.7f),
                        primaryColor,
                        primaryColor
                    ),
                    center = center
                )

                drawArc(
                    brush = brush,
                    startAngle = startAngle,
                    sweepAngle = sweepCurrent,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // 3. Glowing indicator point at current tip
                val angleRad = Math.toRadians((startAngle + sweepCurrent).toDouble())
                val radius = diameter / 2f
                val tipX = center.x + (radius * cos(angleRad)).toFloat()
                val tipY = center.y + (radius * sin(angleRad)).toFloat()

                drawCircle(
                    color = primaryColor,
                    radius = strokePx * 0.7f,
                    center = Offset(tipX, tipY)
                )
                drawCircle(
                    color = Color.White,
                    radius = strokePx * 0.35f,
                    center = Offset(tipX, tipY)
                )
            }
        }

        // Center Readout
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val displayValue = centerValueText ?: "${clamped.toInt()}%"
            Text(
                text = displayValue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = TextPrimary
            )
            if (centerLabelText != null) {
                Text(
                    text = centerLabelText.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
