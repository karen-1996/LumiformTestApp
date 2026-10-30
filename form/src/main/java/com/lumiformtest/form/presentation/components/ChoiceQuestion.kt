package com.lumiformtest.form.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lumiformtest.form.presentation.model.ChoiceOptionUiInput
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.preview.FormPreviewData
import com.lumiformtest.form.presentation.preview.FormPreviewTheme
import com.lumiformtest.resources.R

@Composable
internal fun ChoiceQuestion(question: FormRowUiModel.Choice, enabled: Boolean, onOptionClick: (ChoiceOptionUiInput) -> Unit) {
    val multiple = question.responseSet.allowsMultipleSelection
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question.text, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(if (multiple) R.string.form_multiple_choice else R.string.form_single_choice),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(if (multiple) Modifier else Modifier.selectableGroup()) {
            question.responseSet.options.forEach { option ->
                key(question.responseSet.id, option.id) {
                    val input = remember(question.id, question.responseSet.id, option.id) {
                        ChoiceOptionUiInput(question.id, question.responseSet.id, option.id)
                    }
                    ChoiceOptionRow(option.label, input, multiple, option.id in question.selectedOptionIds, enabled, onOptionClick)
                }
            }
        }
    }
}

@Composable
private fun ChoiceOptionRow(
    label: String,
    input: ChoiceOptionUiInput,
    multiple: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onOptionClick: (ChoiceOptionUiInput) -> Unit
) {
    val selection = if (multiple) Modifier.toggleable(
        value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onOptionClick(input) }
    ) else Modifier.selectable(
        selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onOptionClick(input) }
    )
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(selection)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The whole row handles the tap, so the control must not send it again.
        if (multiple) Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
        else RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Single-choice question", showBackground = true)
@Composable
private fun SingleQuestionPreview() = PreviewQuestion(FormPreviewData.singleChoice)

@Preview(name = "Multiple-choice question · dark", showBackground = true)
@Composable
private fun MultipleQuestionPreview() = PreviewQuestion(FormPreviewData.multipleChoice, darkTheme = true)

@Preview(name = "Disabled question", showBackground = true)
@Composable
private fun DisabledQuestionPreview() = PreviewQuestion(FormPreviewData.singleChoice, enabled = false)

@Composable
private fun PreviewQuestion(question: FormRowUiModel.Choice, enabled: Boolean = true, darkTheme: Boolean = false) {
    FormPreviewTheme(darkTheme = darkTheme) {
        Surface {
            Box(Modifier.width(320.dp).padding(16.dp)) { ChoiceQuestion(question, enabled, {}) }
        }
    }
}

@Preview(name = "Radio option", showBackground = true)
@Composable
private fun RadioOptionPreview() {
    FormPreviewTheme() {
        Surface {
            ChoiceOptionRow(
                label = "Equipment checks", input = ChoiceOptionUiInput(6, 101, 1),
                multiple = false, selected = true, enabled = true, onOptionClick = {}
            )
        }
    }
}

@Preview(name = "Checkbox option · large text", showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun CheckboxOptionPreview() {
    FormPreviewTheme(darkTheme = true) {
        Surface {
            ChoiceOptionRow(
                label = "Entrance and storage area", input = ChoiceOptionUiInput(6, 101, 1),
                multiple = true, selected = true, enabled = true, onOptionClick = {}
            )
        }
    }
}
