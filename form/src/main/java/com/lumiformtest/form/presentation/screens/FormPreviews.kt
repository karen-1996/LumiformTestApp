package com.lumiformtest.form.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lumiformtest.form.presentation.model.ChoiceWriteFailureReasonUi
import com.lumiformtest.form.presentation.model.ChoiceWriteFailureUi
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.preview.FormPreviewData
import com.lumiformtest.form.presentation.preview.FormPreviewTheme
import com.lumiformtest.form.presentation.viewmodel.FormUiState

@Preview(name = "Form content", showBackground = true)
@Composable
private fun ContentPreview() = PreviewForm(FormUiState(content = FormPreviewData.content, isReadingCache = false))

@Preview(name = "Form loading", showBackground = true)
@Composable
private fun LoadingPreview() = PreviewForm(FormUiState())

@Preview(name = "Empty form", showBackground = true)
@Composable
private fun EmptyPreview() = PreviewForm(FormUiState(content = FormPreviewData.content.copy(rows = emptyList()), isReadingCache = false))

@Preview(name = "Initial form failure", showBackground = true)
@Composable
private fun ErrorPreview() = PreviewForm(FormUiState(isReadingCache = false, refreshFailure = FormRefreshFailureUi.FETCH))

@Preview(name = "No internet connection", showBackground = true)
@Composable
private fun OfflinePreview() = PreviewForm(FormUiState(isReadingCache = false, refreshFailure = FormRefreshFailureUi.OFFLINE))

@Preview(name = "Cached form · failed refresh", showBackground = true)
@Composable
private fun CachedFailurePreview() = PreviewForm(FormUiState(
    content = FormPreviewData.content, isReadingCache = false, refreshFailure = FormRefreshFailureUi.FETCH
))

@Preview(name = "Single answer", showBackground = true)
@Composable
private fun SingleAnswerPreview() = PreviewForm(FormUiState(
    content = FormPreviewData.content.copy(rows = FormPreviewData.content.rows.map {
        if (it is FormRowUiModel.Choice) it.copy(responseSet = it.responseSet.copy(allowsMultipleSelection = false), selectedOptionIds = setOf(1)) else it
    }), isReadingCache = false
))

@Preview(name = "Multiple answers", showBackground = true)
@Composable
private fun MultipleAnswersPreview() = PreviewForm(FormUiState(
    content = FormPreviewData.content.copy(rows = FormPreviewData.content.rows.map {
        if (it is FormRowUiModel.Choice) it.copy(selectedOptionIds = setOf(1, 2)) else it
    }), isReadingCache = false
))

@Preview(name = "Answer save error", showBackground = true)
@Composable
private fun SaveFailurePreview() = PreviewForm(FormUiState(
    content = FormPreviewData.content, isReadingCache = false,
    choiceWriteFailure = ChoiceWriteFailureUi(1, 6, ChoiceWriteFailureReasonUi.STORAGE)
))

@Preview(name = "Deep nesting · large text", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun DeepHierarchyPreview() = PreviewForm(FormUiState(
    content = FormPreviewData.content.copy(rows = listOf(
        FormRowUiModel.Page(1, "Main page"),
        FormRowUiModel.Section(2, 1, 8, "A deeply nested section with a longer title"),
        FormRowUiModel.Text(3, 2, 9, "This text keeps its width even when the section is deeply nested.")
    )), isReadingCache = false
), darkTheme = true)

@Composable
private fun PreviewForm(state: FormUiState, darkTheme: Boolean = false) {
    FormPreviewTheme(darkTheme = darkTheme) {
        FormScreen(state, onAction = {})
    }
}

@Preview(name = "Content · dark", showBackground = true)
@Composable
private fun DarkContentPreview() = PreviewForm(
    FormUiState(content = FormPreviewData.content, isReadingCache = false), darkTheme = true
)
