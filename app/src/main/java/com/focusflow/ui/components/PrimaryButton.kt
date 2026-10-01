package com.focusflow.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusflow.R
import com.focusflow.ui.theme.Elevation
import com.focusflow.ui.theme.Sizing

/**
 * The primary call to action.
 *
 * Rebuilt on Material 3's [Button] rather than a hand-rolled `Box` +
 * `Modifier.clickable`. The previous implementation suppressed the ripple
 * (`indication = null`) and drew its own press-scale animation, which cost it
 * the platform behaviours a button is expected to have: a `Role.Button` node
 * for TalkBack, a disabled state announcement, keyboard/D-pad focus, and the
 * system's own press feedback. Those are back.
 *
 * Loading is handled here rather than at each call site: while [loading] is
 * true the button stays enabled-looking but rejects clicks, which is what
 * stops a double tap from firing a sign-in twice. The label is kept in the
 * layout (behind the spinner) so the button doesn't change width mid-request.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    loadingContentDescription: String = stringResource(R.string.a11y_working)
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Sizing.controlHeight)
            .semantics {
                if (loading) stateDescription = loadingContentDescription
            },
        // A loading button must not be `enabled = false`: a disabled node is
        // skipped by TalkBack's traversal, so the user loses the element they
        // just activated mid-request. Blocking the click keeps it announced.
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.none,
            pressedElevation = Elevation.none
        )
    ) {
        ButtonLabel(text = text, loading = loading, spinnerColor = contentColor)
    }
}

/**
 * Lower-emphasis alternative sharing [PrimaryButton]'s contract. Use for the
 * secondary choice in a pair (e.g. "Not now" beside "Allow camera access") so
 * both options stay reachable without two competing filled buttons.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Sizing.controlHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium
    ) {
        ButtonLabel(text = text, loading = loading, spinnerColor = LocalContentColor.current)
    }
}

/**
 * Shared label/spinner slot. The label keeps occupying its space while the
 * spinner shows, so the button's measured width never jumps between states.
 */
@Composable
private fun ButtonLabel(text: String, loading: Boolean, spinnerColor: Color) {
    val loadingLabel = stringResource(R.string.a11y_loading)

    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            // Long translated labels wrap instead of being clipped; the
            // button grows because its height is a *minimum*, not a fixed size.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Held in the layout but drawn transparent while loading, so the
            // button keeps its width and the label isn't read out under the
            // spinner's own announcement.
            modifier = if (loading) {
                Modifier
                    .alpha(0f)
                    .clearAndSetSemantics { }
            } else {
                Modifier
            }
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(Sizing.iconSm)
                    .semantics { contentDescription = loadingLabel },
                color = spinnerColor,
                strokeWidth = 2.dp
            )
        }
    }
}
