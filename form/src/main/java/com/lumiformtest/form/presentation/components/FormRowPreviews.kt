package com.lumiformtest.form.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.preview.FormPreviewData
import com.lumiformtest.form.presentation.preview.FormPreviewTheme

@Preview(name = "Page", showBackground = true)
@Composable
private fun PageRowPreview() = PreviewRow(FormRowUiModel.Page(1, "Main page"))

@Preview(name = "Page · dark", showBackground = true)
@Composable
private fun DarkPageRowPreview() = PreviewRow(FormRowUiModel.Page(1, "Main page"), darkTheme = true)

@Preview(name = "Nested section", showBackground = true)
@Composable
private fun SectionRowPreview() = PreviewRow(FormRowUiModel.Section(2, 1, 3, "Safety inspection"))

@Preview(name = "Nested section · dark", showBackground = true)
@Composable
private fun DarkSectionRowPreview() = PreviewRow(FormRowUiModel.Section(2, 1, 3, "Safety inspection"), darkTheme = true)

@Preview(name = "Text", showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun TextRowPreview() = PreviewRow(FormRowUiModel.Text(3, 2, 2, "Check the equipment before starting the inspection."))

@Preview(name = "Image", showBackground = true)
@Composable
private fun ImageRowPreview() = PreviewRow(FormPreviewData.image)

@Preview(name = "Single choice", showBackground = true)
@Composable
private fun SingleChoiceRowPreview() = PreviewRow(FormPreviewData.singleChoice)

@Preview(name = "Multiple choice · dark", showBackground = true)
@Composable
private fun MultipleChoiceRowPreview() = PreviewRow(FormPreviewData.multipleChoice, darkTheme = true)

@Composable
private fun PreviewRow(row: FormRowUiModel, darkTheme: Boolean = false) {
    FormPreviewTheme(darkTheme = darkTheme) {
        Surface {
            Box(Modifier.width(320.dp).padding(16.dp)) {
                FormRow(row, choicesEnabled = true, onImageClick = {}, onChoiceClick = {})
            }
        }
    }
}
