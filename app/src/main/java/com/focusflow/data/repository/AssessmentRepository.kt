package com.focusflow.data.repository

import com.focusflow.data.local.AssessmentDao
import com.focusflow.data.local.AssessmentHistoryEntity
import com.focusflow.domain.models.CategoryAssessmentResult
import com.focusflow.domain.usecases.CategoryAttentionScore
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class AssessmentRepository(private val dao: AssessmentDao) {

    fun observeHistory(): Flow<List<AssessmentHistoryEntity>> = dao.observeAll()

    /**
     * Persists one completed assessment session (all categories from a
     * single Phase 5 run). [scored] should be the output of
     * CalculateAttentionScoreUseCase.scoreAll(results) so the stored score
     * matches exactly what the Results/Analysis screens showed the user.
     */
    suspend fun saveSession(scored: List<CategoryAttentionScore>) {
        if (scored.isEmpty()) return
        val sessionId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val entities = scored.map { entry ->
            val result: CategoryAssessmentResult = entry.result
            val metrics = result.gazeMetrics
            AssessmentHistoryEntity(
                sessionId = sessionId,
                timestampMs = timestamp,
                categoryId = result.category.id,
                score = entry.score,
                successful = result.successful,
                interestRating = result.interestRating,
                focusRating = result.focusRating,
                skipped = result.skipped,
                skipCount = result.skipCount,
                screenAttentionScore = entry.components.screenAttention,
                distractionDelayScore = entry.components.distractionDelay,
                gazeStabilityScore = entry.components.gazeStability,
                blinkConsistencyScore = entry.components.blinkConsistency,
                screenAttentionPercentage = metrics.screenAttentionPercentage,
                gazeShiftCount = metrics.gazeShiftCount,
                blinkCount = metrics.blinkCount,
                firstDistractionMs = metrics.firstDistractionMs,
                actualDurationMs = metrics.actualDurationMs,
                analysisFrameRate = metrics.analysisFrameRate,
                analyzedFrameCount = metrics.analyzedFrameCount,
                qualityFlags = metrics.qualityFlags().joinToString(",")
            )
        }
        dao.insertAll(entities)
    }
}
