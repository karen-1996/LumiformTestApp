package com.lumiformtest.form.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.resources.R

@Composable
internal fun FormRow(row: FormRowUiModel) {
    // Deep sections should still leave enough room for text on a small screen.
    val indentation = (row.depth - 1).coerceIn(0, 4) * 12
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = indentation.dp).testTag("form_row_${row.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (row) {
            is FormRowUiModel.Page -> Text(
                row.title, modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                style = MaterialTheme.typography.headlineMedium
            )
            is FormRowUiModel.Section -> Text(
                row.title, modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                style = if (row.depth == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
            )
            is FormRowUiModel.Text -> Text(row.text, style = MaterialTheme.typography.bodyMedium)
            is FormRowUiModel.Image -> {
                Text(row.title, style = MaterialTheme.typography.bodyMedium)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(
                        stringResource(R.string.image_unavailable),
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is FormRowUiModel.Choice -> {
                Text(row.text, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(
                        if (row.responseSet.allowsMultipleSelection) R.string.form_multiple_choice
                        else R.string.form_single_choice
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                row.responseSet.options.forEach { option ->
                    Text(stringResource(R.string.form_response_option, option.label), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
