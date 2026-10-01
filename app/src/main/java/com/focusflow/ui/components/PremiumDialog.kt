package com.focusflow.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.focusflow.R
import com.focusflow.ui.theme.Elevation

/**
 * The app's modal prompt — validation failures, confirmations, results of a
 * background action.
 *
 * Rebuilt on Material 3's [AlertDialog] rather than a `Dialog` wrapping a
 * card. The hand-rolled version lost several things the platform dialog gives
 * for free, all of which mattered:
 *
 *  - **Actions competed.** Both buttons were filled [PrimaryButton]s, so a
 *    destructive-ish secondary ("Resend email") carried the same visual weight
 *    as the primary. Text buttons in the standard confirm/dismiss slots put
 *    the emphasis back where it belongs.
 *  - **Long messages were unreachable.** The column had no scroll, so a
 *    message longer than the screen was simply cut off with no way to read the
 *    rest — and the buttons went with it.
 *  - **Semantics.** `AlertDialog` carries the correct dialog role and title
 *    association for accessibility services; a `Dialog` + `Column` does not.
 *
 * The parameter list is unchanged, so the ~10 call sites are untouched.
 */
@Composable
fun PremiumDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    primaryActionLabel: String = stringResource(R.string.action_got_it),
    onPrimaryAction: () -> Unit = onDismiss,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon,
        title = {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(
                modifier = Modifier
                    // Caps the message at roughly half a tall screen and
                    // scrolls beyond that, so the action buttons can never be
                    // pushed off the dialog by a long error string.
                    .heightIn(max = MAX_MESSAGE_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onPrimaryAction) { Text(primaryActionLabel) }
        },
        dismissButton = if (secondaryActionLabel != null && onSecondaryAction != null) {
            { TextButton(onClick = onSecondaryAction) { Text(secondaryActionLabel) } }
        } else null,
        shape = MaterialTheme.shapes.large,
        tonalElevation = Elevation.dialog
    )
}

private val MAX_MESSAGE_HEIGHT = 320.dp
