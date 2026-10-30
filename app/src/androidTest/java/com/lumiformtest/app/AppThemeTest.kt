package com.lumiformtest.app

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.resources.R
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppThemeTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationTestActivity>()

    @Test
    fun nativeWindowAndSplashResolveMatchingBackgroundsAndIconsInBothModes() {
        for (dark in listOf(false, true)) {
            val configuration = configuration(dark)
            val context = compose.activity.createConfigurationContext(configuration)
            val background = context.getColor(R.color.app_background)
            assertEquals(!dark, context.resources.getBoolean(R.bool.is_light_theme))
            assertTrue(if (dark) Color(background).luminance() < 0.1f else Color(background).luminance() > 0.8f)
            for (style in listOf(R.style.Theme_LumiformTestApp, R.style.Theme_LumiformTestApp_Starting)) {
                val themed = ContextThemeWrapper(context, style)
                val attributes = themed.obtainStyledAttributes(intArrayOf(
                    android.R.attr.colorBackground, android.R.attr.windowLightStatusBar,
                    android.R.attr.windowLightNavigationBar, android.R.attr.textColorPrimary
                ))
                try {
                    assertEquals(background, attributes.getColor(0, 0))
                    assertEquals(!dark, attributes.getBoolean(1, dark))
                    assertEquals(!dark, attributes.getBoolean(2, dark))
                    if (style == R.style.Theme_LumiformTestApp) {
                        val text = Color(requireNotNull(attributes.getColorStateList(3)).defaultColor)
                        assertTrue(if (dark) text.luminance() > 0.7f else text.luminance() < 0.2f)
                    }
                } finally { attributes.recycle() }
            }
            val splash = ContextThemeWrapper(context, R.style.Theme_LumiformTestApp_Starting)
                .obtainStyledAttributes(intArrayOf(androidx.core.splashscreen.R.attr.windowSplashScreenBackground))
            try { assertEquals(background, splash.getColor(0, 0)) } finally { splash.recycle() }
            // Original artwork keeps a readable canvas in either app mode.
            assertEquals(compose.activity.getColor(R.color.image_canvas), context.getColor(R.color.image_canvas))
        }
    }

    @Test
    fun composeFollowsSystemModeAndMatchesFallbackWindowPalette() = verifyComposeMode(dynamic = false)

    @Test
    fun dynamicPaletteAlsoFollowsSystemModeAndKeepsReadableContrast() = verifyComposeMode(dynamic = true)

    private fun verifyComposeMode(dynamic: Boolean) {
        var dark by mutableStateOf(false)
        val observed = AtomicReference<Pair<ColorScheme, Color>>()
        compose.setContent {
            val base = LocalContext.current
            val configuration = remember(base, dark) { configuration(dark) }
            val context = remember(base, configuration) { base.createConfigurationContext(configuration) }
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                LumiformTestAppTheme(dynamicColor = dynamic) {
                    val scheme = MaterialTheme.colorScheme
                    val windowBackground = colorResource(R.color.app_background)
                    SideEffect { observed.set(scheme to windowBackground) }
                    Surface(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()
        val light = observed.get().first
        verifyPalette(observed.get(), dark = false, dynamic = dynamic)
        compose.runOnIdle { dark = true }
        compose.waitForIdle()
        verifyPalette(observed.get(), dark = true, dynamic = dynamic)
        assertNotEquals(light.background, observed.get().first.background)
        compose.runOnIdle { dark = false }
        compose.waitForIdle()
        assertEquals(light.background, observed.get().first.background)
    }

    private fun verifyPalette(value: Pair<ColorScheme, Color>, dark: Boolean, dynamic: Boolean) {
        val (scheme, windowBackground) = value
        assertTrue(if (dark) scheme.background.luminance() < 0.2f else scheme.background.luminance() > 0.7f)
        val foreground = scheme.onSurface.luminance()
        val surface = scheme.surface.luminance()
        val contrast = (maxOf(foreground, surface) + 0.05f) / (minOf(foreground, surface) + 0.05f)
        assertTrue("Body text should stay readable in either mode", contrast >= 4.5f)
        if (!dynamic) assertEquals(windowBackground.toArgb(), scheme.background.toArgb())
    }

    private fun configuration(dark: Boolean) = Configuration(compose.activity.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
}
