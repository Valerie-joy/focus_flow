package com.focusflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessmentDao {
    @Insert
    suspend fun insertAll(entities: List<AssessmentHistoryEntity>)

    @Query("SELECT * FROM assessment_history ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<AssessmentHistoryEntity>>
}
