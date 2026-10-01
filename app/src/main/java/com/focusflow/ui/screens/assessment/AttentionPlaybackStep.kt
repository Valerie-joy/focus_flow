package com.focusflow.ui.screens.assessment

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusflow.R
import com.focusflow.ai.attention.AttentionTracker
import com.focusflow.camera.eyetracking.CameraFailure
import com.focusflow.camera.eyetracking.GazeAnalyzer
import com.focusflow.camera.eyetracking.bindGazeCamera
import com.focusflow.camera.eyetracking.itracker.GazeCalibration
import com.focusflow.camera.eyetracking.itracker.GazePointEstimator
import com.focusflow.data.local.UserPreferences
import com.focusflow.domain.dataset.StimulusDataset
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.components.TextAction
import com.focusflow.ui.components.VideoPlayerCard
import com.focusflow.ui.components.YouTubePlayerCard
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.MotionDurations
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.min

private const val TAG = "AttentionPlayback"

object PlaybackTestTags {
    const val PROGRESS = "playback_progress"
    const val TRACKING_STATUS = "playback_tracking_status"
    const val SKIP = "playback_skip"
    const val EXIT = "playback_exit"
}

/**
 * Step 2 of an assessment: the measured clip.
 *
 * This is the screen where the measurement actually happens, so the design
 * rule is different from everywhere else in the app — **the stimulus is the
 * interface**. Changes from the previous version follow from that:
 *
 *  - The page heading and explanatory paragraph above the video are gone. Two
 *    lines of prose sitting above the clip during a *sustained attention*
 *    measurement is text competing with the thing being measured.
 *  - Progress is now shown, which it wasn't. Not knowing how much of a clip is
 *    left is itself a reason to look away, so a slim determinate bar and a
 *    remaining-time readout replace the bare elapsed counter.
 *  - Tracking quality is surfaced as one unobtrusive dot with a label, rather
 *    than either nothing at all or raw gaze values. The user needs to know
 *    "am I being measured", not a landmark count.
 *  - Skipping now asks for confirmation, because a mis-tap on the old bare
 *    text discarded the category irreversibly — and the second skip of a
 *    category retires it from the session for good.
 */
