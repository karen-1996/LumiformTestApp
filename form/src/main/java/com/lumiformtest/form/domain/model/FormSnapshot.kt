package com.lumiformtest.form.domain.model

import java.time.Instant

internal data class FormSnapshot(
    val document: FormDocument,
    val lastUpdated: Instant,
    val selections: Map<Long, ChoiceSelection> = emptyMap()
)

internal sealed interface RefreshFormResult {
    data object Success : RefreshFormResult
    data class Failure(val reason: FormRefreshFailure) : RefreshFormResult
}

internal enum class FormRefreshFailure { FETCH, OFFLINE, INVALID_CONTENT, STORAGE }
