package com.focusflow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.data.local.AssessmentHistoryEntity
import com.focusflow.data.repository.AssessmentRepository
import com.focusflow.domain.models.AttentionCategory
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.models.GazeMetrics
import com.focusflow.domain.usecases.CategoryAttentionScore
import com.focusflow.domain.usecases.GenerateRecommendationsUseCase
import com.focusflow.domain.usecases.RecommendationProfile
import com.focusflow.domain.usecases.ScoreComponents
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CategoryRankEntry(val category: String, val score: Int)

data class ProgressPointData(val dateLabel: String, val score: Int)

data class ResultsUiState(
    val hasData: Boolean = false,
    val latestSessionDateLabel: String? = null,
    val latestSessionRanking: List<CategoryRankEntry> = emptyList(),
    val insightText: String? = null,
    val progressHistory: List<ProgressPointData> = emptyList(),
    /** Learning style, techniques and goals for the latest session. */
    val recommendation: RecommendationProfile? = null,
    /** Successful assessments in the latest session, against the required five. */
    val successfulAssessments: Int = 0,
    val requiredAssessments: Int = REQUIRED_SUCCESSFUL_ASSESSMENTS
) {
    /**
     * A profile built on fewer than five successful assessments is
     * provisional. The PID makes five the threshold for generating a profile
     * at all, so anything below it must be labelled rather than presented as
     * a settled result.
     */
    val isProvisional: Boolean get() = successfulAssessments < requiredAssessments
}

const val REQUIRED_SUCCESSFUL_ASSESSMENTS = 5

/**
 * Phase 9's data source. Unlike the Phase 6 results screens (which only
 * ever show the session that *just* finished, held in the short-lived
 * AssessmentViewModel), this reads persisted history — so it's what
 * Dashboard's "Results" quick-access card should show, and what
 * PdfReportGenerator/EmailReportService export from.
 */
class ResultsViewModel(private val repository: AssessmentRepository) : ViewModel() {

    val state: StateFlow<ResultsUiState> = repository.observeHistory()
        .map { history -> deriveState(history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResultsUiState())

    private fun deriveState(history: List<AssessmentHistoryEntity>): ResultsUiState {
        if (history.isEmpty()) return ResultsUiState(hasData = false)

        val sessionsByTime = history.groupBy { it.sessionId }
            .entries
            .sortedByDescending { it.value.first().timestampMs }

        val latestSession = sessionsByTime.first()
        val latestRanking = latestSession.value
            .map { CategoryRankEntry(AttentionCategory.displayNameFor(it.categoryId), it.score) }
            .sortedByDescending { it.score }

        val insight = latestRanking.firstOrNull()?.let { top ->
            "Your attention is strongest with ${top.category} content (${top.score}%). " +
                (latestRanking.lastOrNull()?.let { weakest ->
                    "It dips most with ${weakest.category} (${weakest.score}%) — that's a good place to try shorter, more interactive material."
                } ?: "")
        }

        val progressHistory = sessionsByTime
            .sortedBy { it.value.first().timestampMs }
            .takeLast(8)
            .map { (_, entries) ->
                ProgressPointData(
                    dateLabel = formatShortDate(entries.first().timestampMs),
                    score = entries.map { it.score }.average().let { Math.round(it).toInt() }
                )
            }

        val scored = latestSession.value.toScoredResults()

        return ResultsUiState(
            hasData = true,
            latestSessionDateLabel = formatFullDate(latestSession.value.first().timestampMs),
            latestSessionRanking = latestRanking,
            insightText = insight,
            progressHistory = progressHistory,
            recommendation = GenerateRecommendationsUseCase().invoke(scored),
            successfulAssessments = latestSession.value.count { it.successful }
        )
    }

    /**
     * Rebuilds scored results from stored rows so the recommendation engine
     * runs on the same numbers the user was shown.
     *
     * The persisted score is carried through rather than recomputed: it is
     * what was displayed and exported at the time, and recomputing it under a
     * possibly-changed weight vector would silently rewrite history. Rows
     * whose category no longer resolves are dropped rather than crashing.
     */
    private fun List<AssessmentHistoryEntity>.toScoredResults(): List<CategoryAttentionScore> =
        mapNotNull { entity ->
            val category = AttentionCategory.fromRawOrNull(entity.categoryId) ?: return@mapNotNull null
            CategoryAttentionScore(
                result = CategoryAssessmentResult(
                    category = category,
                    gazeMetrics = GazeMetrics(
                        screenAttentionPercentage = entity.screenAttentionPercentage,
                        gazeShiftCount = entity.gazeShiftCount,
                        firstDistractionMs = entity.firstDistractionMs,
                        blinkCount = entity.blinkCount,
                        actualDurationMs = entity.actualDurationMs,
                        analyzedFrameCount = entity.analyzedFrameCount,
                        analysisFrameRate = entity.analysisFrameRate
                    ),
                    successful = entity.successful,
                    interestRating = entity.interestRating,
                    focusRating = entity.focusRating,
                    skipped = entity.skipped,
                    skipCount = entity.skipCount
                ),
                score = entity.score,
                preciseScore = entity.score.toDouble(),
                components = ScoreComponents(
                    screenAttention = entity.screenAttentionScore,
                    distractionDelay = entity.distractionDelayScore,
                    gazeStability = entity.gazeStabilityScore,
                    blinkConsistency = entity.blinkConsistencyScore,
                    interest = entity.interestRating,
                    focus = entity.focusRating
                )
            )
        }

    private fun formatShortDate(timestampMs: Long): String =
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))

    private fun formatFullDate(timestampMs: Long): String =
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestampMs))
}
