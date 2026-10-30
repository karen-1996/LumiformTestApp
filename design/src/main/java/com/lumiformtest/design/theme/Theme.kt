package com.lumiformtest.design.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.lumiformtest.resources.R

private val DarkColorScheme by lazy { darkColorScheme() }
private val LightColorScheme by lazy { lightColorScheme() }

@Composable
fun LumiformTestAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val background = colorResource(if (darkTheme) R.color.app_background_dark else R.color.app_background_light)
    val colorScheme = remember(context, configuration, darkTheme, dynamicColor, background) {
        val palette = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
        // Keep both screens and their top bars neutral, including with dynamic colors.
        palette.copy(background = background, surface = background)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
