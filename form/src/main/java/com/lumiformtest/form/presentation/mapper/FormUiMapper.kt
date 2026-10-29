package com.lumiformtest.form.presentation.mapper

import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormRefreshFailure
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.presentation.model.FormContentUiModel
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.model.ResponseOptionUiModel
import com.lumiformtest.form.presentation.model.ResponseSetUiModel
import javax.inject.Inject

internal class FormUiMapper @Inject constructor() {
    fun map(snapshot: FormSnapshot): FormContentUiModel {
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
            snapshot.document.pages.forEach { page ->
                add(FormRowUiModel.Page(page.id, page.title))
                page.items.forEach { addItem(it, page.id, 1) }
            }
        }
        return FormContentUiModel(rows, snapshot.lastUpdated.toEpochMilli())
    }

    fun map(failure: FormRefreshFailure): FormRefreshFailureUi = when (failure) {
        FormRefreshFailure.FETCH -> FormRefreshFailureUi.FETCH
        FormRefreshFailure.INVALID_CONTENT -> FormRefreshFailureUi.INVALID_CONTENT
        FormRefreshFailure.STORAGE -> FormRefreshFailureUi.STORAGE
    }
}
