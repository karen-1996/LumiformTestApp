package com.lumiformtest.form.data.local

import com.lumiformtest.form.data.local.entity.FormNodeType
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity

internal fun reconcileSelections(previous: List<SelectedResponseEntity>, form: StoredForm): List<SelectedResponseEntity> {
    val answers = previous.groupBy { it.questionId to it.responseSetId }
    val sets = form.responseSets.associateBy { it.definition.id }
    return form.nodes.filter { it.type == FormNodeType.CHOICE }.flatMap { question ->
        val set = sets[question.responseSetId] ?: return@flatMap emptyList()
        val selected = answers[question.id to question.responseSetId].orEmpty().map { it.optionId }.toSet()
        val surviving = set.responses.sortedBy { it.position }.filter { it.id in selected }
        // A mode change keeps the first surviving answer in the new display order.
        val kept = if (set.definition.multipleSelection) surviving else surviving.take(1)
        kept.map { SelectedResponseEntity(question.id, set.definition.id, it.id, form.metadata.id) }
    }
}
