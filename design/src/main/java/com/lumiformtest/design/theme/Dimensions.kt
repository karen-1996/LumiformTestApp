package com.lumiformtest.design.theme

import androidx.compose.ui.unit.dp

// Keep layout values together so screens and previews use the same sizes.
object Dimensions {
    const val MAX_SECTION_INDENT_LEVELS = 4

    val none = 0.dp
    val spacingExtraSmall = 4.dp
    val spacingSmall = 8.dp
    val spacingMedium = 12.dp
    val spacingLarge = 16.dp
    val screenPadding = 20.dp

    val minimumTouchTarget = 48.dp
    val chipVerticalPadding = 10.dp
    val chipBadgeMinSize = 24.dp
    val borderWidth = 1.dp
    val selectedBorderWidth = 2.dp

    val imageThumbnailSize = 120.dp
    val imageTitleMaxHeight = 140.dp
    val sectionIndent = 12.dp
    val loadingContentPadding = 48.dp
}
