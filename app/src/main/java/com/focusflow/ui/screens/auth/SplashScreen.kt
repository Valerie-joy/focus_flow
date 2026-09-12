package com.focusflow.ui.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.theme.FocusFlowTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * First screen shown on app launch. Purely presentational + a timed
 * navigation callback — no business logic here per the "no logic in
 * composables" architecture rule; the ViewModel/nav graph decides where
 * [onFinished] actually routes to (e.g. Welcome, or Dashboard if a session
 * already exists).
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    displayDurationMillis: Long = 2200
) {
    val logoScale = remember { Animatable(0.85f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(700))
        logoScale.animateTo(1f, animationSpec = tween(700, easing = EaseInOutSine))
        delay(displayDurationMillis)
        onFinished()
    }

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        FloatingParticles(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .alpha(logoAlpha.value)
                .scale(logoScale.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "FocusFlow",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
            Text(
                text = "Understand your attention.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * Lightweight drifting-dot particle field. Positions are randomized once
 * per composition and animated with independent infinite transitions so
 * they don't move in lockstep.
 */
@Composable
private fun FloatingParticles(modifier: Modifier = Modifier) {
    val particleCount = 18
    val seeds = remember {
        List(particleCount) { Random.nextFloat() to Random.nextFloat() }
    }
    val transition = rememberInfiniteTransition(label = "particles")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "particlePhase"
    )

    Canvas(modifier = modifier) {
        seeds.forEachIndexed { index, (baseX, baseY) ->
            val driftY = 24f * kotlin.math.sin((phase * 2 * Math.PI + index).toFloat())
            val cx = baseX * size.width
            val cy = baseY * size.height + driftY
            drawCircle(
                color = Color.White.copy(alpha = 0.10f + 0.10f * ((index % 3) / 2f)),
                radius = particleRadiusPx(index),
                center = Offset(cx, cy)
            )
        }
    }
}

// Small helper so particle radius varies (2-5dp) without extra state
private fun DrawScope.particleRadiusPx(seedIndex: Int): Float =
    (2 + (seedIndex % 4)).dp.toPx()

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    FocusFlowTheme { SplashScreen(onFinished = {}) }
}
