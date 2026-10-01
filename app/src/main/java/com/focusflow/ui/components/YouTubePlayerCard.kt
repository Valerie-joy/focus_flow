package com.focusflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.Spacing
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Plays one curated stimulus clip.
 *
 * WebView-backed IFrame player under the hood, via the AndroidYouTubePlayer
 * library — Google's own YouTube Android Player API is deprecated. See
 * [com.focusflow.domain.dataset.StimulusDataset] for how a clip is vetted and
 * added; [videoId] is always a `Stimulus.mediaId`, never a runtime search.
 *
 * Two lifecycle defects are fixed here. Both were leaks with a visible symptom
 * during a multi-category session, which is exactly when they compounded:
 *
 *  - The view was registered as a lifecycle observer inside `AndroidView`'s
 *    `factory` and **never removed**. Each category built a new player and
 *    added another observer to the same lifecycle, so by the fifth category
 *    five players — five WebViews — were still receiving lifecycle callbacks.
 *  - `YouTubePlayerView.release()` was never called, so those WebViews kept
 *    their native resources and, in the worst case, kept decoding audio after
 *    the composable left.
 *
 * Both are now handled in a `DisposableEffect` tied to [videoId], which is
 * also what makes a category change actually tear the old player down.
 *
 * @param onVideoEnded fires when the player reports the clip reached its
 * natural end, so the assessment need not rely on a timer alone.
 * @param onError fires if the clip fails to load or play (embedding disabled,
 * no network, bad ID) so the caller can show a real fallback instead of a
 * silently frozen player.
 * @param onDurationKnown fires once the bridge reports the clip's real length
 * in seconds, so a duration cap can use it instead of guessing.
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // rememberUpdatedState so the listener — created once per videoId — always
    // calls the *current* callbacks. Capturing them directly would pin the
    // first composition's lambdas for the life of the player, so a recomposed
    // parent's newer `onVideoEnded` would never be invoked.
    val currentOnEnded by rememberUpdatedState(onVideoEnded)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnDuration by rememberUpdatedState(onDurationKnown)

    val playerViewState = remember { mutableStateOf<YouTubePlayerView?>(null) }

    DisposableEffect(videoId) {
        val playerView = YouTubePlayerView(context).apply {
            // The view must not manage the lifecycle itself as well as being
            // observed, or it double-handles pause/resume.
            enableAutomaticInitialization = false
        }
        playerViewState.value = playerView
        lifecycleOwner.lifecycle.addObserver(playerView)

        playerView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youTubePlayer.loadVideo(videoId, 0f)
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                if (state == PlayerConstants.PlayerState.ENDED) currentOnEnded()
            }

            override fun onError(
                youTubePlayer: YouTubePlayer,
                error: PlayerConstants.PlayerError
            ) {
                currentOnError(error.name)
            }

            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                currentOnDuration(duration.toInt())
            }
        })

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
            playerViewState.value = null
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .semantics {
                contentDescription = "$categoryLabel clip, $elapsedLabel elapsed"
            },
        shape = RoundedCornerShape(FocusFlowRadius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val playerView = playerViewState.value
            if (playerView != null) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(FocusFlowRadius.lg)),
                    // The view is created and owned by the DisposableEffect
                    // above, so the factory only hands it over. Creating it
                    // here instead would tie its lifetime to AndroidView's,
                    // which is not where the observer is registered.
                    factory = { playerView }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MediaChip(text = categoryLabel)
                MediaChip(text = elapsedLabel)
            }
        }
    }
}
