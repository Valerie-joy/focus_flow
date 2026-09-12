package com.focusflow.data.repository

import com.focusflow.data.local.AdhdSelfReportDao
import com.focusflow.data.local.AdhdSelfReportEntity
import com.focusflow.domain.models.FrequencyAnswer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Mirrors [AssessmentRepository]'s thin-wrapper-over-a-DAO shape. */
class AdhdSelfReportRepository(private val dao: AdhdSelfReportDao) {

    suspend fun saveAnswers(answers: Map<String, FrequencyAnswer>) {
        val answersByName = answers.mapValues { (_, answer) -> answer.name }
        dao.insert(
            AdhdSelfReportEntity(
                timestampMs = System.currentTimeMillis(),
                answersJson = Json.encodeToString(answersByName)
            )
        )
    }
}
