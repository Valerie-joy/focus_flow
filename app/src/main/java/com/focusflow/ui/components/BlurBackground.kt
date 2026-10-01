package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.focusflow.ui.theme.LocalFocusFlowColors

/**
 * The page background every screen sits on.
 *
 * Previously this ran a `rememberInfiniteTransition` driving two drifting
 * radial "glow blobs" over a three-stop gradient, redrawn on a full-screen
 * `Canvas` every frame, forever. That has been removed:
 *
 *  - **It fought the product.** This app measures whether a user's gaze stays
 *    on a stimulus. A continuously moving background is, precisely, a
 *    competing visual stimulus — on the assessment screen it was moving
 *    inside the measurement.
 *  - **It never stopped.** An infinite transition keeps the Choreographer
 *    requesting frames for as long as the screen is composed, so the device
 *    never idled while the app was open, on top of the camera and inference
 *    work already running.
 *
 * What remains is a near-flat two-stop wash (a 2-3% luminance shift, see
 * Color.kt) that gives the page a top-to-bottom orientation and nothing more.
 * The name is unchanged so the ~15 screens calling it keep working.
 *
 * @param applyStatusBarPadding content is inset below the status bar while the
 * wash still draws full-bleed behind it, which is the point of the
 * `enableEdgeToEdge()` call in MainActivity. Pass false for screens that manage
 * their own insets (one hosting a Scaffold, for instance) so padding isn't
 * applied twice.
 */
@Composable
fun BlurBackground(
    modifier: Modifier = Modifier,
    applyStatusBarPadding: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val colors = LocalFocusFlowColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.heroGradient)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier),
            content = content
        )
    }
}
