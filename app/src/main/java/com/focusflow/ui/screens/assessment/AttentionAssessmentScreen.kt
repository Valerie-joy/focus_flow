package com.focusflow.ui.screens.assessment

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.focusflow.ai.attention.AttentionTracker
import com.focusflow.camera.eyetracking.GazeAnalyzer
import com.focusflow.camera.eyetracking.GazeFrame
import com.focusflow.camera.eyetracking.itracker.GazeCalibration
import com.focusflow.camera.eyetracking.itracker.GazePointEstimator
import com.focusflow.data.local.UserPreferences
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.domain.dataset.StimulusDataset
import com.focusflow.camera.eyetracking.CalibrationGate
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.VideoPlayerCard
import com.focusflow.ui.components.YouTubePlayerCard
import com.focusflow.ui.theme.FocusFlowTheme
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Phase 5's core screen, in two steps.
 *
 * 1. A short "position yourself" setup step showing the front-camera preview
 *    with a live iris/eye overlay, so the user can confirm tracking is working
 *    before anything is measured.
 * 2. Playback: the per-category YouTube video (see CategoryVideoLibrary.kt)
 *    plays while a camera stream feeds [GazeAnalyzer] into an [AttentionTracker].
 *    A small corner self-view (PiP-style) is shown so the user has a visual
 *    confirmation the camera is active, sized and positioned to stay
 *    unobtrusive relative to the video itself.
 *
 * - Video reaches its natural end, or the [videoDurationSeconds] cap is hit
 *   (whichever comes first), with attention maintained -> [onAttentionMaintained]
 * - Sustained attention drop -> stops immediately -> [onAttentionShifted]
 * - [onSkip] lets the user move to another category; the first skip requeues
 *   it to the back, a second skip of the same category retires it for good
 *   (see [com.focusflow.viewmodel.AssessmentViewModel.skipCategory]).
 * - If YouTube playback errors, falls back to the themed-gradient placeholder
 *   card so the assessment loop still completes end to end.
 *
 * Privacy: frames are converted in memory, handed to on-device MediaPipe, and
 * released immediately — nothing is recorded or written to disk in either step,
 * and no frame ever leaves the phone. The preview in step 1 is a live surface
 * only; it is not captured.
 */
@Composable
fun AttentionAssessmentScreen(
    category: AttentionCategory,
    onAttentionMaintained: (GazeMetrics) -> Unit,
    onAttentionShifted: (GazeMetrics) -> Unit,
    onSkip: (GazeMetrics) -> Unit = {},
    videoDurationSeconds: Int = 45
) {
    var setupComplete by remember(category) { mutableStateOf(false) }

    if (!setupComplete) {
        GazeSetupStep(
            onReady = { setupComplete = true },
            onSkip = onSkip
        )
    } else {
        AttentionPlaybackStep(
            category = category,
            onAttentionMaintained = onAttentionMaintained,
            onAttentionShifted = onAttentionShifted,
            onSkip = onSkip,
            videoDurationSeconds = videoDurationSeconds
        )
    }
}

/**
 * Step 1 — live preview + gaze overlay. Continue unlocks once a face is
 * detected and gaze reads on-screen, so a session can't start with the user
 * out of frame (which previously produced instant false "attention shifted").
 */
