package com.lumiformtest.form.presentation.viewmodel

import androidx.lifecycle.ViewModelStore
import com.lumiformtest.form.domain.model.ChoiceSelection
import com.lumiformtest.form.domain.model.ChoiceSelectionFailure
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.ResponseSet
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.FormRefreshFailure
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.repository.FormRepository
import com.lumiformtest.form.domain.usecase.ObserveFormUseCase
import com.lumiformtest.form.domain.usecase.RefreshFormUseCase
import com.lumiformtest.form.domain.usecase.ToggleChoiceOptionUseCase
import com.lumiformtest.form.presentation.mapper.FormUiMapper
import com.lumiformtest.form.presentation.model.ChoiceOptionUiInput
import com.lumiformtest.form.presentation.model.ChoiceWriteFailureReasonUi
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.model.FormRowUiModel
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FormViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private var nextKey = 0
    private val saved by lazy { snapshot("Saved") }

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `opening an image sends its ID through the navigation output`() = runTest(dispatcher) {
        val viewModel = create(FakeRepository(FormObservation.Snapshot(saved)))
        viewModel.sendAction(FormAction.OpenImage(9))
        runCurrent()
        assertEquals(FormNavigationDestination.OpenImage(9), viewModel.navigateFlow.first())
    }

    @Test
    fun `a failed initial read stays retryable after a successful refresh`() = runTest(dispatcher) {
        val repository = FakeRepository(FormObservation.ReadFailure).apply { refresh = { RefreshFormResult.Success } }
        val viewModel = create(repository)
        runCurrent()
        assertNull(viewModel.viewState.value.content)
        assertTrue(viewModel.viewState.value.hasReadFailure)
        assertFalse(viewModel.viewState.value.isLoading)
        assertFalse(viewModel.viewState.value.isAwaitingSnapshot)
    }

    @Test
    fun `initial cache remains visible during refresh and newer observations`() = runTest(dispatcher) {
        val repository = FakeRepository(FormObservation.Snapshot(saved))
        val viewModel = create(repository)
        runCurrent()

        assertEquals("Saved", viewModel.title())
        assertTrue(viewModel.viewState.value.isRefreshing)
        repository.observations.emit(FormObservation.Snapshot(snapshot("New")))
        runCurrent()
        assertEquals("New", viewModel.title())
        assertTrue(viewModel.viewState.value.isRefreshing)
        assertFalse(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `no cache shows loading then a retryable first fetch failure`() = runTest(dispatcher) {
        val finish = CompletableDeferred<RefreshFormResult>()
        val repository = FakeRepository(FormObservation.Snapshot(null)).apply { refresh = { finish.await() } }
        val viewModel = create(repository)
        runCurrent()
        assertTrue(viewModel.viewState.value.isLoading)

        finish.complete(RefreshFormResult.Failure(FormRefreshFailure.FETCH))
        runCurrent()

        assertFalse(viewModel.viewState.value.isLoading)
        assertNull(viewModel.viewState.value.content)
        assertEquals(FormRefreshFailureUi.FETCH, viewModel.viewState.value.refreshFailure)
    }

    @Test
    fun `all refresh failures retain the saved snapshot and update time`() = runTest(dispatcher) {
        for ((reason, uiReason) in listOf(
            FormRefreshFailure.FETCH to FormRefreshFailureUi.FETCH,
            FormRefreshFailure.OFFLINE to FormRefreshFailureUi.OFFLINE,
            FormRefreshFailure.INVALID_CONTENT to FormRefreshFailureUi.INVALID_CONTENT,
            FormRefreshFailure.STORAGE to FormRefreshFailureUi.STORAGE
        )) {
            val repository = FakeRepository(FormObservation.Snapshot(saved)).apply {
                refresh = { RefreshFormResult.Failure(reason) }
            }
            val viewModel = create(repository)
            runCurrent()
            assertEquals("Saved", viewModel.title())
            assertEquals(saved.lastUpdated.toEpochMilli(), viewModel.viewState.value.content?.lastUpdatedEpochMillis)
            assertEquals(uiReason, viewModel.viewState.value.refreshFailure)
            assertFalse(viewModel.viewState.value.isRefreshing)
        }
    }

    @Test
    fun `successful empty content is different from an absent snapshot`() = runTest(dispatcher) {
        val empty = saved.copy(document = FormDocument(emptyList()))
        val viewModel = create(FakeRepository(FormObservation.Snapshot(empty)))
        runCurrent()
        assertNotNull(viewModel.viewState.value.content)
        assertTrue(requireNotNull(viewModel.viewState.value.content).rows.isEmpty())
        assertFalse(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `refresh success waits for Room instead of publishing an empty screen`() = runTest(dispatcher) {
        val repository = FakeRepository().apply { refresh = { RefreshFormResult.Success } }
        val viewModel = create(repository)
        runCurrent()
        assertTrue(viewModel.viewState.value.isLoading)

        repository.observations.emit(FormObservation.Snapshot(null))
        runCurrent()
        assertTrue(viewModel.viewState.value.isLoading)
        assertTrue(viewModel.viewState.value.isAwaitingSnapshot)

        repository.observations.emit(FormObservation.Snapshot(saved))
        runCurrent()
        assertEquals("Saved", viewModel.title())
        assertFalse(viewModel.viewState.value.isAwaitingSnapshot)
        assertFalse(viewModel.viewState.value.isLoading)
    }

    @Test
    fun `Room can publish before refresh completes without losing content`() = runTest(dispatcher) {
        val finish = CompletableDeferred<RefreshFormResult>()
        val repository = FakeRepository(FormObservation.Snapshot(null)).apply { refresh = { finish.await() } }
        val viewModel = create(repository)
        runCurrent()
        repository.observations.emit(FormObservation.Snapshot(saved))
        runCurrent()
        finish.complete(RefreshFormResult.Success)
        runCurrent()
        assertEquals("Saved", viewModel.title())
        assertFalse(viewModel.viewState.value.isAwaitingSnapshot)
        assertFalse(viewModel.viewState.value.isRefreshing)
    }

    @Test
    fun `initial fetch failure waits for the first cache read before replacing loading`() = runTest(dispatcher) {
        val repository = FakeRepository().apply { refresh = { RefreshFormResult.Failure(FormRefreshFailure.FETCH) } }
        val viewModel = create(repository)
        runCurrent()
        assertTrue(viewModel.viewState.value.isLoading)

        repository.observations.emit(FormObservation.Snapshot(saved))
        runCurrent()
        assertEquals("Saved", viewModel.title())
        assertEquals(FormRefreshFailureUi.FETCH, viewModel.viewState.value.refreshFailure)
    }

    @Test
    fun `duplicate initialization and repeated refresh taps do not create more work`() = runTest(dispatcher) {
        val repository = FakeRepository(FormObservation.Snapshot(saved))
        val viewModel = create(repository)
        repeat(5) {
            viewModel.sendAction(FormAction.Initialize)
            viewModel.sendAction(FormAction.Refresh)
        }
        runCurrent()
        assertEquals(1, repository.observeCalls)
        assertEquals(1, repository.refreshCalls)
        assertEquals(1, repository.activeObservers)
    }

    @Test
    fun `read failure retains displayed content and refresh success does not hide it`() = runTest(dispatcher) {
        val finish = CompletableDeferred<RefreshFormResult>()
        val repository = FakeRepository(FormObservation.Snapshot(saved)).apply { refresh = { finish.await() } }
        val viewModel = create(repository)
        runCurrent()
        repository.observations.emit(FormObservation.ReadFailure)
        runCurrent()
        finish.complete(RefreshFormResult.Success)
        runCurrent()
        assertEquals("Saved", viewModel.title())
        assertTrue(viewModel.viewState.value.hasReadFailure)
        assertFalse(viewModel.viewState.value.isAwaitingSnapshot)
    }

    @Test
    fun `retry restarts a failed observer and recovers only after a readable snapshot`() = runTest(dispatcher) {
        val repository = FakeRepository(FormObservation.Snapshot(saved)).apply { refresh = { RefreshFormResult.Success } }
        val viewModel = create(repository)
        runCurrent()
        repository.observations.emit(FormObservation.ReadFailure)
        runCurrent()
        assertTrue(viewModel.viewState.value.hasReadFailure)
        assertEquals(0, repository.activeObservers)

        viewModel.sendAction(FormAction.Refresh)
        runCurrent()
        assertTrue(viewModel.viewState.value.hasReadFailure)
        repository.observations.emit(FormObservation.Snapshot(snapshot("Recovered")))
        viewModel.sendAction(FormAction.Refresh)
        runCurrent()
        assertEquals("Recovered", viewModel.title())
        assertFalse(viewModel.viewState.value.hasReadFailure)
        assertEquals(1, repository.activeObservers)
    }

    @Test
    fun `retry clears the previous fetch error and can succeed`() = runTest(dispatcher) {
        val repository = FakeRepository(FormObservation.Snapshot(null)).apply {
            refresh = { RefreshFormResult.Failure(FormRefreshFailure.FETCH) }
        }
        val viewModel = create(repository)
        runCurrent()
        val finish = CompletableDeferred<RefreshFormResult>()
        repository.refresh = { finish.await() }
        viewModel.sendAction(FormAction.Refresh)
        runCurrent()
        assertNull(viewModel.viewState.value.refreshFailure)
        assertTrue(viewModel.viewState.value.isLoading)
        repository.observations.emit(FormObservation.Snapshot(saved))
        finish.complete(RefreshFormResult.Success)
        runCurrent()
        assertEquals("Saved", viewModel.title())
        assertEquals(2, repository.refreshCalls)
    }

    @Test
    fun `clearing cancels observation and the in flight refresh`() = runTest(dispatcher) {
        var cancelled = false
        val repository = FakeRepository(FormObservation.Snapshot(saved)).apply {
            refresh = { try { awaitCancellation() } finally { cancelled = true } }
        }
        create(repository)
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(0, repository.activeObservers)
    }

    @Test
    fun `choice writes keep tap order while refresh and the first write are suspended`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        val repository = FakeRepository(FormObservation.Snapshot(choiceSnapshot())).apply {
            toggle = { if (toggles.size == 1) finish.await(); ChoiceSelectionResult.Success }
        }
        val viewModel = create(repository)
        val inputs = listOf(ChoiceOptionUiInput(10, 100, 1), ChoiceOptionUiInput(10, 100, 2), ChoiceOptionUiInput(11, 100, 1))
        inputs.forEach { viewModel.sendAction(FormAction.OptionClicked(it)) }
        runCurrent()
        assertEquals(listOf(ChoiceOption(10, 100, 1)), repository.toggles)
        assertTrue(viewModel.viewState.value.isRefreshing)
        finish.complete(Unit)
        runCurrent()
        assertEquals(inputs.map { ChoiceOption(it.questionId, it.responseSetId, it.optionId) }, repository.toggles)
    }

    @Test
    fun `checked state waits for Room and selection projection reuses unaffected rows`() = runTest(dispatcher) {
        val repository = FakeRepository(FormObservation.Snapshot(choiceSnapshot()))
        val viewModel = create(repository)
        runCurrent()
        val oldRows = requireNotNull(viewModel.viewState.value.content).rows
        viewModel.sendAction(FormAction.OptionClicked(ChoiceOptionUiInput(10, 100, 1)))
        runCurrent()
        assertTrue(viewModel.selected(10).isEmpty())
        repository.observations.emit(FormObservation.Snapshot(choiceSnapshot(setOf(1))))
        runCurrent()
        val newRows = requireNotNull(viewModel.viewState.value.content).rows
        assertEquals(setOf(1L), viewModel.selected(10))
        assertTrue((oldRows.first { it.id == 10L } as FormRowUiModel.Choice).selectedOptionIds.isEmpty())
        assertSame(oldRows.first { it.id == 2L }, newRows.first { it.id == 2L })
        assertSame(oldRows.first { it.id == 11L }, newRows.first { it.id == 11L })
    }

    @Test
    fun `old dismissals and successes from another question do not clear the latest save error`() = runTest(dispatcher) {
        val repository = FakeRepository(FormObservation.Snapshot(choiceSnapshot())).apply {
            toggle = { ChoiceSelectionResult.Failure(ChoiceSelectionFailure.STORAGE) }
        }
        val viewModel = create(repository)
        viewModel.sendAction(FormAction.OptionClicked(ChoiceOptionUiInput(10, 100, 1)))
        runCurrent()
        val first = requireNotNull(viewModel.viewState.value.choiceWriteFailure)
        viewModel.sendAction(FormAction.OptionClicked(ChoiceOptionUiInput(11, 100, 1)))
        runCurrent()
        val latest = requireNotNull(viewModel.viewState.value.choiceWriteFailure)
        viewModel.sendAction(FormAction.DismissChoiceFailure(first.id))
        repository.toggle = { ChoiceSelectionResult.Success }
        viewModel.sendAction(FormAction.OptionClicked(ChoiceOptionUiInput(10, 100, 1)))
        runCurrent()
        assertEquals(latest, viewModel.viewState.value.choiceWriteFailure)
        viewModel.sendAction(FormAction.OptionClicked(ChoiceOptionUiInput(11, 100, 1)))
        runCurrent()
        assertNull(viewModel.viewState.value.choiceWriteFailure)
    }

    @Test
    fun `an older write result cannot replace or clear a newer read failure`() = runTest(dispatcher) {
        for (result in listOf(ChoiceSelectionResult.Success, ChoiceSelectionResult.Failure(ChoiceSelectionFailure.STORAGE))) {
            val finish = CompletableDeferred<ChoiceSelectionResult>()
            val repository = FakeRepository(FormObservation.Snapshot(choiceSnapshot())).apply { toggle = { finish.await() } }
            val viewModel = create(repository)
            viewModel.sendAction(FormAction.OptionClicked(ChoiceOptionUiInput(10, 100, 1)))
            runCurrent()
            repository.observations.emit(FormObservation.ReadFailure)
            runCurrent()
            viewModel.sendAction(FormAction.OptionClicked(ChoiceOptionUiInput(10, 100, 2)))
            runCurrent()
            val failure = requireNotNull(viewModel.viewState.value.choiceWriteFailure)
            assertEquals(ChoiceWriteFailureReasonUi.READ_UNAVAILABLE, failure.reason)
            finish.complete(result)
            runCurrent()
            assertEquals(failure, viewModel.viewState.value.choiceWriteFailure)
            assertEquals(1, repository.toggles.size)
        }
    }

    @Test
    fun `clearing cancels the choice writer and leaves queued taps uncommitted`() = runTest(dispatcher) {
        var cancelled = false
        val repository = FakeRepository(FormObservation.Snapshot(choiceSnapshot())).apply {
            toggle = { try { awaitCancellation() } finally { cancelled = true } }
        }
        val viewModel = create(repository)
        repeat(3) { viewModel.sendAction(FormAction.OptionClicked(ChoiceOptionUiInput(10, 100, 1))) }
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(1, repository.toggles.size)
        assertTrue(viewModel.selected(10).isEmpty())
    }

    @Test
    fun `stale option identity is forwarded and its failure survives snapshot emissions`() = runTest(dispatcher) {
        val repository = FakeRepository(FormObservation.Snapshot(choiceSnapshot())).apply {
            toggle = { ChoiceSelectionResult.Failure(ChoiceSelectionFailure.UNAVAILABLE) }
        }
        val viewModel = create(repository)
        viewModel.sendAction(FormAction.OptionClicked(ChoiceOptionUiInput(10, 999, 1)))
        runCurrent()
        assertEquals(listOf(ChoiceOption(10, 999, 1)), repository.toggles)
        val failure = requireNotNull(viewModel.viewState.value.choiceWriteFailure)
        assertEquals(ChoiceWriteFailureReasonUi.UNAVAILABLE, failure.reason)
        repository.observations.emit(FormObservation.Snapshot(choiceSnapshot(setOf(2))))
        runCurrent()
        assertEquals(failure, viewModel.viewState.value.choiceWriteFailure)
        viewModel.sendAction(FormAction.DismissChoiceFailure(failure.id))
        runCurrent()
        assertNull(viewModel.viewState.value.choiceWriteFailure)
    }

    private fun choiceSnapshot(selected: Set<Long> = emptySet()): FormSnapshot {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null), ResponseOption(2, "Two", null)))
        return saved.copy(
            document = FormDocument(listOf(FormPage(1, "Page", listOf(
                FormItem.Text(2, "Text"), FormItem.Choice(10, "First", set), FormItem.Choice(11, "Shared", set)
            )))),
            selections = if (selected.isEmpty()) emptyMap() else mapOf(10L to ChoiceSelection(100, selected))
        )
    }

    private fun FormViewModel.selected(id: Long) =
        (requireNotNull(viewState.value.content).rows.first { it.id == id } as FormRowUiModel.Choice).selectedOptionIds

    private fun create(repository: FakeRepository) = FormViewModel(
        ObserveFormUseCase(repository), RefreshFormUseCase(repository), ToggleChoiceOptionUseCase(repository), FormUiMapper(), dispatcher
    ).also { store.put("form-${nextKey++}", it) }

    private fun FormViewModel.title() = (viewState.value.content?.rows?.first() as? FormRowUiModel.Page)?.title

    private fun snapshot(title: String) = FormSnapshot(
        FormDocument(listOf(FormPage(1, title, emptyList()))), Instant.parse("2026-10-29T12:00:00Z")
    )

    private class FakeRepository(initial: FormObservation? = null) : FormRepository {
        val observations by lazy { MutableSharedFlow<FormObservation>(replay = 1).apply { initial?.let { tryEmit(it) } } }
        var observeCalls = 0
        var activeObservers = 0
        var refreshCalls = 0
        var refresh: suspend () -> RefreshFormResult = { awaitCancellation() }
        val toggles by lazy { mutableListOf<ChoiceOption>() }
        var toggle: suspend (ChoiceOption) -> ChoiceSelectionResult = { ChoiceSelectionResult.Success }

        override suspend fun toggleChoiceOption(option: ChoiceOption): ChoiceSelectionResult {
            toggles += option
            return toggle(option)
        }

        override fun observeForm() = flow {
            observeCalls++
            activeObservers++
            try {
                emitAll(observations.transformWhile { emit(it); it != FormObservation.ReadFailure })
            } finally { activeObservers-- }
        }

        override suspend fun refreshForm(): RefreshFormResult {
            refreshCalls++
            return refresh()
        }
    }
}
