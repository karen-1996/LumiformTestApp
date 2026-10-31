package com.lumiformtest.form.fixtures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.form.FormScreenTestActivity
import com.lumiformtest.form.data.remote.FormJsonParser
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.presentation.screens.form.components.ChoiceQuestion
import com.lumiformtest.form.presentation.screens.form.mapper.FormUiMapper
import com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel
import com.lumiformtest.form.presentation.screens.form.model.ChoiceOptionUiInput
import com.lumiformtest.form.presentation.screens.form.FormScreen
import com.lumiformtest.form.presentation.screens.form.viewmodel.FormUiState
import com.lumiformtest.resources.R
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormFixtureScreenTest {
    @get:Rule val compose = createAndroidComposeRule<FormScreenTestActivity>()
    private val parser by lazy { FormJsonParser() }
    private val uiMapper by lazy { FormUiMapper() }

    @Test
    fun localFixturesReachTheirLastRowInLightDarkAndRtlLayouts() {
        val cases = FormFixture.cases(::read).filter { it.accepted && it.ui }
        var fixture by mutableStateOf(cases.first())
        var dark by mutableStateOf(false)
        var screenContent by mutableStateOf(content(fixture.file))
        compose.setContent {
            // Local artwork isolates fixture layout from network and image-decoder timing.
            CompositionLocalProvider(LocalInspectionMode provides true,
                LocalLayoutDirection provides if (fixture.file.contains("rtl")) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                LumiformTestAppTheme(darkTheme = dark, dynamicColor = false) {
                    key(fixture.file, dark) {
                        Box(Modifier.width(320.dp)) { FormScreen(FormUiState(content = screenContent, isReadingCache = false), {}) }
                    }
                }
            }
        }
        for (next in cases) for (night in listOf(false, true)) {
            val nextContent = content(next.file)
            compose.runOnIdle { fixture = next; screenContent = nextContent; dark = night }
            if (next.lastId == null) {
                compose.onNodeWithText(compose.activity.getString(R.string.form_empty)).assertIsDisplayed()
            } else {
                compose.onNodeWithTag("form_list").performScrollToKey(next.lastId)
                compose.onNodeWithTag("form_row_${next.lastId}").assertIsDisplayed()
            }
        }
    }

    @Test
    fun longAndMultilineFixtureOptionsRemainReadableOnOneScrollingRow() {
        val question = content("valid/long-copy.json").rows.filterIsInstance<FormRowUiModel.Choice>().single()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                LumiformTestAppTheme {
                    Box(Modifier.width(320.dp)) { ChoiceQuestion(question, enabled = true, onOptionClick = {}) }
                }
            }
        }
        val options = compose.onNodeWithTag("choice_options_${question.id}")
        for (option in question.responseSet.options) {
            options.performScrollToKey(option.id)
            val layouts = mutableListOf<TextLayoutResult>()
            val displayLabel = if (option.id == 4L) "Line one Line two Line three" else option.label
            compose.onNodeWithText(displayLabel, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(option.label, 1, layouts.single().lineCount)
            assertFalse("A chip must not lose part of its label: ${option.label}", layouts.single().hasVisualOverflow)
        }
    }

    @Test
    fun blankOptionsHaveNamesAndEmptyQuestionsExplainWhyThereAreNoChips() {
        val questions = content("probe/blank-display-values.json").rows.filterIsInstance<FormRowUiModel.Choice>()
        var question by mutableStateOf(questions.first().copy(selectedOptionIds = setOf(2)))
        val clicks = mutableListOf<ChoiceOptionUiInput>()
        compose.setContent {
            LumiformTestAppTheme {
                key(question.id) {
                    Box(Modifier.width(320.dp)) { ChoiceQuestion(question, enabled = true, onOptionClick = { clicks += it }) }
                }
            }
        }
        for (index in 1..3) {
            val label = compose.activity.getString(R.string.choice_unnamed_option, index)
            val chip = compose.onNodeWithText(label).assertIsDisplayed()
            val bounds = chip.getUnclippedBoundsInRoot()
            assertTrue(bounds.right - bounds.left >= 48.dp)
            assertTrue(bounds.bottom - bounds.top >= 48.dp)
            if (index == 2) chip.assertIsSelected()
        }
        val secondLabel = compose.activity.getString(R.string.choice_unnamed_option, 2)
        compose.onNodeWithText(compose.activity.getString(R.string.choice_selection_summary,
            secondLabel, compose.activity.getString(R.string.form_single_choice))).assertIsDisplayed()
        compose.onNodeWithText(secondLabel).performClick()
        compose.runOnIdle {
            assertEquals(listOf(ChoiceOptionUiInput(question.id, question.responseSet.id, 2)), clicks)
            question = questions.last()
        }
        compose.onNodeWithText(compose.activity.getString(R.string.choice_no_options)).assertIsDisplayed()
        compose.onNodeWithTag("choice_options_${question.id}").assertDoesNotExist()
    }

    @Test
    fun manyOptionFixtureCanReachItsLastOptionInBothSelectionModes() {
        val questions = content("valid/many-options.json").rows.filterIsInstance<FormRowUiModel.Choice>()
        var question by mutableStateOf(questions.first())
        compose.setContent {
            LumiformTestAppTheme {
                key(question.id) {
                    Box(Modifier.width(320.dp)) { ChoiceQuestion(question, enabled = true, onOptionClick = {}) }
                }
            }
        }
        for (next in questions) {
            compose.runOnIdle { question = next }
            val last = next.responseSet.options.last()
            compose.onNodeWithTag("choice_options_${next.id}").performScrollToKey(last.id)
            compose.onNodeWithText(last.label).assertIsDisplayed()
            val bounds = compose.onNodeWithText(last.label).getUnclippedBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top >= 48.dp)
        }
    }

    private fun read(path: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(path)
        .bufferedReader().use { it.readText() }
    private fun content(file: String) = uiMapper.map(FormSnapshot(parser.parse(read("${FormFixture.ROOT}/$file")), Instant.EPOCH))
}
