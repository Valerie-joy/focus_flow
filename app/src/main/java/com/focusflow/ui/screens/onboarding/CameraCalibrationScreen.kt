package com.focusflow.ui.screens.onboarding

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.focusflow.camera.eyetracking.CalibrationGate
import com.focusflow.camera.eyetracking.GazeAnalyzer
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Success

/**
 * The PID's camera setup step: bind the front camera, detect a face, verify
 * both irises resolve *stably*, and establish the user's neutral screen-facing
 * head orientation before an assessment is allowed to begin.
 *
 * The stability requirement is carried by [CalibrationGate] — Continue unlocks
 * only after a run of consecutive good frames, not on the first frame that
 * happens to find a face, which previously let setup pass on a glance and then
 * produced an instant false "attention shifted" once the clip started.
 *
 * The preview never records: ImageAnalysis frames are converted in memory,
 * handed to on-device MediaPipe and released immediately. No frame, landmark
 * or derived per-frame value is written to disk or leaves the device.
 */
@Composable
fun CameraCalibrationScreen(
    onCalibrationComplete: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var analyzer by remember { mutableStateOf<GazeAnalyzer?>(null) }
    var calibrationUnavailable by remember { mutableStateOf(false) }
    val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    val gate = remember { CalibrationGate() }
    var progress by remember {
        mutableStateOf(
            CalibrationGate.Progress(CalibrationGate.Status.SEARCHING_FOR_FACE, 0f, null)
        )
    }
    val acquisitionReady = progress.isReady

    val transition = rememberInfiniteTransition(label = "calibrationPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1400), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Let's calibrate your focus",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                // Not "follow the dot" — the system measures gaze deflection,
                // not where on the screen you are looking, and an instruction
                // implying otherwise oversells what it does.
                text = "Look at the dot and hold still for a moment",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

                            val newAnalyzer = try {
                                GazeAnalyzer(ctx.applicationContext) { frame ->
                                    progress = gate.accept(
                                        faceDetected = frame.faceDetected,
                                        irisPointCount = frame.irisPoints.size,
                                        headYawDegrees = frame.headYawDegrees,
                                        headPitchDegrees = frame.headPitchDegrees
                                    )
                                }
                            } catch (t: Throwable) {
                                android.util.Log.e("CameraCalibration", "GazeAnalyzer init failed", t)
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
            }

            DisposableEffect(Unit) {
                onDispose {
                    analyzer?.close()
                    analysisExecutor.shutdown()
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            StatusRow(progress = progress, calibrationUnavailable = calibrationUnavailable)

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Keep a comfortable distance and blink naturally.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(
                text = "Continue",
                onClick = onCalibrationComplete,
                enabled = acquisitionReady || calibrationUnavailable
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
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
    calibrationUnavailable: Boolean
) {
    val colors = LocalFocusFlowColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(colors.glassSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (calibrationUnavailable) {
            Text(
                text = "Camera calibration unavailable — you can continue",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = when (progress.status) {
                    CalibrationGate.Status.SEARCHING_FOR_FACE -> "Looking for your face…"
                    CalibrationGate.Status.IRISES_NOT_DETECTED ->
                        "Move a little closer so both eyes are visible"
                    CalibrationGate.Status.NOT_FACING_SCREEN -> "Face the screen straight on"
                    CalibrationGate.Status.STABILIZING ->
                        "Hold still… ${(progress.fraction * 100).toInt()}%"
                    CalibrationGate.Status.READY -> "Eyes tracked steadily — you're ready"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (progress.isReady) Success else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
