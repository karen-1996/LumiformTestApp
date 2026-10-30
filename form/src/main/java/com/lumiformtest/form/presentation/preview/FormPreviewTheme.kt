package com.lumiformtest.form.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import com.lumiformtest.design.theme.LumiformTestAppTheme

@Composable
internal fun FormPreviewTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        LumiformTestAppTheme(darkTheme = darkTheme, dynamicColor = false, content = content)
    }
}
