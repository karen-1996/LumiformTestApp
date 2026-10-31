package com.lumiformtest.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

object HierarchyTypography {
    private val nestedSectionSize = 20.sp
    private val deepSectionSize = 18.sp

    @Composable
    fun section(depth: Int): TextStyle {
        val typography = MaterialTheme.typography
        val level = depth.coerceIn(1, 4)
        return remember(typography, level) {
            // Stop at titleMedium so deeply nested headings stay readable above question text.
            when (level) {
                1 -> typography.titleLarge
                2 -> typography.titleLarge.copy(fontSize = nestedSectionSize)
                3 -> typography.titleLarge.copy(fontSize = deepSectionSize)
                else -> typography.titleMedium
            }
        }
    }
}
