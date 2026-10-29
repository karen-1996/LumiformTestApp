package com.lumiformtest.form.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface FormNodeDto {
    val id: Long

    @Serializable
    @SerialName("page")
    data class Page(
        override val id: Long,
        val title: String,
        val items: List<FormNodeDto>
    ) : FormNodeDto

    @Serializable
    @SerialName("section")
    data class Section(
        override val id: Long,
        val title: String,
        val items: List<FormNodeDto>
    ) : FormNodeDto

    @Serializable
    @SerialName("text")
    data class Text(
        override val id: Long,
        val content: String
    ) : FormNodeDto

    @Serializable
    @SerialName("image")
    data class Image(
        override val id: Long,
        val src: String,
        val title: String
    ) : FormNodeDto

    @Serializable
    @SerialName("choice")
    data class Choice(
        override val id: Long,
        val content: String,
        @SerialName("response_set") val responseSet: ResponseSetDto
    ) : FormNodeDto
}

@Serializable
internal data class ResponseSetDto(
    val id: Long,
    @SerialName("multiple_selection") val multipleSelection: Boolean,
    val responses: List<ResponseOptionDto>
)

@Serializable
internal data class ResponseOptionDto(
    val id: Long,
    val label: String,
    val score: Int? = null
)
