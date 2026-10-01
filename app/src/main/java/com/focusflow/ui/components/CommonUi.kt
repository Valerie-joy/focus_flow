package com.focusflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.LocalFocusFlowColors
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Shared building blocks the screens were previously each re-inventing.
 *
 * Several screens had the same three patterns copied inline: a bare `Text`
 * with `Modifier.clickable` standing in for a button, a colored `Text`
 * standing in for a status indicator, and an unhandled "no data yet" case.
 * All three are consolidated here so they behave consistently and are
 * accessible once rather than 20 times.
 */

/** Which semantic tone a status element carries. */
enum class StatusTone { NEUTRAL, SUCCESS, WARNING, ERROR }

/**
 * A text-only action.
 *
 * Replaces `Text(..., modifier = Modifier.clickable { })`, which gave TalkBack
 * no button role, had no minimum touch target (some were 17dp tall), showed no
 * press feedback, and could not be reached by keyboard or D-pad.
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = Sizing.minTouchTarget),
        enabled = enabled
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/**
 * A compact status indicator: tinted container, matching icon, and a label.
 *
 * The icon is the point. Status here is signalled by *shape and text* as well
 * as color, so it survives a color-vision deficiency, a grayscale screenshot in
 * a report, and a user who has turned on a display color filter.
 */
@Composable
fun StatusPill(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    announce: Boolean = false
) {
    val colors = LocalFocusFlowColors.current
    val (container, content, icon) = when (tone) {
        StatusTone.SUCCESS -> Triple(
            colors.successContainer, colors.onSuccessContainer, Icons.Filled.CheckCircle
        )
        StatusTone.WARNING -> Triple(
            colors.warningContainer, colors.onWarningContainer, Icons.Filled.WarningAmber
        )
        StatusTone.ERROR -> Triple(
            colors.errorContainerSoft, colors.onErrorContainerSoft, Icons.Filled.ErrorOutline
        )
        StatusTone.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Filled.Info
        )
    }

    Surface(
        modifier = modifier.semantics {
            // A status that changes while the screen is open should be spoken
            // without the user having to go looking for it.
            if (announce) liveRegion = LiveRegionMode.Polite
        },
        shape = RoundedCornerShape(FocusFlowRadius.pill),
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // the adjacent label already says it
                modifier = Modifier.size(Sizing.iconSm)
            )
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * An inline, dismissible-by-the-caller error or warning banner.
 *
 * Form and network errors used to be threaded into a text field's error slot
 * (so "No internet connection" appeared beneath the *password* box, implying
 * the password was wrong) or shown as plain red text with no role. This states
 * the problem where it belongs and announces it via an assertive live region,
 * so a TalkBack user learns a submit failed without re-reading the screen.
 */
@Composable
fun MessageBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.ERROR,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = LocalFocusFlowColors.current
    val (container, content, icon) = when (tone) {
        StatusTone.SUCCESS -> Triple(
            colors.successContainer, colors.onSuccessContainer, Icons.Filled.CheckCircle
        )
        StatusTone.WARNING -> Triple(
            colors.warningContainer, colors.onWarningContainer, Icons.Filled.WarningAmber
        )
        StatusTone.NEUTRAL -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Filled.Info
        )
        StatusTone.ERROR -> Triple(
            colors.errorContainerSoft, colors.onErrorContainerSoft, Icons.Filled.ErrorOutline
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive },
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.iconMd)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = Spacing.xs)
                )
            }
            if (actionLabel != null && onAction != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextAction(text = actionLabel, onClick = onAction, color = content)
                }
            }
        }
    }
}

/**
 * The "nothing here yet" state.
 *
 * History and results screens previously rendered an empty list as an empty
 * screen, which is indistinguishable from a screen that failed to load. An
 * empty state has to say what would be here and how to get it.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Sizing.iconLg),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = Sizing.maxContentWidth)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.md))
            TextAction(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * A screen-section heading with an optional explanatory line.
 *
 * Keeps the heading/caption pairing (size, weight and gap) identical
 * everywhere, which is most of what makes a set of screens feel like one app.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A labelled metric with its plain-language meaning.
 *
 * Results screens showed bare percentages. A number with no explanation in an
 * assessment context invites the user to invent a meaning for it, which for
 * this app is exactly the wrong outcome — so the explanation is part of the
 * component rather than optional garnish.
 */
@Composable
fun MetricRow(
    label: String,
    value: String,
    explanation: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.NEUTRAL
) {
    val colors = LocalFocusFlowColors.current
    val valueColor = when (tone) {
        StatusTone.SUCCESS -> colors.success
        StatusTone.WARNING -> colors.warning
        StatusTone.ERROR -> MaterialTheme.colorScheme.error
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                modifier = Modifier.padding(start = Spacing.xs)
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
