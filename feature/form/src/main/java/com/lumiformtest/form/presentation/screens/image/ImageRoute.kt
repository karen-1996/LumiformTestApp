package com.lumiformtest.form.presentation.screens.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiformtest.form.presentation.screens.image.viewmodel.ImageAction
import com.lumiformtest.form.presentation.screens.image.viewmodel.ImageViewModel
import com.lumiformtest.form.presentation.components.LocalFormImageLoader
import com.lumiformtest.form.presentation.navigation.FormImageTransitionState

@Composable
internal fun ImageRoute(
    imageId: Long,
    onBack: () -> Unit,
    imageTransitionState: FormImageTransitionState,
    viewModel: ImageViewModel = hiltViewModel<ImageViewModel, ImageViewModel.Factory>(
        creationCallback = { factory -> factory.create(imageId) }
    )
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val preview = imageTransitionState.preview?.takeIf { it.image.id == imageId }
    val visibleState = if (state.isLoading && !state.hasReadFailure && preview != null) state.copy(image = preview.image) else state
    val loader = rememberUpdatedState(preview?.loader)
    CompositionLocalProvider(LocalFormImageLoader provides loader.takeIf { it.value != null }) {
        ImageScreen(visibleState, onBack, onRetry = { viewModel.sendAction(ImageAction.Retry) })
    }
}
