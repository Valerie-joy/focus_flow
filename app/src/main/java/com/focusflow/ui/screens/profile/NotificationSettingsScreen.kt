package com.focusflow.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.StatusTone
import com.focusflow.ui.components.MessageBanner
import com.focusflow.R
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.SettingsRow
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.Spacing
import com.focusflow.ui.theme.Sizing

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = Sizing.maxContentWidth)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.gutter)
        ) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.a11y_go_back)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )

            // These switches store a preference and nothing reads it yet: the
            // app schedules no notifications, so a "Daily reminder" left on
            // would silently never arrive. Saying so is the honest option —
            // removing the screen would discard a preference the user already
            // set, and leaving it unlabelled promises delivery the app cannot
            // make.
            Spacer(modifier = Modifier.height(Spacing.md))
            MessageBanner(
                message = "Reminders aren't being sent yet. Your choices here are saved and " +
                    "will apply once scheduled notifications are added.",
                tone = StatusTone.NEUTRAL
            )
            Spacer(modifier = Modifier.height(Spacing.md))

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
                                colors = SwitchDefaults.colors()
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
                                colors = SwitchDefaults.colors()
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
                                colors = SwitchDefaults.colors()
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xl))
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
