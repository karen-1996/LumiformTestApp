package com.lumiformtest.form.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.form.FormScreenTestActivity
import com.lumiformtest.form.presentation.model.FormContentUiModel
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.model.ResponseOptionUiModel
import com.lumiformtest.form.presentation.model.ResponseSetUiModel
import com.lumiformtest.form.presentation.viewmodel.FormAction
import com.lumiformtest.form.presentation.viewmodel.FormUiState
import com.lumiformtest.resources.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<FormScreenTestActivity>()

    private val content by lazy {
        FormContentUiModel(listOf(
            FormRowUiModel.Page(1, "Main page"),
            FormRowUiModel.Section(2, 1, 1, "Introduction"),
            FormRowUiModel.Section(3, 2, 2, "Nested section"),
            FormRowUiModel.Text(4, 3, 3, "Nested question"),
            FormRowUiModel.Image(5, 3, 3, "Image title", "https://example.com/image.png"),
            FormRowUiModel.Choice(6, 1, 1, "Choose an area", ResponseSetUiModel(
                101, false, listOf(ResponseOptionUiModel(1, "Entrance", null))
            ))
        ), 1793275200000)
    }

    @Test
    fun hierarchyIncludesEveryRowKindAndAccessibleHeadings() {
        compose.setContent {
            LumiformTestAppTheme { FormScreen(FormUiState(content = content, isReadingCache = false), {}) }
        }
        val heading = SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit)
        compose.onNodeWithText("Main page").assert(heading)
        compose.onNodeWithText("Introduction").assert(heading)
        compose.onNodeWithText("Nested section").assert(heading)
        val pageY = compose.onNodeWithText("Main page").fetchSemanticsNode().boundsInRoot.top
        val sectionY = compose.onNodeWithText("Introduction").fetchSemanticsNode().boundsInRoot.top
        val nestedY = compose.onNodeWithText("Nested section").fetchSemanticsNode().boundsInRoot.top
        val questionY = compose.onNodeWithText("Nested question").fetchSemanticsNode().boundsInRoot.top
        assertTrue(pageY < sectionY && sectionY < nestedY && nestedY < questionY)
        compose.onNodeWithTag("form_list").performScrollToKey(5L)
        compose.onNodeWithText("Image title").assertIsDisplayed()
        compose.onNodeWithTag("form_list").performScrollToKey(6L)
        compose.onNodeWithText("Choose an area").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.form_response_option, "Entrance")).assertIsDisplayed()
    }

    @Test
    fun initialFailureOffersRetryInsteadOfAnEmptyForm() {
        val actions = mutableListOf<FormAction>()
        compose.setContent {
            LumiformTestAppTheme {
                FormScreen(FormUiState(isReadingCache = false, refreshFailure = FormRefreshFailureUi.FETCH), { actions += it })
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.form_load_error)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.form_empty)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.form_retry)).performClick()
        compose.runOnIdle { assertEquals(listOf(FormAction.Refresh), actions) }
    }

    @Test
    fun savedContentStaysVisibleDuringRefreshAndFailure() {
        var state by mutableStateOf(FormUiState(content = content, isReadingCache = false, isRefreshing = true))
        compose.setContent { LumiformTestAppTheme { FormScreen(state, {}) } }
        compose.onNodeWithText("Main page").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.form_refreshing)).assertIsDisplayed()
        compose.runOnIdle { state = state.copy(isRefreshing = false, refreshFailure = FormRefreshFailureUi.FETCH) }
        compose.onNodeWithText("Main page").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.form_cached_refresh_error)).assertIsDisplayed()
    }

    @Test
    fun emptySavedDocumentIsNotInitialLoading() {
        var state by mutableStateOf(FormUiState())
        compose.setContent { LumiformTestAppTheme { FormScreen(state, {}) } }
        compose.onNodeWithTag("form_loading").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.form_empty)).assertDoesNotExist()
        compose.runOnIdle { state = FormUiState(content = content.copy(rows = emptyList()), isReadingCache = false) }
        compose.onNodeWithTag("form_loading").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.form_empty)).assertIsDisplayed()
    }

    @Test
    fun listPositionSurvivesStateRestoreAndAContentRefresh() {
        val restoration = StateRestorationTester(compose)
        var state by mutableStateOf(FormUiState(
            content = content.copy(rows = (1L..80L).map { FormRowUiModel.Text(it, 1000, 1, "Question $it") }),
            isReadingCache = false
        ))
        restoration.setContent { LumiformTestAppTheme { FormScreen(state, {}) } }
        compose.onNodeWithTag("form_list").performScrollToKey(70L)
        compose.onNodeWithText("Question 70").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Question 70").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(content = state.content?.copy(lastUpdatedEpochMillis = 1793275260000)) }
        compose.onNodeWithText("Question 70").assertIsDisplayed()
    }

    @Test
    fun deepLongTextWrapsOnSmallWidthWithLargeFontsInBothThemes() {
        var dark by mutableStateOf(false)
        val text = "A deeply nested question with long text that must stay readable on a small screen."
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                LumiformTestAppTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        FormScreen(FormUiState(
                            content = content.copy(rows = listOf(FormRowUiModel.Text(1, 99, 50, text))),
                            isReadingCache = false
                        ), {})
                    }
                }
            }
        }
        repeat(2) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.single().lineCount > 1)
            assertFalse(layouts.single().hasVisualOverflow)
            compose.runOnIdle { dark = true }
        }
    }
}
