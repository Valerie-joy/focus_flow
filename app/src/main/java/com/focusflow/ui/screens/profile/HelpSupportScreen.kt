package com.focusflow.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowTheme

private val faqItems = listOf(
    "How is my attention measured?" to
        "FocusFlow uses your front camera to check whether your eyes are on the screen during a video, entirely on-device — no video is ever recorded or sent anywhere.",
    "Is this a medical diagnosis?" to
        "No. FocusFlow's self-reflection questionnaire and attention assessment are wellness tools, not a clinical diagnosis. If anything here resonates with you, it's worth a conversation with a doctor or licensed clinician.",
    "Can I delete my data?" to
        "Yes — logging out clears your locally stored profile and settings from this device.",
    "Why did an assessment stop early?" to
        "If the app noticed your attention shift away from the screen for a while, it stops that video early and tries a different category — this is normal and doesn't count against you."
)

@Composable
fun HelpSupportScreen(onBack: () -> Unit, onContactSupport: () -> Unit) {
    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Help & Support",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))

            faqItems.forEach { (question, answer) ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(text = question, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(text = "Contact support", onClick = onContactSupport, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HelpSupportScreenPreview() {
    FocusFlowTheme {
        HelpSupportScreen(onBack = {}, onContactSupport = {})
    }
}
