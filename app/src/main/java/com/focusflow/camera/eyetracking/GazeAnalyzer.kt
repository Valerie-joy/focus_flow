package com.focusflow.camera.eyetracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.focusflow.camera.eyetracking.itracker.AffineMap
import com.focusflow.camera.eyetracking.itracker.GazeFusion
import com.focusflow.camera.eyetracking.itracker.GazePointCm
import com.focusflow.camera.eyetracking.itracker.GazePointEstimator
import com.focusflow.camera.eyetracking.itracker.ITrackerPreprocessor
import com.focusflow.camera.eyetracking.itracker.ScreenPoint
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
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
    val timestampMs: Long,
    /**
     * iTracker's raw point of regard, cm from the camera centre, on the
     * frames it ran for (every Nth frame — see [GazeAnalyzer]); null otherwise
     * and when no estimator is attached. The calibration screen collects these.
     */
    val gazePointCm: GazePointCm? = null,
    /** [gazePointCm] mapped through the user's calibration, when one is set. */
    val gazeScreenPoint: ScreenPoint? = null,
    /** True when [isLookingAtScreen] was decided by the calibrated gaze point rather than the blendshape rule. */
    val decidedByGazePoint: Boolean = false,
    /** Wall time of the iTracker inference on this frame, if it ran. */
    val gazeInferenceMs: Float? = null
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
 * Two gaze signals come out of this class:
 *
 *  1. Blendshape deflection plus head pose, every frame. This measures gaze
 *     *direction* relative to the head and reliably catches "eyes are
 *     deflected well off centre", but cannot say *where* on the screen someone
 *     is looking.
 *  2. Optionally, iTracker's point of regard (Krafka et al., CVPR 2016) via
 *     [GazePointEstimator], on every Nth frame. Fed the landmarker's own
 *     landmarks and the same frame, it returns a gaze point in cm from the
 *     camera, which the user's [AffineMap] from the camera setup step turns
 *     into a screen position. When a calibration is attached, that position
 *     decides [GazeFrame.isLookingAtScreen] through [GazeFusion]; without
 *     one, signal 1 decides and the raw cm point is merely reported.
 *
 * iTracker runs synchronously on the landmarker's result thread so frames
 * reach the consumer in order (AttentionTracker's sustain timing depends on
 * monotonic delivery). It is several times heavier than the landmarker, so
 * it self-throttles: after each run it measures its own wall time and skips
 * enough frames to keep its average cost under [gazePointBudgetMsPerFrame].
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
    /** Attach to also produce iTracker gaze points. The analyzer owns and closes it. */
    private val gazePointEstimator: GazePointEstimator? = null,
    /** The user's calibration; when non-null the gaze point decides isLookingAtScreen. */
    private val calibration: AffineMap? = null,
    /** Amortised per-frame time iTracker may consume; sets the adaptive skip. */
    private val gazePointBudgetMsPerFrame: Float = 25f,
    private val onGazeFrame: (GazeFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastTimestampMs = Long.MIN_VALUE
    private val mainHandler = Handler(Looper.getMainLooper())

    // --- iTracker state, all touched only on the landmarker's result thread ---
    private val preprocessor: ITrackerPreprocessor? = gazePointEstimator?.let { ITrackerPreprocessor() }
    private val fusion = GazeFusion()
    private val landmarkXs = FloatArray(TOTAL_LANDMARKS)
    private val landmarkYs = FloatArray(TOTAL_LANDMARKS)
    private var framesUntilGazePoint = 0
    /** Rotation applied to each in-flight frame, keyed by its timestamp. */
    private val pendingRotation = HashMap<Long, Int>()

    /** The most recent 224x224 face crop fed to iTracker, for a debug view. */
    val lastFaceCrop: Bitmap? get() = preprocessor?.lastFaceCrop
    val gazePointMeanInferenceMs: Float get() = gazePointEstimator?.meanInferenceMs ?: 0f
    val hasGazePointEstimator: Boolean get() = gazePointEstimator != null
    val isCalibrated: Boolean get() = calibration != null

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

    private val appContext = context.applicationContext
    private val landmarkerLock = Any()
    @Volatile
    private var delegateInUse = Delegate.GPU
    /** Frames submitted to the landmarker that have not produced a result or error. */
    private val framesInFlight = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var landmarker: FaceLandmarker = createLandmarker(context)

    // GPU delegate isn't guaranteed to be supported on every device; falling
    // back to CPU keeps this from ever being the reason GazeAnalyzer's
    // constructor throws (the outer crash-guard around GazeAnalyzer(...) in
    // the assessment/calibration screens is the last resort if CPU somehow
    // fails too).
    private fun createLandmarker(context: Context): FaceLandmarker =
        try {
            delegateInUse = Delegate.GPU
            buildLandmarker(context, Delegate.GPU)
        } catch (t: Throwable) {
            Log.w(TAG, "GPU delegate init failed, falling back to CPU", t)
            delegateInUse = Delegate.CPU
            buildLandmarker(context, Delegate.CPU)
        }

    /**
     * The GPU delegate can construct fine and then fail on every frame — the
     * emulator's OpenGL ES does exactly that (GL_INVALID_ENUM inside the face
     * detector), and phones with broken GL drivers exist. When that happens
     * MediaPipe logs the error internally and delivers *nothing*: neither the
     * result listener nor the error listener fires, and detectAsync returns
     * normally. The only observable symptom is silence. So the fallback is a
     * watchdog: frames go in, nothing comes back, and after
     * [STALLED_FRAMES_BEFORE_CPU_FALLBACK] of that on the GPU path the
     * landmarker is rebuilt on CPU instead of reporting "no face" for the
     * rest of the session.
     */
    private fun noteSubmitted() {
        val inFlight = framesInFlight.incrementAndGet()
        if (inFlight >= STALLED_FRAMES_BEFORE_CPU_FALLBACK && delegateInUse == Delegate.GPU) {
            synchronized(landmarkerLock) {
                if (delegateInUse == Delegate.GPU && !closed) {
                    Log.w(TAG, "landmarker produced nothing for $inFlight frames on GPU; rebuilding on CPU")
                    runCatching { landmarker.close() }
                    delegateInUse = Delegate.CPU
                    landmarker = buildLandmarker(appContext, Delegate.CPU)
                    framesInFlight.set(0)
                    synchronized(pendingRotation) { pendingRotation.clear() }
                }
            }
        }
    }

    private fun noteDelivered() {
        framesInFlight.set(0)
    }

    private fun onLandmarkerError(error: Throwable?) {
        if (closed) return
        noteDelivered()
        Log.w(TAG, "landmarker error on $delegateInUse: ${error?.message?.lineSequence()?.firstOrNull()}")
        emitNoFace()
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
                // The second argument is the MPImage handed to detectAsync —
                // the same un-rotated frame — which is what iTracker crops.
                .setResultListener { result, image -> if (!closed) { noteDelivered(); emit(result, image) } }
                .setErrorListener { e -> onLandmarkerError(e) }
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
            if (gazePointEstimator != null) {
                // emit() needs the rotation to upright the frame for cropping.
                // Bounded: a result that never arrives (error path) must not leak.
                synchronized(pendingRotation) {
                    if (pendingRotation.size > 8) pendingRotation.clear()
                    pendingRotation[timestampMs] = rotationDegrees
                }
            }
            synchronized(landmarkerLock) {
                if (!closed) {
                    landmarker.detectAsync(BitmapImageBuilder(bitmap).build(), options, timestampMs)
                    noteSubmitted()
                }
            }
        }.onFailure { e ->
            synchronized(pendingRotation) { pendingRotation.remove(timestampMs) }
            onLandmarkerError(e)
        }
    }

    private fun emitNoFace() {
        // A carried-forward on-screen verdict must not survive losing the face.
        fusion.reset()
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

    private fun emit(result: FaceLandmarkerResult, image: MPImage) {
        val landmarks = result.faceLandmarks().firstOrNull()
        if (landmarks == null || landmarks.size < TOTAL_LANDMARKS) {
            synchronized(pendingRotation) { pendingRotation.remove(result.timestampMs()) }
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
        val blendshapeLooking = !headTurnedAway && !eyesDeflected

        // --- iTracker point of regard, on the frames it is due ---------------
        var gazeCm: GazePointCm? = null
        var inferenceMs: Float? = null
        val estimator = gazePointEstimator
        val prep = preprocessor
        if (estimator != null && prep != null && framesUntilGazePoint <= 0) {
            val rotation = synchronized(pendingRotation) { pendingRotation.remove(result.timestampMs()) } ?: 0
            runCatching {
                val upright = uprightBitmap(BitmapExtractor.extract(image), rotation)
                // Landmarks are normalised to the rotated (upright) frame —
                // the same space the setup overlay draws them in.
                val w = upright.width.toFloat()
                val h = upright.height.toFloat()
                for (i in 0 until TOTAL_LANDMARKS) {
                    landmarkXs[i] = landmarks[i].x() * w
                    landmarkYs[i] = landmarks[i].y() * h
                }
                val inputs = prep.prepare(upright, landmarkXs, landmarkYs)
                // Null means the estimator was closed under us mid-clip; leave
                // inferenceMs unset so the skip backs off instead of reusing a
                // stale timing from the previous frame.
                estimator.estimate(inputs)?.let { point ->
                    gazeCm = point
                    inferenceMs = estimator.lastInferenceMs
                }
            }.onFailure { Log.w(TAG, "iTracker inference failed on this frame", it) }

            // Adaptive skip: keep the amortised cost under budget. A failed
            // run backs off to the maximum rather than retrying every frame.
            framesUntilGazePoint = inferenceMs
                ?.let { ceil(it / gazePointBudgetMsPerFrame).toInt().coerceIn(1, MAX_GAZE_POINT_SKIP) }
                ?: MAX_GAZE_POINT_SKIP
        } else {
            synchronized(pendingRotation) { pendingRotation.remove(result.timestampMs()) }
        }
        framesUntilGazePoint--

        // --- final decision ----------------------------------------------------
        val screenPoint = calibration?.let { cal -> gazeCm?.let(cal::map) }
        val decision = if (calibration != null) {
            fusion.decide(blendshapeLooking, headTurnedAway, screenPoint, lastTimestampMs)
        } else {
            GazeFusion.Decision(blendshapeLooking, fromGazePoint = false, screenPoint = null)
        }

        deliver(
            GazeFrame(
                faceDetected = true,
                isLookingAtScreen = decision.isLooking,
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
                timestampMs = lastTimestampMs,
                gazePointCm = gazeCm,
                gazeScreenPoint = decision.screenPoint ?: screenPoint,
                decidedByGazePoint = decision.fromGazePoint,
                gazeInferenceMs = inferenceMs
            )
        )
    }

    /**
     * The frame as the landmarker saw it after its internal rotation. For
     * 0° the extracted bitmap is returned as-is (no copy).
     */
    private fun uprightBitmap(src: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun close() {
        closed = true
        synchronized(landmarkerLock) { runCatching { landmarker.close() } }
        runCatching { gazePointEstimator?.close() }
    }

    /** Which delegate the landmarker is currently running on (for logs/UI). */
    val landmarkerDelegate: Delegate get() = delegateInUse

    private companion object {
        const val TAG = "GazeAnalyzer"
        const val MODEL_ASSET = "face_landmarker.task"
        const val TOTAL_LANDMARKS = 478
        const val BLINK_THRESHOLD = 0.5f
        /** Upper bound on frames between iTracker runs (~3 Hz at 30 fps). */
        const val MAX_GAZE_POINT_SKIP = 10
        /** Frames submitted with no result or error back (~1 s at 30 fps) before the GPU landmarker is rebuilt on CPU. */
        const val STALLED_FRAMES_BEFORE_CPU_FALLBACK = 30

        /**
         * The 468→478 iris refinement. In MediaPipe's convention 468-472 is
         * the subject's RIGHT iris (image left in an un-mirrored frame) and
         * 473-477 the subject's LEFT. Sidedness is irrelevant to this overlay
         * but matters to iTracker — see ITrackerGeometry, which owns it.
         */
        val IRIS_INDICES = (468..477).toList()

        /** Eye corners/lids, enough to sketch both eyes in the overlay. */
        val EYE_OUTLINE_INDICES = listOf(
            33, 133, 159, 145, 160, 144,   // subject's right eye
            362, 263, 386, 374, 387, 373   // subject's left eye
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
