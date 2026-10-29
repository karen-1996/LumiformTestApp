package com.lumiformtest.form.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lumiformtest.form.presentation.viewmodel.FormNavigationDestination
import com.lumiformtest.form.presentation.viewmodel.FormViewModel

@Composable
internal fun FormRoute(
    onOpenImage: (Long) -> Unit,
    viewModel: FormViewModel = hiltViewModel()
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val openImage by rememberUpdatedState(onOpenImage)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.navigateFlow.collect { destination ->
                when (destination) {
                    is FormNavigationDestination.OpenImage -> openImage(destination.imageId)
                }
            }
        }
    }

    FormScreen(state = state)
}
