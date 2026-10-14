package com.focusflow.camera.eyetracking.itracker

/**
 * Combines the two gaze signals into the single per-frame "looking at the
 * screen" decision that [com.focusflow.ai.attention.AttentionTracker]
 * consumes.
 *
 * Signal 1 — blendshape deflection plus head pose, from every frame. Cheap,
 * always present, but only knows that the eyes are *deflected*, not where
 * they point.
 *
 * Signal 2 — iTracker's calibrated point of regard, from every Nth frame
 * (the model is heavier than the landmarker, see GazeAnalyzer). Knows *where*
 * on the panel the eyes point, to roughly a centimetre after calibration.
 *
 * Rules:
 *  * A turned-away head always wins — no gaze model is trusted through a
 *    profile view.
 *  * A calibrated point can confirm that gaze is on-screen, but an off-screen
 *    point only counts as away when the every-frame blendshape signal agrees.
 *    The point-of-regard model is sensitive to small changes in phone pose;
 *    letting it veto an otherwise centred face/eyes caused focused users to
 *    be removed from a clip after a single biased estimate.
 *  * Between iTracker frames the last verdict is carried forward while it is
 *    fresh; once it goes stale the blendshape rule takes over. Frame order is
 *    preserved because iTracker runs synchronously on the landmarker's
 *    callback thread — nothing here needs to reorder.
 *  * The point is median-filtered over a short window before the edge test,
 *    which removes single-frame jitter without adding perceptible lag.
 *
 * Not thread-safe; feed it from the single analysis thread.
 */
class GazeFusion(
    /** Slack past each screen edge, normalised units. */
    private val onScreenMargin: Float = 0.20f,
    /** A carried-forward iTracker verdict older than this is discarded. */
    private val staleAfterMs: Long = 400L,
    private val medianWindow: Int = 3
) {
    private val recent = ArrayDeque<ScreenPoint>()
    private var lastVerdict: Boolean? = null
    private var lastVerdictMs = Long.MIN_VALUE

    data class Decision(
        val isLooking: Boolean,
        /** True when iTracker (fresh or carried) made the call, false when the blendshape rule did. */
        val fromGazePoint: Boolean,
        /** The filtered screen point used, when there was one. */
        val screenPoint: ScreenPoint?
    )

    fun reset() {
        recent.clear()
        lastVerdict = null
        lastVerdictMs = Long.MIN_VALUE
    }

    fun decide(
        blendshapeLooking: Boolean,
        headTurnedAway: Boolean,
        screenPoint: ScreenPoint?,
        timestampMs: Long
    ): Decision {
        if (headTurnedAway) {
            return Decision(isLooking = false, fromGazePoint = false, screenPoint = null)
        }

        if (screenPoint != null) {
            recent.addLast(screenPoint)
            while (recent.size > medianWindow) recent.removeFirst()
            val filtered = medianOf(recent)
            val pointOnScreen = filtered.isOnScreen(onScreenMargin)
            // False "attention shifted" events are more damaging than
            // missing a marginal glance near an edge. The calibrated point is
            // therefore corroborating evidence: either signal may keep the
            // user on-screen, while both must agree before gaze alone marks
            // them away. A turned head was handled above and still wins.
            val on = pointOnScreen || blendshapeLooking
            lastVerdict = on
            lastVerdictMs = timestampMs
            return Decision(isLooking = on, fromGazePoint = true, screenPoint = filtered)
        }

        val carried = lastVerdict
        if (carried != null && timestampMs - lastVerdictMs <= staleAfterMs) {
            return Decision(isLooking = carried, fromGazePoint = true, screenPoint = recent.lastOrNull())
        }

        return Decision(isLooking = blendshapeLooking, fromGazePoint = false, screenPoint = null)
    }

    private fun medianOf(points: ArrayDeque<ScreenPoint>): ScreenPoint {
        if (points.size == 1) return points.first()
        val xs = points.map { it.x }.sorted()
        val ys = points.map { it.y }.sorted()
        val n = xs.size
        fun mid(v: List<Float>) = if (n % 2 == 1) v[n / 2] else (v[n / 2 - 1] + v[n / 2]) / 2f
        return ScreenPoint(mid(xs), mid(ys))
    }
}
