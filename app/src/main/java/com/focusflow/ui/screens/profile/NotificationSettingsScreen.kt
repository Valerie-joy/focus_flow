package com.focusflow.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.SettingsRow
import com.focusflow.ui.theme.FocusFlowTheme

data class NotificationSettingsState(
    val pushEnabled: Boolean,
    val dailyReminderEnabled: Boolean,
    val weeklySummaryEnabled: Boolean
)

@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsState,
    onBack: () -> Unit,
    onPushChanged: (Boolean) -> Unit,
    onDailyReminderChanged: (Boolean) -> Unit,
    onWeeklySummaryChanged: (Boolean) -> Unit
) {
    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingsRow(
                        icon = Icons.Filled.NotificationsActive,
                        label = "Push notifications",
                        subtitle = "Alerts about your assessments",
                        trailingContent = {
                            Switch(
                                checked = state.pushEnabled,
                                onCheckedChange = onPushChanged,
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    )
                    SettingsRow(
                        icon = Icons.Filled.CalendarToday,
                        label = "Daily reminder",
                        subtitle = "A nudge to take today's assessment",
                        trailingContent = {
                            Switch(
                                checked = state.dailyReminderEnabled,
                                onCheckedChange = onDailyReminderChanged,
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    )
                    SettingsRow(
                        icon = Icons.Filled.Summarize,
                        label = "Weekly summary",
                        subtitle = "A recap of your weekly focus score",
                        trailingContent = {
                            Switch(
                                checked = state.weeklySummaryEnabled,
                                onCheckedChange = onWeeklySummaryChanged,
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationSettingsScreenPreview() {
    FocusFlowTheme {
        NotificationSettingsScreen(
            state = NotificationSettingsState(true, true, false),
            onBack = {},
            onPushChanged = {},
            onDailyReminderChanged = {},
            onWeeklySummaryChanged = {}
        )
    }
}
