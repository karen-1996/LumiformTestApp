package com.lumiformtest.base.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * I handle state changes and side effects in the same action handler.
 * Actions are handled in order, but work launched by them can finish in a different order.
 * State describes the screen; navigation and events are temporary, single-consumer outputs.
 */
abstract class BaseCoroutineViewModel<A : Action, V : ViewState, N : NavDestination, E : Event>(
    initialState: V
) : ViewModel() {

    private val _viewState = MutableStateFlow(initialState)
    val viewState = _viewState.asStateFlow()

    // This queue is for discrete UI actions, not high-frequency streams.
    private val actionChannel = Channel<A>(Channel.UNLIMITED)
    private val navigationChannel = Channel<N>(Channel.BUFFERED)
    private val events = Channel<E>(Channel.BUFFERED)

    val navigateFlow = navigationChannel.receiveAsFlow()
    val eventChannel = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            for (action in actionChannel) {
                onAction(action, viewState.value)
            }
        }
    }

    fun sendAction(action: A) = viewModelScope.launch {
        actionChannel.send(action)
    }

    protected abstract fun onAction(action: A, prevState: V)

    /** The editor can run more than once, so it must only calculate the next state. */
    protected fun updateState(editor: V.() -> V) {
        _viewState.update { current -> current.editor() }
    }

    protected fun navigateTo(destination: N) = viewModelScope.launch {
        navigationChannel.send(destination)
    }

    protected fun sendEvent(event: E) = viewModelScope.launch {
        events.send(event)
    }

    override fun onCleared() {
        actionChannel.cancel()
        navigationChannel.cancel()
        events.cancel()
    }
}
