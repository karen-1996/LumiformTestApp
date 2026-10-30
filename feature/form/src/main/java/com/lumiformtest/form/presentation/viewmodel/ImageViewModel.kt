package com.lumiformtest.form.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.lumiformtest.base.viewmodel.BaseCoroutineViewModel
import com.lumiformtest.form.domain.model.ImageObservation
import com.lumiformtest.form.domain.usecase.ObserveImageUseCase
import com.lumiformtest.form.presentation.mapper.ImageUiMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
internal class ImageViewModel @Inject constructor(
    private val observeImage: ObserveImageUseCase,
    private val mapper: ImageUiMapper
) : BaseCoroutineViewModel<ImageAction, ImageUiState, ImageNavigationDestination, ImageEvent>(ImageUiState()) {
    private var imageId: Long? = null
    private var observation: Job? = null

    override fun onAction(action: ImageAction, prevState: ImageUiState) {
        when (action) {
            is ImageAction.Initialize -> if (imageId == null) {
                imageId = action.imageId
                observeSavedImage(action.imageId)
            }
            is ImageAction.ImageObserved -> updateState { ImageUiState(image = action.image, isLoading = false) }
            ImageAction.ReadFailed -> updateState { copy(isLoading = false, hasReadFailure = true) }
            ImageAction.Retry -> if (prevState.hasReadFailure && !prevState.isLoading) {
                imageId?.let(::observeSavedImage)
            }
        }
    }

    private fun observeSavedImage(id: Long) {
        observation?.cancel()
        updateState { copy(isLoading = true) }
        observation = viewModelScope.launch {
            observeImage(id).collect { result ->
                sendAction(when (result) {
                    is ImageObservation.Content -> ImageAction.ImageObserved(result.image?.let(mapper::map))
                    ImageObservation.ReadFailure -> ImageAction.ReadFailed
                })
            }
        }
    }
}
