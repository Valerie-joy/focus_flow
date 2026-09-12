package com.focusflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.AIInsightCard
import com.focusflow.ui.components.AnimatedGauge
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.CustomSlider
import com.focusflow.ui.components.FloatingBottomBar
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.GradientButton
import com.focusflow.ui.components.NavItem
import com.focusflow.ui.components.PremiumDialog
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.components.ProgressRing
import com.focusflow.ui.theme.FocusFlowTheme

@Composable
fun DesignSystemPreviewScreen() {
    var rating by remember { mutableIntStateOf(4) }
    var showDialog by remember { mutableStateOf(false) }
    var currentRoute by remember { mutableStateOf("home") }

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("FocusFlow", style = MaterialTheme.typography.displayLarge)
            Text("Design system preview", style = MaterialTheme.typography.bodyLarge)

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Glass Card", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Frosted, rounded, softly shadowed — the base surface for everything.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            PrimaryButton(text = "Sign In", onClick = {})
            GradientButton(text = "Start assessment", onClick = {})

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("How interesting was this video?", style = MaterialTheme.typography.titleMedium)
                    CustomSlider(
                        value = rating,
                        onValueChange = { rating = it },
                        leftLabel = "Not interesting",
                        rightLabel = "Very interesting"
                    )
                }
            }

            GlassCard {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProgressRing(progress = 0.62f, label = "62%")
                    Text("Assessments completed", style = MaterialTheme.typography.bodySmall)
                }
            }

            GlassCard {
                AnimatedGauge(score = 78, label = "Attention Strength")
            }

            AIInsightCard(
                text = "You focus best in the evening. Consider tackling deep work during that time.",
                label = "Daily insight"
            )

            PrimaryButton(text = "Show error dialog", onClick = { showDialog = true })

            FloatingBottomBar(
                items = listOf(
                    NavItem("Home", Icons.Filled.Home, "home"),
                    NavItem("Assessment", Icons.Filled.Timer, "assessment"),
                    NavItem("Results", Icons.Filled.BarChart, "results"),
                    NavItem("Profile", Icons.Filled.Person, "profile")
                ),
                currentRoute = currentRoute,
                onNavigate = { currentRoute = it }
            )
        }
    }

    if (showDialog) {
        PremiumDialog(
            title = "Couldn't verify document",
            message = "That file doesn't look like a valid diagnosis letter. Try another file or take the assessment instead.",
            onDismiss = { showDialog = false }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DesignSystemPreviewLight() {
    FocusFlowTheme(darkTheme = false) { DesignSystemPreviewScreen() }
}

@Preview(showBackground = true)
@Composable
private fun DesignSystemPreviewDark() {
    FocusFlowTheme(darkTheme = true) { DesignSystemPreviewScreen() }
}
