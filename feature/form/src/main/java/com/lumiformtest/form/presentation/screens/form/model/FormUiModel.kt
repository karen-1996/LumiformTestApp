package com.lumiformtest.form.presentation.screens.form.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf

internal data class FormContentUiModel(
    val rows: PersistentList<FormRowUiModel>,
    val lastUpdatedEpochMillis: Long,
    val definitionVersion: Long? = null,
    val rowIndexById: Map<Long, Int> = rows.withIndex().associate { it.value.id to it.index },
    val answers: PersistentMap<Long, ChoiceSelectionUiModel> = persistentHashMapOf(),
    val hasImages: Boolean = rows.any { it is FormRowUiModel.Image }
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FormContentUiModel) return false
        // A changed answer or definition must not trigger a comparison of every row.
        return definitionVersion == other.definitionVersion && lastUpdatedEpochMillis == other.lastUpdatedEpochMillis &&
            hasImages == other.hasImages && answers == other.answers && rowIndexById == other.rowIndexById && rows == other.rows
    }
}

internal sealed interface FormRowUiModel {
    val id: Long
    val parentId: Long?
    val depth: Int

    data class Page(override val id: Long, val title: String) : FormRowUiModel {
        override val parentId: Long? = null
        override val depth: Int = 0
    }

    data class Section(
        override val id: Long,
        override val parentId: Long,
        override val depth: Int,
        val title: String
    ) : FormRowUiModel

    data class Text(
        override val id: Long,
        override val parentId: Long,
        override val depth: Int,
        val text: String
    ) : FormRowUiModel

    data class Image(
        override val id: Long,
        override val parentId: Long,
        override val depth: Int,
        val title: String, val imageUrl: String
    ) : FormRowUiModel

    data class Choice(
        override val id: Long,
        override val parentId: Long,
        override val depth: Int,
        val text: String, val responseSet: ResponseSetUiModel,
        val selectedOptionIds: Set<Long> = emptySet()
    ) : FormRowUiModel
}

internal data class ResponseSetUiModel(
    val id: Long,
    val allowsMultipleSelection: Boolean,
    val options: List<ResponseOptionUiModel>
)

internal data class ResponseOptionUiModel(val id: Long, val label: String, val score: Int?)

internal enum class FormRefreshFailureUi { FETCH, OFFLINE, INVALID_CONTENT, STORAGE }

internal data class ChoiceOptionUiInput(
    val questionId: Long,
    val responseSetId: Long,
    val optionId: Long
)

internal data class ChoiceWriteAttemptUiModel(val id: Long, val option: ChoiceOptionUiInput)

internal enum class ChoiceWriteFailureReasonUi { UNAVAILABLE, STORAGE, READ_UNAVAILABLE }

internal data class ChoiceWriteFailureUi(
    val id: Long,
    val questionId: Long,
    val reason: ChoiceWriteFailureReasonUi
)

internal data class ChoiceSelectionUiModel(val responseSetId: Long, val optionIds: Set<Long>)
