package com.focusflow.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusflow.ui.theme.FocusFlowRadius
import com.focusflow.ui.theme.LocalFocusFlowColors

/**
 * The core "premium" surface used throughout FocusFlow: a rounded, softly
 * elevated, semi-translucent card with a hairline highlight border to sell
 * the glass effect. On API 31+ it applies a real background blur so content
 * behind the card genuinely frosts; below that it falls back gracefully to
 * a flat translucent fill (still reads as "glass" against gradient
 * backgrounds, just without the blur).
 *
 * @param blurBehind if true and running on API 31+, blurs whatever is
 * rendered directly behind this composable in the same layer. Turn off for
 * cards sitting on a flat background where the blur cost isn't worth it.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = FocusFlowRadius.md,
    padding: Dp = 20.dp,
    blurBehind: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = LocalFocusFlowColors.current
    val shape = RoundedCornerShape(cornerRadius)

    val glassModifier = if (blurBehind && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(radius = 18.dp)
    } else Modifier

    // The blur and the content are kept on separate Boxes (background sized
    // via matchParentSize() to the content-driven outer Box) so blur() only
    // ever rasterizes the glass fill — it would otherwise blur content()
    // into an indistinct smear too, since Modifier.blur() affects everything
    // drawn by the node it's attached to, not just what's behind it.
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = if (colors.isDark) 0.dp else 18.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.10f)
                )
                .then(glassModifier)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.glassSurface,
                            colors.glassSurface.copy(alpha = colors.glassSurface.alpha * 0.85f)
                        )
                    )
                )
                .border(width = 1.dp, color = colors.glassBorder, shape = shape)
        )
        Box(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
