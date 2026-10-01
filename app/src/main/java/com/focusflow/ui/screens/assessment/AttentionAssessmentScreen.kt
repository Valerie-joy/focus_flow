package com.focusflow.ui.screens.assessment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.focusflow.domain.dataset.StimulusDataset
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.ui.theme.FocusFlowTheme

/**
 * One category's assessment, in two steps.
 *
 * This file used to be ~720 lines holding both steps, the camera binding for
 * each of them, the gaze overlay and the formatting helpers. It has been split
 * by responsibility, with no change to the measurement path:
 *
 *  - [GazeSetupStep] — readiness: preview, gaze overlay, acquisition gate, and
 *    what the session will ask of the user
 *  - [AttentionPlaybackStep] — the measured clip: stimulus, progress, tracking
 *    status, skip
 *  - [bindGazeCamera] — the camera/analysis binding both steps share, which
 *    each previously carried its own drifting copy of
 *
 * Flow:
 * - the clip reaches its natural end, or the duration cap is hit (whichever
 *   comes first), with attention maintained → [onAttentionMaintained]
 * - a sustained attention drop stops it immediately → [onAttentionShifted]
 * - [onSkip] moves to another category; the first skip requeues this one to
 *   the back, a second skip retires it (see
 *   [com.focusflow.viewmodel.AssessmentViewModel.skipCategory])
 * - [onExit] leaves the assessment deliberately, keeping whatever categories
 *   were already completed. Both steps route the system Back gesture here
 *   through a confirmation, so a stray back-swipe can no longer discard a
 *   half-finished session without saying so.
 *
 * Privacy: frames are converted in memory, handed to on-device MediaPipe, and
 * released immediately. Nothing is recorded or written to disk in either step,
 * and no frame ever leaves the phone.
 */
@Composable
fun AttentionAssessmentScreen(
    category: AttentionCategory,
    onAttentionMaintained: (GazeMetrics) -> Unit,
    onAttentionShifted: (GazeMetrics) -> Unit,
    onSkip: (GazeMetrics) -> Unit = {},
    onExit: () -> Unit = {},
    videoDurationSeconds: Int = 45,
    modifier: Modifier = Modifier
) {
    // rememberSaveable keyed on the category: a rotation partway through setup
    // previously threw the user back to step 1 and re-ran acquisition from
    // scratch. Still re-keyed per category, so each new category starts at
    // setup as before.
    var setupComplete by rememberSaveable(category) { mutableStateOf(false) }

    // The user-facing estimate uses the same cap the playback step will apply,
    // so "about 45 seconds" on the setup screen is the duration actually
    // enforced rather than a number picked for the copy.
    val estimatedDurationSeconds = remember(category, videoDurationSeconds) {
        StimulusDataset.selectFor(category)?.durationLimitSeconds ?: videoDurationSeconds
    }

    if (!setupComplete) {
        GazeSetupStep(
            onReady = { setupComplete = true },
            onSkip = onSkip,
            onExit = onExit,
            estimatedDurationSeconds = estimatedDurationSeconds,
            modifier = modifier
        )
    } else {
        AttentionPlaybackStep(
            category = category,
            onAttentionMaintained = onAttentionMaintained,
            onAttentionShifted = onAttentionShifted,
            onSkip = onSkip,
            onExit = onExit,
            videoDurationSeconds = videoDurationSeconds,
            modifier = modifier
        )
    }
}

@Preview(showBackground = true, name = "Assessment — setup")
@Composable
private fun AttentionAssessmentScreenPreview() {
    FocusFlowTheme {
        AttentionAssessmentScreen(
            category = AttentionCategory.MUSIC,
            onAttentionMaintained = {},
            onAttentionShifted = {}
        )
    }
}
