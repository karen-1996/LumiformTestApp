package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.StoredForm
import com.lumiformtest.form.data.local.StoredFormAnswers
import com.lumiformtest.form.data.local.StoredFormDefinition
import com.lumiformtest.form.domain.model.FormSnapshot

internal data class MappedFormDefinition(
    val snapshot: FormSnapshot,
    val choices: Map<Long, ChoiceReadDefinition>,
    val snapshotId: Int
)

internal fun StoredFormDefinition.toMappedDefinition(): MappedFormDefinition {
    val stored = StoredForm(metadata, nodes, responseSets)
    return MappedFormDefinition(stored.toDomain(), stored.choiceReadIndex(), metadata.id)
}

internal fun MappedFormDefinition.withAnswers(answers: StoredFormAnswers): FormSnapshot =
    snapshot.copy(selections = answers.selections.toSelections(choices, snapshotId))