@Composable
private fun GazeSetupStep(
    onReady: () -> Unit,
    onSkip: (GazeMetrics) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var gazeFrame by remember { mutableStateOf<GazeFrame?>(null) }
    val gate = remember { CalibrationGate() }
    var progress by remember {
        mutableStateOf(
            CalibrationGate.Progress(CalibrationGate.Status.SEARCHING_FOR_FACE, 0f, null)
        )
    }
    var analyzer by remember { mutableStateOf<GazeAnalyzer?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var cameraRetryToken by remember { mutableIntStateOf(0) }

    // A returning user (one who already has a profile) is routed straight from
    // sign-in to Dashboard and from there straight into this screen, skipping
    // the onboarding CAMERA_PERMISSION step entirely — so this screen can't
    // assume permission was already granted the way CameraPermissionScreen's
    // own downstream callers can.
    fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var cameraPermissionGranted by remember { mutableStateOf(hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> cameraPermissionGranted = granted }

    // This screen is always entered via a NavHost transition (fresh navigation,
    // or a pop back from the rating/attention-shifted screens between
    // categories). Binding the camera immediately races the frames it starts
    // producing right away against that transition's own AnimatedContent
    // measure pass still settling, which crashes with "LayoutNode should be
    // attached to an owner" — a known Compose Navigation/Animation timing
    // issue, reproducible even with camera frames already correctly delivered
    // on the main thread. Waiting for the transition to finish avoids it.
    var readyToBindCamera by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        readyToBindCamera = true
    }

    LaunchedEffect(readyToBindCamera, cameraPermissionGranted, cameraRetryToken) {
        if (!readyToBindCamera || !cameraPermissionGranted) return@LaunchedEffect
        val pv = previewView ?: return@LaunchedEffect
        cameraUnavailable = false
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e("AttentionAssessment", "Camera provider unavailable in GazeSetupStep", e)
                cameraUnavailable = true
                return@addListener
            }
            val preview = CameraPreview.Builder().build().also {
                it.setSurfaceProvider(pv.surfaceProvider)
            }
            val newAnalyzer = try {
                GazeAnalyzer(context.applicationContext) { frame ->
                    gazeFrame = frame
                    progress = gate.accept(
                        faceDetected = frame.faceDetected,
                        irisPointCount = frame.irisPoints.size,
                        headYawDegrees = frame.headYawDegrees,
                        headPitchDegrees = frame.headPitchDegrees
                    )
                }
            } catch (t: Throwable) {
                Log.e("AttentionAssessment", "GazeAnalyzer init failed in GazeSetupStep", t)
                cameraUnavailable = true
                return@addListener
            }
            analyzer = newAnalyzer

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, newAnalyzer) }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                // Previously silent — a missing/revoked CAMERA permission (or
                // any other bind failure) produced a permanently black preview
                // with no error and no clue why.
                Log.e("AttentionAssessment", "Camera bind failed in GazeSetupStep", e)
                cameraUnavailable = true
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer?.close()
            executor.shutdown()
        }
    }

    // Acquisition must be *stable* before a measured clip can start — see
    // CalibrationGate. A single good frame used to be enough, which let the
    // clip begin on a glance and immediately register a false attention shift.
    val acquisitionReady = progress.isReady

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Get comfortable",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Hold your phone so your face fits in the frame. We'll hide this once the video starts.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                if (cameraPermissionGranted) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }.also { previewView = it }
                        }
                    )

                    GazeOverlay(frame = gazeFrame)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "FocusFlow needs your front camera to measure attention during assessments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryButton(
                            text = "Allow camera access",
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when {
                    cameraUnavailable -> "Eye-tracking unavailable on this device."
                    !cameraPermissionGranted -> "Camera access needed to continue."
                    else -> when (progress.status) {
                        CalibrationGate.Status.SEARCHING_FOR_FACE -> "Looking for your face…"
                        CalibrationGate.Status.IRISES_NOT_DETECTED ->
                            "Move a little closer so both eyes are visible."
                        CalibrationGate.Status.NOT_FACING_SCREEN ->
                            "Face the screen straight on."
                        CalibrationGate.Status.STABILIZING ->
                            "Hold still… ${(progress.fraction * 100).toInt()}%"
                        CalibrationGate.Status.READY ->
                            "Great — eyes tracked steadily and on screen."
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
            if (cameraUnavailable) {
                PrimaryButton(
                    text = "Continue without eye-tracking",
                    onClick = onReady,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Retry camera",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            cameraUnavailable = false
                            cameraRetryToken++
                        }
                        .padding(8.dp)
                )
            } else {
                PrimaryButton(
                    text = "Start the video",
                    onClick = onReady,
                    enabled = acquisitionReady,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Skip this category",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        // No AttentionTracker exists yet at this point — setup
                        // hasn't started playback, so there's no partial gaze
                        // signal to report beyond "zero time observed".
                        onSkip(
                            GazeMetrics(
                                screenAttentionPercentage = 0f,
                                gazeShiftCount = 0,
                                firstDistractionMs = null,
                                blinkCount = 0,
                                actualDurationMs = 0L
                            )
                        )
                    }
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Draws the tracked irises and eye outline over the preview.
 *
 * Alignment is approximate: MediaPipe's normalized coordinates come from the
 * analysis stream, which is cropped differently from the FILL_CENTER preview.
 * That's fine for the purpose here — showing the user that tracking is live
 * and responsive — but it is not a precisely registered overlay.
 */
@Composable
private fun GazeOverlay(frame: GazeFrame?) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val f = frame ?: return@Canvas
        f.eyePoints.forEach { p ->
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = 3.dp.toPx(),
                center = Offset(p.x * size.width, p.y * size.height)
            )
        }
        f.irisPoints.forEach { p ->
            drawCircle(
                color = if (f.isLookingAtScreen) accent else Color.Red,
                radius = 4.dp.toPx(),
                center = Offset(p.x * size.width, p.y * size.height)
            )
        }
    }
}

