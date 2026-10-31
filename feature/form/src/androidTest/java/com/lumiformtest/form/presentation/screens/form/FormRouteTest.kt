package com.lumiformtest.form.presentation.screens.form

import android.os.Looper
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.form.FormScreenTestActivity
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.ImageObservation
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.repository.FormRepository
import com.lumiformtest.form.domain.usecase.ObserveFormUseCase
import com.lumiformtest.form.domain.usecase.RefreshFormUseCase
import com.lumiformtest.form.domain.usecase.ToggleChoiceOptionUseCase
import com.lumiformtest.form.presentation.navigation.rememberFormImageTransitionState
import com.lumiformtest.form.presentation.screens.form.model.FormContentUiModel
import com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel
import com.lumiformtest.form.presentation.screens.form.model.ResponseOptionUiModel
import com.lumiformtest.form.presentation.screens.form.model.ResponseSetUiModel
import com.lumiformtest.form.presentation.screens.form.viewmodel.FormAction
import com.lumiformtest.form.presentation.screens.form.viewmodel.FormViewModel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormRouteTest {
    @get:Rule val compose = createAndroidComposeRule<FormScreenTestActivity>()

    @Test
    fun aChoiceTapDoesNotScanRowsOnMain() {
        val repository = object : FormRepository {
            override fun observeForm() = emptyFlow<FormObservation>()
            override fun observeImage(imageId: Long) = emptyFlow<ImageObservation>()
            override suspend fun refreshForm(): RefreshFormResult = awaitCancellation()
            override suspend fun toggleChoiceOption(option: ChoiceOption): ChoiceSelectionResult = awaitCancellation()
        }
        val model = FormViewModel(ObserveFormUseCase(repository), RefreshFormUseCase(repository), ToggleChoiceOptionUseCase(repository), Dispatchers.Default)
        compose.activityRule.scenario.onActivity { it.viewModelStore.put("form-route", model) }
        val rows = ((1L..1999L).map { FormRowUiModel.Text(it, 0, 1, "Question $it") } +
            FormRowUiModel.Choice(2000, 0, 1, "Last choice", ResponseSetUiModel(100, false, listOf(ResponseOptionUiModel(1, "Option A", null)))))
            .toPersistentList()
        val visits = AtomicInteger()
        val guardedRows = object : PersistentList<FormRowUiModel> by rows {
            override fun iterator(): Iterator<FormRowUiModel> {
                val actual = rows.iterator()
                return object : Iterator<FormRowUiModel> {
                    override fun hasNext() = actual.hasNext()
                    override fun next(): FormRowUiModel {
                        if (Looper.myLooper() == Looper.getMainLooper()) visits.incrementAndGet()
                        return actual.next()
                    }
                }
            }
        }
        val content = FormContentUiModel(guardedRows, 0, definitionVersion = 1, rowIndexById = rows.withIndex().associate { it.value.id to it.index })
        compose.runOnIdle { model.sendAction(FormAction.ContentObserved(content)) }
        compose.setContent { LumiformTestAppTheme { FormRoute({}, rememberFormImageTransitionState(), model) } }
        compose.onNodeWithTag("form_list").performScrollToKey(2000L)
        compose.waitForIdle()
        visits.set(0)
        compose.onNodeWithText("Option A").performClick()
        compose.waitForIdle()
        assertEquals("A choice tap must not traverse form rows on Main", 0, visits.get())
    }
}
