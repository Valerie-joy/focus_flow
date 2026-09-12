package com.focusflow.data.local

import android.content.Context
import com.focusflow.camera.eyetracking.itracker.AffineMap

/**
 * Lightweight SharedPreferences-backed store for profile fields and simple
 * app settings toggles (Phase 10: notifications, privacy, dark mode). A
 * fuller user profile belongs in its own Room entity once there's a real
 * account/backend system to sync it with; SharedPreferences is the right
 * tool for a handful of small persisted values like these, not a database
 * table.
 */
class UserPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("focusflow_prefs", Context.MODE_PRIVATE)

    // ── Profile ──────────────────────────────────────────────────────
    fun saveProfile(name: String, age: Int, sex: String, email: String) {
        prefs.edit()
            .putString(KEY_NAME, name)
            .putInt(KEY_AGE, age)
            .putString(KEY_SEX, sex)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun getName(): String? = prefs.getString(KEY_NAME, null)
    fun getAge(): Int? = if (prefs.contains(KEY_AGE)) prefs.getInt(KEY_AGE, 0) else null
    fun getSex(): String? = prefs.getString(KEY_SEX, null)
    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    // ── Notification settings ────────────────────────────────────────
    fun isPushEnabled(): Boolean = prefs.getBoolean(KEY_PUSH_ENABLED, true)
    fun setPushEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_PUSH_ENABLED, enabled).apply()

    fun isDailyReminderEnabled(): Boolean = prefs.getBoolean(KEY_DAILY_REMINDER, true)
    fun setDailyReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_DAILY_REMINDER, enabled).apply()

    fun isWeeklySummaryEnabled(): Boolean = prefs.getBoolean(KEY_WEEKLY_SUMMARY, true)
    fun setWeeklySummaryEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_WEEKLY_SUMMARY, enabled).apply()

    // ── Privacy settings ─────────────────────────────────────────────
    fun isAnonymizedAnalyticsEnabled(): Boolean = prefs.getBoolean(KEY_ANALYTICS, false)
    fun setAnonymizedAnalyticsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ANALYTICS, enabled).apply()

    // ── Dark mode override ───────────────────────────────────────────
    // null = follow system (FocusFlowTheme's default). Stored as an Int
    // since SharedPreferences has no nullable Boolean: -1 unset, 0 false, 1 true.
    fun getDarkModeOverride(): Boolean? = when (prefs.getInt(KEY_DARK_MODE, -1)) {
        1 -> true
        0 -> false
        else -> null
    }
    fun setDarkModeOverride(value: Boolean?) {
        prefs.edit().putInt(KEY_DARK_MODE, when (value) {
            true -> 1
            false -> 0
            null -> -1
        }).apply()
    }

    // ── Gaze calibration ─────────────────────────────────────────────
    // The affine fit from the camera setup step that maps iTracker's cm
    // output onto this user's screen. Per user *and* per device by nature —
    // the camera offset it absorbs belongs to the handset — so it is stored
    // here beside the profile rather than synced to the backend.
    fun getGazeCalibration(): AffineMap? = AffineMap.decode(prefs.getString(KEY_GAZE_CALIBRATION, null))
    fun saveGazeCalibration(map: AffineMap) = prefs.edit().putString(KEY_GAZE_CALIBRATION, map.encode()).apply()
    fun clearGazeCalibration() = prefs.edit().remove(KEY_GAZE_CALIBRATION).apply()

    /** Clears everything — called on logout. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_NAME = "user_name"
        private const val KEY_AGE = "user_age"
        private const val KEY_SEX = "user_sex"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_PUSH_ENABLED = "settings_push_enabled"
        private const val KEY_DAILY_REMINDER = "settings_daily_reminder"
        private const val KEY_WEEKLY_SUMMARY = "settings_weekly_summary"
        private const val KEY_ANALYTICS = "settings_analytics"
        private const val KEY_DARK_MODE = "settings_dark_mode"
        private const val KEY_GAZE_CALIBRATION = "gaze_calibration_affine"
    }
}
