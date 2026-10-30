package com.lumiformtest.form.presentation.viewmodel

import com.lumiformtest.base.viewmodel.Action
import com.lumiformtest.base.viewmodel.Event
import com.lumiformtest.base.viewmodel.NavDestination
import com.lumiformtest.base.viewmodel.ViewState
import com.lumiformtest.form.presentation.model.ImageUiModel

internal data class ImageUiState(
    val image: ImageUiModel? = null,
    val isLoading: Boolean = true,
    val hasReadFailure: Boolean = false
) : ViewState

internal sealed interface ImageAction : Action {
    data class Initialize(val imageId: Long) : ImageAction
    data class ImageObserved(val image: ImageUiModel?) : ImageAction
    data object ReadFailed : ImageAction
    data object Retry : ImageAction
}

internal sealed interface ImageNavigationDestination : NavDestination
internal sealed interface ImageEvent : Event
