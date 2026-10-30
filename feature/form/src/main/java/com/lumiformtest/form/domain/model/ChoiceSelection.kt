package com.lumiformtest.form.domain.model

internal data class ChoiceSelection(val responseSetId: Long, val optionIds: Set<Long>)

internal data class ChoiceOption(val questionId: Long, val responseSetId: Long, val optionId: Long)

internal sealed interface ChoiceSelectionResult {
    data object Success : ChoiceSelectionResult
    data class Failure(val reason: ChoiceSelectionFailure) : ChoiceSelectionResult
}

internal enum class ChoiceSelectionFailure { UNAVAILABLE, STORAGE }
