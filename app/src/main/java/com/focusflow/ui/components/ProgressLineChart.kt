package com.focusflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.Accent
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Primary

data class ProgressPoint(val label: String, val score: Int) // score 0..100

/**
 * Trend line for "progress history" — score over successive assessment
 * sessions. Deliberately simple (no zoom/pan/tooltips) since it's a
 * summary view, not an analytics tool.
 */
@Composable
fun ProgressLineChart(points: List<ProgressPoint>, modifier: Modifier = Modifier) {
    val colors = LocalFocusFlowColors.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800),
        label = "lineChartProgress"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            if (points.size < 2) return@Canvas

            val paddingX = 8.dp.toPx()
            val paddingY = 12.dp.toPx()
            val chartWidth = size.width - paddingX * 2
            val chartHeight = size.height - paddingY * 2
            val stepX = chartWidth / (points.size - 1)

            fun pointFor(index: Int): Offset {
                val fraction = (points[index].score / 100f).coerceIn(0f, 1f)
                return Offset(
                    x = paddingX + stepX * index,
                    y = paddingY + chartHeight * (1f - fraction)
                )
            }

            // Grid lines
            for (i in 0..3) {
                val y = paddingY + chartHeight * (i / 3f)
                drawLine(colors.glassBorder, Offset(paddingX, y), Offset(size.width - paddingX, y), strokeWidth = 1.dp.toPx())
            }

            val visibleCount = (points.size * animatedProgress).toInt().coerceAtLeast(1)
            val visiblePoints = (0 until visibleCount).map { pointFor(it) }

            if (visiblePoints.size >= 2) {
                val linePath = Path().apply {
                    visiblePoints.forEachIndexed { i, p ->
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                }
                drawPath(
                    linePath,
                    brush = Brush.horizontalGradient(listOf(Primary, Accent)),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            visiblePoints.forEach { p ->
                drawCircle(color = Primary, radius = 4.dp.toPx(), center = p)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    style = TextStyle(fontSize = 10.sp, textAlign = TextAlign.Center),
                    color = labelColor
                )
            }
        }
    }
}
