package com.lumiformtest.form.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.Image
import coil3.ImageLoader
import coil3.memory.MemoryCache
import com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel
import com.lumiformtest.form.presentation.screens.image.model.ImageUiModel

class FormImageTransitionState internal constructor() {
    internal var preview: ImageTransitionPreview? by mutableStateOf(null)
        private set

    internal fun prepare(row: FormRowUiModel.Image?, loader: ImageLoader?) {
        // This is only a visual handoff. Room remains the viewer's source of truth.
        preview = row?.let {
            ImageTransitionPreview(
                ImageUiModel(it.id, it.title, it.imageUrl), loader,
                loader?.memoryCache?.get(MemoryCache.Key("thumbnail:${it.imageUrl}"))?.image
            )
        }
    }
}

internal data class ImageTransitionPreview(val image: ImageUiModel, val loader: ImageLoader?, val thumbnail: Image?)

@Composable
fun rememberFormImageTransitionState(): FormImageTransitionState = remember { FormImageTransitionState() }
