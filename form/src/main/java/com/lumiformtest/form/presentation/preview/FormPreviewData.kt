package com.lumiformtest.form.presentation.preview

import com.lumiformtest.form.presentation.model.FormContentUiModel
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.model.ResponseOptionUiModel
import com.lumiformtest.form.presentation.model.ResponseSetUiModel

internal object FormPreviewData {
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
            responseSet = question.responseSet.copy(allowsMultipleSelection = false), selectedOptionIds = setOf(1))
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
            ),
            lastUpdatedEpochMillis = 1793275200000
        )
    }
}