@Composable
fun AttentionPlaybackStep(
    category: AttentionCategory,
    onAttentionMaintained: (GazeMetrics) -> Unit,
    onAttentionShifted: (GazeMetrics) -> Unit,
    onSkip: (GazeMetrics) -> Unit,
    onExit: () -> Unit,
    videoDurationSeconds: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val tracker = remember(category) { AttentionTracker() }
    var isPlaying by remember(category) { mutableStateOf(true) }
    var elapsedSeconds by remember(category) { mutableIntStateOf(0) }
    var stopped by remember(category) { mutableStateOf(false) }
    var trackingFailure by remember(category) { mutableStateOf<CameraFailure?>(null) }
    var previewView by remember(category) { mutableStateOf<PreviewView?>(null) }
    var showSkipConfirmation by remember(category) { mutableStateOf(false) }
    var showExitConfirmation by remember(category) { mutableStateOf(false) }
    // Drives the small live tracking indicator. Held as a plain Boolean
    // updated from the analyzer callback rather than exposing frame data to
    // the UI, so a 30fps stream can't drive 30 recompositions a second — this
    // only changes when the *state* flips.
    var gazeOnScreen by remember(category) { mutableStateOf(true) }

    // The clip comes from the curated, manually verified stimulus library —
    // never a runtime search.
    val stimulus = remember(category) { StimulusDataset.selectFor(category) }
    val videoId = stimulus?.mediaId
    var playerErrored by remember(category) { mutableStateOf(false) }
    val usingRealVideo = videoId != null && !playerErrored

    // Clips are capped regardless of the source video's real length.
    val cappedDurationSeconds = stimulus?.durationLimitSeconds ?: videoDurationSeconds
    var reportedDurationSeconds by remember(category) { mutableStateOf<Int?>(null) }
    val effectiveDurationSeconds = min(
        reportedDurationSeconds ?: cappedDurationSeconds,
        cappedDurationSeconds
    )

    val onMaintainedState = rememberUpdatedState(onAttentionMaintained)
    val onShiftedState = rememberUpdatedState(onAttentionShifted)

    fun completeIfNotStopped() {
        if (!stopped) {
            stopped = true
            onMaintainedState.value(
                tracker.currentMetrics().copy(actualDurationMs = elapsedSeconds * 1000L)
            )
        }
    }

    // Keyed on effectiveDurationSeconds as well, so a duration arriving later
    // from the player re-arms the loop instead of running against a stale bound.
    LaunchedEffect(category, isPlaying, stopped, effectiveDurationSeconds) {
        while (isPlaying && !stopped && elapsedSeconds < effectiveDurationSeconds) {
            delay(1000)
            elapsedSeconds += 1
        }
        if (!stopped && elapsedSeconds >= effectiveDurationSeconds) {
            completeIfNotStopped()
        }
    }

    DisposableEffect(category, previewView) {
        val session = bindGazeCamera(
            context = context,
            lifecycleOwner = lifecycleOwner,
            executor = Executors.newSingleThreadExecutor(),
            analyzerFactory = {
                // Point-of-regard mode needs a calibration good enough to
                // trust; without one the clip runs on the blendshape rule
                // alone and the iTracker model is not even loaded (it is the
                // expensive part).
                val calibration = UserPreferences(context).getGazeCalibration()
                    ?.takeIf { it.residualRms <= GazeCalibration.MAX_ACCEPTABLE_RESIDUAL }
                val estimator = calibration?.let {
                    runCatching { GazePointEstimator(context.applicationContext) }
                        .onFailure { Log.w(TAG, "iTracker unavailable, blendshape-only", it) }
                        .getOrNull()
                }
                GazeAnalyzer(
                    context.applicationContext,
                    gazePointEstimator = estimator,
                    calibration = if (estimator != null) calibration else null
                ) { frame ->
                    if (stopped) return@GazeAnalyzer
                    if (frame.isLookingAtScreen != gazeOnScreen) {
                        gazeOnScreen = frame.isLookingAtScreen
                    }
                    val result = tracker.recordFrame(
                        faceDetected = frame.faceDetected,
                        isLookingAtScreen = frame.isLookingAtScreen,
                        eyesClosed = frame.eyesClosed,
                        // A real monotonic frame timestamp — deriving this from
                        // the 1-second UI tick quantised every duration to whole
                        // seconds, so the 1.5s sustain threshold could never fire
                        // on time.
                        timestampMs = frame.timestampMs,
                        decidedByGazePoint = frame.decidedByGazePoint,
                        gazeInferenceMs = frame.gazeInferenceMs
                    )
                    if (result.attentionDroppedSustained && !stopped) {
                        stopped = true
                        isPlaying = false
                        onShiftedState.value(
                            tracker.currentMetrics().copy(actualDurationMs = elapsedSeconds * 1000L)
                        )
                    }
                }
            },
            previewView = previewView,
            onFailure = { trackingFailure = it }
        )
        onDispose { session.close() }
    }

    val cameraPreviewLabel = stringResource(R.string.a11y_camera_preview)
    val remainingSeconds = (effectiveDurationSeconds - elapsedSeconds).coerceAtLeast(0)
    val progressFraction = if (effectiveDurationSeconds > 0) {
        (elapsedSeconds.toFloat() / effectiveDurationSeconds).coerceIn(0f, 1f)
    } else 0f

    BlurBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = Sizing.maxContentWidth)
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter)
            ) {
                Spacer(modifier = Modifier.height(Spacing.sm))

                PlaybackProgress(
                    categoryLabel = category.displayName,
                    progressFraction = progressFraction,
                    remainingSeconds = remainingSeconds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PlaybackTestTags.PROGRESS)
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                if (usingRealVideo) {
                    YouTubePlayerCard(
                        videoId = videoId,
                        categoryLabel = category.displayName,
                        elapsedLabel = formatElapsed(elapsedSeconds),
                        onVideoEnded = { completeIfNotStopped() },
                        onError = { code ->
                            Log.e(TAG, "Player error for video $videoId ($category): $code")
                            playerErrored = true
                        },
                        onDurationKnown = { seconds -> reportedDurationSeconds = seconds }
                    )
                } else {
                    VideoPlayerCard(
                        categoryLabel = category.displayName,
                        elapsedLabel = formatElapsed(elapsedSeconds),
                        isPlaying = isPlaying,
                        onTogglePlay = { isPlaying = !isPlaying },
                        unavailableReason = if (playerErrored) {
                            "This clip couldn't load — check your connection. " +
                                "You can skip to another category."
                        } else null
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrackingIndicator(
                        failure = trackingFailure,
                        gazeOnScreen = gazeOnScreen,
                        modifier = Modifier.testTag(PlaybackTestTags.TRACKING_STATUS)
                    )

                    // Self-view: small and beside the status, so the user can
                    // confirm they're in frame without it becoming a second
                    // thing to watch. Hidden when tracking couldn't bind —
                    // there is nothing to preview in that case.
                    if (trackingFailure == null) {
                        AndroidView(
                            modifier = Modifier
                                .width(SELF_VIEW_WIDTH)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(FocusFlowRadius.sm))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .semantics { contentDescription = cameraPreviewLabel },
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }.also { previewView = it }
                            }
                        )
                    }
                }

                if (trackingFailure != null) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    MessageBanner(
                        message = "Eye tracking isn't running, so this category will finish on " +
                            "a timer and won't contribute attention data.",
                        tone = StatusTone.WARNING
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextAction(
                        text = stringResource(R.string.assessment_end_session),
                        onClick = { showExitConfirmation = true },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(PlaybackTestTags.EXIT)
                    )
                    TextAction(
                        text = stringResource(R.string.assessment_skip_category),
                        onClick = { showSkipConfirmation = true },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(PlaybackTestTags.SKIP)
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
        }
    }

    // Back during a measured clip used to pop straight out of the assessment
    // graph, discarding the whole session with no warning and no way to undo
    // it. Intercepting Back routes it to the same confirmation the explicit
    // "End session" control uses, so there is exactly one way to leave and it
    // is always deliberate.
    BackHandler(enabled = !stopped) { showExitConfirmation = true }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("End this assessment?") },
            text = {
                Text(
                    "Categories you've already finished are kept. This one won't be " +
                        "measured, and the session will end here."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    stopped = true
                    isPlaying = false
                    onExit()
                }) { Text("End session") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Keep watching") }
            }
        )
    }

    if (showSkipConfirmation) {
        AlertDialog(
            onDismissRequest = { showSkipConfirmation = false },
            title = { Text("Skip ${category.displayName}?") },
            text = {
                Text(
                    "You'll move to a different category. Skipping the same one twice " +
                        "removes it from this session."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSkipConfirmation = false
                    stopped = true
                    isPlaying = false
                    onSkip(
                        tracker.currentMetrics().copy(actualDurationMs = elapsedSeconds * 1000L)
                    )
                }) { Text("Skip") }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirmation = false }) { Text("Keep watching") }
            }
        )
    }
}

