package com.lumiformtest.form.presentation.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.form.FormScreenTestActivity
import com.lumiformtest.form.presentation.model.ChoiceOptionUiInput
import com.lumiformtest.form.presentation.model.ChoiceWriteFailureUi
import com.lumiformtest.form.presentation.model.ChoiceWriteFailureReasonUi
import com.lumiformtest.form.presentation.model.FormContentUiModel
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.model.ResponseOptionUiModel
import com.lumiformtest.form.presentation.model.ResponseSetUiModel
import com.lumiformtest.form.presentation.viewmodel.FormAction
import com.lumiformtest.form.presentation.viewmodel.FormUiState
import com.lumiformtest.form.presentation.components.ChoiceQuestion
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
        compose.onNodeWithText("Entrance").assertIsDisplayed()
    }

    @Test
    fun tappingThumbnailSendsOnlyItsImageId() {
        val actions = mutableListOf<FormAction>()
        compose.setContent { LumiformTestAppTheme {
            FormScreen(FormUiState(content = content, isReadingCache = false), { actions += it })
        } }
        compose.onNodeWithTag("form_list").performScrollToKey(5L)
        compose.onNodeWithTag("image_thumbnail_5").performTouchInput { click(Offset(center.x, 8f)) }
        compose.runOnIdle { assertEquals(listOf(FormAction.OpenImage(5)), actions) }
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
    fun offlineFailuresShowNoInternetWhileOtherFailuresKeepTheirMessage() {
        var state by mutableStateOf(FormUiState(isReadingCache = false, refreshFailure = FormRefreshFailureUi.OFFLINE))
        compose.setContent { LumiformTestAppTheme { FormScreen(state, {}) } }
        val offline = compose.activity.getString(R.string.no_internet_connection)
        val generic = compose.activity.getString(R.string.form_load_error)
        compose.onNodeWithText(offline).assertIsDisplayed()
        compose.onNodeWithText(generic).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(content = content) }
        compose.onNodeWithText(offline).assertIsDisplayed()
        compose.onNodeWithText("Main page").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(content = null, refreshFailure = FormRefreshFailureUi.FETCH) }
        compose.onNodeWithText(generic).assertIsDisplayed()
        compose.onNodeWithText(offline).assertDoesNotExist()
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
    @Test
    fun singleChoiceChipsDispatchOnceAndRenderOnlyTheObservedSingleAnswer() {
        var state by mutableStateOf(FormUiState(content = choiceContent, isReadingCache = false, isRefreshing = true))
        val actions = mutableListOf<FormAction>()
        compose.setContent { LumiformTestAppTheme { FormScreen(state, { actions += it }) } }
        selectionSummary(R.string.form_single_choice, compose.activity.getString(R.string.choice_none_selected)).assertIsDisplayed()
        compose.onNodeWithText("First option").performClick()
        selectionSummary(R.string.form_single_choice, compose.activity.getString(R.string.choice_none_selected)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(FormAction.OptionClicked(ChoiceOptionUiInput(2, 101, 1))), actions)
            state = state.copy(content = selected(choiceContent, 2, setOf(1)))
        }
        compose.onNodeWithText("First option").assertIsSelected()
        selectionSummary(R.string.form_single_choice, "First option").assertIsDisplayed()
        compose.onNodeWithText("Second option").assertIsNotSelected().performClick()
        compose.runOnIdle { state = state.copy(content = selected(choiceContent, 2, setOf(2))) }
        compose.onNodeWithText("First option").assertIsNotSelected()
        selectionSummary(R.string.form_single_choice, "Second option").assertIsDisplayed()
        compose.onNodeWithText("Second option").assertIsSelected().performClick()
        compose.runOnIdle { assertEquals(3, actions.filterIsInstance<FormAction.OptionClicked>().size) }
        compose.onNodeWithText("Second option").assertIsSelected()
    }

    @Test
    fun multipleChoiceChipsDispatchOnceAndKeepIndependentObservedSelections() {
        var state by mutableStateOf(FormUiState(content = choiceContent, isReadingCache = false))
        val actions = mutableListOf<FormAction>()
        compose.setContent { LumiformTestAppTheme { FormScreen(state, { actions += it }) } }
        selectionSummary(R.string.form_multiple_choice, compose.activity.getString(R.string.choice_none_selected)).assertIsDisplayed()
        compose.onNodeWithText("First area").assertIsOff().performClick()
        compose.runOnIdle { state = state.copy(content = selected(choiceContent, 3, setOf(1))) }
        compose.onNodeWithText("First area").assertIsOn()
        selectionSummary(R.string.form_multiple_choice, "First area").assertIsDisplayed()
        compose.onNodeWithText("Second area").assertIsOff().performClick()
        compose.runOnIdle { state = state.copy(content = selected(choiceContent, 3, setOf(1, 2))) }
        selectionSummary(R.string.form_multiple_choice, "First area", "Second area").assertIsDisplayed()
        compose.onNodeWithText("First area").assertIsOn().performClick()
        compose.runOnIdle { state = state.copy(content = selected(choiceContent, 3, setOf(2))) }
        compose.onNodeWithText("First area").assertIsOff()
        compose.onNodeWithText("Second area").assertIsOn()
        selectionSummary(R.string.form_multiple_choice, "Second area").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(
                ChoiceOptionUiInput(3, 102, 1), ChoiceOptionUiInput(3, 102, 2), ChoiceOptionUiInput(3, 102, 1)
            ), actions.filterIsInstance<FormAction.OptionClicked>().map { it.option })
        }
    }

    @Test
    fun choicesStayEnabledAfterRefreshFailureButDisableOnSavedReadFailure() {
        var state by mutableStateOf(FormUiState(content = choiceContent, isReadingCache = false, refreshFailure = FormRefreshFailureUi.FETCH))
        compose.setContent { LumiformTestAppTheme { FormScreen(state, {}) } }
        compose.onNodeWithText("First option").assertIsEnabled()
        compose.onNodeWithText("First area").assertIsEnabled()
        compose.runOnIdle { state = state.copy(hasReadFailure = true) }
        compose.onNodeWithText("First option").assertIsNotEnabled()
        compose.onNodeWithText("First area").assertIsNotEnabled()
    }

    @Test
    fun saveFailureIsVisibleWithoutReplacingTheForm() {
        compose.setContent {
            LumiformTestAppTheme { FormScreen(FormUiState(
                content = choiceContent, isReadingCache = false,
                choiceWriteFailure = ChoiceWriteFailureUi(1, 2, ChoiceWriteFailureReasonUi.STORAGE)
            ), {}) }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.choice_save_error)).assertIsDisplayed()
        compose.onNodeWithText("Single question").assertIsDisplayed()
        compose.onNodeWithText("First option").assertIsEnabled()
    }

    @Test
    fun longChoiceLabelsScrollWithoutTruncationInBothThemes() {
        var dark by mutableStateOf(false)
        val text = "An entrance area with a long description that should scroll on narrow screens."
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                LumiformTestAppTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        FormScreen(FormUiState(
                            content = content.copy(rows = listOf(FormRowUiModel.Choice(
                                2, 1, 50, "Question", ResponseSetUiModel(101, false, listOf(ResponseOptionUiModel(1, text, null))), setOf(1)
                            ))), isReadingCache = false
                        ), {})
                    }
                }
            }
        }
        repeat(2) {
            val options = compose.onNodeWithTag("choice_options_2")
            options.performSemanticsAction(SemanticsActions.ScrollBy) { it(-10_000f, 0f) }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse("Long chip text: size=${layouts.single().size}, paragraph=${layouts.single().multiParagraph.width}x${layouts.single().multiParagraph.height}, widthOverflow=${layouts.single().didOverflowWidth}, heightOverflow=${layouts.single().didOverflowHeight}", layouts.single().hasVisualOverflow)
            val before = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value
            options.performSemanticsAction(SemanticsActions.ScrollBy) { it(10_000f, 0f) }
            val after = compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot().left.value
            assertTrue("A long label should be readable by horizontal scrolling", after < before)
            compose.runOnIdle { dark = true }
        }
    }

    @Test
    fun choiceRowScrollsToBothScreenEdgesWhileTextKeepsItsIndentation() {
        val question = FormRowUiModel.Choice(2, 1, 4, "Indented question", ResponseSetUiModel(101, false, listOf(
            ResponseOptionUiModel(1, "First option with a longer label", null),
            ResponseOptionUiModel(2, "Last option with a longer label", null)
        )))
        compose.setContent {
            LumiformTestAppTheme {
                Box(Modifier.width(320.dp)) {
                    FormScreen(FormUiState(content = content.copy(rows = listOf(question)), isReadingCache = false), {})
                }
            }
        }
        val list = compose.onNodeWithTag("form_list").getUnclippedBoundsInRoot()
        val options = compose.onNodeWithTag("choice_options_2")
        val viewport = options.getUnclippedBoundsInRoot()
        assertEquals(list.left.value, viewport.left.value, 0.5f)
        assertEquals(list.right.value, viewport.right.value, 0.5f)
        val title = compose.onNodeWithText("Indented question").getUnclippedBoundsInRoot()
        val summary = selectionSummary(R.string.form_single_choice, compose.activity.getString(R.string.choice_none_selected))
        val summaryBefore = summary.getUnclippedBoundsInRoot()
        assertEquals(list.left.value + 56f, title.left.value, 0.5f)
        assertEquals(title.left.value, compose.onNodeWithText("First option with a longer label").getUnclippedBoundsInRoot().left.value, 0.5f)
        options.performSemanticsAction(SemanticsActions.ScrollBy) { it(100f * compose.activity.resources.displayMetrics.density, 0f) }
        assertTrue("Chips can pass through the text inset", compose.onNodeWithText("First option with a longer label")
            .getUnclippedBoundsInRoot().left < title.left)
        options.performSemanticsAction(SemanticsActions.ScrollBy) { it(10_000f, 0f) }
        assertEquals(list.right.value - 20f, compose.onNodeWithText("Last option with a longer label")
            .getUnclippedBoundsInRoot().right.value, 0.5f)
        assertEquals(summaryBefore, summary.getUnclippedBoundsInRoot())
    }

    @Test
    fun chipsScrollInOneRowWithAccessibleTouchTargetsAndProvidedScoreBadges() {
        val question = FormRowUiModel.Choice(2, 1, 1, "Question", ResponseSetUiModel(101, false, listOf(
            ResponseOptionUiModel(1, "Scored option", 0), ResponseOptionUiModel(2, "Another scored option", 2),
            ResponseOptionUiModel(3, "No score", null)
        )))
        compose.setContent {
            LumiformTestAppTheme {
                Box(Modifier.width(220.dp)) { ChoiceQuestion(question, enabled = true, onOptionClick = {}) }
            }
        }
        val options = compose.onNodeWithTag("choice_options_2")
        val first = compose.onNodeWithText("Scored option").getUnclippedBoundsInRoot()
        val formatter = java.text.NumberFormat.getIntegerInstance(compose.activity.resources.configuration.locales[0])
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.choice_option_with_score,
            "Scored option", formatter.format(0))).assertIsDisplayed()
        compose.onNodeWithText(formatter.format(0), useUnmergedTree = true).assertIsDisplayed()
        options.performScrollToKey(2L)
        val second = compose.onNodeWithText("Another scored option").assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.onNodeWithText(formatter.format(2), useUnmergedTree = true).assertIsDisplayed()
        options.performScrollToKey(3L)
        val last = compose.onNodeWithText("No score").assertIsDisplayed().getUnclippedBoundsInRoot()
        for (chip in listOf(first, second, last)) {
            assertEquals("Options should stay on the same row", first.top.value, chip.top.value, 0.5f)
            assertTrue("Each chip needs a 48 dp touch target", (chip.bottom - chip.top).value >= 47.5f)
        }
        val footer = selectionSummary(R.string.form_single_choice, compose.activity.getString(R.string.choice_none_selected))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Selection details should stay below the chips", footer.top >= last.bottom.value * density)
    }

    private fun selectionSummary(type: Int, vararg labels: String): SemanticsNodeInteraction {
        var matcher = hasText(compose.activity.getString(type), substring = true)
        for (label in labels) matcher = matcher and hasText(label, substring = true)
        return compose.onNode(matcher)
    }

    @Test
    fun refreshInsertionAndRemovalKeepTheVisibleItemAnchoredById() {
        val rows = listOf(FormRowUiModel.Page(1, "Page")) + (2L..90L).map {
            FormRowUiModel.Text(it, 1, 1, "Row $it")
        }
        var state by mutableStateOf(FormUiState(content = content.copy(rows = rows), isReadingCache = false))
        compose.setContent { LumiformTestAppTheme { FormScreen(state, {}) } }
        compose.onNodeWithTag("form_list").performScrollToKey(50L)
        val before = compose.onNodeWithText("Row 50").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle {
            state = state.copy(content = state.content!!.copy(rows =
                listOf(rows.first(), FormRowUiModel.Text(1000, 1, 1, "Inserted")) + rows.drop(1)
            ))
        }
        assertEquals(before, compose.onNodeWithText("Row 50").fetchSemanticsNode().boundsInRoot.top, 0.1f)
        compose.runOnIdle {
            state = state.copy(content = state.content!!.copy(rows = state.content!!.rows.filter { it.id !in 2L..30L }))
        }
        assertEquals(before, compose.onNodeWithText("Row 50").fetchSemanticsNode().boundsInRoot.top, 0.1f)
    }

    @Test
    fun refreshedOptionOrderKeepsFocusAndSelectionOnTheSameId() {
        var state by mutableStateOf(FormUiState(content = selected(choiceContent, 2, setOf(1)), isReadingCache = false))
        val actions = mutableListOf<FormAction>()
        lateinit var inputModeManager: InputModeManager
        compose.setContent {
            inputModeManager = LocalInputModeManager.current
            LumiformTestAppTheme { FormScreen(state, { actions += it }) }
        }
        compose.runOnIdle { inputModeManager.requestInputMode(InputMode.Keyboard) }
        compose.onNodeWithText("First option").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("First option").assertIsFocused().assertIsSelected()
        compose.runOnIdle {
            state = state.copy(content = state.content!!.copy(rows = state.content!!.rows.map {
                if (it is FormRowUiModel.Choice && it.id == 2L)
                    it.copy(responseSet = it.responseSet.copy(options = it.responseSet.options.reversed())) else it
            }))
        }
        compose.onNodeWithText("First option").assertIsFocused().assertIsSelected()
        compose.onNodeWithText("Second option").assertIsNotSelected().performClick()
        compose.runOnIdle { assertEquals(listOf(FormAction.OptionClicked(ChoiceOptionUiInput(2, 101, 2))), actions) }
    }

    private val choiceContent by lazy {
        content.copy(rows = listOf(
            FormRowUiModel.Page(1, "Main page"),
            FormRowUiModel.Choice(2, 1, 1, "Single question", ResponseSetUiModel(101, false, listOf(
                ResponseOptionUiModel(1, "First option", null), ResponseOptionUiModel(2, "Second option", null)
            ))),
            FormRowUiModel.Choice(3, 1, 1, "Multiple question", ResponseSetUiModel(102, true, listOf(
                ResponseOptionUiModel(1, "First area", null), ResponseOptionUiModel(2, "Second area", null)
            )))
        ))
    }

    private fun selected(content: FormContentUiModel, questionId: Long, selected: Set<Long>) = content.copy(
        rows = content.rows.map { if (it is FormRowUiModel.Choice && it.id == questionId) it.copy(selectedOptionIds = selected) else it }
    )

}
