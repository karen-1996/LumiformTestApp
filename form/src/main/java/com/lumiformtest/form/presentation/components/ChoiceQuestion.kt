package com.lumiformtest.form.presentation.components

import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lumiformtest.design.components.ChoiceOptionChip
import com.lumiformtest.form.presentation.model.ChoiceOptionUiInput
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.preview.FormPreviewData
import com.lumiformtest.form.presentation.preview.FormPreviewTheme
import com.lumiformtest.resources.R
import java.text.NumberFormat

@Composable
internal fun ChoiceQuestion(
    question: FormRowUiModel.Choice,
    enabled: Boolean,
    onOptionClick: (ChoiceOptionUiInput) -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val multiple = question.responseSet.allowsMultipleSelection
    val locale = LocalConfiguration.current.locales[0]
    val scoreFormatter = remember(locale) { lazy { NumberFormat.getIntegerInstance(locale) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question.text, modifier = Modifier.padding(contentPadding), style = MaterialTheme.typography.bodyMedium)
        // Scope option IDs and saved scroll position to this response set.
        key(question.id, question.responseSet.id) {
            LazyRow(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxWidth().testTag("choice_options_${question.id}")
                    .then(if (multiple) Modifier else Modifier.selectableGroup()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = contentPadding,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(question.responseSet.options, key = { it.id }, contentType = { "choice_option" }) { option ->
                    val input = remember(question.id, question.responseSet.id, option.id) {
                        ChoiceOptionUiInput(question.id, question.responseSet.id, option.id)
                    }
                    val score = remember(option.score, scoreFormatter) { option.score?.let { scoreFormatter.value.format(it) } }
                    val onClick = remember(input, onOptionClick) { { onOptionClick(input) } }
                    ChoiceOptionChip(
                        label = option.label, multiple = multiple, selected = option.id in question.selectedOptionIds,
                        enabled = enabled, onClick = onClick, badge = score,
                        accessibilityLabel = score?.let { stringResource(R.string.choice_option_with_score, option.label, it) }
                    )
                }
            }
        }
        val labels = remember(question.responseSet.options, question.selectedOptionIds) {
            question.responseSet.options.filter { it.id in question.selectedOptionIds }.map { it.label }
        }
        val selection = if (labels.isEmpty()) stringResource(R.string.choice_none_selected) else {
            remember(labels, locale) { ListFormatter.getInstance(locale).format(labels) }
        }
        val type = stringResource(if (multiple) R.string.form_multiple_choice else R.string.form_single_choice)
        Text(
            stringResource(R.string.choice_selection_summary, selection, type),
            modifier = Modifier.padding(contentPadding),
            style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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

@Preview(name = "No answer selected", showBackground = true)
@Composable
private fun UnansweredQuestionPreview() = PreviewQuestion(FormPreviewData.singleChoice.copy(selectedOptionIds = emptySet()))

@Preview(name = "Edge-to-edge options · indented question", showBackground = true, widthDp = 320)
@Composable
private fun EdgeToEdgeQuestionPreview() {
    FormPreviewTheme {
        Surface {
            ChoiceQuestion(FormPreviewData.singleChoice, enabled = true, onOptionClick = {},
                contentPadding = PaddingValues(start = 56.dp, end = 20.dp))
        }
    }
}

@Preview(name = "Long labels · large text", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun LongLabelsQuestionPreview() = PreviewQuestion(FormPreviewData.singleChoice.copy(
    responseSet = FormPreviewData.singleChoice.responseSet.copy(options = FormPreviewData.singleChoice.responseSet.options.map {
        if (it.id == 1L) it.copy(label = "Entrance and storage area with a longer description") else it
    })
), darkTheme = true)
