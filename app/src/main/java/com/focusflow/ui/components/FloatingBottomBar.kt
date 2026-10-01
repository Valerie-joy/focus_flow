package com.focusflow.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.Elevation
import com.focusflow.ui.theme.Sizing
import com.focusflow.ui.theme.Spacing

/**
 * Marked [Immutable] so Compose can skip the bar when the list identity is
 * unchanged. `List<NavItem>` is an unstable type to the compiler; without this
 * every recomposition of the host screen recomposed all four items.
 */
@Immutable
data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

/**
 * The floating bottom navigation bar.
 *
 * Changes of substance:
 *
 *  - **It sat under the system navigation bar.** No inset handling meant the
 *    bar overlapped the gesture handle on most modern devices, so the bottom
 *    row of labels was partly obscured and the touch targets fought the system
 *    gesture area. It now insets itself.
 *  - **Items were anonymous buttons.** `clickable(indication = null)` gave no
 *    ripple and no selected state to TalkBack — selection was signalled only
 *    by tint and a 6dp dot. `Role.Tab` inside a `selectableGroup` makes the
 *    current destination part of the announcement.
 *  - **Labels could clip.** A fixed 64dp height clipped the two-line layout at
 *    large font scales; the bar now grows with its content.
 */
@Composable
fun FloatingBottomBar(
    items: List<NavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = Elevation.bar,
        border = androidx.compose.foundation.BorderStroke(
            Sizing.hairline,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                NavBarItem(
                    item = item,
                    selected = item.route == currentRoute,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NavBarItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .defaultMinSize(minHeight = Sizing.minTouchTarget)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = item.icon,
            // The label directly below says the same thing; a description here
            // would make TalkBack read every destination twice.
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Sizing.iconMd)
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
