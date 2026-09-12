package com.focusflow.ui.screens.onboarding

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.focusflow.BuildConfig
import com.focusflow.camera.eyetracking.CalibrationGate
import com.focusflow.camera.eyetracking.GazeAnalyzer
import com.focusflow.camera.eyetracking.itracker.AffineMap
import com.focusflow.camera.eyetracking.itracker.GazeCalibration
import com.focusflow.camera.eyetracking.itracker.GazePointCm
import com.focusflow.camera.eyetracking.itracker.GazePointEstimator
import com.focusflow.data.local.UserPreferences
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Success
import kotlinx.coroutines.delay

/**
 * The camera setup step, in two parts.
 *
 * 1. Acquisition — bind the front camera, detect a face, verify both irises
 *    resolve *stably*, and establish the user's neutral screen-facing head
 *    orientation. Carried by [CalibrationGate]; Continue unlocks only after
 *    a run of consecutive good frames.
 *
 * 2. Gaze calibration — when the iTracker point-of-regard model loaded, the
 *    user looks at five dots in turn while the model's raw (cm-from-camera)
 *    output is collected. An affine fit maps that output onto this screen
 *    ([GazeCalibration]); a fit within tolerance is saved to
 *    [UserPreferences] and the assessment then decides "looking at the
 *    screen" from *where* the eyes point rather than only whether they are
 *    deflected. Every exit from this part is graceful: no model, a poor fit,
 *    or a tap on "skip" all leave the app in the blendshape-only mode it had
 *    before, never blocked.
 *
 * The preview never records: ImageAnalysis frames are converted in memory,
 * handed to on-device MediaPipe (and iTracker) and released. No frame,
 * landmark or derived per-frame value is written to disk or leaves the device.
 */