/** Step 2 — measured playback, with a small corner self-view preview. */
@Composable
private fun AttentionPlaybackStep(
    category: AttentionCategory,
    onAttentionMaintained: (GazeMetrics) -> Unit,
    onAttentionShifted: (GazeMetrics) -> Unit,
    onSkip: (GazeMetrics) -> Unit,
    videoDurationSeconds: Int
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val tracker = remember(category) { AttentionTracker() }
    var isPlaying by remember(category) { mutableStateOf(true) }
    var elapsedSeconds by remember(category) { mutableIntStateOf(0) }
    var stopped by remember(category) { mutableStateOf(false) }
    var trackingUnavailable by remember(category) { mutableStateOf(false) }
    var previewView by remember(category) { mutableStateOf<PreviewView?>(null) }

    // The clip comes from the curated, manually verified stimulus library —
    // never a runtime search. Selection among a category's approved clips is
    // random; the dataset behind it is fixed.
    val stimulus = remember(category) { StimulusDataset.selectFor(category) }
    val videoId = stimulus?.mediaId
    var playerErrored by remember(category) { mutableStateOf(false) }
    val usingRealVideo = videoId != null && !playerErrored

    // Clips are capped regardless of the source video's real length. Five
    // uncapped videos meant 15-50 minutes of forced viewing — a poor ask for
    // anyone, and self-defeating in an attention assessment. The cap is the
    // stimulus's own per-clip limit, falling back to the caller's default.
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
    // from YouTube actually re-arms the loop instead of leaving it running
    // against a stale bound.
    LaunchedEffect(category, isPlaying, stopped, effectiveDurationSeconds) {
        while (isPlaying && !stopped && elapsedSeconds < effectiveDurationSeconds) {
            delay(1000)
            elapsedSeconds += 1
        }
        if (!stopped && elapsedSeconds >= effectiveDurationSeconds) {
            completeIfNotStopped()
        }
    }

    // Binds analysis plus a small corner self-view Preview (rendered as a PiP
    // overlay in the layout below); falls back to analysis-only if the
    // PreviewView hasn't composed yet for this category.
    DisposableEffect(category) {
        var analyzer: GazeAnalyzer? = null
        val executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = try {
                cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e("AttentionAssessment", "Camera provider unavailable in AttentionPlaybackStep", e)
                trackingUnavailable = true
                return@Runnable
            }
            // Point-of-regard mode needs a calibration good enough to trust;
            // without one the clip runs on the blendshape rule alone and the
            // iTracker model is not even loaded (it is the expensive part).
            val calibration = UserPreferences(context).getGazeCalibration()
                ?.takeIf { it.residualRms <= GazeCalibration.MAX_ACCEPTABLE_RESIDUAL }
            val estimator = calibration?.let {
                runCatching { GazePointEstimator(context.applicationContext) }
                    .onFailure { Log.w("AttentionAssessment", "iTracker unavailable, blendshape-only", it) }
                    .getOrNull()
            }
            val newAnalyzer = try {
                GazeAnalyzer(
                    context.applicationContext,
                    gazePointEstimator = estimator,
                    calibration = if (estimator != null) calibration else null
                ) { frame ->
                    if (stopped) return@GazeAnalyzer
                    val result = tracker.recordFrame(
                        faceDetected = frame.faceDetected,
                        isLookingAtScreen = frame.isLookingAtScreen,
                        eyesClosed = frame.eyesClosed,
                        // A real monotonic frame timestamp. Deriving this from the
                        // 1-second UI tick quantised every duration to whole
                        // seconds, so the 1.5s sustain threshold could never fire
                        // on time — and froze entirely whenever playback paused.
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
            } catch (t: Throwable) {
                Log.e("AttentionAssessment", "GazeAnalyzer init failed in AttentionPlaybackStep", t)
                trackingUnavailable = true
                return@Runnable
            }
            analyzer = newAnalyzer

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                // A dedicated background thread: inference used to run on the
                // main executor, competing with Compose and the YouTube
                // WebView, which is what made tracking feel laggy.
                .also { it.setAnalyzer(executor, newAnalyzer) }

            // The corner self-view PreviewView composes independently of this
            // async listener; if it hasn't landed yet for this category, bind
            // analysis alone rather than blocking tracking on it.
            val pv = previewView
            val preview = pv?.let {
                CameraPreview.Builder().build().also { p -> p.setSurfaceProvider(it.surfaceProvider) }
            }

            try {
                cameraProvider.unbindAll()
                if (preview != null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis
                    )
                }
            } catch (_: Exception) {
                // Binding can fail on rapid navigation away.
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            analyzer?.close()
            executor.shutdown()
            try {
                cameraProviderFuture.get().unbindAll()
            } catch (_: Exception) {
                // Provider may already be torn down.
            }
        }
    }

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Stay focused on\nthe video",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "We're measuring your natural attention. Just watch and relax.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (usingRealVideo) {
                YouTubePlayerCard(
                    videoId = videoId,
                    categoryLabel = category.displayName,
                    elapsedLabel = formatElapsed(elapsedSeconds),
                    onVideoEnded = { completeIfNotStopped() },
                    onError = { code ->
                        android.util.Log.e(
                            "AttentionAssessment",
                            "YouTube player error for video $videoId ($category): $code"
                        )
                        playerErrored = true
                    },
                    onDurationKnown = { seconds -> reportedDurationSeconds = seconds }
                )
            } else {
                VideoPlayerCard(
                    categoryLabel = category.displayName,
                    elapsedLabel = formatElapsed(elapsedSeconds),
                    isPlaying = isPlaying,
                    onTogglePlay = { isPlaying = !isPlaying }
                )
            }

            // Small self-view below the video so the user can confirm they're
            // in frame while being tracked. Hidden when tracking couldn't bind
            // for this category — nothing to preview in that case.
            if (!trackingUnavailable) {
                Spacer(modifier = Modifier.height(12.dp))
                AndroidView(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(80.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp)),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also { previewView = it }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (trackingUnavailable) {
                        "Eye-tracking unavailable — this category will complete on a timer."
                    } else {
                        "Keep your eyes on the screen"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Skip video",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            stopped = true
                            isPlaying = false
                            onSkip(
                                tracker.currentMetrics().copy(actualDurationMs = elapsedSeconds * 1000L)
                            )
                        }
                        .padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

@Preview(showBackground = true)
@Composable
private fun AttentionAssessmentScreenPreview() {
    FocusFlowTheme {
        AttentionAssessmentScreen(
            category = AttentionCategory.MUSIC,
            onAttentionMaintained = {},
            onAttentionShifted = {}
        )
    }
}
