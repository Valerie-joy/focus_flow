package com.focusflow.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focusflow.BuildConfig
import com.focusflow.R
import com.focusflow.camera.eyetracking.CalibrationGate
import com.focusflow.camera.eyetracking.CameraFailure
import com.focusflow.camera.eyetracking.GazeAnalyzer
import com.focusflow.camera.eyetracking.bindGazeCamera
import com.focusflow.camera.eyetracking.itracker.AffineMap
import com.focusflow.camera.eyetracking.itracker.CalibrationQuality
import com.focusflow.camera.eyetracking.itracker.GazeCalibration
import com.focusflow.camera.eyetracking.itracker.GazePointCm
import com.focusflow.camera.eyetracking.itracker.GazePointEstimator
import com.focusflow.data.local.UserPreferences
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.MessageBanner
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SecondaryButton
import com.focusflow.ui.components.StatusPill
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.MotionDurations
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

object CalibrationTestTags {
    const val STATUS = "calibration_status"
    const val PRIMARY_ACTION = "calibration_primary"
}

/**
 * Camera setup, in two parts.
 *
 * 1. **Acquisition** — bind the front camera, detect a face, verify both
 *    irises resolve *stably*, and establish the user's neutral screen-facing
 *    orientation. Carried by [CalibrationGate]; Continue unlocks only after a
 *    run of consecutive good frames.
 * 2. **Gaze calibration** — when the iTracker point-of-regard model loaded,
 *    the user looks at five dots while the model's raw (cm-from-camera) output
 *    is collected. An affine fit maps that onto this screen
 *    ([GazeCalibration]); a fit within tolerance is saved and the assessment
 *    then decides "looking at the screen" from *where* the eyes point rather
 *    than only whether they are deflected.
 *
 * Every exit is graceful: no model, a poor fit, or a skip all leave the app in
 * the blendshape-only mode it had before. Nothing is ever blocked.
 *
 * Fixed here:
 *
 *  - **It reported an error as an accuracy.** See [CalibrationQuality] — the
 *    success line printed the fit's RMS residual as a bare "% of screen"
 *    immediately after claiming success.
 *  - **It never checked camera permission.** The camera was bound from inside
 *    an `AndroidView` factory with no permission check, so a user who reached
 *    this screen without granting it saw a black square and "Looking for your
 *    face…" forever, with Continue permanently disabled — an unescapable
 *    screen.
 *  - **Camera failure had no retry**, and binding from a view factory meant it
 *    could not be re-run. It now uses the shared [bindGazeCamera] keyed on a
 *    retry token, like the assessment screens.
 *  - **It could not scroll.** A fixed square preview plus a headline, status
 *    and two buttons overflowed a short screen at large font scales, clipping
 *    the buttons off the bottom.
 *  - **Failure advice was one generic line** for every failure mode, including
 *    "no readings at all", which has a different cause and a different fix.
 */
