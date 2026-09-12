package com.focusflow.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory, app-wide reactive mirror of the dark-mode override preference.
 * Exists so toggling it in Profile applies instantly everywhere (MainActivity's
 * FocusFlowTheme included) instead of only on the next app launch — the fix
 * the comment in the previous version of MainActivity already called for.
 *
 * [UserPreferences] remains the source of truth for persistence; this object
 * is just the live cache both MainActivity and ProfileNavGraph read/write
 * through. A manual singleton, matching this codebase's existing stand-ins
 * for DI (e.g. FocusFlowDatabase) rather than pulling in Hilt for one value.
 */
object AppSettings {
    @Volatile
    private var initialized = false

    val darkModeOverride = MutableStateFlow<Boolean?>(null)

    /** Call once, early (e.g. MainActivity.onCreate), to seed from persisted storage. */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            darkModeOverride.value = UserPreferences(context.applicationContext).getDarkModeOverride()
            initialized = true
        }
    }
}