/**
 * Session progress: which category, how far through, how long is left.
 *
 * Determinate rather than a spinner, and paired with a remaining-time readout,
 * because "how much longer" is the question that makes people look away.
 */
@Composable
private fun PlaybackProgress(
    categoryLabel: String,
    progressFraction: Float,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(MotionDurations.PROGRESS),
        label = "clipProgress"
    )
    val progressDescription = stringResource(
        R.string.assessment_a11y_clip_progress,
        (progressFraction * 100).toInt()
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(
                    R.string.assessment_time_left,
                    formatElapsed(remainingSeconds)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .semantics { contentDescription = progressDescription },
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            drawStopIndicator = {}
        )
    }
}

/**
 * The "am I being measured" indicator.
 *
 * Deliberately the smallest thing on the screen. It answers one question and
 * exposes no raw gaze values — a landmark count or a confidence float means
 * nothing to the person being assessed, and inviting them to watch a number
 * fluctuate is inviting them to stop watching the clip.
 *
 * It is *not* a live region: gaze crossing on and off screen many times a
 * minute would make TalkBack talk over the clip continuously. The state is
 * still readable on demand via its content description.
 */
@Composable
private fun TrackingIndicator(
    failure: CameraFailure?,
    gazeOnScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalFocusFlowColors.current
    val (dotColor, label) = when {
        failure != null ->
            MaterialTheme.colorScheme.error to stringResource(R.string.assessment_status_not_tracking)
        gazeOnScreen ->
            colors.success to stringResource(R.string.assessment_status_tracking)
        else ->
            colors.warning to stringResource(R.string.assessment_status_eyes_off)
    }

    val statusDescription = stringResource(R.string.assessment_a11y_tracking_status, label)

    Row(
        modifier = modifier.semantics { contentDescription = statusDescription },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(Spacing.xs)
                .clip(RoundedCornerShape(FocusFlowRadius.pill))
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

private val SELF_VIEW_WIDTH = 64.dp
