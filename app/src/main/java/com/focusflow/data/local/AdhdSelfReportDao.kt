package com.focusflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AdhdSelfReportDao {
    @Insert
    suspend fun insert(entity: AdhdSelfReportEntity)

    @Query("SELECT * FROM adhd_self_report ORDER BY timestampMs DESC LIMIT 1")
    suspend fun latest(): AdhdSelfReportEntity?
}
