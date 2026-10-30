package com.lumiformtest.form.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface FormDestination : NavKey {
    @Serializable
    data object Form : FormDestination

    // I pass only the ID. The viewer reads the image data from local storage.
    @Serializable
    data class Image(val imageId: Long) : FormDestination
}
