package com.lumiformtest.form.presentation.viewmodel

import com.lumiformtest.base.viewmodel.BaseCoroutineViewModel
import androidx.lifecycle.viewModelScope
import com.lumiformtest.form.di.FormDefaultDispatcher
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.usecase.ObserveFormUseCase
import com.lumiformtest.form.domain.usecase.RefreshFormUseCase
import com.lumiformtest.form.presentation.mapper.FormUiMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
internal class FormViewModel @Inject constructor(
    private val observeForm: ObserveFormUseCase,
    private val refreshForm: RefreshFormUseCase,
    private val mapper: FormUiMapper,
    @FormDefaultDispatcher private val mappingDispatcher: CoroutineDispatcher
) : BaseCoroutineViewModel<FormAction, FormUiState, FormNavigationDestination, FormEvent>(
    initialState = FormUiState()
) {
    private var initialized = false
    private var observation: Job? = null

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
        }
    }

    private fun observeSavedContent() {
        observation?.cancel()
        observation = viewModelScope.launch {
            observeForm().collect { result ->
                when (result) {
                    is FormObservation.Snapshot -> {
                        val content =
                            withContext(mappingDispatcher) { result.value?.let(mapper::map) }
                        sendAction(FormAction.ContentObserved(content))
                    }

                    FormObservation.ReadFailure -> sendAction(FormAction.ReadFailed)
                }
            }
        }
    }
}
