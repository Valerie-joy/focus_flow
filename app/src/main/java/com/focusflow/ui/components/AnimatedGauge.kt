package com.focusflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.Accent
import com.focusflow.ui.theme.Error
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Primary
import com.focusflow.ui.theme.Warning
import kotlin.math.cos
import kotlin.math.sin

/**
 * Semicircular "speedometer" style gauge for a single 0-100 score (e.g.
 * Attention Strength Score). Track colors sweep error -> warning -> primary
 * -> accent so low/high scores read at a glance.
 */
@Composable
fun AnimatedGauge(
    score: Int, // 0..100
    modifier: Modifier = Modifier,
    label: String = "Attention Score"
) {
    val colors = LocalFocusFlowColors.current
    val animatedScore by animateFloatAsState(
        targetValue = score.coerceIn(0, 100).toFloat(),
        animationSpec = tween(durationMillis = 1000),
        label = "gaugeScore"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val strokeWidth = 16.dp.toPx()
            val diameter = size.width - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

            // Track (half circle, 180deg)
            drawArc(
                color = colors.glassBorder,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            // Value arc
            drawArc(
                brush = Brush.horizontalGradient(listOf(Error, Warning, Primary, Accent)),
                startAngle = 180f,
                sweepAngle = 180f * (animatedScore / 100f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // Needle
            val center = Offset(size.width / 2f, size.height - strokeWidth / 2f)
            val angleDeg = 180f + 180f * (animatedScore / 100f)
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val needleLength = diameter / 2f - strokeWidth
            val needleEnd = Offset(
                x = center.x + (needleLength * cos(angleRad)).toFloat(),
                y = center.y + (needleLength * sin(angleRad)).toFloat()
            )
            drawLine(
                color = colors.isDark.let { if (it) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF3B2F3A) },
                start = center,
                end = needleEnd,
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Primary,
                radius = 7.dp.toPx(),
                center = center
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${animatedScore.toInt()}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
