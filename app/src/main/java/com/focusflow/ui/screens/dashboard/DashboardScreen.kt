package com.focusflow.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.AIInsightCard
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.FloatingBottomBar
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.NavItem
import com.focusflow.ui.components.ProgressRing
import com.focusflow.ui.theme.Accent
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Primary
import com.focusflow.viewmodel.DashboardUiState
import com.focusflow.viewmodel.SessionSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Phase 8. Every field the spec calls for — greeting, today's focus,
 * continue-assessment CTA, weekly focus score + progress ring, recent
 * recommendation, daily insight, assessment history, quick access — reads
 * from [DashboardUiState], which DashboardViewModel derives from real
 * persisted history (see AssessmentRepository) rather than placeholder
 * numbers.
 */
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    userName: String,
    onStartAssessment: () -> Unit,
    onViewResults: () -> Unit,
    onViewProfile: () -> Unit
) {
    var currentTab by remember { mutableStateOf("home") }

    Box(modifier = Modifier.fillMaxSize()) {
        BlurBackground(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 100.dp)
            ) {
                Spacer(modifier = Modifier.height(28.dp))
                GreetingHeader(userName = userName)

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Today's focus",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                TodaysFocusCard(onStart = onStartAssessment)

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Your progress",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                WeeklyProgressCard(uiState = uiState)

                if (uiState.dailyInsight != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Daily insight",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    AIInsightCard(text = uiState.dailyInsight, label = "Daily insight")
                }

                if (uiState.recentRecommendation != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AIInsightCard(text = uiState.recentRecommendation, label = "Recent recommendation")
                }

                if (uiState.recentSessions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Assessment history",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            uiState.recentSessions.forEachIndexed { index, session ->
                                HistoryRow(session = session)
                                if (index != uiState.recentSessions.lastIndex) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Quick access",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAccessCard(
                        label = "Results",
                        icon = Icons.Filled.BarChart,
                        onClick = onViewResults,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAccessCard(
                        label = "Profile",
                        icon = Icons.Filled.Person,
                        onClick = onViewProfile,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        FloatingBottomBar(
            items = listOf(
                NavItem("Home", Icons.Filled.Home, "home"),
                NavItem("Assessment", Icons.Filled.Timer, "assessment"),
                NavItem("Results", Icons.Filled.BarChart, "results"),
                NavItem("Profile", Icons.Filled.Person, "profile")
            ),
            currentRoute = currentTab,
            onNavigate = { route ->
                currentTab = route
                when (route) {
                    "assessment" -> onStartAssessment()
                    "results" -> onViewResults()
                    "profile" -> onViewProfile()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun GreetingHeader(userName: String) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = "$greeting,",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val colors = LocalFocusFlowColors.current
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.glassSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TodaysFocusCard(onStart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Primary, Accent)))
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "Take a short assessment",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Track your attention today",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onStart
                    )
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(text = "Start", style = MaterialTheme.typography.labelLarge, color = Primary)
            }
        }
    }
}

@Composable
private fun WeeklyProgressCard(uiState: DashboardUiState) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Assessments completed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${uiState.assessmentsCompletedThisWeek}",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "of ${uiState.weeklyGoal} weekly goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProgressRing(
                progress = uiState.weeklyProgress,
                size = 80.dp,
                label = "${(uiState.weeklyProgress * 100).toInt()}%"
            )
        }
    }
}

@Composable
private fun HistoryRow(session: SessionSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = formatDate(session.timestampMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Best: ${session.topCategory}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${session.averageScore}%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun QuickAccessCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        blurBehind = false
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun formatDate(timestampMs: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    val sampleState = DashboardUiState(
        assessmentsCompletedThisWeek = 3,
        weeklyGoal = 5,
        weeklyProgress = 0.6f,
        dailyInsight = "You focus best in the evening. Consider tackling deep work during that time.",
        recentRecommendation = "Music sessions are working best for you right now — worth leaning into that format this week.",
        recentSessions = listOf(
            SessionSummary("1", System.currentTimeMillis(), "Music", 91),
            SessionSummary("2", System.currentTimeMillis() - 86_400_000, "Gaming", 87)
        )
    )
    FocusFlowTheme {
        DashboardScreen(
            uiState = sampleState,
            userName = "Sophia",
            onStartAssessment = {},
            onViewResults = {},
            onViewProfile = {}
        )
    }
}
