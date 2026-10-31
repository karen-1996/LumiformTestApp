package com.lumiformtest.form.data.local

import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
import com.lumiformtest.form.data.mapper.toChoiceDefinition
import com.lumiformtest.form.data.mapper.toEntities
import com.lumiformtest.form.domain.model.ChoiceSelection
import com.lumiformtest.form.domain.policy.reconcileSelection

internal fun reconcileSelections(previous: List<SelectedResponseEntity>, form: StoredForm): List<SelectedResponseEntity> {
    val answers = previous.groupBy { it.questionId to it.responseSetId }
    val sets = form.responseSets.associateBy { it.definition.id }
    return form.nodes.flatMap { question ->
        val definition = question.toChoiceDefinition(sets[question.responseSetId]) ?: return@flatMap emptyList()
        val previousIds = answers[question.id to definition.responseSetId].orEmpty().map { it.optionId }.toSet()
        definition.reconcileSelection(ChoiceSelection(definition.responseSetId, previousIds))
            ?.toEntities(question.id, form.metadata.id).orEmpty()
    }
}
