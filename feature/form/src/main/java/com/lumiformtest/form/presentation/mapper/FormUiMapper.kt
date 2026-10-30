package com.lumiformtest.form.presentation.mapper

import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionFailure
import com.lumiformtest.form.domain.model.FormRefreshFailure
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.presentation.model.FormContentUiModel
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.model.ResponseOptionUiModel
import com.lumiformtest.form.presentation.model.ResponseSetUiModel
import com.lumiformtest.form.presentation.model.ChoiceOptionUiInput
import com.lumiformtest.form.presentation.model.ChoiceWriteFailureReasonUi
import javax.inject.Inject

internal class FormUiMapper @Inject constructor() {
    fun map(snapshot: FormSnapshot): FormContentUiModel = applySelections(mapStructure(snapshot.document), snapshot)

    fun mapStructure(document: FormDocument): List<FormRowUiModel> {
        val rows = buildList {
            fun addItem(item: FormItem, parentId: Long, depth: Int) {
                add(when (item) {
                    is FormItem.Section -> FormRowUiModel.Section(item.id, parentId, depth, item.title)
                    is FormItem.Text -> FormRowUiModel.Text(item.id, parentId, depth, item.text)
                    is FormItem.Image -> FormRowUiModel.Image(item.id, parentId, depth, item.title, item.imageUrl)
                    is FormItem.Choice -> FormRowUiModel.Choice(
                        item.id, parentId, depth, item.text,
                        ResponseSetUiModel(
                            item.responseSet.id, item.responseSet.allowsMultipleSelection,
                            item.responseSet.responses.map { ResponseOptionUiModel(it.id, it.label, it.score) }
                        )
                    )
                })
                if (item is FormItem.Section) item.items.forEach { addItem(it, item.id, depth + 1) }
            }
            document.pages.forEach { page ->
                add(FormRowUiModel.Page(page.id, page.title))
                page.items.forEach { addItem(it, page.id, 1) }
            }
        }
        return rows
    }

    fun applySelections(rows: List<FormRowUiModel>, snapshot: FormSnapshot): FormContentUiModel = FormContentUiModel(
        rows.map { row ->
            if (row !is FormRowUiModel.Choice) row else {
                val selection = snapshot.selections[row.id]?.takeIf { it.responseSetId == row.responseSet.id }
                val selected = selection?.optionIds.orEmpty()
                if (selected == row.selectedOptionIds) row else row.copy(selectedOptionIds = selected)
            }
        },
        snapshot.lastUpdated.toEpochMilli()
    )

    fun map(option: ChoiceOptionUiInput): ChoiceOption = ChoiceOption(option.questionId, option.responseSetId, option.optionId)

    fun map(failure: ChoiceSelectionFailure): ChoiceWriteFailureReasonUi = when (failure) {
        ChoiceSelectionFailure.UNAVAILABLE -> ChoiceWriteFailureReasonUi.UNAVAILABLE
        ChoiceSelectionFailure.STORAGE -> ChoiceWriteFailureReasonUi.STORAGE
    }

    fun map(failure: FormRefreshFailure): FormRefreshFailureUi = when (failure) {
        FormRefreshFailure.FETCH -> FormRefreshFailureUi.FETCH
        FormRefreshFailure.OFFLINE -> FormRefreshFailureUi.OFFLINE
        FormRefreshFailure.INVALID_CONTENT -> FormRefreshFailureUi.INVALID_CONTENT
        FormRefreshFailure.STORAGE -> FormRefreshFailureUi.STORAGE
    }
}
