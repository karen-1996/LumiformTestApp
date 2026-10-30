package com.lumiformtest.app.navigation

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumiformtest.app.NavigationTestActivity
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.form.presentation.navigation.FormDestination
import com.lumiformtest.resources.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<NavigationTestActivity>()

    @Test
    fun imageDestinationSurvivesSavedStateRestoreAndBackReturnsToForm() {
        lateinit var backStack: NavBackStack<NavKey>
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val savedBackStack = rememberNavBackStack(FormDestination.Form)
            SideEffect { backStack = savedBackStack }
            LumiformTestAppTheme { AppNavigation(savedBackStack) }
        }

        val formTitle = compose.activity.getString(R.string.form_title)
        val imageUnavailable = compose.activity.getString(R.string.image_unavailable)
        val back = compose.activity.getString(R.string.back)
        compose.onNodeWithText(formTitle).assertIsDisplayed()

        compose.runOnIdle { backStack.add(FormDestination.Image(imageId = Long.MAX_VALUE)) }
        compose.onNodeWithText(imageUnavailable).assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        compose.runOnIdle {
            assertEquals(2, backStack.size)
            assertEquals(FormDestination.Image(imageId = Long.MAX_VALUE), backStack.last())
        }
        compose.onNodeWithText(imageUnavailable).assertIsDisplayed()
        compose.onNodeWithText(back).performClick()
        compose.onNodeWithText(formTitle).assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(FormDestination.Form), backStack.toList()) }
    }
}
