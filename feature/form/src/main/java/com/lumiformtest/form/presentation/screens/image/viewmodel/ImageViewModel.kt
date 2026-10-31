package com.lumiformtest.form.presentation.screens.image.viewmodel

import androidx.lifecycle.viewModelScope
import com.lumiformtest.base.viewmodel.BaseCoroutineViewModel
import com.lumiformtest.form.domain.model.ImageObservation
import com.lumiformtest.form.domain.usecase.ObserveImageUseCase
import com.lumiformtest.form.presentation.screens.image.mapper.toUiModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile

@HiltViewModel(assistedFactory = ImageViewModel.Factory::class)
internal class ImageViewModel @AssistedInject constructor(
    private val observeImage: ObserveImageUseCase,
    @Assisted imageId: Long
) : BaseCoroutineViewModel<ImageAction, ImageUiState, ImageNavigationDestination, ImageEvent>(
    initialState = ImageUiState(imageId = imageId)
) {
    init {
        observeSavedImage(imageId)
    }

    override fun onAction(action: ImageAction, prevState: ImageUiState) {
        when (action) {
            is ImageAction.ImageObserved -> updateState {
                copy(image = action.image, isLoading = false, hasReadFailure = false)
            }
            ImageAction.ReadFailed -> updateState { copy(isLoading = false, hasReadFailure = true) }
            ImageAction.Retry -> if (prevState.hasReadFailure && !prevState.isLoading) {
                prevState.imageId?.let(::observeSavedImage)
            }
        }
    }

    private fun observeSavedImage(id: Long) {
        updateState { copy(isLoading = true) }
        observeImage(id).onEach { result ->
            sendAction(when (result) {
                is ImageObservation.Content -> ImageAction.ImageObserved(result.image?.toUiModel())
                ImageObservation.ReadFailure -> ImageAction.ReadFailed
            })
        }.takeWhile { it != ImageObservation.ReadFailure }.launchIn(viewModelScope)
    }

    @AssistedFactory
    interface Factory {
        fun create(imageId: Long): ImageViewModel
    }
}
