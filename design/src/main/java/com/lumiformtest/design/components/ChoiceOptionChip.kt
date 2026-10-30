package com.lumiformtest.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.lumiformtest.design.theme.Dimensions
import com.lumiformtest.design.theme.LumiformTestAppTheme

private val ChipLineBreaks by lazy { Regex("[\\r\\n\\t\\u2028\\u2029]+") }

@Composable
fun ChoiceOptionChip(
    label: String,
    multiple: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    accessibilityLabel: String? = null
) {
    // Keep every word visible when the server sends line breaks.
    val displayLabel = remember(label) { label.replace(ChipLineBreaks, " ") }
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val foreground = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        selected -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    // Keep radio/checkbox semantics while the whole chip handles a single tap.
    val selection = if (multiple) Modifier.toggleable(
        value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onClick() }
    ) else Modifier.selectable(
        selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick
    )
    Surface(
        modifier = modifier.sizeIn(minWidth = Dimensions.minimumTouchTarget, minHeight = Dimensions.minimumTouchTarget).clip(shape).then(selection).then(
            if (accessibilityLabel != null) Modifier.semantics { contentDescription = accessibilityLabel } else Modifier
        ),
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = foreground,
        border = BorderStroke(
            if (selected) Dimensions.selectedBorderWidth else Dimensions.borderWidth,
            if (enabled && selected) colors.primary else colors.outlineVariant
        )
    ) {
        Row(
            Modifier.padding(horizontal = Dimensions.spacingLarge, vertical = Dimensions.chipVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSmall), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(displayLabel, Modifier.width(IntrinsicSize.Max), style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
            if (badge != null) Surface(
                shape = CircleShape,
                color = if (enabled && selected) colors.primary else colors.surfaceContainerHighest,
                contentColor = if (enabled && selected) colors.onPrimary else foreground
            ) {
                Text(badge, Modifier.sizeIn(minWidth = Dimensions.chipBadgeMinSize, minHeight = Dimensions.chipBadgeMinSize)
                    .padding(horizontal = Dimensions.spacingSmall, vertical = Dimensions.spacingExtraSmall),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Preview(name = "Unselected chip with score", showBackground = true)
@Composable
private fun UnselectedChipPreview() = PreviewChip(selected = false, badge = "1")

@Preview(name = "Selected chip · dark", showBackground = true)
@Composable
private fun SelectedChipPreview() = PreviewChip(selected = true, darkTheme = true)

@Preview(name = "Disabled chip", showBackground = true)
@Composable
private fun DisabledChipPreview() = PreviewChip(selected = true, enabled = false, badge = "2")

@Preview(name = "Chip · large text", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun LargeChipPreview() = PreviewChip(selected = true, badge = "0", label = "Entrance and storage area")

@Preview(name = "Multiline label on one row", showBackground = true, widthDp = 320)
@Composable
private fun MultilineChipPreview() = PreviewChip(selected = false, label = "Entrance\nStorage\nLoading bay")

@Composable
private fun PreviewChip(selected: Boolean, enabled: Boolean = true, darkTheme: Boolean = false, badge: String? = null, label: String = "Equipment checks") {
    LumiformTestAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface {
            Box(Modifier.padding(Dimensions.spacingLarge).horizontalScroll(rememberScrollState())) {
                ChoiceOptionChip(label, multiple = true, selected = selected, enabled = enabled, onClick = {}, badge = badge)
            }
        }
    }
}
