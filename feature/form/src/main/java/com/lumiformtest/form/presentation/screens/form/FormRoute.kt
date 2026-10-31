package com.lumiformtest.form.presentation.screens.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lumiformtest.form.presentation.components.LocalFormImageLoader
import com.lumiformtest.form.presentation.components.rememberImageLoaderState
import com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel
import com.lumiformtest.form.presentation.navigation.FormImageTransitionState
import com.lumiformtest.form.presentation.screens.form.viewmodel.FormNavigationDestination
import com.lumiformtest.form.presentation.screens.form.viewmodel.FormViewModel

@Composable
internal fun FormRoute(
    onOpenImage: (Long) -> Unit,
    imageTransitionState: FormImageTransitionState,
    viewModel: FormViewModel = hiltViewModel()
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val openImage by rememberUpdatedState(onOpenImage)
    val latestContent by rememberUpdatedState(state.content)
    val preparedLoader = imageTransitionState.preview?.loader
    val loader = if (state.content?.hasImages == true) {
        if (preparedLoader != null) rememberUpdatedState(preparedLoader) else rememberImageLoaderState()
    } else null
    val latestLoader by rememberUpdatedState(loader)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.navigateFlow.collect { destination ->
                when (destination) {
                    is FormNavigationDestination.OpenImage -> {
                        val content = latestContent
                        val row = content?.rowIndexById?.get(destination.imageId)
                            ?.let { content.rows[it] } as? FormRowUiModel.Image
                        imageTransitionState.prepare(row, latestLoader?.value)
                        openImage(destination.imageId)
                    }
                }
            }
        }
    }

    // Keep preparation owned by the entry, so returning rows already have their loader.
    CompositionLocalProvider(LocalFormImageLoader provides loader) {
        FormScreen(state = state, onAction = { viewModel.sendAction(it) })
    }
}
