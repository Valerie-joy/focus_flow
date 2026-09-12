package com.focusflow.camera.eyetracking

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max

/** A normalized (0..1) point in the analyzed image's coordinate space. */
data class GazePoint(val x: Float, val y: Float)

/**
 * One frame's worth of gaze signal. [isLookingAtScreen] is the decision the
 * attention tracker consumes; the rest is exposed so the calibration/setup UI
 * can draw an overlay and so scoring can stay explainable.
 */
data class GazeFrame(
    val faceDetected: Boolean,
    val isLookingAtScreen: Boolean,
    val headYawDegrees: Float?,
    val headPitchDegrees: Float?,
    /** 0f (dead centre) .. 1f (fully deflected) horizontal eye deviation. */
    val horizontalGaze: Float,
    val verticalGaze: Float,
    val eyesClosed: Boolean,
    /** Iris outline points for both eyes, for the setup-step overlay. */
    val irisPoints: List<GazePoint>,
    /** Eye-corner/lid points, for the setup-step overlay. */
    val eyePoints: List<GazePoint>,
    val timestampMs: Long
)

/**
 * CameraX analyzer backed by MediaPipe's Face Landmarker.
 *
 * Why this replaced ML Kit face detection: ML Kit exposes head Euler angles,
 * a bounding box, eye *centre* landmarks and eye-open probabilities — but no
 * iris or pupil anywhere, at any setting. Attention could therefore only be
 * inferred from head angle, which misses the single most important case for
 * this app: eyes leaving the screen while the head stays still.
 *
 * Face Landmarker gives 478 landmarks — the 468 face mesh points plus 10 iris
 * points (indices 468-477) — and 52 blendshapes, of which eight describe gaze
 * direction directly (`eyeLook{In,Out,Up,Down}{Left,Right}`).
 *
 * Honest scope note: this measures gaze *direction* relative to head pose, and
 * reliably catches "eyes are deflected well off centre". It is NOT a calibrated
 * gaze-point estimator — it cannot say *where* on the screen someone is looking
 * (e.g. reading a caption vs watching the centre). That would need a per-user
 * calibration fit, which the existing CameraCalibrationScreen is the natural
 * place to add.
 *
 * All inference is on-device; frames are converted in memory and never stored.
 */
