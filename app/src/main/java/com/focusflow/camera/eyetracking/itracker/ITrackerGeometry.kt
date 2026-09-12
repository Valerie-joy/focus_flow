package com.focusflow.camera.eyetracking.itracker

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The crop-box and face-grid conventions for iTracker, with no Android
 * types so they can be unit-tested on the JVM.
 *
 * This is a line-for-line port of tools/itracker/preprocess.py, which is the
 * reference implementation and carries the full rationale. In short:
 * GazeCapture's training crops came from Apple's CIDetector boxes, and these
 * functions synthesise equivalent boxes from the 478 MediaPipe landmarks.
 * The two constants are the only approximation; the per-user affine
 * calibration ([GazeCalibration]) absorbs whatever bias they leave.
 *
 * Any change to a constant or a rounding rule here must be mirrored in
 * preprocess.py, or the desktop harness stops describing what the app does.
 */
object ITrackerGeometry {
    const val INPUT_SIZE = 224
    const val GRID_SIZE = 25
    const val GRID_LENGTH = GRID_SIZE * GRID_SIZE

    /** Apple's face box includes the forehead; the landmark extent stops at
     *  the eyebrows, so grow the square and shift it up a little. */
    const val FACE_BOX_SCALE = 1.15f
    const val FACE_BOX_UP_SHIFT = 0.08f

    /** Apple eye boxes are ~square and about this fraction of the face width. */
    const val EYE_BOX_FRAC_OF_FACE = 0.30f

    /**
     * MediaPipe canonical face-mesh indices. "Left" is the SUBJECT'S left eye —
     * the one that appears on the RIGHT side of an un-mirrored camera frame
     * (its landmarks have x > 0.5 there). This is also GazeCapture's
     * convention, so the eye_left input gets the subject's left eye.
     */
    val LEFT_EYE_CONTOUR = intArrayOf(362, 382, 381, 380, 374, 373, 390, 249, 263, 466, 388, 387, 386, 385, 384, 398)
    val RIGHT_EYE_CONTOUR = intArrayOf(33, 7, 163, 144, 145, 153, 154, 155, 133, 173, 157, 158, 159, 160, 161, 246)

    /** Integer pixel box; may extend outside the frame — the crop zero-pads. */
    data class Box(val x: Int, val y: Int, val w: Int, val h: Int)

    data class Boxes(val face: Box, val eyeLeft: Box, val eyeRight: Box)

    /**
     * Round half up, identical to Python's `int(np.floor(v + 0.5))` used in
     * preprocess.py. (Python's builtin round() is banker's rounding and
     * Kotlin's roundToInt() rounds half toward +∞; neither matches the other
     * at exact .5, so both sides use this explicit form.)
     */
    fun roundHalfUp(v: Float): Int = floor(v + 0.5f).toInt()

    /**
     * Square box around the whole landmark set, grown and shifted upward per
     * the constants above. [xs]/[ys] are pixel coordinates in the upright
     * frame.
     */
    fun faceBox(xs: FloatArray, ys: FloatArray): Box {
        require(xs.size == ys.size && xs.isNotEmpty())
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in xs.indices) {
            x0 = min(x0, xs[i]); x1 = max(x1, xs[i])
            y0 = min(y0, ys[i]); y1 = max(y1, ys[i])
        }
        val cx = (x0 + x1) / 2f
        val side = max(x1 - x0, y1 - y0) * FACE_BOX_SCALE
        val cy = (y0 + y1) / 2f - side * FACE_BOX_UP_SHIFT
        val s = roundHalfUp(side)
        return Box(roundHalfUp(cx - side / 2f), roundHalfUp(cy - side / 2f), s, s)
    }

    /** Square box centred on the mean of one eye's contour landmarks. */
    fun eyeBox(xs: FloatArray, ys: FloatArray, contour: IntArray, faceWidth: Int): Box {
        var sx = 0f; var sy = 0f
        for (i in contour) { sx += xs[i]; sy += ys[i] }
        val cx = sx / contour.size
        val cy = sy / contour.size
        val side = faceWidth * EYE_BOX_FRAC_OF_FACE
        val s = roundHalfUp(side)
        return Box(roundHalfUp(cx - side / 2f), roundHalfUp(cy - side / 2f), s, s)
    }

    fun boxes(xs: FloatArray, ys: FloatArray): Boxes {
        val face = faceBox(xs, ys)
        return Boxes(
            face = face,
            eyeLeft = eyeBox(xs, ys, LEFT_EYE_CONTOUR, face.w),
            eyeRight = eyeBox(xs, ys, RIGHT_EYE_CONTOUR, face.w)
        )
    }

    /**
     * ITrackerData.makeGrid with 0-based params: a 25x25 mask of where the
     * face box lies within the full frame, flattened row-major
     * (index = y * 25 + x), 1f inside the box and 0f elsewhere. Boxes that
     * extend past the frame are clamped to the grid edge.
     */
    fun faceGrid(frameWidth: Int, frameHeight: Int, face: Box): FloatArray {
        val sx = GRID_SIZE.toFloat() / frameWidth
        val sy = GRID_SIZE.toFloat() / frameHeight
        val gx = roundHalfUp(face.x * sx)
        val gy = roundHalfUp(face.y * sy)
        val gw = roundHalfUp(face.w * sx)
        val gh = roundHalfUp(face.h * sy)
        val x0 = max(0, gx); val x1 = min(GRID_SIZE, gx + gw)
        val y0 = max(0, gy); val y1 = min(GRID_SIZE, gy + gh)
        val grid = FloatArray(GRID_LENGTH)
        if (x1 > x0 && y1 > y0) {
            for (y in y0 until y1) {
                val row = y * GRID_SIZE
                for (x in x0 until x1) grid[row + x] = 1f
            }
        }
        return grid
    }
}
