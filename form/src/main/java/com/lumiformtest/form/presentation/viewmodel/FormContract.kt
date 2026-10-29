package com.lumiformtest.form.presentation.viewmodel

import com.lumiformtest.base.viewmodel.Action
import com.lumiformtest.base.viewmodel.Event
import com.lumiformtest.base.viewmodel.NavDestination
import com.lumiformtest.base.viewmodel.ViewState
import com.lumiformtest.form.presentation.model.FormContentUiModel
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi

internal data class FormUiState(
    val content: FormContentUiModel? = null,
    val isReadingCache: Boolean = true,
    val isRefreshing: Boolean = false,
    val isAwaitingSnapshot: Boolean = false,
    val hasReadFailure: Boolean = false,
    val refreshFailure: FormRefreshFailureUi? = null
) : ViewState {
    val isLoading: Boolean
        get() = content == null && !hasReadFailure &&
            (isReadingCache || isRefreshing || isAwaitingSnapshot || refreshFailure == null)
}

internal sealed interface FormAction : Action {
    data object Initialize : FormAction
    data object Refresh : FormAction
    data class ContentObserved(val content: FormContentUiModel?) : FormAction
    data object ReadFailed : FormAction
    data class RefreshFinished(val failure: FormRefreshFailureUi?) : FormAction
    data class OpenImage(val imageId: Long) : FormAction
}

internal sealed interface FormNavigationDestination : NavDestination {
    data class OpenImage(val imageId: Long) : FormNavigationDestination
}

internal sealed interface FormEvent : Event
