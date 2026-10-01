package com.focusflow.ui.screens.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.AIInsightCard
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.FloatingBottomBar
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.NavItem
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.ProgressRing
import com.focusflow.ui.components.SectionHeader
import com.focusflow.ui.theme.FocusFlowTheme
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing
import com.focusflow.viewmodel.DashboardUiState
import com.focusflow.viewmodel.SessionSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The app's home screen.
 *
 * Reads entirely from [DashboardUiState], which `DashboardViewModel` derives
 * from real persisted history rather than placeholder numbers.
 *
 * Fixed here beyond styling:
 *
 *  - **The bottom bar lied about where you were.** `currentTab` was local
 *    mutable state set on tap, so navigating to Results and pressing Back left
 *    "Results" highlighted while the Dashboard was on screen. The bar is on
 *    the Dashboard, so Home is *always* the current tab; the other items are
 *    navigation actions, and the highlight now says so truthfully.
 *  - **Two cards were fake buttons.** The "Start" pill and both quick-access
 *    cards used `clickable(indication = null)` — no ripple, no button role for
 *    TalkBack, no guaranteed touch target. They are now real buttons and real
 *    clickable cards.
 *  - **A brand-new user saw a dashboard of nothing.** With no history there
 *    was no insight, no recommendation and no history list, leaving three
 *    headings over empty space. First-run now gets a short orienting card
 *    instead.
 *  - **The bottom bar overlapped content** on gesture-navigation devices: a
 *    hardcoded 100dp bottom pad took no account of the navigation inset.
 */
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    userName: String,
    onStartAssessment: () -> Unit,
    onViewResults: () -> Unit,
    onViewProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasHistory = uiState.recentSessions.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        BlurBackground(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = Sizing.maxContentWidth)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter)
                        // Clears the floating bar, which insets itself for the
                        // system navigation bar separately.
                        .padding(bottom = BOTTOM_BAR_CLEARANCE)
                ) {
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    GreetingHeader(userName = userName)

                    Spacer(modifier = Modifier.height(Spacing.xl))
                    StartAssessmentCard(
                        onStart = onStartAssessment,
                        isFirstAssessment = !hasHistory
                    )

                    if (hasHistory) {
                        Spacer(modifier = Modifier.height(Spacing.xl))
                        SectionHeader(
                            title = "Your week",
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        WeeklyProgressCard(uiState = uiState)
                    }

                    if (uiState.dailyInsight != null) {
                        Spacer(modifier = Modifier.height(Spacing.xl))
                        SectionHeader(
                            title = "What we noticed",
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        AIInsightCard(text = uiState.dailyInsight, label = "Daily insight")
                    }

                    if (uiState.recentRecommendation != null) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        AIInsightCard(
                            text = uiState.recentRecommendation,
                            label = "Recent recommendation"
                        )
                    }

                    if (hasHistory) {
                        Spacer(modifier = Modifier.height(Spacing.xl))
                        SectionHeader(
                            title = "Recent sessions",
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onViewResults,
                            contentDescription = "Recent sessions. Opens your full report."
                        ) {
                            Column {
                                uiState.recentSessions.forEachIndexed { index, session ->
                                    HistoryRow(session = session)
                                    if (index != uiState.recentSessions.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = Spacing.xs),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = Sizing.hairline
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))
                    SectionHeader(
                        title = "Quick access",
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                    Spacer(modifier = Modifier.height(Spacing.xl))
                }
            }
        }

        FloatingBottomBar(
            items = remember {
                listOf(
                    NavItem("Home", Icons.Filled.Home, HOME_ROUTE),
                    NavItem("Assessment", Icons.Filled.Timer, "assessment"),
                    NavItem("Results", Icons.Filled.BarChart, "results"),
                    NavItem("Profile", Icons.Filled.Person, "profile")
                )
            },
            // This bar only ever renders on the Dashboard, so Home is the
            // current destination by construction. Tracking it in local state
            // made the highlight disagree with the screen after a back press.
            currentRoute = HOME_ROUTE,
            onNavigate = { route ->
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
private fun GreetingHeader(userName: String, modifier: Modifier = Modifier) {
    // Computed once per composition rather than on every recomposition; the
    // greeting doesn't need to track the clock while the screen is open.
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val colors = LocalFocusFlowColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = "$greeting,",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
        }
        Box(
            modifier = Modifier
                .size(Sizing.minTouchTarget)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                // Decorative: the name it initialises is read out right beside it.
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * The primary call to action.
 *
 * Previously a gradient panel with a hand-rolled white pill. It is now a
 * standard card with a real button, and its copy adapts: a first-time user is
 * told what an assessment involves rather than being asked to start one
 * blind.
 */
@Composable
private fun StartAssessmentCard(
    onStart: () -> Unit,
    isFirstAssessment: Boolean,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier.fillMaxWidth(), padding = Spacing.md) {
        Column {
            Text(
                text = if (isFirstAssessment) "Run your first assessment" else "Today's assessment",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.xxs))
            Text(
                text = if (isFirstAssessment) {
                    "You'll watch a few short clips while the front camera follows where " +
                        "your eyes go. It takes a few minutes, and nothing is recorded."
                } else {
                    "A few short clips to see what's holding your attention today."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            PrimaryButton(
                text = if (isFirstAssessment) "Get started" else "Start assessment",
                onClick = onStart
            )
        }
    }
}

@Composable
private fun WeeklyProgressCard(uiState: DashboardUiState, modifier: Modifier = Modifier) {
    val completed = uiState.assessmentsCompletedThisWeek
    val goal = uiState.weeklyGoal

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$completed of $goal assessments completed this week"
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Assessments completed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = "$completed",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "of $goal this week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ProgressRing(
                progress = uiState.weeklyProgress,
                size = 76.dp,
                label = "${(uiState.weeklyProgress * 100).toInt()}%"
            )
        }
    }
}

@Composable
private fun HistoryRow(session: SessionSummary, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "${formatDate(session.timestampMs)}, " +
                    "best category ${session.topCategory}, " +
                    "average ${session.averageScore} percent"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
            color = MaterialTheme.colorScheme.onSurface
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
        modifier = modifier,
        onClick = onClick,
        contentDescription = label,
        padding = Spacing.md
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizing.iconMd)
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatDate(timestampMs: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMs))

private const val HOME_ROUTE = "home"

/** Height of the floating bar plus breathing room above it. */
private val BOTTOM_BAR_CLEARANCE = 96.dp

@Preview(showBackground = true, name = "Dashboard")
@Composable
private fun DashboardScreenPreview() {
    val sampleState = DashboardUiState(
        assessmentsCompletedThisWeek = 3,
        weeklyGoal = 5,
        weeklyProgress = 0.6f,
        dailyInsight = "You focus best in the evening. Consider tackling deep work during that time.",
        recentRecommendation = "Music sessions are working best for you right now — " +
            "worth leaning into that format this week.",
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

@Preview(showBackground = true, name = "Dashboard — first run, dark")
@Composable
private fun DashboardFirstRunPreview() {
    FocusFlowTheme(darkTheme = true) {
        DashboardScreen(
            uiState = DashboardUiState(),
            userName = "Sophia",
            onStartAssessment = {},
            onViewResults = {},
            onViewProfile = {}
        )
    }
}
