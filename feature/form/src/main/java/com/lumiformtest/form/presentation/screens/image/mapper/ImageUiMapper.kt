package com.lumiformtest.form.presentation.screens.image.mapper

import com.lumiformtest.form.domain.model.FormImage
import com.lumiformtest.form.presentation.screens.image.model.ImageUiModel

internal fun FormImage.toUiModel(): ImageUiModel = ImageUiModel(id, title, url)
