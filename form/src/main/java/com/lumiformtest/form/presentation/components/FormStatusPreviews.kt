package com.lumiformtest.form.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.preview.FormPreviewData
import com.lumiformtest.form.presentation.preview.FormPreviewTheme
import com.lumiformtest.form.presentation.viewmodel.FormUiState

@Preview(name = "Saved status", showBackground = true)
@Composable
private fun SavedStatusPreview() = PreviewStatus(FormUiState(content = FormPreviewData.content, isReadingCache = false))

@Preview(name = "Refreshing status", showBackground = true)
@Composable
private fun RefreshingStatusPreview() = PreviewStatus(FormUiState(content = FormPreviewData.content, isReadingCache = false, isRefreshing = true))

@Preview(name = "Refresh failed · dark", showBackground = true)
@Composable
private fun FailedRefreshStatusPreview() = PreviewStatus(FormUiState(
    content = FormPreviewData.content, isReadingCache = false, refreshFailure = FormRefreshFailureUi.FETCH
), darkTheme = true)

@Preview(name = "Saved form · offline", showBackground = true)
@Composable
private fun OfflineStatusPreview() = PreviewStatus(FormUiState(
    content = FormPreviewData.content, isReadingCache = false, refreshFailure = FormRefreshFailureUi.OFFLINE
))

@Preview(name = "Saved read failed", showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun FailedReadStatusPreview() = PreviewStatus(FormUiState(
    content = FormPreviewData.content, isReadingCache = false, hasReadFailure = true
))

@Composable
private fun PreviewStatus(state: FormUiState, darkTheme: Boolean = false) {
    FormPreviewTheme(darkTheme = darkTheme) {
        Surface {
            Box(Modifier.width(320.dp).padding(16.dp)) { FormStatus(state) }
        }
    }
}
