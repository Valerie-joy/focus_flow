package com.focusflow.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.domain.dataset.StimulusDatasetValidator
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AssessmentPhase { PLAYING, ATTENTION_SHIFTED, RATING, COMPLETE, EXHAUSTED }

data class AssessmentSessionState(
    val remainingCategories: List<AttentionCategory> = emptyList(),
    val currentCategory: AttentionCategory? = null,
    val results: List<CategoryAssessmentResult> = emptyList(),
    val phase: AssessmentPhase = AssessmentPhase.PLAYING,
    val pendingGazeMetrics: GazeMetrics? = null, // metrics for the category awaiting a rating
    val successfulCount: Int = 0,
    val targetSuccessfulCount: Int = 5,
    val categorySkipCounts: Map<AttentionCategory, Int> = emptyMap()
)

/**
 * Owns the Phase 5 assessment loop end to end:
 *  - shuffles categories once at session start
 *  - on sustained attention drop: marks the category low-attention, does
 *    NOT increment the successful count, and rotates to a fresh untested
 *    category
 *  - on maintained attention: holds the gaze metrics until the user submits
 *    their 1-5 ratings, then counts it as successful
 *  - stops at 5 successful assessments, or earlier if categories run out
 *    (EXHAUSTED — handled alongside COMPLETE in AssessmentNavGraph, which
 *    saves whatever was measured and routes to results rather than crashing)
 *
 * Scoped to the assessment nested nav graph (shared across its three
 * screens via `viewModel(parentEntry)`), so state survives navigating
 * between Playing -> Shifted -> Playing -> Rating -> Playing... without
 * needing SavedStateHandle plumbing yet. Swap this for a @HiltViewModel
 * once Hilt is wired app-wide — the StateFlow contract doesn't need to
 * change.
 */