@Composable
fun CameraCalibrationScreen(
    onCalibrationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var permissionGranted by remember { mutableStateOf(hasCameraPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permissionGranted = it }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraFailure by remember { mutableStateOf<CameraFailure?>(null) }
    var gazePointAvailable by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }

    val gate = remember { CalibrationGate() }
    var progress by remember {
        mutableStateOf(
            CalibrationGate.Progress(CalibrationGate.Status.SEARCHING_FOR_FACE, 0f, null)
        )
    }
    val acquisitionReady = progress.isReady

    // ---- gaze-calibration state --------------------------------------------
    var phase by remember { mutableStateOf(Phase.ACQUIRING) }
    var activeDot by remember { mutableIntStateOf(-1) }
    var collecting by remember { mutableStateOf(false) }
    // Filled from the analyzer callback (GazeAnalyzer delivers on the main
    // thread) and read by the dot loop, also on the main thread.
    val dotSamples = remember { List(GazeCalibration.TARGETS.size) { mutableListOf<GazePointCm>() } }
    var fitResult by remember { mutableStateOf<AffineMap?>(null) }
    var failureAdvice by remember { mutableStateOf<String?>(null) }
    var attemptCount by remember { mutableIntStateOf(0) }
    var faceCropPreview by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(permissionGranted, previewView, retryToken) {
        if (!permissionGranted || previewView == null) {
            return@DisposableEffect onDispose { }
        }
        cameraFailure = null
        val session = bindGazeCamera(
            context = context,
            lifecycleOwner = lifecycleOwner,
            executor = Executors.newSingleThreadExecutor(),
            analyzerFactory = {
                // iTracker is optional: if the model can't load on this device
                // the screen is exactly what it was before, with no
                // gaze-calibration part offered.
                val estimator = runCatching { GazePointEstimator(context.applicationContext) }
                    .getOrNull()
                gazePointAvailable = estimator != null
                // Self-reference so the frame callback can read the analyzer's
                // own last face crop for the debug overlay below.
                lateinit var created: GazeAnalyzer
                created = GazeAnalyzer(
                    context.applicationContext,
                    gazePointEstimator = estimator,
                    calibration = null // raw cm is what calibration collects
                ) { frame ->
                    progress = gate.accept(
                        faceDetected = frame.faceDetected,
                        irisPointCount = frame.irisPoints.size,
                        headYawDegrees = frame.headYawDegrees,
                        headPitchDegrees = frame.headPitchDegrees
                    )
                    val cm = frame.gazePointCm
                    if (cm != null && collecting && activeDot in dotSamples.indices) {
                        dotSamples[activeDot] += cm
                    }
                    if (BuildConfig.DEBUG && cm != null) {
                        created.lastFaceCrop?.let { crop ->
                            faceCropPreview = crop.copy(
                                crop.config ?: Bitmap.Config.ARGB_8888,
                                false
                            )
                        }
                    }
                }
                created
            },
            previewView = previewView,
            onFailure = { cameraFailure = it }
        )
        onDispose { session.close() }
    }

    // The dot sequence. Settle first (a saccade to a new dot plus the eyes
    // landing takes a few hundred ms), then collect; the per-dot median in
    // GazeCalibration.fit discards what noise remains.
    LaunchedEffect(phase, attemptCount) {
        if (phase != Phase.CALIBRATING) return@LaunchedEffect
        dotSamples.forEach { it.clear() }
        fitResult = null
        failureAdvice = null
        for (i in GazeCalibration.TARGETS.indices) {
            activeDot = i
            collecting = false
            delay(SETTLE_MS)
            collecting = true
            delay(COLLECT_MS)
            collecting = false
        }
        activeDot = -1

        val samples = GazeCalibration.TARGETS.indices.mapNotNull { i ->
            GazeCalibration.median(dotSamples[i])?.let {
                GazeCalibration.Sample(it, GazeCalibration.TARGETS[i])
            }
        }
        val fit = GazeCalibration.fit(samples)
        if (fit != null && fit.residualRms <= GazeCalibration.MAX_ACCEPTABLE_RESIDUAL) {
            UserPreferences(context).saveGazeCalibration(fit)
            fitResult = fit
        } else {
            // Advice now depends on *how* it failed: no readings at all is a
            // lighting or occlusion problem, too few dots is a fixation
            // problem, and a poor fit across all five is something else again.
            failureAdvice = CalibrationQuality.failureAdvice(samples.size)
        }
        phase = Phase.RESULT
    }

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
                Spacer(modifier = Modifier.height(Spacing.lg))
                Text(
                    text = when {
                        phase == Phase.RESULT && fitResult != null -> "Gaze calibrated"
                        phase == Phase.RESULT -> "Calibration didn't take"
                        else -> "Set up eye tracking"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { heading() }
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = when (phase) {
                        Phase.ACQUIRING ->
                            "Get your face in the frame and hold still for a moment."
                        Phase.CALIBRATING -> "Follow the dot with your eyes."
                        Phase.RESULT -> fitResult?.let {
                            CalibrationQuality.describe(it.residualRms, it.pointCount)
                        } ?: "Attention will be measured from eye deflection instead — " +
                            "that works, just without screen position."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Spacing.md))

                if (phase == Phase.ACQUIRING) {
                    PositioningTips(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(Spacing.md))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(FocusFlowRadius.lg))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
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
                        AcquisitionTarget(ready = acquisitionReady)

                        // Debug-only: the live 224² face crop iTracker sees.
                        // The quickest way to catch a rotation, mirror or box
                        // mistake on a real device.
                        val crop = faceCropPreview
                        if (BuildConfig.DEBUG && crop != null) {
                            Image(
                                bitmap = crop.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(Spacing.xs)
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(FocusFlowRadius.xs))
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(Spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Eye tracking needs the front camera. Frames are " +
                                    "analysed on this phone and never saved.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            PrimaryButton(
                                text = "Allow camera access",
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))

                CalibrationStatus(
                    permissionGranted = permissionGranted,
                    cameraFailure = cameraFailure,
                    phase = phase,
                    progress = progress,
                    fitResult = fitResult,
                    failureAdvice = failureAdvice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CalibrationTestTags.STATUS)
                )

                Spacer(modifier = Modifier.height(Spacing.lg))

                CalibrationActions(
                    permissionGranted = permissionGranted,
                    cameraFailure = cameraFailure,
                    phase = phase,
                    fitFailed = failureAdvice != null,
                    acquisitionReady = acquisitionReady,
                    gazePointAvailable = gazePointAvailable,
                    onRetryCamera = { retryToken++ },
                    onStartCalibration = {
                        attemptCount++
                        phase = Phase.CALIBRATING
                    },
                    onContinue = onCalibrationComplete
                )
                Spacer(modifier = Modifier.height(Spacing.xl))
            }
        }

        // Full-window overlay for the dot sequence. The map is fitted in
        // normalised *window* coordinates, and the assessment judges on-screen
        // in the same units, so the two stay consistent whatever the insets are.
        if (phase == Phase.CALIBRATING) {
            CalibrationDotOverlay(activeDot = activeDot, collecting = collecting)
        }
    }
}

