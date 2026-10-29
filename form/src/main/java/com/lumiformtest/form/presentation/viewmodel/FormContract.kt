package com.lumiformtest.form.presentation.viewmodel

import com.lumiformtest.base.viewmodel.Action
import com.lumiformtest.base.viewmodel.Event
import com.lumiformtest.base.viewmodel.NavDestination
import com.lumiformtest.base.viewmodel.ViewState

internal data class FormUiState(val isLoading: Boolean = false) : ViewState

internal sealed interface FormAction : Action {
    data class OpenImage(val imageId: Long) : FormAction
}

internal sealed interface FormNavigationDestination : NavDestination {
    data class OpenImage(val imageId: Long) : FormNavigationDestination
}

internal sealed interface FormEvent : Event
