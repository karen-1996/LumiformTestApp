package com.lumiformtest.base.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseCoroutineViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `queued actions are handled in order using the latest state`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.sendAction(TestAction.Add(1))
        viewModel.sendAction(TestAction.Multiply(10))
        viewModel.sendAction(TestAction.Add(2))
        runCurrent()

        assertEquals(12, viewModel.viewState.value.count)
        assertEquals(listOf(0, 1, 10), viewModel.previousCounts)
    }

    @Test
    fun `overlapping side effects edit current state without losing newer changes`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.sendAction(TestAction.AddLater(1, 100))
        viewModel.sendAction(TestAction.AddLater(2, 50))
        viewModel.sendAction(TestAction.Add(10))
        runCurrent()
        assertEquals(10, viewModel.viewState.value.count)

        advanceTimeBy(50)
        runCurrent()
        assertEquals(12, viewModel.viewState.value.count)

        advanceUntilIdle()
        assertEquals(13, viewModel.viewState.value.count)
    }

    @Test
    fun `navigation and events wait for their own consumer without replacing state`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.sendAction(TestAction.Add(3))
        viewModel.sendAction(TestAction.Navigate)
        viewModel.sendAction(TestAction.Notify)
        runCurrent()

        assertEquals(TestNavigation.Details, viewModel.navigateFlow.first())
        assertEquals(TestEvent.Message, viewModel.eventChannel.first())
        assertEquals(3, viewModel.viewState.value.count)
    }

    @Test
    fun `consumed navigation is not replayed to a new consumer`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.sendAction(TestAction.Navigate)
        runCurrent()
        assertEquals(TestNavigation.Details, viewModel.navigateFlow.first())

        assertNull(withTimeoutOrNull(1) { viewModel.navigateFlow.first() })
    }

    @Test
    fun `clearing the ViewModel cancels unfinished side effects`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.sendAction(TestAction.AddLater(1, 100))
        runCurrent()

        store.clear()
        advanceUntilIdle()

        assertEquals(0, viewModel.viewState.value.count)
    }

    private fun createViewModel() = TestViewModel().also { store.put("test", it) }
}

private data class TestState(val count: Int = 0) : ViewState

private sealed interface TestAction : Action {
    data class Add(val amount: Int) : TestAction
    data class Multiply(val amount: Int) : TestAction
    data class AddLater(val amount: Int, val delayMillis: Long) : TestAction
    data object Navigate : TestAction
    data object Notify : TestAction
}

private enum class TestNavigation : NavDestination { Details }
private enum class TestEvent : Event { Message }

private class TestViewModel : BaseCoroutineViewModel<
    TestAction, TestState, TestNavigation, TestEvent
>(TestState()) {
    val previousCounts = mutableListOf<Int>()

    override fun onAction(action: TestAction, prevState: TestState) {
        previousCounts += prevState.count
        when (action) {
            is TestAction.Add -> updateState { copy(count = count + action.amount) }
            is TestAction.Multiply -> updateState { copy(count = count * action.amount) }
            is TestAction.AddLater -> viewModelScope.launch {
                delay(action.delayMillis)
                updateState { copy(count = count + action.amount) }
            }
            TestAction.Navigate -> navigateTo(TestNavigation.Details)
            TestAction.Notify -> sendEvent(TestEvent.Message)
        }
    }
}
