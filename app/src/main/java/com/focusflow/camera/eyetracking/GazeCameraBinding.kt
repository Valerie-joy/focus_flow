package com.focusflow.camera.eyetracking

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService

private const val TAG = "GazeCameraBinding"

/** Why a camera session could not be established, in terms the UI can explain. */
enum class CameraFailure {
    /** The CAMERA permission is not currently granted. */
    PERMISSION,
    /** No usable camera, or CameraX could not bind to one. */
    UNAVAILABLE,
    /** MediaPipe/the gaze analyzer could not be constructed on this device. */
    TRACKING_UNSUPPORTED
}

/**
 * Binds the front camera for gaze analysis, with an optional preview surface.
 *
 * All three camera screens — the assessment's readiness step, its measured
 * playback step, and the onboarding gaze calibration — previously carried
 * their own near-identical copy of this roughly 90-line block: build a resolution selector, construct a
 * [GazeAnalyzer], wire it to an [ImageAnalysis], resolve the provider future,
 * unbind, bind. The two copies had already drifted: the setup step reported
 * bind failures to the user while the playback step swallowed them in an empty
 * `catch`, so a camera that failed to bind during a measured clip produced a
 * session that silently recorded nothing.
 *
 * Consolidating them means a fix lands once. The analysis configuration —
 * 640x480, `STRATEGY_KEEP_ONLY_LATEST`, a dedicated single-thread executor —
 * is carried over unchanged, because it is tuned to the gaze pipeline and is
 * not a UI concern.
 *
 * This is a plain function rather than a composable so it can be called from
 * inside a `DisposableEffect` and so the binding logic stays testable in
 * isolation from Compose.
 *
 * @param analyzerFactory builds the [GazeAnalyzer] for this session. Passed as
 * a factory rather than an instance because the playback step needs to load
 * calibration and the iTracker model first, and that work must not happen
 * until we know a camera actually exists.
 * @param previewView optional surface for a live preview. When null, only
 * analysis is bound — tracking must never be blocked on a preview that has not
 * composed yet.
 * @param onFailure called on the main thread with the reason binding failed.
 * @return a handle to close when the session ends. Always call [CameraSession.close].
 */
fun bindGazeCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    executor: ExecutorService,
    analyzerFactory: () -> GazeAnalyzer,
    previewView: PreviewView?,
    onFailure: (CameraFailure) -> Unit
): CameraSession {
    val session = CameraSession(executor)
    val providerFuture = ProcessCameraProvider.getInstance(context)

    providerFuture.addListener({
        if (session.isClosed) return@addListener

        val cameraProvider = try {
            providerFuture.get()
        } catch (e: Exception) {
            Log.e(TAG, "Camera provider unavailable", e)
            onFailure(CameraFailure.UNAVAILABLE)
            return@addListener
        }

        val analyzer = try {
            analyzerFactory()
        } catch (t: Throwable) {
            // Thrown when the MediaPipe task model can't be loaded — a real
            // possibility on devices without the required native support.
            Log.e(TAG, "Gaze analyzer could not be created", t)
            onFailure(CameraFailure.TRACKING_UNSUPPORTED)
            return@addListener
        }

        // Closed while the provider future was resolving (fast navigation
        // away). Release the analyzer we just built rather than leaking it.
        if (session.isClosed) {
            analyzer.close()
            return@addListener
        }
        session.analyzer = analyzer

        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            // A dedicated background thread: inference on the main executor
            // competes with Compose and the video player, which is what made
            // tracking feel laggy.
            .also { it.setAnalyzer(executor, analyzer) }

        val preview = previewView?.let { pv ->
            CameraPreview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
        }

        try {
            cameraProvider.unbindAll()
            val useCases = listOfNotNull(preview, imageAnalysis).toTypedArray()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                *useCases
            )
            session.provider = cameraProvider
        } catch (e: Exception) {
            // Previously swallowed in the playback step, which left a session
            // measuring nothing with no indication anything was wrong. A bind
            // failure here is almost always a missing or revoked CAMERA
            // permission, so it is reported as such.
            Log.e(TAG, "Camera bind failed", e)
            analyzer.close()
            session.analyzer = null
            onFailure(
                if (e is SecurityException) CameraFailure.PERMISSION else CameraFailure.UNAVAILABLE
            )
        }
    }, ContextCompat.getMainExecutor(context))

    return session
}

/**
 * Owns everything a bound camera session allocated.
 *
 * [close] is idempotent and safe to call before binding has finished — the
 * `isClosed` checks inside the provider callback mean a session torn down
 * mid-bind releases its analyzer rather than leaking a MediaPipe instance and
 * a native model, which is what happened when the user navigated away during
 * the ~200ms the provider future takes to resolve.
 */
class CameraSession internal constructor(private val executor: ExecutorService) {
    internal var analyzer: GazeAnalyzer? = null
    internal var provider: ProcessCameraProvider? = null

    @Volatile
    var isClosed: Boolean = false
        private set

    fun close() {
        if (isClosed) return
        isClosed = true
        try {
            provider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Provider already torn down", e)
        }
        analyzer?.close()
        analyzer = null
        provider = null
        executor.shutdown()
    }
}

/**
 * Analysis resolution. Low by design: the gaze models take small crops, and a
 * larger stream costs conversion time per frame without improving landmark
 * accuracy. Do not raise these without re-measuring inference latency.
 */
private const val ANALYSIS_WIDTH = 640
private const val ANALYSIS_HEIGHT = 480
