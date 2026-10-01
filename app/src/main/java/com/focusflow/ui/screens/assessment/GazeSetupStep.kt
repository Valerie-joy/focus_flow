package com.focusflow.ui.screens.assessment

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusflow.camera.eyetracking.CalibrationGate
import com.focusflow.camera.eyetracking.CameraFailure
import com.focusflow.camera.eyetracking.GazeAnalyzer
import com.focusflow.camera.eyetracking.bindGazeCamera
import com.focusflow.camera.eyetracking.GazeFrame
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SecondaryButton
import com.focusflow.ui.components.StatusPill
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.components.TextAction
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.MotionDurations
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

object GazeSetupTestTags {
    const val START = "setup_start"
    const val STATUS = "setup_status"
}

/**
 * Step 1 of an assessment: readiness.
 *
 * The user positions themselves while a live preview and gaze overlay confirm
 * tracking is working, and "Start" only unlocks once [CalibrationGate] reports
 * *stable* acquisition — a single good frame used to be enough, which let a
 * clip begin on a glance and immediately register a false attention shift.
 *
 * What this step adds over the previous version is **telling the user what the
 * session involves before it starts**: how long it runs, what the phone needs
 * to be doing, and what the lighting has to be like. That information existed
 * nowhere in the app, so a user's first assessment was also the first time
 * they learned they had to sit still for it.
 */