@Composable
fun CameraCalibrationScreen(
    onCalibrationComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var analyzer by remember { mutableStateOf<GazeAnalyzer?>(null) }
    var calibrationUnavailable by remember { mutableStateOf(false) }
    var gazePointAvailable by remember { mutableStateOf(false) }
    val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

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
    // Filled on the main thread (GazeAnalyzer delivers there); read by the
    // dot loop, also on the main thread.
    val dotSamples = remember { List(GazeCalibration.TARGETS.size) { mutableListOf<GazePointCm>() } }
    var fitResult by remember { mutableStateOf<AffineMap?>(null) }
    var fitFailed by remember { mutableStateOf(false) }
    var faceCropPreview by remember { mutableStateOf<Bitmap?>(null) }

    val transition = rememberInfiniteTransition(label = "calibrationPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1400), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    // The dot sequence. Settle first (a saccade to a new dot plus the eyes
    // landing takes a few hundred ms), then collect; the per-dot median in
    // GazeCalibration.fit discards what noise remains.
    LaunchedEffect(phase) {
        if (phase != Phase.CALIBRATING) return@LaunchedEffect
        dotSamples.forEach { it.clear() }
        fitResult = null
        fitFailed = false
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
            GazeCalibration.median(dotSamples[i])?.let { GazeCalibration.Sample(it, GazeCalibration.TARGETS[i]) }
        }
        val fit = GazeCalibration.fit(samples)
        if (fit != null && fit.residualRms <= GazeCalibration.MAX_ACCEPTABLE_RESIDUAL) {
            UserPreferences(context).saveGazeCalibration(fit)
            fitResult = fit
        } else {
            fitFailed = true
        }
        phase = Phase.RESULT
    }

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = when (phase) {
                    Phase.RESULT -> if (fitResult != null) "Gaze calibrated" else "Calibration didn't take"
                    else -> "Let's calibrate your focus"
                },
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = when (phase) {
                    Phase.ACQUIRING -> "Look at the dot and hold still for a moment"
                    Phase.CALIBRATING -> "Follow the dot with your eyes"
                    Phase.RESULT -> fitResult?.let {
                        "We can now tell where on the screen you're looking (≈${(it.residualRms * 100).toInt()}% of screen)."
                    } ?: "We'll measure attention from eye deflection instead — that works fine too."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = try {
                                cameraProviderFuture.get()
                            } catch (e: Exception) {
                                android.util.Log.e("CameraCalibration", "Camera provider unavailable", e)
                                calibrationUnavailable = true
                                return@addListener
                            }

                            val preview = CameraPreview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            // iTracker is optional: if the model can't load on
                            // this device the screen is exactly what it was
                            // before, with no gaze-calibration part.
                            val estimator = runCatching { GazePointEstimator(ctx.applicationContext) }
                                .onFailure { android.util.Log.w("CameraCalibration", "iTracker unavailable", it) }
                                .getOrNull()
                            gazePointAvailable = estimator != null

                            val newAnalyzer = try {
                                GazeAnalyzer(
                                    ctx.applicationContext,
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
                                    if (cm != null) {
                                        if (collecting && activeDot in dotSamples.indices) dotSamples[activeDot] += cm
                                        if (BuildConfig.DEBUG) {
                                            analyzer?.lastFaceCrop?.let { faceCropPreview = it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                android.util.Log.e("CameraCalibration", "GazeAnalyzer init failed", t)
                                estimator?.close()
                                calibrationUnavailable = true
                                return@addListener
                            }
                            analyzer = newAnalyzer

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                // Background thread — running inference on the
                                // main executor competed with Compose and made
                                // detection feel laggy.
                                .also { it.setAnalyzer(analysisExecutor, newAnalyzer) }

                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner, cameraSelector, preview, imageAnalysis
                                )
                            } catch (e: Exception) {
                                // Binding can fail if the lifecycle is already destroyed
                                // (e.g. rapid navigation away); nothing to recover here.
                                android.util.Log.e("CameraCalibration", "Camera bind failed", e)
                                calibrationUnavailable = true
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )

                CalibrationTarget(eyesDetected = acquisitionReady, pulse = pulse)

                // Debug-only: the live 224² face crop iTracker actually sees.
                // The quickest way to catch a rotation, mirror or box mistake
                // on a real device — it should show an upright face with the
                // forehead in frame.
                val crop = faceCropPreview
                if (BuildConfig.DEBUG && crop != null) {
                    Image(
                        bitmap = crop.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    analyzer?.close()
                    analysisExecutor.shutdown()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            StatusRow(
                progress = progress,
                calibrationUnavailable = calibrationUnavailable,
                phase = phase,
                fitResult = fitResult
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Keep a comfortable distance and blink naturally.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            when {
                calibrationUnavailable -> PrimaryButton(text = "Continue", onClick = onCalibrationComplete, enabled = true)

                phase == Phase.ACQUIRING && gazePointAvailable -> {
                    PrimaryButton(
                        text = "Calibrate gaze",
                        onClick = { phase = Phase.CALIBRATING },
                        enabled = acquisitionReady
                    )
                    TextButton(onClick = onCalibrationComplete, enabled = acquisitionReady) {
                        Text("Continue without calibrating")
                    }
                }

                phase == Phase.RESULT && fitFailed -> {
                    PrimaryButton(text = "Try again", onClick = { phase = Phase.CALIBRATING }, enabled = true)
                    TextButton(onClick = onCalibrationComplete) { Text("Continue anyway") }
                }

                else -> PrimaryButton(
                    text = "Continue",
                    onClick = onCalibrationComplete,
                    enabled = acquisitionReady && phase != Phase.CALIBRATING
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Full-window overlay for the dot sequence. The map is fitted in
        // normalised *window* coordinates, and the assessment judges
        // on-screen in the same units, so the two stay consistent whatever the
        // insets are.
        if (phase == Phase.CALIBRATING) {
            CalibrationDotOverlay(activeDot = activeDot, collecting = collecting)
        }
    }
}

private enum class Phase { ACQUIRING, CALIBRATING, RESULT }

private const val SETTLE_MS = 700L
private const val COLLECT_MS = 1300L

@Composable
private fun CalibrationDotOverlay(activeDot: Int, collecting: Boolean) {
    val ringScale by animateFloatAsState(
        targetValue = if (collecting) 0.55f else 1f,
        animationSpec = tween(if (collecting) COLLECT_MS.toInt() else 250),
        label = "dotRing"
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (activeDot in GazeCalibration.TARGETS.indices) {
            val t = GazeCalibration.TARGETS[activeDot]
            val dot = 28.dp
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * t.x - dot / 2, y = maxHeight * t.y - dot / 2)
                    .size(dot),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(dot * 2.4f * ringScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                )
                Box(
                    modifier = Modifier
                        .size(dot * 0.55f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Text(
            text = "Keep your head still — follow the dot with your eyes only" +
                if (activeDot >= 0) "  (${activeDot + 1}/${GazeCalibration.TARGETS.size})" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp, start = 24.dp, end = 24.dp)
        )
    }
}

@Composable
private fun CalibrationTarget(eyesDetected: Boolean, pulse: Float) {
    val ringColor = if (eyesDetected) Success else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size((72 * pulse).dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(ringColor.copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(ringColor)
        )
    }
}

@Composable
private fun StatusRow(
    progress: CalibrationGate.Progress,
    calibrationUnavailable: Boolean,
    phase: Phase,
    fitResult: AffineMap?
) {
    val colors = LocalFocusFlowColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(colors.glassSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        val (text, good) = when {
            calibrationUnavailable -> "Camera calibration unavailable — you can continue" to false
            phase == Phase.RESULT && fitResult != null ->
                "Gaze calibrated on ${fitResult.pointCount} points" to true
            phase == Phase.RESULT ->
                "Not enough steady readings — try again in better light" to false
            else -> when (progress.status) {
                CalibrationGate.Status.SEARCHING_FOR_FACE -> "Looking for your face…" to false
                CalibrationGate.Status.IRISES_NOT_DETECTED ->
                    "Move a little closer so both eyes are visible" to false
                CalibrationGate.Status.NOT_FACING_SCREEN -> "Face the screen straight on" to false
                CalibrationGate.Status.STABILIZING ->
                    "Hold still… ${(progress.fraction * 100).toInt()}%" to false
                CalibrationGate.Status.READY -> "Eyes tracked steadily — you're ready" to true
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (good) Success else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraCalibrationScreenPreview() {
    FocusFlowTheme {
        // Camera preview won't render inside @Preview (no real camera in
        // the layout inspector), but the surrounding UI can still be checked.
        CameraCalibrationScreen(onCalibrationComplete = {})
    }
}
