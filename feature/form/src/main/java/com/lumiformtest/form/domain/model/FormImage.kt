package com.lumiformtest.form.domain.model

internal data class FormImage(val id: Long, val title: String, val url: String)

internal sealed interface ImageObservation {
    data class Content(val image: FormImage?) : ImageObservation
    data object ReadFailure : ImageObservation
}
