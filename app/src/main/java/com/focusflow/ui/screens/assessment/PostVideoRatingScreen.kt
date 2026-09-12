package com.focusflow.ui.screens.assessment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.focusflow.ui.components.BlurBackground
import com.focusflow.ui.components.CustomSlider
import com.focusflow.ui.components.GlassCard
import com.focusflow.ui.components.PrimaryButton
import com.focusflow.ui.theme.FocusFlowTheme

/**
 * Shown after a video completes with attention maintained. Reuses the
 * design-system CustomSlider for both 1-5 ratings, matching the reference
 * design's "How interesting was this video?" / "How easy was it to stay
 * focused?" pair, plus the "Successful assessments: X/5" progress footer.
 */
@Composable
fun PostVideoRatingScreen(
    successfulCount: Int,
    targetCount: Int,
    onSubmit: (interestRating: Int, focusRating: Int) -> Unit
) {
    var interestRating by remember { mutableIntStateOf(3) }
    var focusRating by remember { mutableIntStateOf(3) }

    BlurBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "How was that?",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Help us understand your experience.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "How interesting was this video?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CustomSlider(
                        value = interestRating,
                        onValueChange = { interestRating = it },
                        leftLabel = "Not interesting",
                        rightLabel = "Very interesting"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "How easy was it to stay focused?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CustomSlider(
                        value = focusRating,
                        onValueChange = { focusRating = it },
                        leftLabel = "Very hard",
                        rightLabel = "Very easy"
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            PrimaryButton(
                text = "Continue",
                onClick = { onSubmit(interestRating, focusRating) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Successful assessments: $successfulCount / $targetCount",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostVideoRatingScreenPreview() {
    FocusFlowTheme {
        PostVideoRatingScreen(successfulCount = 2, targetCount = 5, onSubmit = { _, _ -> })
    }
}
