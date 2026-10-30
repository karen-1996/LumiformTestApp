package com.lumiformtest.form.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumiformtest.form.presentation.viewmodel.ImageAction
import com.lumiformtest.form.presentation.viewmodel.ImageViewModel

@Composable
internal fun ImageRoute(imageId: Long, onBack: () -> Unit, viewModel: ImageViewModel = hiltViewModel()) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, imageId) { viewModel.sendAction(ImageAction.Initialize(imageId)) }
    ImageScreen(state, onBack, onRetry = { viewModel.sendAction(ImageAction.Retry) })
}
