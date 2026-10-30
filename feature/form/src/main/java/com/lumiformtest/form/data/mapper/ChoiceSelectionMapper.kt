package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.local.StoredForm
import com.lumiformtest.form.data.local.entity.FormNodeType
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelection
import javax.inject.Inject

internal class ChoiceSelectionMapper @Inject constructor() {
    fun toEntity(option: ChoiceOption) = SelectedResponseEntity(option.questionId, option.responseSetId, option.optionId)

    fun toDomain(form: StoredForm): Map<Long, ChoiceSelection> {
        val nodes = form.nodes.associateBy { it.id }
        val sets = form.responseSets.associateBy { it.definition.id }
        return form.selections.groupBy { it.questionId }.mapValues { (questionId, answers) ->
            val question = nodes[questionId]
            val set = sets[question?.responseSetId]
            if (question?.type != FormNodeType.CHOICE || set == null || answers.any {
                it.responseSetId != set.definition.id || it.snapshotId != form.metadata.id ||
                    set.responses.none { option -> option.id == it.optionId }
            }) throw FormStorageException("Saved answers do not match the question definition")
            val ids = answers.map { it.optionId }.toSet()
            if (ids.size != answers.size || (!set.definition.multipleSelection && ids.size > 1)) {
                throw FormStorageException("Saved answers do not match the selection mode")
            }
            ChoiceSelection(set.definition.id, ids)
        }
    }
}
