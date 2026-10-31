package com.lumiformtest.form.domain.policy

import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelection

internal data class ChoiceDefinition(
    val questionId: Long,
    val responseSetId: Long,
    val allowsMultipleSelection: Boolean,
    val optionIds: List<Long>
)

internal fun ChoiceDefinition.nextSelection(option: ChoiceOption, selected: Set<Long>): ChoiceSelection? {
    if (option.questionId != questionId || option.responseSetId != responseSetId || option.optionId !in optionIds) return null
    val next = when {
        !allowsMultipleSelection -> setOf(option.optionId)
        option.optionId in selected -> selected - option.optionId
        else -> selected + option.optionId
    }
    return ChoiceSelection(responseSetId, next)
}

internal fun ChoiceDefinition.reconcileSelection(previous: ChoiceSelection?): ChoiceSelection? {
    if (previous == null || previous.responseSetId != responseSetId) return null
    val surviving = optionIds.filter { it in previous.optionIds }
    // A mode change keeps the first surviving answer in the new display order.
    val kept = if (allowsMultipleSelection) surviving else surviving.take(1)
    return kept.takeIf { it.isNotEmpty() }?.let { ChoiceSelection(responseSetId, it.toSet()) }
}