class GazeAnalyzer(
    context: Context,
    /** Blendshape deflection past which the eyes count as off-screen. */
    private val gazeDeviationThreshold: Float = 0.45f,
    /** Head rotation past which the head counts as turned away. */
    private val headYawThresholdDegrees: Float = 25f,
    private val headPitchThresholdDegrees: Float = 20f,
    private val onGazeFrame: (GazeFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastTimestampMs = Long.MIN_VALUE
    private val mainHandler = Handler(Looper.getMainLooper())

    // MediaPipe's LIVE_STREAM result/error listeners fire on MediaPipe's own
    // internal thread — not the CameraX analyzer executor, and not main. Every
    // frame was therefore mutating Compose state (onGazeFrame -> mutableStateOf
    // writes in the caller) from a background thread, which Compose's layout/
    // attachment machinery doesn't support and crashes with "LayoutNode should
    // be attached to an owner" — reliably, not just as a rare race. deliver()
    // hops back to main before onGazeFrame ever runs.
    //
    // Separately: those listeners also aren't guaranteed to stop the instant
    // close() is called, so a frame already in flight can still land after
    // teardown — closed guards that stale-callback case too.
    @Volatile
    private var closed = false

    private fun deliver(frame: GazeFrame) {
        if (closed) return
        mainHandler.post {
            if (!closed) onGazeFrame(frame)
        }
    }

    private val landmarker: FaceLandmarker = createLandmarker(context)

    // GPU delegate isn't guaranteed to be supported on every device; falling
    // back to CPU keeps this from ever being the reason GazeAnalyzer's
    // constructor throws (the outer crash-guard around GazeAnalyzer(...) in
    // the assessment/calibration screens is the last resort if CPU somehow
    // fails too).
    private fun createLandmarker(context: Context): FaceLandmarker =
        try {
            buildLandmarker(context, Delegate.GPU)
        } catch (t: Throwable) {
            Log.w("GazeAnalyzer", "GPU delegate init failed, falling back to CPU", t)
            buildLandmarker(context, Delegate.CPU)
        }

    private fun buildLandmarker(context: Context, delegate: Delegate): FaceLandmarker =
        FaceLandmarker.createFromOptions(
            context,
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .setDelegate(delegate)
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumFaces(1)
                // Blendshapes carry the gaze signal; the transformation matrix
                // carries head pose (yaw AND pitch — the old pipeline only ever
                // looked at yaw, so looking down at your lap read as attentive).
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .setResultListener { result, _ -> if (!closed) emit(result) }
                .setErrorListener { if (!closed) emitNoFace() }
                .build()
        )

    override fun analyze(imageProxy: ImageProxy) {
        if (closed) {
            imageProxy.close()
            return
        }
        // LIVE_STREAM requires strictly increasing timestamps; CameraX can
        // occasionally hand back a duplicate, which would throw.
        val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000
        if (timestampMs <= lastTimestampMs) {
            imageProxy.close()
            return
        }
        lastTimestampMs = timestampMs

        // Rotation is captured before close() and handed to MediaPipe via
        // ImageProcessingOptions instead of being pre-baked into a second
        // Bitmap.createBitmap copy — MediaPipe rotates internally and returns
        // landmarks already in the rotated (upright) coordinate space. There's
        // no equivalent option for the front-camera mirror, so that's applied
        // to the output coordinates in emit() instead (see irisPoints/eyePoints).
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val bitmap = try {
            imageProxy.toBitmap()
        } catch (_: Throwable) {
            imageProxy.close()
            return
        } finally {
            // The bitmap is an independent copy, so the frame can be released
            // immediately rather than being held for the whole inference.
            imageProxy.close()
        }

        runCatching {
            val options = ImageProcessingOptions.builder()
                .setRotationDegrees(rotationDegrees)
                .build()
            landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), options, timestampMs)
        }
    }

    private fun emitNoFace() {
        deliver(
            GazeFrame(
                faceDetected = false,
                isLookingAtScreen = false,
                headYawDegrees = null,
                headPitchDegrees = null,
                horizontalGaze = 0f,
                verticalGaze = 0f,
                eyesClosed = false,
                irisPoints = emptyList(),
                eyePoints = emptyList(),
                timestampMs = lastTimestampMs
            )
        )
    }

    private fun emit(result: FaceLandmarkerResult) {
        val landmarks = result.faceLandmarks().firstOrNull()
        if (landmarks == null || landmarks.size < TOTAL_LANDMARKS) {
            emitNoFace()
            return
        }

        val blendshapes = result.faceBlendshapes()
            .orElse(null)
            ?.firstOrNull()
            ?.associate { it.categoryName() to it.score() }
            .orEmpty()

        fun shape(name: String) = blendshapes[name] ?: 0f

        // Horizontal: for either eye, deflection is whichever of "toward the
        // nose" / "away from the nose" is firing. Taking the max across both
        // eyes keeps a single deflected eye from being averaged away.
        val horizontalGaze = maxOf(
            shape("eyeLookInLeft"), shape("eyeLookOutLeft"),
            shape("eyeLookInRight"), shape("eyeLookOutRight")
        )
        val verticalGaze = maxOf(
            shape("eyeLookUpLeft"), shape("eyeLookDownLeft"),
            shape("eyeLookUpRight"), shape("eyeLookDownRight")
        )
        val eyesClosed = max(shape("eyeBlinkLeft"), shape("eyeBlinkRight")) > BLINK_THRESHOLD

        val (yaw, pitch) = result.facialTransformationMatrixes()
            .orElse(null)
            ?.firstOrNull()
            ?.let(::headAnglesFrom)
            ?: (null to null)

        val headTurnedAway = (yaw != null && abs(yaw) > headYawThresholdDegrees) ||
            (pitch != null && abs(pitch) > headPitchThresholdDegrees)
        val eyesDeflected = horizontalGaze > gazeDeviationThreshold ||
            verticalGaze > gazeDeviationThreshold

        // A blink shouldn't read as looking away — the tracker counts blinks
        // separately and they'd otherwise shred the attention percentage.
        val isLooking = !headTurnedAway && !eyesDeflected

        deliver(
            GazeFrame(
                faceDetected = true,
                isLookingAtScreen = isLooking,
                headYawDegrees = yaw,
                headPitchDegrees = pitch,
                horizontalGaze = horizontalGaze,
                verticalGaze = verticalGaze,
                eyesClosed = eyesClosed,
                // MediaPipe never sees a mirrored frame (mirroring the front
                // camera used to be baked into the analyzed bitmap; now it
                // isn't, per the analyze() comment above), so the x-coordinate
                // is flipped here instead to match the mirrored preview these
                // points are drawn over.
                irisPoints = IRIS_INDICES.map { GazePoint(x = 1f - landmarks[it].x(), y = landmarks[it].y()) },
                eyePoints = EYE_OUTLINE_INDICES.map { GazePoint(x = 1f - landmarks[it].x(), y = landmarks[it].y()) },
                timestampMs = lastTimestampMs
            )
        )
    }

    fun close() {
        closed = true
        runCatching { landmarker.close() }
    }

    private companion object {
        const val MODEL_ASSET = "face_landmarker.task"
        const val TOTAL_LANDMARKS = 478
        const val BLINK_THRESHOLD = 0.5f

        /** Left iris 468-472, right iris 473-477 — the 468→478 refinement. */
        val IRIS_INDICES = (468..477).toList()

        /** Eye corners/lids, enough to sketch both eyes in the overlay. */
        val EYE_OUTLINE_INDICES = listOf(
            33, 133, 159, 145, 160, 144,   // left eye
            362, 263, 386, 374, 387, 373   // right eye
        )

        /**
         * Yaw/pitch in degrees from a column-major 4x4 rigid transform.
         * Used only as a coarse "head clearly turned away" gate, with generous
         * thresholds, so exact convention differences can't dominate the far
         * more reliable blendshape gaze signal.
         */
        fun headAnglesFrom(m: FloatArray): Pair<Float, Float>? {
            if (m.size < 11) return null
            val yaw = Math.toDegrees(atan2(m[8].toDouble(), m[10].toDouble())).toFloat()
            val pitch = Math.toDegrees(asin(m[9].coerceIn(-1f, 1f).toDouble())).toFloat()
            return yaw to pitch
        }
    }
}
