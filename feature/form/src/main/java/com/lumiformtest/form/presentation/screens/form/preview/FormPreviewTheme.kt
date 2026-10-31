package com.lumiformtest.form.presentation.screens.form.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.lumiformtest.design.theme.LumiformTestAppTheme

@Composable
internal fun FormPreviewTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    // Resource colors should use the same night mode as the preview's Compose theme.
    val configuration = remember(baseConfiguration, darkTheme) {
        Configuration(baseConfiguration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }
    val previewContext = remember(context, configuration) { context.createConfigurationContext(configuration) }
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalConfiguration provides configuration,
        LocalContext provides previewContext
    ) {
        LumiformTestAppTheme(darkTheme = darkTheme, dynamicColor = false, content = content)
    }
}
