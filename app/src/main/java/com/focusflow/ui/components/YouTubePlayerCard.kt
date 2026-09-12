package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Embeds real YouTube playback for one video (WebView-based IFrame Player
 * under the hood, via the AndroidYouTubePlayer library — Google's own
 * "YouTube Android Player API" is deprecated). Keeps the same category
 * chip / timer overlay from the original placeholder VideoPlayerCard so
 * the assessment screen's visual language doesn't change, just what's
 * actually playing underneath.
 *
 * @param videoId a YouTube video ID (see CategoryVideoLibrary.kt for where
 * these come from and how to vet one)
 * @param onVideoEnded fires when YouTube reports the video reached its
 * natural end (PlayerConstants.PlayerState.ENDED) — the assessment screen
 * uses this instead of a hardcoded timer where possible.
 * @param onError fires if the player fails to load/play (e.g. the video ID
 * is a placeholder, embedding is disabled, or there's no network) so the
 * caller can show a fallback instead of a silently frozen player.
 * @param onDurationKnown fires once the IFrame bridge reports the video's
 * real length (seconds), so callers relying on a fixed playback-time cap
 * (e.g. a safety-cap timer) can use the real duration instead of guessing.
 */
@Composable
fun YouTubePlayerCard(
    videoId: String,
    categoryLabel: String,
    elapsedLabel: String,
    onVideoEnded: () -> Unit,
    onError: (String) -> Unit,
    onDurationKnown: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(28.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val playerView = YouTubePlayerView(context)
                lifecycleOwner.lifecycle.addObserver(playerView)

                playerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.loadVideo(videoId, 0f)
                    }

                    override fun onStateChange(
                        youTubePlayer: YouTubePlayer,
                        state: PlayerConstants.PlayerState
                    ) {
                        if (state == PlayerConstants.PlayerState.ENDED) {
                            onVideoEnded()
                        }
                    }

                    override fun onError(
                        youTubePlayer: YouTubePlayer,
                        error: PlayerConstants.PlayerError
                    ) {
                        onError(error.name)
                    }

                    override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                        onDurationKnown(duration.toInt())
                    }
                })

                playerView
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Chip(text = categoryLabel)
            Chip(text = elapsedLabel)
        }
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(PaddingValues(horizontal = 12.dp, vertical = 6.dp))
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}
