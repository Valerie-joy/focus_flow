package com.focusflow.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusflow.data.local.AssessmentHistoryEntity
import com.focusflow.data.repository.AssessmentRepository
import com.focusflow.domain.models.AttentionCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class SessionSummary(
    val sessionId: String,
    val timestampMs: Long,
    val topCategory: String,
    val averageScore: Int
)

data class DashboardUiState(
    val history: List<AssessmentHistoryEntity> = emptyList(),
    val assessmentsCompletedThisWeek: Int = 0,
    val weeklyGoal: Int = 5,
    val weeklyProgress: Float = 0f, // 0f..1f
    val dailyInsight: String? = null,
    val recentRecommendation: String? = null,
    val recentSessions: List<SessionSummary> = emptyList()
)

/**
 * Reads persisted assessment history (across app restarts, via Room —
 * see AssessmentRepository) and derives everything the Dashboard needs:
 * weekly completion count/progress ring fraction, a time-of-day daily
 * insight, a plain-language recommendation blurb from the most recent
 * session, and a grouped session history list.
 *
 * Not a @HiltViewModel yet — see FocusFlowDatabase's doc comment for why.
 */
class DashboardViewModel(private val repository: AssessmentRepository) : ViewModel() {

    val state: StateFlow<DashboardUiState> = repository.observeHistory()
        .map { history -> deriveState(history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private fun deriveState(history: List<AssessmentHistoryEntity>): DashboardUiState {
        val oneWeekAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val thisWeekSessionIds = history.filter { it.timestampMs >= oneWeekAgo }.map { it.sessionId }.distinct()
        val completedThisWeek = thisWeekSessionIds.size
        val weeklyGoal = 5

        val sessions = history.groupBy { it.sessionId }
            .map { (sessionId, entries) ->
                val top = entries.maxByOrNull { it.score }
                SessionSummary(
                    sessionId = sessionId,
                    timestampMs = entries.first().timestampMs,
                    topCategory = top?.categoryId?.let(AttentionCategory::displayNameFor) ?: "—",
                    averageScore = entries.map { it.score }.average().let { Math.round(it).toInt() }
                )
            }
            .sortedByDescending { it.timestampMs }

        val dailyInsight = deriveDailyInsight(history)
        val recentRecommendation = sessions.firstOrNull()?.let { latest ->
            "${latest.topCategory} sessions are working best for you right now — worth leaning into that format this week."
        }

        return DashboardUiState(
            history = history,
            assessmentsCompletedThisWeek = completedThisWeek,
            weeklyGoal = weeklyGoal,
            weeklyProgress = (completedThisWeek.toFloat() / weeklyGoal).coerceIn(0f, 1f),
            dailyInsight = dailyInsight,
            recentRecommendation = recentRecommendation,
            recentSessions = sessions.take(5)
        )
    }

    /**
     * Buckets every historical entry by time-of-day and reports whichever
     * bucket has the highest average score — a real computed insight from
     * the user's own history, not a canned line, though it needs a
     * handful of sessions across different times of day before it says
     * anything meaningful.
     */
    private fun deriveDailyInsight(history: List<AssessmentHistoryEntity>): String? {
        if (history.size < 3) return null

        val buckets = mutableMapOf<String, MutableList<Int>>()
        history.forEach { entry ->
            val hour = Calendar.getInstance().apply { timeInMillis = entry.timestampMs }.get(Calendar.HOUR_OF_DAY)
            val bucket = when (hour) {
                in 5..11 -> "morning"
                in 12..16 -> "afternoon"
                in 17..21 -> "evening"
                else -> "night"
            }
            buckets.getOrPut(bucket) { mutableListOf() }.add(entry.score)
        }

        val best = buckets.entries
            .filter { it.value.size >= 2 } // avoid a single lucky data point deciding the insight
            .maxByOrNull { it.value.average() }
            ?: return null

        return "You focus best in the ${best.key}. Consider tackling deep work during that time."
    }
}
