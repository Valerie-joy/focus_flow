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

    /** Used by account deletion to erase every stored session. */
    @Query("DELETE FROM assessment_history")
    suspend fun deleteAll()
}
