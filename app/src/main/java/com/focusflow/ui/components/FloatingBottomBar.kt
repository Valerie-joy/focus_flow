package com.focusflow.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.LocalFocusFlowColors

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

/**
 * Floating, glassy pill nav bar (Home / Assessment / Results / Profile).
 * Sits with margin from the screen edges rather than spanning full-bleed,
 * matching the "rounded navigation" spec.
 */
@Composable
fun FloatingBottomBar(
    items: List<NavItem>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFocusFlowColors.current
    val shape = RoundedCornerShape(FocusFlowRadius.pill)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(64.dp)
            .shadow(elevation = 20.dp, shape = shape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.1f))
            .clip(shape)
            .background(colors.glassSurface)
            .padding(horizontal = 8.dp),
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

@Composable
private fun RowScope.NavBarItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFocusFlowColors.current
    val indicatorSize by animateDpAsState(
        targetValue = if (selected) 6.dp else 0.dp,
        label = "navIndicator"
    )
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = item.icon, contentDescription = item.label, tint = tint)
            Text(text = item.label, style = MaterialTheme.typography.labelSmall, color = tint)
            Box(
                modifier = Modifier
                    .size(indicatorSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
