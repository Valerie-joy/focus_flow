package com.focusflow.data.remote

import android.content.Context
import com.focusflow.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Lazy singleton [SupabaseClient], mirroring the manual-singleton style
 * already used for [com.focusflow.data.local.FocusFlowDatabase] (a
 * stand-in for proper DI until Hilt is wired app-wide).
 *
 * Only the client-safe URL/publishable (anon) key ever reach this class —
 * both come from BuildConfig, generated from local.properties at build time
 * so neither is a string literal in source control. The Supabase *secret*
 * key must never be referenced here or anywhere else in the app.
 *
 * The Auth plugin's default session manager (`SettingsSessionManager`, a
 * SharedPreferences-backed store on Android via multiplatform-settings)
 * already gives real on-device session persistence with no setup needed,
 * which is what lets SplashScreen tell a returning, still-logged-in user
 * apart from a new one.
 */
object SupabaseClientProvider {
    @Volatile
    private var instance: SupabaseClient? = null

    fun getInstance(context: Context): SupabaseClient =
        instance ?: synchronized(this) {
            instance ?: createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY
            ) {
                install(Auth)
                install(Storage)
                install(Postgrest)
            }.also { instance = it }
        }
}
