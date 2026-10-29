package com.lumiformtest.form.domain.repository

import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import kotlinx.coroutines.flow.Flow

internal interface FormRepository {
    fun observeForm(): Flow<FormObservation>
    suspend fun refreshForm(): RefreshFormResult
    suspend fun toggleChoiceOption(option: ChoiceOption): ChoiceSelectionResult
}
