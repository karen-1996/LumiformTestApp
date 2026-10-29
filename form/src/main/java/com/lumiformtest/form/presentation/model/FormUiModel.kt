package com.lumiformtest.form.presentation.model

internal data class FormContentUiModel(
    val rows: List<FormRowUiModel>,
    val lastUpdatedEpochMillis: Long
)

internal sealed interface FormRowUiModel {
    val id: Long
    val parentId: Long?
    val depth: Int

    data class Page(override val id: Long, val title: String) : FormRowUiModel {
        override val parentId: Long? = null
        override val depth: Int = 0
    }

    data class Section(
        override val id: Long, override val parentId: Long, override val depth: Int, val title: String
    ) : FormRowUiModel

    data class Text(
        override val id: Long, override val parentId: Long, override val depth: Int, val text: String
    ) : FormRowUiModel

    data class Image(
        override val id: Long, override val parentId: Long, override val depth: Int,
        val title: String, val imageUrl: String
    ) : FormRowUiModel

    data class Choice(
        override val id: Long, override val parentId: Long, override val depth: Int,
        val text: String, val responseSet: ResponseSetUiModel
    ) : FormRowUiModel
}

internal data class ResponseSetUiModel(
    val id: Long,
    val allowsMultipleSelection: Boolean,
    val options: List<ResponseOptionUiModel>
)

internal data class ResponseOptionUiModel(val id: Long, val label: String, val score: Int?)

internal enum class FormRefreshFailureUi { FETCH, INVALID_CONTENT, STORAGE }
