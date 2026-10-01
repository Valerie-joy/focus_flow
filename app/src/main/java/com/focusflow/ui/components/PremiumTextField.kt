package com.focusflow.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * The single text input used across auth and form screens.
 *
 * Rebuilt on Material 3's [OutlinedTextField]. The previous version was a
 * `BasicTextField` inside a styled `Box`, which meant it had no floating
 * label, no `Role.TextField`/error semantics for TalkBack, no IME action
 * support (every field showed a "newline" key and the keyboard never advanced
 * between fields), and a hardcoded 56dp height that clipped its own text at
 * large font scales.
 *
 * Accessibility: when [errorMessage] is set, the field carries an
 * `SemanticsProperties.Error` node so TalkBack announces *that the field is
 * invalid* in addition to reading the message — status is never carried by the
 * red border alone.
 *
 * @param isRequired appends a visible "(required)" hint to the label. Required
 * fields are marked positively rather than leaving the user to discover which
 * ones are optional by trial.
 * @param imeAction which key the keyboard shows. Pass [ImeAction.Next] on every
 * field but the last one in a form, and [ImeAction.Done] on the last.
 * @param onImeAction invoked when that key is pressed — wire the last field's
 * to the form's submit action so the keyboard can complete the form.
 */
@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    errorMessage: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    isRequired: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null
) {
    // rememberSaveable: the reveal state should survive rotation, but it must
    // reset when the composable genuinely leaves — a password left revealed
    // across a process death and restore would be a small privacy leak.
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val currentOnImeAction by rememberUpdatedState(onImeAction)
    val isError = errorMessage != null

    val labelText = if (isRequired) "$label (required)" else label

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // The caller's modifier goes on the field, not on a wrapper.
        //
        // This was `Column(modifier = modifier) { OutlinedTextField(...) }`,
        // which quietly broke two things that only showed up on a device:
        // a `focusRequester` passed in by the auth and profile forms attached
        // to the Column, so `requestFocus()` did nothing and the keyboard's
        // "Next" key never moved between fields; and a `testTag` resolved to a
        // node with no text-input semantics, so every Compose test that typed
        // into a field failed with "RequestFocus is not defined".
        //
        // The Column served no other purpose — the supporting text and error
        // message are slots inside OutlinedTextField itself.
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (isError) error(errorMessage)
            },
        enabled = enabled,
        isError = isError,
        singleLine = singleLine,
        label = { Text(labelText) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, style = MaterialTheme.typography.bodyLarge) }
        } else null,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.small,
        visualTransformation = when {
            isPassword && !passwordVisible -> PasswordVisualTransformation()
            else -> VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            capitalization = capitalization,
            imeAction = imeAction,
            // Password managers and the system keyboard both rely on this
            // to offer the right autofill; it is off by default for
            // password fields, which is why suggestions never appeared.
            autoCorrectEnabled = !isPassword && keyboardType == KeyboardType.Text
        ),
        keyboardActions = KeyboardActions(
            onNext = { currentOnImeAction() },
            onDone = { currentOnImeAction() },
            onGo = { currentOnImeAction() },
            onSend = { currentOnImeAction() }
        ),
        // Supporting text lives in the field itself rather than a separate
        // Text below: OutlinedTextField reserves the space for it, so an
        // error appearing no longer shifts every control beneath it.
        supportingText = when {
            errorMessage != null -> {
                { Text(errorMessage, style = MaterialTheme.typography.bodySmall) }
            }
            supportingText != null -> {
                { Text(supportingText, style = MaterialTheme.typography.bodySmall) }
            }
            else -> null
        },
        trailingIcon = when {
            isPassword -> {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            }
                        )
                    }
                }
            }
            trailingContent != null -> trailingContent
            else -> null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}