package com.focusflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusflow.ui.theme.Accent
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Primary
import kotlin.math.cos
import kotlin.math.sin

data class RadarChartEntry(val label: String, val value: Float) // value 0f..1f

/**
 * Spider/radar chart comparing a score across N categories at once — used
 * on the full attention-analysis screen so all 9 categories can be seen
 * relative to each other in one shape, not just as a ranked list.
 */
@Composable
fun RadarChart(
    entries: List<RadarChartEntry>,
    modifier: Modifier = Modifier,
    ringCount: Int = 4
) {
    val colors = LocalFocusFlowColors.current
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900),
        label = "radarProgress"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        if (entries.isEmpty()) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = (size.minDimension / 2f) * 0.72f
        val angleStep = (2 * Math.PI / entries.size).toFloat()

        fun pointFor(index: Int, radiusFraction: Float): Offset {
            val angle = -Math.PI.toFloat() / 2f + angleStep * index
            return Offset(
                x = center.x + radiusFraction * maxRadius * cos(angle),
                y = center.y + radiusFraction * maxRadius * sin(angle)
            )
        }

        // Background rings
        for (ring in 1..ringCount) {
            val fraction = ring / ringCount.toFloat()
            val path = Path().apply {
                entries.indices.forEach { i ->
                    val p = pointFor(i, fraction)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, color = colors.glassBorder, style = Stroke(width = 1.dp.toPx()))
        }

        // Axis lines + labels
        entries.forEachIndexed { i, entry ->
            val outer = pointFor(i, 1f)
            drawLine(colors.glassBorder, center, outer, strokeWidth = 1.dp.toPx())

            val labelPoint = pointFor(i, 1.18f)
            val measured = textMeasurer.measure(
                entry.label,
                style = TextStyle(fontSize = 10.sp, color = labelColor)
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(labelPoint.x - measured.size.width / 2f, labelPoint.y - measured.size.height / 2f)
            )
        }

        // Data polygon
        val dataPath = Path().apply {
            entries.indices.forEach { i ->
                val p = pointFor(i, entries[i].value.coerceIn(0f, 1f) * animatedProgress)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(
            path = dataPath,
            brush = Brush.radialGradient(
                listOf(Primary.copy(alpha = 0.35f), Accent.copy(alpha = 0.15f)),
                center = center,
                radius = maxRadius
            )
        )
        drawPath(dataPath, color = Primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        entries.indices.forEach { i ->
            val p = pointFor(i, entries[i].value.coerceIn(0f, 1f) * animatedProgress)
            drawCircle(color = Primary, radius = 3.dp.toPx(), center = p)
        }
    }
}
