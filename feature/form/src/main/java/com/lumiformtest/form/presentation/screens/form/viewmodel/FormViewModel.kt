package com.lumiformtest.form.presentation.screens.form.viewmodel

import com.lumiformtest.base.viewmodel.BaseCoroutineViewModel
import androidx.lifecycle.viewModelScope
import com.lumiformtest.form.di.FormDefaultDispatcher
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.usecase.ObserveFormUseCase
import com.lumiformtest.form.domain.usecase.RefreshFormUseCase
import com.lumiformtest.form.domain.usecase.ToggleChoiceOptionUseCase
import com.lumiformtest.form.presentation.screens.form.mapper.toDomain
import com.lumiformtest.form.presentation.screens.form.mapper.toUiModel
import com.lumiformtest.form.presentation.screens.form.model.ChoiceWriteAttemptUiModel
import com.lumiformtest.form.presentation.screens.form.model.ChoiceWriteFailureUi
import com.lumiformtest.form.presentation.screens.form.model.ChoiceWriteFailureReasonUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
internal class FormViewModel @Inject constructor(
    private val observeForm: ObserveFormUseCase,
    private val refreshForm: RefreshFormUseCase,
    private val toggleChoiceOption: ToggleChoiceOptionUseCase,
    @FormDefaultDispatcher private val mappingDispatcher: CoroutineDispatcher
) : BaseCoroutineViewModel<FormAction, FormUiState, FormNavigationDestination, FormEvent>(
    initialState = FormUiState()
) {
    init {
        observeSavedContent()
        sendAction(FormAction.Refresh)
    }

    override fun onAction(action: FormAction, prevState: FormUiState) {
        when (action) {
            FormAction.Refresh -> if (!prevState.isRefreshing && !prevState.isAwaitingSnapshot) {
                if (prevState.hasReadFailure && !prevState.isReadingCache) {
                    updateState { copy(isReadingCache = true) }
                    observeSavedContent()
                }
                updateState { copy(isRefreshing = true, refreshFailure = null) }
                viewModelScope.launch {
                    val failure = when (val result = refreshForm()) {
                        RefreshFormResult.Success -> null
                        is RefreshFormResult.Failure -> result.reason.toUiModel()
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
                updateState {
                    val attempt = ChoiceWriteAttemptUiModel(lastChoiceAttemptId + 1, action.option)
                    if (hasReadFailure) copy(
                        lastChoiceAttemptId = attempt.id,
                        choiceWriteFailure = ChoiceWriteFailureUi(
                            attempt.id, attempt.option.questionId, ChoiceWriteFailureReasonUi.READ_UNAVAILABLE
                        )
                    ) else copy(lastChoiceAttemptId = attempt.id, pendingChoiceWrites = pendingChoiceWrites + attempt)
                }
                if (prevState.pendingChoiceWrites.isEmpty()) writeNextChoice()
            }
            is FormAction.ChoiceWriteFinished -> if (prevState.pendingChoiceWrites.firstOrNull()?.id == action.attemptId) {
                updateState {
                    val previous = choiceWriteFailure
                    copy(
                        pendingChoiceWrites = pendingChoiceWrites.drop(1),
                        choiceWriteFailure = when {
                            previous != null && previous.id > action.attemptId -> previous
                            action.failure != null -> ChoiceWriteFailureUi(action.attemptId, action.questionId, action.failure)
                            previous?.questionId == action.questionId && previous.id <= action.attemptId -> null
                            else -> previous
                        }
                    )
                }
                writeNextChoice()
            }
            is FormAction.DismissChoiceFailure -> updateState {
                if (choiceWriteFailure?.id == action.id) copy(choiceWriteFailure = null) else this
            }
        }
    }

    private fun observeSavedContent() {
        observeForm().onEach { result ->
            when (result) {
                is FormObservation.Snapshot -> {
                    val previous = viewState.value.content
                    val content = withContext(mappingDispatcher) { result.value?.toUiModel(previous) }
                    sendAction(FormAction.ContentObserved(content))
                    // The next snapshot must project from the state accepted by the action handler.
                    viewState.first { it.content === content }
                }

                FormObservation.ReadFailure -> sendAction(FormAction.ReadFailed)
            }
        }.takeWhile { it != FormObservation.ReadFailure }.launchIn(viewModelScope)
    }

    private fun writeNextChoice() {
        val (id, option) = viewState.value.pendingChoiceWrites.firstOrNull() ?: return
        // The queue head stays in state until its write finishes.
        viewModelScope.launch {
            val failure = when (val result = toggleChoiceOption(option.toDomain())) {
                ChoiceSelectionResult.Success -> null
                is ChoiceSelectionResult.Failure -> result.reason.toUiModel()
            }
            sendAction(FormAction.ChoiceWriteFinished(id, option.questionId, failure))
        }
    }
}