private enum class Phase { ACQUIRING, CALIBRATING, RESULT }

private const val SETTLE_MS = 700L
private const val COLLECT_MS = 1300L

/**
 * What to actually do with the phone, before acquisition starts failing for
 * reasons the user can't guess.
 *
 * The screen previously offered one line — "Keep a comfortable distance and
 * blink naturally" — pinned to the bottom, which said nothing about the two
 * things that actually break iris detection: backlighting and a moving phone.
 */
@Composable
private fun PositioningTips(modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, padding = Spacing.sm) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            TipRow("Prop the phone up", "Steadier than holding it, and it stays put.")
            TipRow("Face the light", "A window behind you leaves your eyes in shadow.")
            TipRow("Arm's length away", "Close enough that both eyes fill the frame clearly.")
        }
    }
}

@Composable
private fun TipRow(title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.padding(start = Spacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The acquisition reticle.
 *
 * The pulse was driven by a `rememberInfiniteTransition` that kept running in
 * every phase, including after the result. It now animates only on the
 * transition into the ready state, which is the moment worth marking.
 */
@Composable
private fun AcquisitionTarget(ready: Boolean, modifier: Modifier = Modifier) {
    val ringScale by animateFloatAsState(
        targetValue = if (ready) 0.8f else 1f,
        animationSpec = tween(MotionDurations.STANDARD),
        label = "acquisitionRing"
    )
    val ringColor = if (ready) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier.size(TARGET_SIZE * ringScale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(ringColor.copy(alpha = 0.22f))
        )
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(ringColor)
        )
    }
}

