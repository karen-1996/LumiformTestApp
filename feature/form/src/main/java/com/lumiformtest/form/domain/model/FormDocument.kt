package com.lumiformtest.form.domain.model

internal data class FormDocument(val pages: List<FormPage>)

internal data class FormPage(
    val id: Long,
    val title: String,
    val items: List<FormItem>
)

internal sealed interface FormItem {
    val id: Long

    data class Section(
        override val id: Long,
        val title: String,
        val items: List<FormItem>
    ) : FormItem

    data class Text(
        override val id: Long,
        val text: String
    ) : FormItem

    data class Image(
        override val id: Long,
        val imageUrl: String,
        val title: String
    ) : FormItem

    data class Choice(
        override val id: Long,
        val text: String,
        val responseSet: ResponseSet
    ) : FormItem
}

internal data class ResponseSet(
    val id: Long,
    val allowsMultipleSelection: Boolean,
    val responses: List<ResponseOption>
)

internal data class ResponseOption(
    val id: Long,
    val label: String,
    val score: Int?
)
