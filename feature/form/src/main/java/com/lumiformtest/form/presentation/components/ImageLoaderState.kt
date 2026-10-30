package com.lumiformtest.form.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalFormImageLoader = compositionLocalOf<State<ImageLoader?>?> { null }

@Composable
internal fun rememberImageLoaderState(): State<ImageLoader?> {
    val context = LocalContext.current.applicationContext
    return produceState<ImageLoader?>(null, context) {
        // The first image should not build Coil and its dependencies on the UI thread.
        value = withContext(Dispatchers.IO) { SingletonImageLoader.get(context) }
    }
}

@Composable
internal fun rememberFormImageLoader(provided: ImageLoader?): ImageLoader? {
    if (provided != null) return provided
    val shared = LocalFormImageLoader.current
    return (shared ?: rememberImageLoaderState()).value
}
