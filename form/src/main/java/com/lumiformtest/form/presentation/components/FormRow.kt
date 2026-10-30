package com.lumiformtest.form.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.model.ChoiceOptionUiInput
import com.lumiformtest.resources.R

@Composable
internal fun FormRow(
    row: FormRowUiModel,
    choicesEnabled: Boolean,
    onImageClick: (Long) -> Unit,
    onChoiceClick: (ChoiceOptionUiInput) -> Unit
) {
    // Deep sections should still leave enough room for text on a small screen.
    val indentation = (row.depth - 1).coerceIn(0, 4) * 12
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = indentation.dp).testTag("form_row_${row.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (row) {
            is FormRowUiModel.Page -> Text(
                row.title, modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = colorResource(R.color.form_page_heading)
            )
            is FormRowUiModel.Section -> Text(
                row.title, modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                style = if (row.depth == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                color = colorResource(R.color.form_section_heading)
            )
            is FormRowUiModel.Text -> Text(row.text, style = MaterialTheme.typography.bodyMedium)
            is FormRowUiModel.Image -> {
                Text(row.title, style = MaterialTheme.typography.bodyMedium)
                val openLabel = stringResource(R.string.image_open, row.title)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    FormImage(
                        url = row.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        isThumbnail = true,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                            .clickable(role = Role.Button, onClickLabel = openLabel) { onImageClick(row.id) }
                            .semantics { contentDescription = openLabel }.testTag("image_thumbnail_${row.id}")
                    )
                }
            }
            is FormRowUiModel.Choice -> ChoiceQuestion(row, choicesEnabled, onChoiceClick)
        }
    }
}
