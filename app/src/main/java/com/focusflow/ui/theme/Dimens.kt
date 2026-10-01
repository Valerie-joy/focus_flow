package com.focusflow.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale — a 4dp grid. Screens should reach for these rather than
 * literal dp values, so vertical rhythm stays consistent when a screen is
 * edited later and so a global adjustment is a one-line change here.
 *
 * Naming is by role, not by size, so the value can be retuned without every
 * call site becoming a lie.
 */
object Spacing {
    /** Between an icon and its adjacent label. */
    val xxs = 4.dp
    /** Between tightly related lines (a value and its caption). */
    val xs = 8.dp
    /** Between items inside a card. */
    val sm = 12.dp
    /** Default gap between sibling elements; card inner padding. */
    val md = 16.dp
    /** Between a heading and the content it introduces. */
    val lg = 20.dp
    /** Between distinct sections of a screen. */
    val xl = 24.dp
    /** Around a major block; top of a screen below the app bar. */
    val xxl = 32.dp
    /** Page gutter — the horizontal inset every screen uses. */
    val gutter = 20.dp
}

/**
 * Elevation. Kept deliberately shallow: a large soft shadow under every card
 * (the previous scheme used 18dp) turns a list of cards into a field of blur.
 * Cards carry a hairline border instead, with elevation reserved for things
 * that genuinely float above the page.
 */
object Elevation {
    val none = 0.dp
    /** Resting cards. */
    val card = 1.dp
    /** Pressed/hovered cards and raised buttons. */
    val raised = 3.dp
    /** Bottom bars, sticky footers. */
    val bar = 6.dp
    /** Dialogs. */
    val dialog = 8.dp
}

/** Component sizes that need to agree across screens. */
object Sizing {
    /** Android's minimum accessible touch target. */
    val minTouchTarget = 48.dp
    /** Standard control height (buttons, text fields). */
    val controlHeight = 52.dp
    /** Inline icons next to body text. */
    val iconSm = 18.dp
    /** Default icon size. */
    val iconMd = 24.dp
    /** Feature/illustrative icons. */
    val iconLg = 40.dp
    /** Hairline used for card borders and dividers. */
    val hairline = 1.dp
    /** Widest a single column of text should get on a large screen. */
    val maxContentWidth = 600.dp
}

/**
 * Animation durations, in milliseconds.
 *
 * Short by design. Motion in this app exists to explain a state change, not to
 * entertain — anything long enough to notice as an animation is long enough to
 * pull attention away from the task, which is the one thing this app must not
 * do. There are no looping/ambient animations.
 */
object MotionDurations {
    /** State changes on a control (color, border). */
    const val QUICK = 120
    /** Content entering or leaving. */
    const val STANDARD = 220
    /** Progress and value transitions that should read as continuous. */
    const val PROGRESS = 450
}
