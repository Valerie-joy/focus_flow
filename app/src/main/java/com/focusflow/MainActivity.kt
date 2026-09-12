package com.focusflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.focusflow.data.local.AppSettings
import com.focusflow.ui.navigation.FocusFlowDestinations
import com.focusflow.ui.navigation.assessmentGraph
import com.focusflow.ui.navigation.authGraph
import com.focusflow.ui.navigation.onboardingGraph
import com.focusflow.ui.navigation.profileGraph
import com.focusflow.ui.navigation.registrationGraph
import com.focusflow.ui.theme.FocusFlowTheme

/**
 * The single Activity for the whole app (standard for a Compose Navigation
 * setup — every screen from Stages 1-10 is a composable destination inside
 * one NavHost, not a separate Activity).
 *
 * Reads Profile's dark-mode override preference via [AppSettings], a live
 * shared StateFlow (not a one-shot read) — so toggling dark mode in Profile
 * re-themes the whole app instantly instead of only on the next launch.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.ensureInitialized(applicationContext)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkModeOverride by AppSettings.darkModeOverride.collectAsStateWithLifecycle()

            FocusFlowTheme(darkTheme = darkModeOverride ?: systemDark) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = FocusFlowDestinations.SPLASH
                ) {
                    authGraph(navController)
                    onboardingGraph(navController)
                    registrationGraph(navController)
                    assessmentGraph(navController)
                    profileGraph(navController)
                }
            }
        }
    }
}
