package com.lumiformtest.form.presentation.screens.form.preview

import kotlinx.collections.immutable.toPersistentList
import com.lumiformtest.form.presentation.screens.form.model.FormContentUiModel
import com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel
import com.lumiformtest.form.presentation.screens.form.model.ResponseOptionUiModel
import com.lumiformtest.form.presentation.screens.form.model.ResponseSetUiModel

internal object FormPreviewData {
    val hierarchyRows by lazy {
        listOf(
            FormRowUiModel.Page(1, "Page heading"),
            FormRowUiModel.Section(2, 1, 1, "Section level 1"),
            FormRowUiModel.Section(3, 2, 2, "Section level 2"),
            FormRowUiModel.Section(4, 3, 3, "Section level 3"),
            FormRowUiModel.Section(5, 4, 4, "Section level 4"),
            FormRowUiModel.Section(6, 5, 40, "Deep section at the minimum"),
            FormRowUiModel.Text(7, 6, 41, "Question text stays below section headings.")
        )
    }
    val question by lazy {
        FormRowUiModel.Choice(
            6, 5, 3, "Which areas were inspected?",
            ResponseSetUiModel(101, true, listOf(
                ResponseOptionUiModel(1, "Entrance", null),
                ResponseOptionUiModel(2, "Storage", null),
                ResponseOptionUiModel(3, "Loading bay", null)
            ))
        )
    }
    val singleChoice by lazy {
        question.copy(text = "Which area needs attention?",
            responseSet = question.responseSet.copy(allowsMultipleSelection = false, options = question.responseSet.options.map {
                it.copy(score = when (it.id) { 1L -> 1; 2L -> 2; else -> null })
            }), selectedOptionIds = setOf(1))
    }
    val multipleChoice by lazy { question.copy(selectedOptionIds = setOf(1, 3)) }
    val image by lazy { FormRowUiModel.Image(4, 2, 2, "Welcome image", "preview://image") }
    val content by lazy {
        FormContentUiModel(
            rows = listOf(
                FormRowUiModel.Page(1, "Main page"),
                FormRowUiModel.Section(2, 1, 1, "Introduction"),
                FormRowUiModel.Text(3, 2, 2, "Welcome to the main page."),
                image,
                FormRowUiModel.Section(5, 2, 2, "Inspection"),
                question
            ).toPersistentList(),
            lastUpdatedEpochMillis = 1793275200000
        )
    }
}
