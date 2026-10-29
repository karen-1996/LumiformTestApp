package com.lumiformtest.form.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.form.presentation.model.FormContentUiModel
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.model.ResponseOptionUiModel
import com.lumiformtest.form.presentation.model.ResponseSetUiModel
import com.lumiformtest.form.presentation.viewmodel.FormUiState

private val PreviewContent by lazy {
    FormContentUiModel(
        rows = listOf(
            FormRowUiModel.Page(1, "Main page"),
            FormRowUiModel.Section(2, 1, 1, "Introduction"),
            FormRowUiModel.Text(3, 2, 2, "Welcome to the main page."),
            FormRowUiModel.Image(4, 2, 2, "Welcome image", "https://example.com/image.png"),
            FormRowUiModel.Section(5, 2, 2, "Inspection"),
            FormRowUiModel.Choice(
                6, 5, 3, "Which areas were inspected?",
                ResponseSetUiModel(101, true, listOf(
                    ResponseOptionUiModel(1, "Entrance", null), ResponseOptionUiModel(2, "Storage", null)
                ))
            )
        ),
        lastUpdatedEpochMillis = 1793275200000
    )
}

@Preview(showBackground = true)
@Composable
private fun ContentPreview() = PreviewForm(FormUiState(content = PreviewContent, isReadingCache = false))

@Preview(showBackground = true)
@Composable
private fun LoadingPreview() = PreviewForm(FormUiState())

@Preview(showBackground = true)
@Composable
private fun EmptyPreview() = PreviewForm(FormUiState(content = PreviewContent.copy(rows = emptyList()), isReadingCache = false))

@Preview(showBackground = true)
@Composable
private fun ErrorPreview() = PreviewForm(FormUiState(isReadingCache = false, refreshFailure = FormRefreshFailureUi.FETCH))

@Preview(showBackground = true)
@Composable
private fun CachedFailurePreview() = PreviewForm(FormUiState(
    content = PreviewContent, isReadingCache = false, refreshFailure = FormRefreshFailureUi.FETCH
))

@Preview(showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun DeepHierarchyPreview() = PreviewForm(FormUiState(
    content = PreviewContent.copy(rows = listOf(
        FormRowUiModel.Page(1, "Main page"),
        FormRowUiModel.Section(2, 1, 8, "A deeply nested section with a longer title"),
        FormRowUiModel.Text(3, 2, 9, "This text keeps its width even when the section is deeply nested.")
    )), isReadingCache = false
), darkTheme = true)

@Composable
private fun PreviewForm(state: FormUiState, darkTheme: Boolean = false) {
    LumiformTestAppTheme(darkTheme = darkTheme, dynamicColor = false) {
        FormScreen(state, onAction = {})
    }
}
