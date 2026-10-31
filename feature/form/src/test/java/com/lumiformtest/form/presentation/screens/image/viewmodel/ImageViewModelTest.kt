package com.lumiformtest.form.presentation.screens.image.viewmodel

import androidx.lifecycle.ViewModelStore
import com.lumiformtest.form.domain.model.*
import com.lumiformtest.form.domain.repository.FormRepository
import com.lumiformtest.form.domain.usecase.ObserveImageUseCase
import com.lumiformtest.form.presentation.screens.image.mapper.ImageUiMapper
import com.lumiformtest.form.presentation.screens.image.model.ImageUiModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test
    fun `ID resolves nested saved image and observes changed title URL or removal`() = runTest(dispatcher) {
        val repository = FakeRepository(snapshot("Saved", "https://example.com/old"))
        val model = create(repository)
        model.sendAction(ImageAction.Initialize(9))
        runCurrent()
        assertEquals(ImageUiModel(9, "Saved", "https://example.com/old"), model.viewState.value.image)
        assertFalse(model.viewState.value.isLoading)
        repository.saved.value = snapshot("Updated", "https://example.com/new")
        runCurrent()
        assertEquals(ImageUiModel(9, "Updated", "https://example.com/new"), model.viewState.value.image)
        repository.saved.value = FormObservation.Snapshot(null)
        runCurrent()
        assertNull(model.viewState.value.image)
        assertFalse(model.viewState.value.hasReadFailure)
        assertEquals(0, repository.refreshes)
    }

    @Test
    fun `missing and non-image IDs never resolve to unrelated content`() = runTest(dispatcher) {
        val repository = FakeRepository(snapshot("Saved", "https://example.com/a"))
        for (id in listOf(1L, 2L, 10L, Long.MAX_VALUE)) {
            val model = create(repository, id.toString())
            model.sendAction(ImageAction.Initialize(id))
            runCurrent()
            assertNull(model.viewState.value.image)
            assertFalse(model.viewState.value.isLoading)
        }
    }

    @Test
    fun `read failure retries observation without refreshing remote content`() = runTest(dispatcher) {
        val repository = FakeRepository(FormObservation.ReadFailure)
        val model = create(repository)
        model.sendAction(ImageAction.Initialize(9))
        runCurrent()
        assertTrue(model.viewState.value.hasReadFailure)
        model.sendAction(ImageAction.Initialize(1))
        runCurrent()
        assertEquals(1, repository.observers)
        repository.saved.value = snapshot("Recovered", "https://example.com/a")
        model.sendAction(ImageAction.Retry)
        runCurrent()
        assertEquals(2, repository.observers)
        assertEquals("Recovered", model.viewState.value.image?.title)
        assertFalse(model.viewState.value.hasReadFailure)
        assertEquals(0, repository.refreshes)
    }

    @Test
    fun `clearing entry stops saved-image observation`() = runTest(dispatcher) {
        val repository = FakeRepository(snapshot("Saved", "https://example.com/a"))
        val model = create(repository)
        model.sendAction(ImageAction.Initialize(9))
        runCurrent()
        store.clear()
        repository.saved.value = snapshot("Changed", "https://example.com/b")
        runCurrent()
        assertEquals("Saved", model.viewState.value.image?.title)
    }

    private fun create(repository: FakeRepository, key: String = "image") =
        ImageViewModel(ObserveImageUseCase(repository), ImageUiMapper()).also { store.put(key, it) }

    private fun snapshot(title: String, url: String) = FormObservation.Snapshot(FormSnapshot(
        FormDocument(listOf(FormPage(1, "Page", listOf(FormItem.Section(2, "Section", listOf(
            FormItem.Text(10, "Text"), FormItem.Image(9, url, title)
        )))))), Instant.EPOCH
    ))

    private class FakeRepository(initial: FormObservation) : FormRepository {
        override fun observeImage(imageId: Long): kotlinx.coroutines.flow.Flow<com.lumiformtest.form.domain.model.ImageObservation> = error("Not used")
        val saved = MutableStateFlow(initial)
        var observers = 0
        var refreshes = 0
        override fun observeForm() = flow {
            observers++
            if (saved.value == FormObservation.ReadFailure) emit(FormObservation.ReadFailure) else emitAll(saved)
        }
        override suspend fun refreshForm(): RefreshFormResult { refreshes++; return RefreshFormResult.Success }
        override suspend fun toggleChoiceOption(option: ChoiceOption) = ChoiceSelectionResult.Success
    }
}