class AssessmentViewModel : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<AssessmentSessionState> = _state.asStateFlow()

    /**
     * Guards the end-of-session write. Not part of [AssessmentSessionState]
     * because it is a record of an *effect having run*, not something the UI
     * renders — and it must not be resettable by a state copy.
     */
    private var sessionPersistStarted = false

    private val _persistFailed = MutableStateFlow(false)

    /** True when the end-of-session save failed. Drives a retry prompt. */
    val persistFailed: StateFlow<Boolean> = _persistFailed.asStateFlow()

    /**
     * Writes the finished session exactly once, on [viewModelScope].
     *
     * Two bugs are fixed by this living here rather than in the nav graph.
     *
     * The nav graph ran the save inside `LaunchedEffect(state.phase)` keyed on
     * a phase that *stays* COMPLETE. Pressing Back from the results screen
     * re-entered the playback destination with the phase unchanged, so the
     * effect fired again and wrote **a second copy of the same session** —
     * which then double-counted in the Dashboard's weekly total and appeared
     * twice in the progress history. The `sessionPersistStarted` latch makes
     * the write idempotent regardless of how often the effect re-runs.
     *
     * It also ran the save on `rememberCoroutineScope()`, which is cancelled
     * when the composable leaves composition — and the very next line
     * navigated away. The save was racing its own cancellation, so a slow
     * write could lose a completed session silently. `viewModelScope` outlives
     * the destination, and the ViewModel is scoped to the assessment graph, so
     * the write survives the navigation that follows it.
     *
     * @param save performs the actual persistence. Passed in rather than
     * injecting a repository so the ViewModel keeps its no-argument
     * constructor (it is obtained via `viewModel(parentEntry)` with no factory)
     * and stays unit-testable without Room.
     */
    fun persistSessionOnce(save: suspend (List<CategoryAssessmentResult>) -> Unit) {
        if (sessionPersistStarted) return
        sessionPersistStarted = true
        val results = _state.value.results
        viewModelScope.launch {
            runCatching { save(results) }.onFailure { error ->
                Log.e(TAG, "Failed to persist assessment session", error)
                // Allow a retry: the latch is released only on failure, so the
                // user can try again without risking a duplicate on success.
                sessionPersistStarted = false
                _persistFailed.value = true
            }
        }
    }

    /** Called when the user dismisses or retries after a failed save. */
    fun clearPersistFailure() {
        _persistFailed.value = false
    }

    /**
     * The user chose to end the session before reaching the target.
     *
     * Whatever was already measured is kept and the session is treated as
     * [AssessmentPhase.EXHAUSTED] — the same terminal state as running out of
     * categories, which the nav graph already handles by saving and routing to
     * results. The partially-complete profile will be labelled provisional
     * downstream, which is exactly what it is.
     *
     * @return true when there was something worth showing. False means nothing
     * was measured, and the caller should simply leave rather than present an
     * empty results screen.
     */
    fun endSessionEarly(): Boolean {
        val hasResults = _state.value.results.isNotEmpty()
        if (hasResults) {
            _state.update {
                it.copy(phase = AssessmentPhase.EXHAUSTED, currentCategory = null)
            }
        }
        return hasResults
    }

    private fun initialState(): AssessmentSessionState {
        // Category *presentation order* is randomized per session, as the PID
        // requires. The dataset behind it stays deterministic — this shuffles
        // which approved category comes first, it never generates categories,
        // metadata or scores.
        //
        // Only categories with at least one structurally valid stimulus enter
        // the pool: a malformed dataset row should quietly cost that category
        // its slot, not drop the user into a category with nothing to play.
        val playable = StimulusDatasetValidator.validRecords()
            .map { it.category }
            .distinct()
            .ifEmpty { AttentionCategory.entries.toList() }

        val shuffled = playable.shuffled()
        return AssessmentSessionState(
            currentCategory = shuffled.firstOrNull(),
            remainingCategories = shuffled.drop(1)
        )
    }

    /** Video played to completion with attention staying above threshold. */
    fun onAttentionMaintained(metrics: GazeMetrics) {
        _state.update { it.copy(phase = AssessmentPhase.RATING, pendingGazeMetrics = metrics) }
    }

    /** Attention dropped below threshold for a sustained period; video was stopped early. */
    fun onAttentionShifted(metrics: GazeMetrics) {
        _state.update { current ->
            val category = current.currentCategory ?: return@update current
            val result = CategoryAssessmentResult(
                category = category,
                gazeMetrics = metrics,
                successful = false,
                skipCount = current.categorySkipCounts[category] ?: 0
            )
            current.copy(
                results = current.results + result,
                phase = AssessmentPhase.ATTENTION_SHIFTED
            )
        }
    }

    /** User tapped through the "we noticed your attention shifted" screen. */
    fun acknowledgeAttentionShift() {
        _state.update { withNextCategory(it) }
    }

    /**
     * User chose to move on without watching this category.
     *
     * The first skip of a category requeues it to the *back* of the queue
     * rather than dropping it: with 9 categories and 5 successes required, a
     * dropping skip would leave only 4 combined skips + attention failures
     * before the session ran out of categories entirely. A second skip of the
     * *same* category retires it for good (a single-strike drop, same as an
     * attention shift), so repeatedly skipping the same handful of categories
     * can't cycle forever — worst case is 9 categories x [MAX_SKIPS_PER_CATEGORY]
     * skips before the session reaches EXHAUSTED.
     *
     * A result is only recorded (`skipped = true`) at the skip that actually
     * retires the category — a grace requeue records nothing — so a category
     * later watched successfully or attention-shifted still ends up with
     * exactly one [CategoryAssessmentResult], never two.
     */
    fun skipCategory(partialMetrics: GazeMetrics) {
        _state.update { current ->
            val skippedCategory = current.currentCategory ?: return@update current
            val newSkipCount = (current.categorySkipCounts[skippedCategory] ?: 0) + 1
            val updatedSkipCounts = current.categorySkipCounts + (skippedCategory to newSkipCount)
            val retiredForGood = newSkipCount >= MAX_SKIPS_PER_CATEGORY
            val next = current.remainingCategories.firstOrNull()
            val shouldRecordResult = retiredForGood || next == null

            val newResults = if (shouldRecordResult) {
                current.results + CategoryAssessmentResult(
                    category = skippedCategory,
                    gazeMetrics = partialMetrics,
                    successful = false,
                    skipped = true,
                    skipCount = newSkipCount
                )
            } else current.results

            when {
                next == null -> current.copy(
                    results = newResults,
                    categorySkipCounts = updatedSkipCounts,
                    currentCategory = null,
                    phase = AssessmentPhase.EXHAUSTED
                )
                retiredForGood -> current.copy(
                    results = newResults,
                    categorySkipCounts = updatedSkipCounts,
                    currentCategory = next,
                    remainingCategories = current.remainingCategories.drop(1),
                    phase = AssessmentPhase.PLAYING
                )
                else -> current.copy(
                    results = newResults,
                    categorySkipCounts = updatedSkipCounts,
                    currentCategory = next,
                    remainingCategories = current.remainingCategories.drop(1) + skippedCategory,
                    phase = AssessmentPhase.PLAYING
                )
            }
        }
    }

    /** User submitted the post-video 1-5 ratings. */
    fun submitRating(interestRating: Int, focusRating: Int) {
        _state.update { current ->
            val category = current.currentCategory ?: return@update current
            val metrics = current.pendingGazeMetrics ?: return@update current
            val result = CategoryAssessmentResult(
                category = category,
                gazeMetrics = metrics,
                successful = true,
                interestRating = interestRating,
                focusRating = focusRating,
                skipCount = current.categorySkipCounts[category] ?: 0
            )
            val newSuccessfulCount = current.successfulCount + 1
            val newResults = current.results + result

            if (newSuccessfulCount >= current.targetSuccessfulCount) {
                current.copy(
                    results = newResults,
                    successfulCount = newSuccessfulCount,
                    phase = AssessmentPhase.COMPLETE,
                    pendingGazeMetrics = null
                )
            } else {
                withNextCategory(
                    current.copy(
                        results = newResults,
                        successfulCount = newSuccessfulCount,
                        pendingGazeMetrics = null
                    )
                )
            }
        }
    }

    private fun withNextCategory(state: AssessmentSessionState): AssessmentSessionState {
        val next = state.remainingCategories.firstOrNull()
        return if (next == null) {
            state.copy(currentCategory = null, phase = AssessmentPhase.EXHAUSTED)
        } else {
            state.copy(
                currentCategory = next,
                remainingCategories = state.remainingCategories.drop(1),
                phase = AssessmentPhase.PLAYING
            )
        }
    }

    private companion object {
        const val MAX_SKIPS_PER_CATEGORY = 2
        const val TAG = "AssessmentViewModel"
    }
}