/** The single status line, plus the acquisition progress bar while stabilising. */
@Composable
private fun CalibrationStatus(
    permissionGranted: Boolean,
    cameraFailure: CameraFailure?,
    phase: Phase,
    progress: CalibrationGate.Progress,
    fitResult: AffineMap?,
    failureAdvice: String?,
    modifier: Modifier = Modifier
) {
    when {
        !permissionGranted -> MessageBanner(
            message = "Camera access is needed to set up eye tracking. You can skip this " +
                "and still use FocusFlow — assessments just won't measure attention.",
            tone = StatusTone.WARNING,
            modifier = modifier
        )

        cameraFailure != null -> MessageBanner(
            message = when (cameraFailure) {
                CameraFailure.PERMISSION ->
                    "FocusFlow doesn't have camera access, so eye tracking can't be set up."
                CameraFailure.TRACKING_UNSUPPORTED ->
                    "Eye tracking isn't supported on this device. You can continue without it."
                CameraFailure.UNAVAILABLE ->
                    "The camera couldn't start. Close any other app using it, then try again."
            },
            tone = StatusTone.WARNING,
            modifier = modifier
        )

        phase == Phase.RESULT && failureAdvice != null -> MessageBanner(
            message = failureAdvice,
            tone = StatusTone.WARNING,
            modifier = modifier
        )

        phase == Phase.RESULT && fitResult != null -> {
            val tone = when (CalibrationQuality.bandFor(fitResult.residualRms)) {
                CalibrationQuality.Band.GOOD -> StatusTone.SUCCESS
                else -> StatusTone.WARNING
            }
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                StatusPill(
                    text = CalibrationQuality.bandFor(fitResult.residualRms).label,
                    tone = tone,
                    announce = true
                )
            }
        }

        else -> {
            val (tone, text) = when (progress.status) {
                CalibrationGate.Status.SEARCHING_FOR_FACE ->
                    StatusTone.NEUTRAL to "Looking for your face…"
                CalibrationGate.Status.IRISES_NOT_DETECTED ->
                    StatusTone.WARNING to "Move a little closer so both eyes are visible."
                CalibrationGate.Status.NOT_FACING_SCREEN ->
                    StatusTone.WARNING to "Face the screen straight on."
                CalibrationGate.Status.STABILIZING ->
                    StatusTone.NEUTRAL to "Hold still…"
                CalibrationGate.Status.READY ->
                    StatusTone.SUCCESS to "Eyes tracked steadily — you're ready."
            }
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                StatusPill(text = text, tone = tone, announce = true)
                if (progress.status == CalibrationGate.Status.STABILIZING ||
                    progress.status == CalibrationGate.Status.READY
                ) {
                    val fraction by animateFloatAsState(
                        targetValue = if (progress.status == CalibrationGate.Status.READY) {
                            1f
                        } else {
                            progress.fraction
                        },
                        animationSpec = tween(MotionDurations.PROGRESS),
                        label = "acquisitionProgress"
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
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
 * The action area.
 *
 * Extracted so the branching is readable in one place: there are seven
 * distinct states here (no permission, camera failed, acquiring with and
 * without the gaze model, calibrating, and a result that either took or
 * didn't), and every one of them must offer a way forward.
 */
@Composable
private fun CalibrationActions(
    permissionGranted: Boolean,
    cameraFailure: CameraFailure?,
    phase: Phase,
    fitFailed: Boolean,
    acquisitionReady: Boolean,
    gazePointAvailable: Boolean,
    onRetryCamera: () -> Unit,
    onStartCalibration: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        when {
            // No permission, or the camera is unusable: continuing is the only
            // sensible primary action, and it must always be available.
            !permissionGranted -> {
                PrimaryButton(
                    text = stringResource(R.string.action_skip_for_now),
                    onClick = onContinue,
                    modifier = Modifier.testTag(CalibrationTestTags.PRIMARY_ACTION)
                )
            }

            cameraFailure != null -> {
                PrimaryButton(
                    text = stringResource(R.string.action_try_again),
                    onClick = onRetryCamera,
                    modifier = Modifier.testTag(CalibrationTestTags.PRIMARY_ACTION)
                )
                SecondaryButton(text = "Continue without eye tracking", onClick = onContinue)
            }

            phase == Phase.CALIBRATING -> {
                // Nothing to press mid-sequence; a disabled button states that
                // more clearly than an absent one, which would shift the layout.
                PrimaryButton(
                    text = "Calibrating…",
                    onClick = {},
                    enabled = false,
                    loading = true,
                    modifier = Modifier.testTag(CalibrationTestTags.PRIMARY_ACTION)
                )
            }

            phase == Phase.RESULT && fitFailed -> {
                PrimaryButton(
                    text = "Try calibration again",
                    onClick = onStartCalibration,
                    modifier = Modifier.testTag(CalibrationTestTags.PRIMARY_ACTION)
                )
                SecondaryButton(text = "Continue anyway", onClick = onContinue)
            }

            phase == Phase.RESULT -> {
                PrimaryButton(
                    text = "Continue",
                    onClick = onContinue,
                    modifier = Modifier.testTag(CalibrationTestTags.PRIMARY_ACTION)
                )
                SecondaryButton(text = "Calibrate again", onClick = onStartCalibration)
            }

            gazePointAvailable -> {
                PrimaryButton(
                    text = "Calibrate gaze",
                    onClick = onStartCalibration,
                    enabled = acquisitionReady,
                    modifier = Modifier.testTag(CalibrationTestTags.PRIMARY_ACTION)
                )
                SecondaryButton(text = "Skip calibration", onClick = onContinue)
            }

            else -> {
                PrimaryButton(
                    text = "Continue",
                    onClick = onContinue,
                    enabled = acquisitionReady,
                    modifier = Modifier.testTag(CalibrationTestTags.PRIMARY_ACTION)
                )
            }
        }
    }
}

@Composable
private fun CalibrationDotOverlay(activeDot: Int, collecting: Boolean) {
    val ringScale by animateFloatAsState(
        targetValue = if (collecting) 0.55f else 1f,
        animationSpec = tween(if (collecting) COLLECT_MS.toInt() else 250),
        label = "dotRing"
    )
    val total = GazeCalibration.TARGETS.size

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .semantics {
                contentDescription = if (activeDot >= 0) {
                    "Calibrating, dot ${activeDot + 1} of $total. " +
                        "Keep your head still and look at the dot."
                } else {
                    "Calibrating"
                }
            }
    ) {
        if (activeDot in GazeCalibration.TARGETS.indices) {
            val t = GazeCalibration.TARGETS[activeDot]
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * t.x - DOT_SIZE / 2, y = maxHeight * t.y - DOT_SIZE / 2)
                    .size(DOT_SIZE),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(DOT_SIZE * 2.4f * ringScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                )
                Box(
                    modifier = Modifier
                        .size(DOT_SIZE * 0.55f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Spacing.xl, start = Spacing.gutter, end = Spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // Progress through the sequence, so the user knows how much is
            // left. Previously this was a "(2/5)" suffix inside a sentence.
            LinearProgressIndicator(
                progress = {
                    if (activeDot < 0) 0f else (activeDot + 1).toFloat() / total
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                drawStopIndicator = {}
            )
            Text(
                text = "Keep your head still — follow the dot with your eyes only",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val DOT_SIZE = 28.dp
private val TARGET_SIZE = 76.dp

@Preview(showBackground = true)
@Composable
private fun CameraCalibrationScreenPreview() {
    FocusFlowTheme {
        // The camera preview won't render inside @Preview, but the surrounding
        // UI can still be checked.
        CameraCalibrationScreen(onCalibrationComplete = {})
    }
}
