package com.focusflow.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.focusflow.ui.theme.LocalFocusFlowColors
import kotlin.math.sin

/**
 * Full-bleed ambient background: a soft base gradient with two slowly
 * drifting glow blobs layered on top, used on Splash / Welcome / Calibration
 * screens to create the "floating particles / gradient movement" feel from
 * the spec. Cheap enough to run continuously — pure Canvas, no bitmaps.
 */
@Composable
fun BlurBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = LocalFocusFlowColors.current
    val transition = rememberInfiniteTransition(label = "ambient")

    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Base ambient gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.heroGradient)
        )

        // Drifting glow blobs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val blob1X = w * (0.5f + 0.3f * sin(Math.toRadians(drift.toDouble())).toFloat())
            val blob1Y = h * (0.25f + 0.15f * sin(Math.toRadians(drift * 1.3).toDouble())).toFloat()

            val blob2X = w * (0.5f + 0.35f * sin(Math.toRadians((drift + 180) * 0.8).toDouble())).toFloat()
            val blob2Y = h * (0.75f + 0.12f * sin(Math.toRadians(drift * 0.6).toDouble())).toFloat()

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(blob1X, blob1Y),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(blob1X, blob1Y)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = if (colors.isDark) 0.0f else 0.05f), Color.Transparent),
                    center = Offset(blob2X, blob2Y),
                    radius = w * 0.5f
                ),
                radius = w * 0.5f,
                center = Offset(blob2X, blob2Y)
            )
        }

        // Only the content gets pushed clear of the status bar — the gradient
        // and glow blobs above keep drawing full-bleed behind it, which is the
        // point of enableEdgeToEdge() (see MainActivity).
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) { content() }
    }
}
