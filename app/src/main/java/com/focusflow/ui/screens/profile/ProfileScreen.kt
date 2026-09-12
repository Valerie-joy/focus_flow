package com.focusflow.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.PremiumDialog
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.SettingsRow
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors

@Composable
fun ProfileScreen(
    userName: String,
    userEmail: String?,
    darkModeOverride: Boolean?,
    onDarkModeOverrideChange: (Boolean?) -> Unit,
    onBack: () -> Unit,
    onPersonalInformation: () -> Unit,
    onAssessmentHistory: () -> Unit,
    onAiRecommendations: () -> Unit,
    onNotifications: () -> Unit,
    onPrivacy: () -> Unit,
    onHelpSupport: () -> Unit,
    onLogout: () -> Unit
) {
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp)) // balances the back button so title stays centered
            }

            Spacer(modifier = Modifier.height(16.dp))
            AvatarHeader(userName = userName, userEmail = userEmail)

            Spacer(modifier = Modifier.height(24.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.Person,
                        label = "Personal information",
                        onClick = onPersonalInformation
                    )
                    SettingsRow(
                        icon = Icons.Filled.Assessment,
                        label = "Assessment history",
                        onClick = onAssessmentHistory
                    )
                    SettingsRow(
                        icon = Icons.Filled.AutoAwesome,
                        label = "AI Recommendations",
                        onClick = onAiRecommendations
                    )
                    SettingsRow(
                        icon = Icons.Filled.Shield,
                        label = "Privacy & Security",
                        onClick = onPrivacy
                    )
                    SettingsRow(
                        icon = Icons.Filled.Notifications,
                        label = "Notifications",
                        onClick = onNotifications
                    )
                    SettingsRow(
                        icon = Icons.Filled.HelpOutline,
                        label = "Help & Support",
                        onClick = onHelpSupport
                    )
                    SettingsRow(
                        icon = Icons.Filled.DarkMode,
                        label = "Dark mode",
                        subtitle = when (darkModeOverride) {
                            true -> "On"
                            false -> "Off"
                            null -> "Follows system"
                        },
                        trailingContent = {
                            Switch(
                                checked = darkModeOverride ?: systemDark,
                                onCheckedChange = { onDarkModeOverrideChange(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(
                text = "Log out",
                onClick = { showLogoutConfirm = true },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showLogoutConfirm) {
        PremiumDialog(
            title = "Log out?",
            message = "You'll need to sign in again to continue tracking your attention.",
            onDismiss = { showLogoutConfirm = false },
            primaryActionLabel = "Log out",
            onPrimaryAction = {
                showLogoutConfirm = false
                onLogout()
            },
            secondaryActionLabel = "Cancel",
            onSecondaryAction = { showLogoutConfirm = false }
        )
    }
}

@Composable
private fun AvatarHeader(userName: String, userEmail: String?) {
    val colors = LocalFocusFlowColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(colors.glassSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = userName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (userEmail != null) {
            Text(
                text = userEmail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    FocusFlowTheme {
        ProfileScreen(
            userName = "Sophia Martinez",
            userEmail = "sophia.m@email.com",
            darkModeOverride = null,
            onDarkModeOverrideChange = {},
            onBack = {},
            onPersonalInformation = {},
            onAssessmentHistory = {},
            onAiRecommendations = {},
            onNotifications = {},
            onPrivacy = {},
            onHelpSupport = {},
            onLogout = {}
        )
    }
}
