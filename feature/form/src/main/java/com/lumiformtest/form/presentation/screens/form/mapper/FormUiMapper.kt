package com.lumiformtest.form.presentation.screens.form.mapper

import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.persistentHashMapOf
import com.lumiformtest.form.presentation.screens.form.model.ChoiceSelectionUiModel
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionFailure
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormRefreshFailure
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.ResponseSet
import com.lumiformtest.form.presentation.screens.form.model.ChoiceOptionUiInput
import com.lumiformtest.form.presentation.screens.form.model.ChoiceWriteFailureReasonUi
import com.lumiformtest.form.presentation.screens.form.model.FormContentUiModel
import com.lumiformtest.form.presentation.screens.form.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel
import com.lumiformtest.form.presentation.screens.form.model.ResponseOptionUiModel
import com.lumiformtest.form.presentation.screens.form.model.ResponseSetUiModel

internal fun FormSnapshot.toUiModel(previous: FormContentUiModel? = null): FormContentUiModel {
    if (previous?.definitionVersion == definitionVersion) return updateAnswers(previous)
    val previousRows = previous?.rows?.associateBy { it.id }.orEmpty()
    val rows = buildList {
        fun addItem(item: FormItem, parentId: Long, depth: Int) {
            val old = previousRows[item.id]
            val samePosition = old?.parentId == parentId && old.depth == depth
            // Check the current projection before allocating another row or option list.
            add(when (item) {
                is FormItem.Section -> (old as? FormRowUiModel.Section)
                    ?.takeIf { samePosition && it.title == item.title }
                    ?: FormRowUiModel.Section(item.id, parentId, depth, item.title)
                is FormItem.Text -> (old as? FormRowUiModel.Text)
                    ?.takeIf { samePosition && it.text == item.text }
                    ?: FormRowUiModel.Text(item.id, parentId, depth, item.text)
                is FormItem.Image -> (old as? FormRowUiModel.Image)
                    ?.takeIf { samePosition && it.title == item.title && it.imageUrl == item.imageUrl }
                    ?: FormRowUiModel.Image(item.id, parentId, depth, item.title, item.imageUrl)
                is FormItem.Choice -> {
                    val oldChoice = old as? FormRowUiModel.Choice
                    val responseSet = item.responseSet.toUiModel(oldChoice?.responseSet)
                    val selected = selections[item.id]?.takeIf { it.responseSetId == responseSet.id }?.optionIds.orEmpty()
                    oldChoice?.takeIf {
                        samePosition && it.text == item.text && it.responseSet === responseSet && it.selectedOptionIds == selected
                    } ?: FormRowUiModel.Choice(item.id, parentId, depth, item.text, responseSet, selected)
                }
            })
            if (item is FormItem.Section) item.items.forEach { addItem(it, item.id, depth + 1) }
        }
        document.pages.forEach { page ->
            add((previousRows[page.id] as? FormRowUiModel.Page)?.takeIf { it.title == page.title }
                ?: FormRowUiModel.Page(page.id, page.title))
            page.items.forEach { addItem(it, page.id, 1) }
        }
    }
    val retainedRows = previous?.rows?.takeIf { old ->
        old.size == rows.size && old.indices.all { old[it] === rows[it] }
    } ?: rows
    val answerModels = persistentHashMapOf<Long, ChoiceSelectionUiModel>().builder()
    for (row in retainedRows) if (row is FormRowUiModel.Choice && row.selectedOptionIds.isNotEmpty()) {
        answerModels[row.id] = ChoiceSelectionUiModel(row.responseSet.id, row.selectedOptionIds)
    }
    return FormContentUiModel(retainedRows.toPersistentList(), lastUpdated.toEpochMilli(), definitionVersion, answers = answerModels.build())
}

private fun FormSnapshot.updateAnswers(previous: FormContentUiModel): FormContentUiModel {
    val rows = previous.rows.builder()
    val answers = previous.answers.builder()
    fun update(questionId: Long, optionIds: Set<Long>) {
        val index = previous.rowIndexById[questionId] ?: return
        val row = rows[index] as? FormRowUiModel.Choice ?: return
        if (row.selectedOptionIds == optionIds) return
        rows[index] = row.copy(selectedOptionIds = optionIds)
        if (optionIds.isEmpty()) answers.remove(questionId)
        else answers[questionId] = ChoiceSelectionUiModel(row.responseSet.id, optionIds)
    }
    // This path reads answers and indexed choice rows only, never the document tree or option definitions.
    for ((questionId, selection) in selections) {
        val current = previous.answers[questionId]
        if (current?.responseSetId == selection.responseSetId && current.optionIds == selection.optionIds) continue
        val index = previous.rowIndexById[questionId] ?: continue
        val row = rows[index] as? FormRowUiModel.Choice ?: continue
        update(questionId, if (row.responseSet.id == selection.responseSetId) selection.optionIds else emptySet())
    }
    for (questionId in previous.answers.keys) if (questionId !in selections) update(questionId, emptySet())
    val updatedRows = rows.build()
    val updatedAnswers = answers.build()
    val updatedTime = lastUpdated.toEpochMilli()
    return if (updatedRows === previous.rows && updatedAnswers === previous.answers && updatedTime == previous.lastUpdatedEpochMillis) previous
    else previous.copy(rows = updatedRows, answers = updatedAnswers, lastUpdatedEpochMillis = updatedTime)
}

private fun ResponseSet.toUiModel(previous: ResponseSetUiModel?): ResponseSetUiModel {
    val sameOptions = previous != null && previous.options.size == responses.size && responses.indices.all { index ->
        val option = responses[index]
        val old = previous.options[index]
        old.id == option.id && old.label == option.label && old.score == option.score
    }
    val options = if (sameOptions) previous.options else responses.map { ResponseOptionUiModel(it.id, it.label, it.score) }
    return previous?.takeIf { it.id == id && it.allowsMultipleSelection == allowsMultipleSelection && it.options === options }
        ?: ResponseSetUiModel(id, allowsMultipleSelection, options)
}

internal fun ChoiceOptionUiInput.toDomain(): ChoiceOption = ChoiceOption(questionId, responseSetId, optionId)

internal fun ChoiceSelectionFailure.toUiModel(): ChoiceWriteFailureReasonUi = when (this) {
    ChoiceSelectionFailure.UNAVAILABLE -> ChoiceWriteFailureReasonUi.UNAVAILABLE
    ChoiceSelectionFailure.STORAGE -> ChoiceWriteFailureReasonUi.STORAGE
}

internal fun FormRefreshFailure.toUiModel(): FormRefreshFailureUi = when (this) {
    FormRefreshFailure.FETCH -> FormRefreshFailureUi.FETCH
    FormRefreshFailure.OFFLINE -> FormRefreshFailureUi.OFFLINE
    FormRefreshFailure.INVALID_CONTENT -> FormRefreshFailureUi.INVALID_CONTENT
    FormRefreshFailure.STORAGE -> FormRefreshFailureUi.STORAGE
}