@Composable
fun GazeSetupStep(
    onReady: () -> Unit,
    onSkip: (GazeMetrics) -> Unit,
    onExit: () -> Unit,
    estimatedDurationSeconds: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalFocusFlowColors.current

    // Held as a State object rather than read into this composable via `by`.
    //
    // MediaPipe delivers ~30 frames a second. Reading the value here made every
    // frame invalidate the whole of GazeSetupStep: the expectations card, the
    // status pill, the progress bar and both buttons were all re-evaluated 30
    // times a second while the user was simply sitting still. Passing the State
    // down means only GazeOverlay's Canvas — which is the one thing that
    // genuinely depends on per-frame data — is invalidated.
    //
    // `progress` below is deliberately still read here: CalibrationGate emits a
    // coarse status that changes a handful of times per session, and the Start
    // button's enabled state depends on it.
    val gazeFrame = remember { mutableStateOf<GazeFrame?>(null) }
    val gate = remember { CalibrationGate() }
    var progress by remember {
        mutableStateOf(
            CalibrationGate.Progress(CalibrationGate.Status.SEARCHING_FOR_FACE, 0f, null)
        )
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var failure by remember { mutableStateOf<CameraFailure?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    // A returning user is routed from sign-in straight to Dashboard and from
    // there into an assessment, skipping the onboarding permission step — so
    // this screen cannot assume permission was already granted.
    fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var permissionGranted by remember { mutableStateOf(hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) failure = null
    }

    // This screen is always entered via a NavHost transition. Binding the
    // camera immediately races the frames it starts producing against that
    // transition's AnimatedContent measure pass still settling, which crashes
    // with "LayoutNode should be attached to an owner" — a known Compose
    // Navigation/Animation timing issue. Waiting for the transition avoids it.
    var readyToBind by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(TRANSITION_SETTLE_MS)
        readyToBind = true
    }

    DisposableEffect(readyToBind, permissionGranted, previewView, retryToken) {
        if (!readyToBind || !permissionGranted || previewView == null) {
            return@DisposableEffect onDispose { }
        }
        failure = null
        val session = bindGazeCamera(
            context = context,
            lifecycleOwner = lifecycleOwner,
            executor = Executors.newSingleThreadExecutor(),
            analyzerFactory = {
                GazeAnalyzer(context.applicationContext) { frame ->
                    gazeFrame.value = frame
                    progress = gate.accept(
                        faceDetected = frame.faceDetected,
                        irisPointCount = frame.irisPoints.size,
                        headYawDegrees = frame.headYawDegrees,
                        headPitchDegrees = frame.headPitchDegrees
                    )
                }
            },
            previewView = previewView,
            onFailure = { failure = it }
        )
        onDispose { session.close() }
    }

    val acquisitionReady = progress.isReady

    // Nothing has been measured yet at this point, so leaving costs the user
    // nothing and needs no confirmation — but it does need to be *possible*.
    // Back previously popped out of the graph from here too, which happened to
    // be the right outcome by accident; routing it through onExit makes both
    // steps agree on what leaving means.
    BackHandler { onExit() }

    BlurBackground(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = Sizing.maxContentWidth)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.gutter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "Get set up",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = "Hold your phone so your whole face fits in the frame. " +
                        "This preview disappears once the clip starts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                SessionExpectations(
                    estimatedDurationSeconds = estimatedDurationSeconds,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(FocusFlowRadius.lg))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    if (permissionGranted) {
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
                                .padding(Spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "FocusFlow needs the front camera to measure where " +
                                    "your attention goes. Frames are analysed on this phone " +
                                    "and never saved.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            PrimaryButton(
                                text = "Allow camera access",
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                TrackingReadiness(
                    failure = failure,
                    permissionGranted = permissionGranted,
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(GazeSetupTestTags.STATUS)
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                if (failure != null) {
                    // Tracking is broken, not the app. Offer the honest
                    // choice — retry, or run the clip without measurement —
                    // rather than stranding the user on a screen whose only
                    // button is permanently disabled.
                    PrimaryButton(text = "Try the camera again", onClick = { retryToken++ })
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    SecondaryButton(text = "Continue without eye tracking", onClick = onReady)
                } else {
                    PrimaryButton(
                        text = "Start the clip",
                        onClick = onReady,
                        enabled = acquisitionReady,
                        modifier = Modifier.testTag(GazeSetupTestTags.START)
                    )
                }

                TextAction(
                    text = "Skip this category",
                    onClick = {
                        // Setup hasn't started playback, so there is no partial
                        // gaze signal to report beyond "nothing observed".
                        onSkip(
                            GazeMetrics(
                                screenAttentionPercentage = 0f,
                                gazeShiftCount = 0,
                                firstDistractionMs = null,
                                blinkCount = 0,
                                actualDurationMs = 0L
                            )
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }
    }
}

/**
 * What the session is going to ask of the user, stated before it starts.
 *
 * Kept to four short lines — the point is to set expectations, not to make the
 * user read an instruction manual while an assessment waits.
 */
@Composable
private fun SessionExpectations(estimatedDurationSeconds: Int, modifier: Modifier = Modifier) {
    val minutes = (estimatedDurationSeconds + 59) / 60
    val duration = if (estimatedDurationSeconds < 90) {
        "about $estimatedDurationSeconds seconds"
    } else {
        "about $minutes minutes"
    }

    GlassCard(modifier = modifier, padding = Spacing.sm) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = "This clip runs for $duration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Sit where the light is on your face, not behind you. Keep the phone " +
                    "steady — propped up works better than held. Watch normally; blinking " +
                    "and small movements are expected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Turns [CalibrationGate]'s state into one instruction and a progress bar.
 *
 * The gate's stabilisation fraction is shown as an actual progress bar rather
 * than a percentage buried in a sentence, because it is the only thing on the
 * screen that tells the user their holding-still is working. The number is
 * real — it comes from the gate's own frame accounting — so no confidence is
 * being invented here.
 */
@Composable
private fun TrackingReadiness(
    failure: CameraFailure?,
    permissionGranted: Boolean,
    progress: CalibrationGate.Progress,
    modifier: Modifier = Modifier
) {
    when {
        failure != null -> MessageBanner(
            message = when (failure) {
                CameraFailure.PERMISSION ->
                    "FocusFlow doesn't have camera access, so attention can't be measured."
                CameraFailure.TRACKING_UNSUPPORTED ->
                    "Eye tracking isn't supported on this device. You can still watch the clip, " +
                        "but no attention data will be recorded."
                CameraFailure.UNAVAILABLE ->
                    "The camera couldn't start. Close any other app using it and try again."
            },
            tone = StatusTone.WARNING,
            modifier = modifier
        )

        !permissionGranted -> MessageBanner(
            message = "Camera access is needed to continue.",
            tone = StatusTone.WARNING,
            modifier = modifier
        )

        else -> {
            val (tone, message) = when (progress.status) {
                CalibrationGate.Status.SEARCHING_FOR_FACE ->
                    StatusTone.NEUTRAL to "Looking for your face…"
                CalibrationGate.Status.IRISES_NOT_DETECTED ->
                    StatusTone.WARNING to "Move a little closer so both eyes are visible."
                CalibrationGate.Status.NOT_FACING_SCREEN ->
                    StatusTone.WARNING to "Face the screen straight on."
                CalibrationGate.Status.STABILIZING ->
                    StatusTone.NEUTRAL to "Hold still…"
                CalibrationGate.Status.READY ->
                    StatusTone.SUCCESS to "Tracking steadily — you're good to go."
            }

            val animatedFraction by animateFloatAsState(
                targetValue = if (progress.status == CalibrationGate.Status.READY) {
                    1f
                } else {
                    progress.fraction
                },
                animationSpec = tween(MotionDurations.PROGRESS),
                label = "acquisitionProgress"
            )

            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                // announce = true: this is the one status on the screen that
                // changes without the user touching anything, and it gates the
                // Start button, so it has to reach a TalkBack user.
                StatusPill(text = message, tone = tone, announce = true)
                if (progress.status == CalibrationGate.Status.STABILIZING ||
                    progress.status == CalibrationGate.Status.READY
                ) {
                    LinearProgressIndicator(
                        progress = { animatedFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        drawStopIndicator = {}
                    )
                }
            }
        }
    }
}

/**
 * Draws the tracked irises and eye outline over the preview.
 *
 * Alignment is approximate: MediaPipe's normalized coordinates come from the
 * analysis stream, which is cropped differently from the FILL_CENTER preview.
 * That is fine for the purpose — showing the user that tracking is live and
 * responsive — but it is not a precisely registered overlay.
 *
 * The iris marker changes *size* as well as color when gaze leaves the screen,
 * so the signal doesn't depend on telling teal from red.
 *
 * Takes a [State] rather than a value so per-frame updates stay inside the draw
 * phase; see the note on `gazeFrame` in [GazeSetupStep].
 */
@Composable
private fun GazeOverlay(frame: State<GazeFrame?>, modifier: Modifier = Modifier) {
    val onScreenColor = MaterialTheme.colorScheme.primary
    val offScreenColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier.fillMaxSize()) {
        // Read inside the draw scope, so a new frame schedules a redraw without
        // recomposing anything — not even this composable.
        val f = frame.value ?: return@Canvas
        f.eyePoints.forEach { p ->
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = 3.dp.toPx(),
                center = Offset(p.x * size.width, p.y * size.height)
            )
        }
        f.irisPoints.forEach { p ->
            drawCircle(
                color = if (f.isLookingAtScreen) onScreenColor else offScreenColor,
                radius = if (f.isLookingAtScreen) 5.dp.toPx() else 3.dp.toPx(),
                center = Offset(p.x * size.width, p.y * size.height)
            )
        }
    }
}

/** How long to let a nav transition settle before binding the camera. */
private const val TRANSITION_SETTLE_MS = 350L
