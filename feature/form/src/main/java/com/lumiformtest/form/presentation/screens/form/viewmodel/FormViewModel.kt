package com.lumiformtest.form.presentation.screens.form.viewmodel

import com.lumiformtest.base.viewmodel.BaseCoroutineViewModel
import androidx.lifecycle.viewModelScope
import com.lumiformtest.form.di.FormDefaultDispatcher
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.usecase.ObserveFormUseCase
import com.lumiformtest.form.domain.usecase.RefreshFormUseCase
import com.lumiformtest.form.domain.usecase.ToggleChoiceOptionUseCase
import com.lumiformtest.form.presentation.screens.form.mapper.FormUiMapper
import com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel
import com.lumiformtest.form.presentation.screens.form.model.ChoiceOptionUiInput
import com.lumiformtest.form.presentation.screens.form.model.ChoiceWriteFailureUi
import com.lumiformtest.form.presentation.screens.form.model.ChoiceWriteFailureReasonUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel

@HiltViewModel
internal class FormViewModel @Inject constructor(
    private val observeForm: ObserveFormUseCase,
    private val refreshForm: RefreshFormUseCase,
    private val toggleChoiceOption: ToggleChoiceOptionUseCase,
    private val mapper: FormUiMapper,
    @FormDefaultDispatcher private val mappingDispatcher: CoroutineDispatcher
) : BaseCoroutineViewModel<FormAction, FormUiState, FormNavigationDestination, FormEvent>(
    initialState = FormUiState()
) {
    private var initialized = false
    private var observation: Job? = null
    private var nextChoiceAttempt = 0L
    private val choiceQueueDelegate = lazy { Channel<ChoiceWriteAttempt>(Channel.UNLIMITED) }
    private val choiceQueue by choiceQueueDelegate
    private val choiceWriter by lazy {
        viewModelScope.launch {
            for (attempt in choiceQueue) {
                val failure = when (val result = toggleChoiceOption(mapper.map(attempt.option))) {
                    ChoiceSelectionResult.Success -> null
                    is ChoiceSelectionResult.Failure -> mapper.map(result.reason)
                }
                sendAction(FormAction.ChoiceWriteFinished(attempt.id, attempt.option.questionId, failure))
            }
        }
    }

    init {
        sendAction(FormAction.Initialize)
    }

    override fun onAction(action: FormAction, prevState: FormUiState) {
        when (action) {
            FormAction.Initialize -> if (!initialized) {
                initialized = true
                observeSavedContent()
                sendAction(FormAction.Refresh)
            }

            FormAction.Refresh -> if (!prevState.isRefreshing && !prevState.isAwaitingSnapshot) {
                if (prevState.hasReadFailure) {
                    updateState { copy(isReadingCache = true) }
                    observeSavedContent()
                }
                updateState { copy(isRefreshing = true, refreshFailure = null) }
                viewModelScope.launch {
                    val failure = when (val result = refreshForm()) {
                        RefreshFormResult.Success -> null
                        is RefreshFormResult.Failure -> mapper.map(result.reason)
                    }
                    sendAction(FormAction.RefreshFinished(failure))
                }
            }

            is FormAction.ContentObserved -> updateState {
                copy(
                    content = action.content, isReadingCache = false, hasReadFailure = false,
                    isAwaitingSnapshot = isAwaitingSnapshot && action.content == null
                )
            }

            FormAction.ReadFailed -> updateState {
                copy(isReadingCache = false, hasReadFailure = true, isAwaitingSnapshot = false)
            }

            is FormAction.RefreshFinished -> updateState {
                // Room can publish after refresh returns. Only its emission supplies content.
                copy(
                    isRefreshing = false, refreshFailure = action.failure,
                    isAwaitingSnapshot = action.failure == null && content == null && !hasReadFailure
                )
            }

            is FormAction.OpenImage -> navigateTo(FormNavigationDestination.OpenImage(action.imageId))
            is FormAction.OptionClicked -> {
                val attempt = ChoiceWriteAttempt(++nextChoiceAttempt, action.option)
                if (prevState.hasReadFailure) {
                    updateState { copy(choiceWriteFailure = ChoiceWriteFailureUi(
                        attempt.id, attempt.option.questionId, ChoiceWriteFailureReasonUi.READ_UNAVAILABLE
                    )) }
                } else submitChoice(attempt)
            }
            is FormAction.ChoiceWriteFinished -> updateState {
                val previous = choiceWriteFailure
                copy(choiceWriteFailure = when {
                    previous != null && previous.id > action.attemptId -> previous
                    action.failure != null -> ChoiceWriteFailureUi(action.attemptId, action.questionId, action.failure)
                    previous?.questionId == action.questionId && previous.id <= action.attemptId -> null
                    else -> previous
                })
            }
            is FormAction.DismissChoiceFailure -> updateState {
                if (choiceWriteFailure?.id == action.id) copy(choiceWriteFailure = null) else this
            }
        }
    }

    private fun observeSavedContent() {
        observation?.cancel()
        observation = viewModelScope.launch {
            var previousDocument: FormDocument? = null
            var previousRows: List<FormRowUiModel> = emptyList()
            observeForm().collect { result ->
                when (result) {
                    is FormObservation.Snapshot -> {
                        val snapshot = result.value
                        val content = withContext(mappingDispatcher) {
                            snapshot?.let {
                                val rows = if (it.document == previousDocument) previousRows else mapper.mapStructure(it.document)
                                mapper.applySelections(rows, it)
                            }
                        }
                        previousDocument = snapshot?.document
                        previousRows = content?.rows.orEmpty()
                        sendAction(FormAction.ContentObserved(content))
                    }

                    FormObservation.ReadFailure -> sendAction(FormAction.ReadFailed)
                }
            }
        }
    }

    private fun submitChoice(attempt: ChoiceWriteAttempt): Job {
        // One writer keeps tap order, even when a database write suspends.
        choiceQueue.trySend(attempt).getOrThrow()
        return choiceWriter
    }

    override fun onCleared() {
        if (choiceQueueDelegate.isInitialized()) choiceQueue.cancel()
        super.onCleared()
    }

    private data class ChoiceWriteAttempt(val id: Long, val option: ChoiceOptionUiInput)
}
