package com.focusflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Singleton Room database. This manual singleton is a deliberate stand-in
 * for Hilt's `@Singleton @Provides Database` — the spec calls for Hilt DI,
 * but wiring the full Hilt module graph is app-wide plumbing outside the
 * scope of any single screen/feature. Swap `getInstance` usages for
 * constructor-injected instances once Hilt is set up; the DAO/Repository
 * API surface won't need to change.
 */
@Database(
    entities = [AssessmentHistoryEntity::class, AdhdSelfReportEntity::class],
    version = 4,
    exportSchema = false
)
abstract class FocusFlowDatabase : RoomDatabase() {
    abstract fun assessmentDao(): AssessmentDao
    abstract fun adhdSelfReportDao(): AdhdSelfReportDao

    companion object {
        @Volatile
        private var INSTANCE: FocusFlowDatabase? = null

        fun getInstance(context: Context): FocusFlowDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FocusFlowDatabase::class.java,
                    "focusflow.db"
                )
                    // No shipped install base yet and no migration story built
                    // out — destructive fallback is fine for this pre-release
                    // schema bump (version 1 -> 2 adds adhd_self_report;
                    // 2 -> 3 adds assessment_history.skipped; 3 -> 4 renames
                    // assessment_history.category to the canonical categoryId
                    // and adds the component sub-scores, capture-quality and
                    // analysis-frame-rate columns).
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
