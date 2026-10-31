package com.lumiformtest.form.presentation.screens.image.mapper

import com.lumiformtest.form.domain.model.FormImage
import com.lumiformtest.form.presentation.screens.image.model.ImageUiModel
import javax.inject.Inject

internal class ImageUiMapper @Inject constructor() {
    fun map(image: FormImage): ImageUiModel = ImageUiModel(image.id, image.title, image.url)
}
