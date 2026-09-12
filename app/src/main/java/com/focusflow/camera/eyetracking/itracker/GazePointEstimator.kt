package com.focusflow.camera.eyetracking.itracker

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Runs the iTracker point-of-regard model (Krafka et al., CVPR 2016) from
 * app/src/main/assets/itracker.tflite.
 *
 * Output is the gaze point in centimetres from the camera centre, in the
 * model's own axes. It is only meaningful once passed through the user's
 * [AffineMap] from the camera setup step — uncalibrated, the model is
 * roughly a 2.5 cm instrument trained on iOS hardware whose camera offsets
 * differ from every Android phone.
 *
 * Licence: the model is under the GazeCapture Research License — research
 * use only, no commercial use, cite the paper. See tools/itracker/README.md.
 *
 * CPU only, four threads through XNNPACK. The GPU delegate has no LRN kernel
 * (iTracker has six), so it would partition the graph and end up slower.
 * Not thread-safe; call [estimate] from the single analysis thread.
 */
class GazePointEstimator(context: Context) : AutoCloseable {
    private val interpreter: Interpreter
    private val inputIndex: IntArray   // [face, eyeLeft, eyeRight, faceGrid] -> tensor index
    private val output = Array(1) { FloatArray(2) }

    /** Wall time of the most recent [estimate] call. */
    var lastInferenceMs: Float = 0f
        private set
    /** Rolling mean of inference wall-time, for the per-session performance log. */
    var meanInferenceMs: Float = 0f
        private set
    var inferenceCount: Int = 0
        private set

    init {
        val model = mapAsset(context, MODEL_ASSET)
        val options = Interpreter.Options().apply {
            setNumThreads(THREADS)
            setUseXNNPACK(true)
        }
        interpreter = Interpreter(model, options)
        inputIndex = IntArray(INPUT_NAMES.size) { i ->
            val idx = interpreter.getInputIndex(INPUT_NAMES[i])
            check(idx >= 0) { "itracker.tflite has no input named ${INPUT_NAMES[i]}" }
            idx
        }
        Log.i(TAG, "iTracker loaded: ${interpreter.inputTensorCount} inputs, $THREADS threads")
    }

    fun estimate(inputs: ITrackerPreprocessor.Inputs): GazePointCm {
        val feed = arrayOfNulls<Any>(INPUT_NAMES.size)
        feed[inputIndex[0]] = inputs.face
        feed[inputIndex[1]] = inputs.eyeLeft
        feed[inputIndex[2]] = inputs.eyeRight
        feed[inputIndex[3]] = inputs.faceGrid

        val t0 = SystemClock.elapsedRealtime()
        interpreter.runForMultipleInputsOutputs(feed, mapOf(0 to output))
        val dt = (SystemClock.elapsedRealtime() - t0).toFloat()

        lastInferenceMs = dt
        inferenceCount++
        meanInferenceMs += (dt - meanInferenceMs) / inferenceCount
        return GazePointCm(output[0][0], output[0][1])
    }

    override fun close() {
        runCatching { interpreter.close() }
    }

    private fun mapAsset(context: Context, name: String): MappedByteBuffer =
        context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                // Requires the asset to be stored uncompressed (noCompress in
                // build.gradle.kts), otherwise the offset/length don't describe
                // a contiguous mappable region.
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }

    companion object {
        private const val TAG = "GazePointEstimator"
        const val MODEL_ASSET = "itracker.tflite"
        private const val THREADS = 4

        /** Tensor names as written by the export (see itracker.json). */
        private val INPUT_NAMES = arrayOf(
            "serving_default_face:0",
            "serving_default_eye_left:0",
            "serving_default_eye_right:0",
            "serving_default_face_grid:0"
        )
    }
}
