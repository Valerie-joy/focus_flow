package com.focusflow.camera.eyetracking.itracker

import kotlin.math.abs
import kotlin.math.sqrt

/** iTracker's raw output: centimetres from the camera centre. */
data class GazePointCm(val x: Float, val y: Float)

/** A point on the display in normalised units: (0,0) top-left, (1,1) bottom-right. */
data class ScreenPoint(val x: Float, val y: Float) {
    /** Inside the panel, allowing [margin] of slack past each edge (in the same
     *  normalised units) — iTracker is a ~1–2 cm instrument, and a user looking
     *  near an edge should not read as off-screen because of that noise. */
    fun isOnScreen(margin: Float): Boolean =
        x >= -margin && x <= 1f + margin && y >= -margin && y <= 1f + margin
}

/**
 * Affine map from iTracker's camera-centred cm to normalised screen
 * coordinates:  sx = a*cx + b*cy + c,  sy = d*cx + e*cy + f.
 *
 * Why affine, and why per user: iTracker was trained on iPhones and iPads,
 * whose front cameras sit at known offsets from the display. Android devices
 * put the camera anywhere, screens differ in physical size, and the model's
 * own bias varies per person. Six parameters absorb all of that — camera
 * offset (c, f), cm-to-screen scale (a, e), and any axis sign or mild
 * mixing (b, d) — from five dots the user looks at during the camera setup
 * step. No device database is needed.
 */
data class AffineMap(
    val a: Float, val b: Float, val c: Float,
    val d: Float, val e: Float, val f: Float,
    /** RMS residual of the fit over the calibration dots, normalised units. */
    val residualRms: Float,
    val pointCount: Int,
    val fittedAtEpochMs: Long
) {
    fun map(p: GazePointCm): ScreenPoint =
        ScreenPoint(a * p.x + b * p.y + c, d * p.x + e * p.y + f)

    /** Serialised for SharedPreferences: nine numbers, comma-separated. */
    fun encode(): String = listOf(a, b, c, d, e, f, residualRms).joinToString(",") + ",$pointCount,$fittedAtEpochMs"

    companion object {
        fun decode(s: String?): AffineMap? {
            val parts = s?.split(",") ?: return null
            if (parts.size != 9) return null
            return runCatching {
                AffineMap(
                    parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat(),
                    parts[3].toFloat(), parts[4].toFloat(), parts[5].toFloat(),
                    residualRms = parts[6].toFloat(),
                    pointCount = parts[7].toInt(),
                    fittedAtEpochMs = parts[8].toLong()
                )
            }.getOrNull()
        }
    }
}

object GazeCalibration {
    /** One calibration observation: what iTracker said while the user looked at [target]. */
    data class Sample(val gaze: GazePointCm, val target: ScreenPoint)

    /**
     * The five targets, in normalised screen units. Centre plus four inset
     * corners: enough to pin all six parameters with margin, spread wide
     * enough that the corners are clearly distinguishable at iTracker's
     * resolution, and inset enough that a user can comfortably fixate them.
     */
    val TARGETS = listOf(
        ScreenPoint(0.5f, 0.5f),
        ScreenPoint(0.15f, 0.15f),
        ScreenPoint(0.85f, 0.15f),
        ScreenPoint(0.15f, 0.85f),
        ScreenPoint(0.85f, 0.85f)
    )

    /** Fit worse than this (normalised RMS) is not trusted; the app falls back
     *  to the blendshape rule. 0.20 ≈ a fifth of the screen — generous, but a
     *  calibration that cannot do better than that is not adding information. */
    const val MAX_ACCEPTABLE_RESIDUAL = 0.20f
    const val MIN_POINTS = 4

    /**
     * Component-wise median of a dot's samples. Blinks, a glance at the
     * status text, and single bad frames land far from the cluster; the
     * median ignores them where the mean would be dragged.
     */
    fun median(points: List<GazePointCm>): GazePointCm? {
        if (points.isEmpty()) return null
        fun med(v: List<Float>): Float {
            val s = v.sorted()
            val n = s.size
            return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2f
        }
        return GazePointCm(med(points.map { it.x }), med(points.map { it.y }))
    }

    /**
     * Least-squares affine fit. Each output axis is an independent 3-parameter
     * linear regression on (cx, cy, 1), solved from its 3x3 normal equations.
     * Returns null when there are too few points or they are (near-)collinear,
     * which the five-dot layout never produces unless dots were skipped.
     */
    fun fit(samples: List<Sample>, nowEpochMs: Long = System.currentTimeMillis()): AffineMap? {
        if (samples.size < MIN_POINTS) return null

        // Normal-equation accumulators: M = Σ v vᵀ with v = (cx, cy, 1);
        // rx = Σ v·sx ; ry = Σ v·sy.
        val m = DoubleArray(9)
        val rx = DoubleArray(3)
        val ry = DoubleArray(3)
        for (s in samples) {
            val v = doubleArrayOf(s.gaze.x.toDouble(), s.gaze.y.toDouble(), 1.0)
            for (i in 0 until 3) {
                for (j in 0 until 3) m[i * 3 + j] += v[i] * v[j]
                rx[i] += v[i] * s.target.x
                ry[i] += v[i] * s.target.y
            }
        }
        // A whisper of ridge on the two slope terms keeps a nearly-degenerate
        // system solvable without measurably changing a well-posed one.
        m[0] += 1e-6; m[4] += 1e-6

        val px = solve3(m, rx) ?: return null
        val py = solve3(m, ry) ?: return null

        val map = AffineMap(
            px[0].toFloat(), px[1].toFloat(), px[2].toFloat(),
            py[0].toFloat(), py[1].toFloat(), py[2].toFloat(),
            residualRms = 0f, pointCount = samples.size, fittedAtEpochMs = nowEpochMs
        )
        var se = 0.0
        for (s in samples) {
            val p = map.map(s.gaze)
            val dx = (p.x - s.target.x).toDouble()
            val dy = (p.y - s.target.y).toDouble()
            se += dx * dx + dy * dy
        }
        return map.copy(residualRms = sqrt(se / samples.size).toFloat())
    }

    /** Gaussian elimination with partial pivoting on a 3x3 system. */
    private fun solve3(mIn: DoubleArray, rIn: DoubleArray): DoubleArray? {
        val a = Array(3) { i -> doubleArrayOf(mIn[i * 3], mIn[i * 3 + 1], mIn[i * 3 + 2], rIn[i]) }
        for (col in 0 until 3) {
            var pivot = col
            for (r in col + 1 until 3) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            if (abs(a[pivot][col]) < 1e-12) return null
            if (pivot != col) { val t = a[pivot]; a[pivot] = a[col]; a[col] = t }
            for (r in col + 1 until 3) {
                val k = a[r][col] / a[col][col]
                for (cc in col until 4) a[r][cc] -= k * a[col][cc]
            }
        }
        val x = DoubleArray(3)
        for (i in 2 downTo 0) {
            var s = a[i][3]
            for (j in i + 1 until 3) s -= a[i][j] * x[j]
            x[i] = s / a[i][i]
        }
        return x
    }
}
