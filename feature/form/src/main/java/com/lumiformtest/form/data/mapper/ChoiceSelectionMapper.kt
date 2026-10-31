package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.local.StoredForm
import com.lumiformtest.form.data.local.entity.FormNodeType
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelection

internal fun ChoiceOption.toEntity() = SelectedResponseEntity(questionId, responseSetId, optionId)
internal fun SelectedResponseEntity.toDomain(): ChoiceOption = ChoiceOption(questionId, responseSetId, optionId)

internal data class ChoiceReadDefinition(val responseSetId: Long, val multiple: Boolean, val optionIds: Set<Long>)

internal fun StoredForm.choiceReadIndex(): Map<Long, ChoiceReadDefinition> {
    val sets = responseSets.associateBy { it.definition.id }
    return nodes.filter { it.type == FormNodeType.CHOICE }.associate { question ->
        val set = sets[question.responseSetId] ?: throw FormStorageException("Missing saved response set")
        question.id to ChoiceReadDefinition(set.definition.id, set.definition.multipleSelection, set.responses.map { it.id }.toSet())
    }
}

internal fun List<SelectedResponseEntity>.toSelections(
    definitions: Map<Long, ChoiceReadDefinition>, snapshotId: Int
): Map<Long, ChoiceSelection> = groupBy { it.questionId }.mapValues { (questionId, answers) ->
    val definition = definitions[questionId]
    if (definition == null || answers.any {
        it.responseSetId != definition.responseSetId || it.snapshotId != snapshotId || it.optionId !in definition.optionIds
    }) throw FormStorageException("Saved answers do not match the question definition")
    val ids = answers.map { it.optionId }.toSet()
    if (ids.size != answers.size || (!definition.multiple && ids.size > 1)) {
        throw FormStorageException("Saved answers do not match the selection mode")
    }
    ChoiceSelection(definition.responseSetId, ids)
}

internal fun StoredForm.toSelections(): Map<Long, ChoiceSelection> =
    if (selections.isEmpty()) emptyMap() else selections.toSelections(choiceReadIndex(), metadata.id)
