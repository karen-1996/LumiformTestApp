package com.lumiformtest.form.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiformtest.form.presentation.viewmodel.ImageAction
import com.lumiformtest.form.presentation.viewmodel.ImageViewModel
import com.lumiformtest.form.presentation.components.LocalFormImageLoader
import com.lumiformtest.form.presentation.navigation.FormImageTransitionState

@Composable
internal fun ImageRoute(imageId: Long, onBack: () -> Unit, imageTransitionState: FormImageTransitionState, viewModel: ImageViewModel = hiltViewModel()) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, imageId) { viewModel.sendAction(ImageAction.Initialize(imageId)) }
    val preview = imageTransitionState.preview?.takeIf { it.image.id == imageId }
    val visibleState = if (state.isLoading && !state.hasReadFailure && preview != null) state.copy(image = preview.image) else state
    val loader = rememberUpdatedState(preview?.loader)
    CompositionLocalProvider(LocalFormImageLoader provides loader.takeIf { it.value != null }) {
        ImageScreen(visibleState, onBack, onRetry = { viewModel.sendAction(ImageAction.Retry) })
    }
}
