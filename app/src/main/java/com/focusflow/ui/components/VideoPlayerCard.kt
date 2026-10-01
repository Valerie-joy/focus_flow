package com.focusflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/**
 * The fallback surface shown when the real stimulus clip cannot play.
 *
 * **On the Media3/ExoPlayer question.** The old `TODO` here asked for a
 * migration to an ExoPlayer `PlayerView`. Reading how this composable is
 * actually reached, that migration would be wrong to make today: this card has
 * never played video and there is no video for it to play. Stimuli are YouTube
 * IDs from the curated [com.focusflow.domain.dataset.StimulusDataset], played
 * by [YouTubePlayerCard]; this card is only reached when that player reports
 * an error or a category has no clip. There are no local or progressive-HTTP
 * video assets in the project, and ExoPlayer cannot play a YouTube watch URL.
 *
 * So adding `androidx.media3` would add a dependency with nothing to decode,
 * to render a player whose source does not exist — while leaving the actual
 * defect in place, which was that this card *impersonated a working player*.
 * It showed a play/pause button controlling a gradient, so a user whose clip
 * failed to load saw a video player that appeared to be playing and was told
 * nothing. Meanwhile the assessment behind it kept measuring their attention
 * on a blank rectangle.
 *
 * It is now an honest unavailable-state: it says the clip could not load, it
 * says what that means for the assessment, and the transport control only
 * appears when there is a timed placeholder session to actually pause.
 *
 * If local video assets are added later, Media3 becomes the right call and
 * this is the file to change — the surrounding contract (aspect ratio, the
 * category/elapsed overlay, the caller in [com.focusflow.ui.screens.assessment.AttentionPlaybackStep])
 * would not need to move.
 *
 * @param unavailableReason when non-null, the clip genuinely failed and this
 * explains why; the card renders as an error state rather than a player.
 */
@Composable
fun VideoPlayerCard(
    categoryLabel: String,
    elapsedLabel: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    unavailableReason: String? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FALLBACK_MIN_HEIGHT),
        shape = RoundedCornerShape(FocusFlowRadius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.VideocamOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizing.iconLg)
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = unavailableReason ?: "No clip is available for this category.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (unavailableReason == null) {
                    Spacer(Modifier.height(Spacing.md))
                    // Only offered when there is a timed placeholder session
                    // to pause. Previously this control was always present and
                    // toggled nothing the user could see.
                    FilledIconButton(
                        onClick = onTogglePlay,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(Sizing.minTouchTarget)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = if (isPlaying) {
                                "Pause this category's timer"
                            } else {
                                "Resume this category's timer"
                            }
                        )
                    }
                }
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

/**
 * The small label overlaid on a media surface.
 *
 * Shared with [YouTubePlayerCard] — both previously declared a private `Chip`
 * of their own, with the same 35%-black fill that could not be guaranteed
 * legible against arbitrary video frames. This one uses an opaque themed
 * container so the label reads over any frame.
 */
@Composable
internal fun MediaChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(FocusFlowRadius.pill),
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp))
        )
    }
}

/**
 * Roughly the height a 16:9 card would occupy on a typical phone, so the
 * fallback does not visibly jump relative to the real player — but a floor
 * rather than a fixed size, so its message can never be clipped.
 */
private val FALLBACK_MIN_HEIGHT = 200.dp
