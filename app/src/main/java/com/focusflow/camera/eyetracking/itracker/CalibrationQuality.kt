package com.focusflow.camera.eyetracking.itracker

/**
 * Describes a completed gaze calibration in terms a participant can act on.
 *
 * This exists because the calibration screen was **misreporting its own
 * accuracy**. It rendered:
 *
 * ```
 * "We can now tell where on the screen you're looking
 *  (≈${(residualRms * 100).toInt()}% of screen)."
 * ```
 *
 * [AffineMap.residualRms] is the RMS *residual* of the fit — an error, in
 * normalised screen units, where smaller is better. Printed as a bare
 * percentage directly after a sentence claiming success, it reads as a
 * precision figure: a barely-passing fit of 0.18 announced itself as
 * "≈18% of screen", which a reader takes as either "18% accurate" (alarming
 * and wrong) or "accurate to within 18%" (right, but only by luck of
 * interpretation). A fit that just scraped past
 * [GazeCalibration.MAX_ACCEPTABLE_RESIDUAL] and a near-perfect one were
 * described in the same confident language.
 *
 * Nothing here invents a number. The band and the margin are both read
 * straight off the real fit; the only change is saying which direction is
 * good, and in what units.
 */
object CalibrationQuality {

    enum class Band(val label: String) {
        /** Comfortably better than the usable threshold. */
        GOOD("Good"),
        /** Usable, but toward the limit of what is trusted. */
        USABLE("Usable"),
        /** Worse than [GazeCalibration.MAX_ACCEPTABLE_RESIDUAL] — not saved. */
        REJECTED("Not accurate enough")
    }

    /**
     * A fit at or below half the acceptable residual is comfortably good;
     * between there and the threshold is usable; above it is rejected and the
     * app stays on the blendshape-only path.
     */
    fun bandFor(residualRms: Float): Band = when {
        residualRms > GazeCalibration.MAX_ACCEPTABLE_RESIDUAL -> Band.REJECTED
        residualRms <= GazeCalibration.MAX_ACCEPTABLE_RESIDUAL / 2f -> Band.GOOD
        else -> Band.USABLE
    }

    /**
     * The fit's typical error as a percentage of screen size, stated as an
     * error rather than an accuracy.
     *
     * Rounded to whole percent because the underlying estimate is
     * centimetre-level at best — a decimal place would imply precision the
     * model does not have.
     */
    fun marginPercent(residualRms: Float): Int = (residualRms * 100f).toInt().coerceAtLeast(1)

    /**
     * One sentence describing what the calibration bought, in the right
     * direction, with the units named.
     */
    fun describe(residualRms: Float, pointCount: Int): String {
        val margin = marginPercent(residualRms)
        return when (bandFor(residualRms)) {
            Band.GOOD ->
                "Calibrated on $pointCount points. Where you're looking is estimated to " +
                    "within roughly $margin% of the screen — good enough to use screen " +
                    "position during assessments."
            Band.USABLE ->
                "Calibrated on $pointCount points, but only roughly — estimates land within " +
                    "about $margin% of the screen. Redoing this in brighter, even light " +
                    "usually helps."
            Band.REJECTED ->
                "The readings were too scattered to use (off by about $margin% of the " +
                    "screen). Attention will be measured from eye deflection instead."
        }
    }

    /**
     * Why a calibration attempt failed, when it did.
     *
     * The old screen said only "Not enough steady readings — try again in
     * better light" for every failure mode, including the case where the model
     * produced no usable samples at all (a different problem with a different
     * fix).
     */
    fun failureAdvice(collectedPoints: Int): String = when {
        collectedPoints == 0 ->
            "No gaze readings came through. Check the light is on your face rather than " +
                "behind you, and that nothing is covering the front camera."
        collectedPoints < GazeCalibration.MIN_POINTS ->
            "Only $collectedPoints of ${GazeCalibration.TARGETS.size} dots were read " +
                "clearly. Try to look straight at each dot and keep your head still."
        else ->
            "The readings didn't line up well enough. Even, front-on light and a steady " +
                "phone make the biggest difference."
    }
}
